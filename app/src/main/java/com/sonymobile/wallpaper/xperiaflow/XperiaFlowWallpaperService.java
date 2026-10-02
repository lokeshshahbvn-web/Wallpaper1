package com.sonymobile.wallpaper.xperiaflow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sony Xperia Cosmic Flow Live Wallpaper Service
 * Multi-layer gradient ribbon engine with quadratic Bezier curves,
 * specular crest highlights, radial atmospheric lighting, and touch shockwaves.
 */
public class XperiaFlowWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new FlowEngine();
    }

    private class FlowEngine extends Engine {
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRibbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mCrestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path mRibbonPath = new Path();
        private final Path mCrestPath = new Path();

        private boolean mVisible = false;
        private float mScrollOffset = 0.5f;

        private float mTouchX = -1000f, mTouchY = -1000f;
        private float mTouchIntensity = 0f;
        private boolean mTouchActive = false;

        private final List<Shockwave> mShockwaves = new ArrayList<>();
        private long mStartTime;
        private long mLastTime;

        private final float mSpeed = 0.35f;
        private final float mTouchSens = 1.2f;
        private final float mWaveAmp = 1f;

        // Theme colors
        private final int mBgTop = Color.parseColor("#4a3c36");
        private final int mBgCenter = Color.parseColor("#2b211d");
        private final int mBgBottom = Color.parseColor("#130d0b");
        private final int mRibbon1 = Color.parseColor("#c7a791");
        private final int mRibbon2 = Color.parseColor("#9e7d69");
        private final int mRibbon3 = Color.parseColor("#5c4338");
        private final int mRimLight = Color.parseColor("#f4e4d7");

        private static class Shockwave {
            float x, y, radius, strength;
            Shockwave(float x, float y, float strength) {
                this.x = x;
                this.y = y;
                this.radius = 12f;
                this.strength = strength;
            }
        }

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
            mLastTime = mStartTime;
            setTouchEventsEnabled(true);

            mRibbonPaint.setStyle(Paint.Style.FILL);
            mCrestPaint.setStyle(Paint.Style.STROKE);
            mCrestPaint.setStrokeCap(Paint.Cap.ROUND);
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
                mLastTime = SystemClock.elapsedRealtime();
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
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchX = x;
                    mTouchY = y;
                    mTouchIntensity = 1.0f;
                    mTouchActive = true;
                    if (mShockwaves.size() < 6) {
                        mShockwaves.add(new Shockwave(x, y, 1.0f * mTouchSens));
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    mTouchX = x;
                    mTouchY = y;
                    mTouchActive = true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mTouchActive = false;
                    break;
            }
            super.onTouchEvent(event);
        }

        private void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    renderWallpaper(canvas);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
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
            long now = SystemClock.elapsedRealtime();
            float dt = Math.min((now - mLastTime) / 1000f, 0.1f);
            mLastTime = now;

            float time = (now - mStartTime) * (0.00038f * mSpeed);

            // Update touch relaxation
            if (!mTouchActive && mTouchIntensity > 0.01f) {
                mTouchIntensity *= (1.0f - dt * 4.5f);
            }

            // Update shockwaves
            Iterator<Shockwave> it = mShockwaves.iterator();
            while (it.hasNext()) {
                Shockwave sw = it.next();
                sw.radius += dt * 420f;
                sw.strength *= (1.0f - dt * 2.2f);
                if (sw.radius > Math.max(w, h) * 0.9f || sw.strength < 0.02f) {
                    it.remove();
                }
            }

            // 1. Draw 3-stop ambient background gradient
            mBgPaint.setShader(new LinearGradient(
                0, 0, w * 0.40f, h,
                new int[] { mBgTop, mBgCenter, mBgBottom },
                new float[] { 0.0f, 0.48f, 1.0f },
                Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, mBgPaint);

            // 2. Draw atmospheric upper radial glow
            int glowColor = Color.argb(110, Color.red(mRibbon1), Color.green(mRibbon1), Color.blue(mRibbon1));
            mGlowPaint.setShader(new RadialGradient(
                w * 0.35f, h * 0.32f, Math.max(w, h) * 0.75f,
                new int[] { glowColor, Color.TRANSPARENT },
                null,
                Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, mGlowPaint);

            // 3. Render 3 depth layers back-to-front
            renderRibbonLayer(canvas, 2, 3, time * 0.75f, mScrollOffset, w, h);
            renderRibbonLayer(canvas, 1, 3, time * 0.90f, mScrollOffset, w, h);
            renderRibbonLayer(canvas, 0, 3, time * 1.05f, mScrollOffset, w, h);
        }

        private void renderRibbonLayer(Canvas canvas, int layerIndex, int totalLayers, float time, float scrollShift, int w, int h) {
            int pts = 36;
            float step = (float) w / (pts - 1);
            float depthFactor = 0.65f + (layerIndex / (float) totalLayers) * 0.45f;
            float layerOffset = (layerIndex - 1.2f) * 38f;
            float parallax = (scrollShift - 0.5f) * 160f * depthFactor;

            float[] px = new float[pts];
            float[] py = new float[pts];

            for (int i = 0; i < pts; i++) {
                float x = i * step;
                float normX = (x - parallax) / w;

                // Xperia sweeping diagonal spline
                float diagonal = (1.0f - (float) Math.pow(Math.max(0, Math.min(1.05f, normX)), 1.35f)) * 0.48f;
                float wave1 = (float) Math.sin(normX * 3.2f - time * 0.8f + layerIndex * 0.9f) * (42f * mWaveAmp);
                float wave2 = (float) Math.cos(normX * 5.4f + time * 0.45f - layerIndex * 0.6f) * (18f * mWaveAmp);
                float harmonic = (wave1 + wave2) * depthFactor;

                float y = h * (0.54f - diagonal) + layerOffset + harmonic;

                // Interactive touch deflection
                if (mTouchIntensity > 0.01f) {
                    float dx = x - mTouchX;
                    float dy = y - mTouchY;
                    float dist = (float) Math.hypot(dx, dy);
                    float maxRadius = 240f * mTouchSens;
                    if (dist < maxRadius) {
                        float force = (1.0f - dist / maxRadius) * 65f * mTouchSens * mTouchIntensity;
                        y += (float) Math.sin((dist / maxRadius) * Math.PI) * force;
                    }
                }

                // Hydrodynamic shockwaves
                for (Shockwave sw : mShockwaves) {
                    float dx = x - sw.x;
                    float dy = y - sw.y;
                    float dist = (float) Math.hypot(dx, dy);
                    float ringDiff = Math.abs(dist - sw.radius);
                    if (ringDiff < 70f) {
                        float shockForce = (1.0f - ringDiff / 70f) * sw.strength * 28f;
                        y += (float) Math.sin((ringDiff / 70f) * Math.PI) * shockForce;
                    }
                }

                px[i] = x;
                py[i] = y;
            }

            // Build smooth quadratic Bezier ribbon body
            mRibbonPath.reset();
            mRibbonPath.moveTo(0, h);
            mRibbonPath.lineTo(px[0], py[0]);

            mCrestPath.reset();
            mCrestPath.moveTo(px[0], py[0]);

            for (int i = 1; i < pts; i++) {
                float prevX = px[i - 1];
                float prevY = py[i - 1];
                float midX = (prevX + px[i]) / 2f;
                float midY = (prevY + py[i]) / 2f;
                mRibbonPath.quadTo(prevX, prevY, midX, midY);
                mCrestPath.quadTo(prevX, prevY, midX, midY);
            }
            mRibbonPath.lineTo(px[pts - 1], py[pts - 1]);
            mCrestPath.lineTo(px[pts - 1], py[pts - 1]);

            mRibbonPath.lineTo(w, h);
            mRibbonPath.close();

            // Distinct multi-stop linear gradient per depth layer
            LinearGradient ribbonShader;
            if (layerIndex == 0) {
                ribbonShader = new LinearGradient(
                    0, h * 0.25f, w * 0.5f, h,
                    new int[] { mRibbon1, mRibbon2, mBgBottom },
                    new float[] { 0.0f, 0.65f, 1.0f },
                    Shader.TileMode.CLAMP
                );
                mRibbonPaint.setAlpha(245);
            } else if (layerIndex == 1) {
                ribbonShader = new LinearGradient(
                    0, h * 0.25f, w * 0.5f, h,
                    new int[] { mRibbon2, mRibbon3, mBgBottom },
                    new float[] { 0.0f, 0.50f, 1.0f },
                    Shader.TileMode.CLAMP
                );
                mRibbonPaint.setAlpha(215);
            } else {
                ribbonShader = new LinearGradient(
                    0, h * 0.25f, w * 0.5f, h,
                    new int[] { mRibbon3, mBgBottom },
                    new float[] { 0.0f, 1.0f },
                    Shader.TileMode.CLAMP
                );
                mRibbonPaint.setAlpha(180);
            }

            mRibbonPaint.setShader(ribbonShader);
            canvas.drawPath(mRibbonPath, mRibbonPaint);

            // Specular illuminated crest line on the upper edge
            if (layerIndex == 0) {
                mCrestPaint.setColor(mRimLight);
                mCrestPaint.setStrokeWidth(3.2f);
                mCrestPaint.setAlpha(235);
            } else if (layerIndex == 1) {
                mCrestPaint.setColor(mRimLight);
                mCrestPaint.setStrokeWidth(2.0f);
                mCrestPaint.setAlpha(165);
            } else {
                mCrestPaint.setColor(mRibbon1);
                mCrestPaint.setStrokeWidth(1.2f);
                mCrestPaint.setAlpha(115);
            }
            canvas.drawPath(mCrestPath, mCrestPaint);
        }
    }
}
