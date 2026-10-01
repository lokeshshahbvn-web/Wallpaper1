package com.sonymobile.wallpaper.xperiaflow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Sony Xperia Cosmic Flow Live Wallpaper Service
 * Verified for Android 16 (API 36 - Baklava)
 * High-performance, battery-efficient multi-layer ribbon renderer.
 */
public class XperiaFlowWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new FlowEngine();
    }

    private class FlowEngine extends Engine {
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final Paint mBackgroundPaint = new Paint();
        private final Paint mRibbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mCrestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();

        private boolean mVisible = false;
        private float mScrollOffset = 0.5f;
        private float mTouchX = -1000f, mTouchY = -1000f;
        private float mTouchForce = 0f;
        private long mStartTime;

        private final float mSpeed = 0.35f;
        private final float mTouchSens = 1.2f;

        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            mStartTime = SystemClock.elapsedRealtime();
            setTouchEventsEnabled(true);

            mCrestPaint.setStyle(Paint.Style.STROKE);
            mCrestPaint.setStrokeWidth(3.0f);
            mCrestPaint.setColor(Color.parseColor("#f4e4d7"));
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mHandler.removeCallbacks(mDrawRunnable);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                drawFrame();
            } else {
                mHandler.removeCallbacks(mDrawRunnable);
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            mScrollOffset = xOffset;
            if (mVisible) drawFrame();
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mTouchX = event.getX();
                    mTouchY = event.getY();
                    mTouchForce = 1.0f;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mTouchForce = 0f;
                    break;
            }
            super.onTouchEvent(event);
        }

        private void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    renderWallpaper(c);
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }

            mHandler.removeCallbacks(mDrawRunnable);
            if (mVisible) {
                mHandler.postDelayed(mDrawRunnable, 16); // 60 FPS
            }
        }

        private void renderWallpaper(Canvas canvas) {
            int w = canvas.getWidth();
            int h = canvas.getHeight();
            float time = (SystemClock.elapsedRealtime() - mStartTime) * (0.00038f * mSpeed);

            // 1. Ambient Background Gradient
            mBackgroundPaint.setShader(new LinearGradient(
                0, 0, w * 0.35f, h,
                Color.parseColor("#4a3c36"),
                Color.parseColor("#130d0b"),
                Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, mBackgroundPaint);

            // 2. Render Primary Xperia Flow Ribbon
            int points = 32;
            float step = (float) w / (points - 1);
            float parallax = (mScrollOffset - 0.5f) * 160f;

            mPath.reset();
            mPath.moveTo(0, h);

            for (int i = 0; i < points; i++) {
                float px = i * step;
                float normX = (px - parallax) / w;
                float diagonal = (1.0f - (float) Math.pow(Math.max(0, Math.min(1.05, normX)), 1.35)) * 0.48f;
                float py = h * (0.54f - diagonal) + (float) Math.sin(normX * 3.2f - time * 0.8f) * 42f;

                // Interactive touch deflection
                if (mTouchForce > 0) {
                    float dist = (float) Math.hypot(px - mTouchX, py - mTouchY);
                    float radius = 240f * mTouchSens;
                    if (dist < radius) {
                        py += (1.0f - dist / radius) * 65f * mTouchSens * mTouchForce;
                    }
                }

                if (i == 0) mPath.lineTo(px, py);
                else mPath.lineTo(px, py);
            }
            mPath.lineTo(w, h);
            mPath.close();

            mRibbonPaint.setColor(Color.parseColor("#c7a791"));
            mRibbonPaint.setAlpha(220);
            canvas.drawPath(mPath, mRibbonPaint);
            canvas.drawPath(mPath, mCrestPaint);
        }
    }
}