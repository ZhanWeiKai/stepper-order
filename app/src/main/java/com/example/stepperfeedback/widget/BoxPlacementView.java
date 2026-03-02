package com.example.stepperfeedback.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class BoxPlacementView extends View {

    // Colors - Yellow cardboard box style
    private static final int COLOR_BOX_TOP = Color.parseColor("#F5D547");      // Light yellow (top)
    private static final int COLOR_BOX_FRONT = Color.parseColor("#E5B91B");    // Medium yellow (front)
    private static final int COLOR_BOX_SIDE = Color.parseColor("#C9A018");     // Dark yellow (side)
    private static final int COLOR_BOX_TAPE = Color.parseColor("#8B7355");     // Brown tape
    private static final int COLOR_SURFACE_TOP = Color.parseColor("#E0E0E0");  // Gray surface top
    private static final int COLOR_SURFACE_BOTTOM = Color.parseColor("#BDBDBD"); // Gray surface bottom
    private static final int COLOR_SHADOW = Color.parseColor("#30000000");
    private static final int COLOR_TEXT = Color.parseColor("#424242");

    // Dimensions
    private int boxSize = 100;
    private int surfaceHeight = 80;

    // Animation state
    private float boxY;
    private float boxAlpha = 0f;
    private float shadowAlpha = 0f;

    // Positions
    private float boxStartY;
    private float boxEndY;
    private float centerX;
    private float centerY;
    private float platformY;

    // Paints
    private Paint boxTopPaint;
    private Paint boxFrontPaint;
    private Paint boxSidePaint;
    private Paint boxTapePaint;
    private Paint surfacePaint;
    private Paint shadowPaint;
    private Paint textPaint;

    // Animation
    private ValueAnimator placeAnimator;
    private ValueAnimator fadeOutAnimator;
    private ValueAnimator fadeInAnimator;
    private boolean isAnimating = false;

    public BoxPlacementView(Context context) {
        this(context, null);
    }

    public BoxPlacementView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoxPlacementView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Box paints - yellow cardboard style
        boxTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxTopPaint.setColor(COLOR_BOX_TOP);

        boxFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxFrontPaint.setColor(COLOR_BOX_FRONT);

        boxSidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxSidePaint.setColor(COLOR_BOX_SIDE);

        boxTapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxTapePaint.setColor(COLOR_BOX_TAPE);

        // Surface paint (gradient set in onSizeChanged)
        surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Shadow paint
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(COLOR_SHADOW);

        // Text paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(42);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        centerX = w / 2f;
        centerY = h / 2f;

        // Surface position (lower part of view)
        platformY = h * 0.55f;

        // Box start and end positions
        boxStartY = centerY - boxSize * 2f;
        boxEndY = platformY - boxSize * 0.3f;

        // Set surface gradient
        LinearGradient surfaceGradient = new LinearGradient(
                0, platformY, 0, platformY + surfaceHeight,
                COLOR_SURFACE_TOP, COLOR_SURFACE_BOTTOM, Shader.TileMode.CLAMP
        );
        surfacePaint.setShader(surfaceGradient);

        // Start animation
        startAnimationLoop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw platform first (bottom layer)
        drawPlatform(canvas);

        // Draw shadow (if visible)
        if (shadowAlpha > 0) {
            drawShadow(canvas);
        }

        // Draw box (if visible)
        if (boxAlpha > 0) {
            drawBox(canvas);
        }

        // Draw instruction text
        drawText(canvas);
    }

    private void drawPlatform(Canvas canvas) {
        // Trapezoid surface with perspective effect (gray gradient)
        Path surfacePath = new Path();
        float perspectiveOffset = 40;
        int width = getWidth();

        surfacePath.moveTo(perspectiveOffset, platformY);
        surfacePath.lineTo(width - perspectiveOffset, platformY);
        surfacePath.lineTo(width - perspectiveOffset * 0.5f, platformY + surfaceHeight);
        surfacePath.lineTo(perspectiveOffset * 0.5f, platformY + surfaceHeight);
        surfacePath.close();

        canvas.drawPath(surfacePath, surfacePaint);
    }

    private void drawShadow(Canvas canvas) {
        shadowPaint.setAlpha((int) (shadowAlpha * 0x30));

        float shadowWidth = boxSize * 0.7f;
        float shadowHeight = boxSize * 0.15f;
        float shadowLeft = centerX - shadowWidth / 2f;
        float shadowTop = platformY - shadowHeight / 2f + 5;

        RectF shadowRect = new RectF(shadowLeft, shadowTop, shadowLeft + shadowWidth, shadowTop + shadowHeight);
        canvas.drawOval(shadowRect, shadowPaint);
    }

    private void drawBox(Canvas canvas) {
        boxTopPaint.setAlpha((int) (boxAlpha * 255));
        boxFrontPaint.setAlpha((int) (boxAlpha * 255));
        boxSidePaint.setAlpha((int) (boxAlpha * 255));
        boxTapePaint.setAlpha((int) (boxAlpha * 255));

        float perspectiveOffset = boxSize * 0.12f;
        float boxLeft = centerX - boxSize / 2f;
        float boxRight = centerX + boxSize / 2f;
        float boxTop = boxY;
        float boxBottom = boxY + boxSize;

        // Box top surface
        Path topPath = new Path();
        topPath.moveTo(boxLeft, boxTop);
        topPath.lineTo(boxRight, boxTop);
        topPath.lineTo(boxRight + perspectiveOffset, boxTop + perspectiveOffset);
        topPath.lineTo(boxLeft + perspectiveOffset, boxTop + perspectiveOffset);
        topPath.close();
        canvas.drawPath(topPath, boxTopPaint);

        // Box front surface
        Path frontPath = new Path();
        frontPath.moveTo(boxLeft + perspectiveOffset, boxTop + perspectiveOffset);
        frontPath.lineTo(boxRight + perspectiveOffset, boxTop + perspectiveOffset);
        frontPath.lineTo(boxRight + perspectiveOffset, boxBottom + perspectiveOffset);
        frontPath.lineTo(boxLeft + perspectiveOffset, boxBottom + perspectiveOffset);
        frontPath.close();
        canvas.drawPath(frontPath, boxFrontPaint);

        // Box right side surface
        Path sidePath = new Path();
        sidePath.moveTo(boxRight, boxTop);
        sidePath.lineTo(boxRight + perspectiveOffset, boxTop + perspectiveOffset);
        sidePath.lineTo(boxRight + perspectiveOffset, boxBottom + perspectiveOffset);
        sidePath.lineTo(boxRight, boxBottom);
        sidePath.close();
        canvas.drawPath(sidePath, boxSidePaint);

        // Draw tape on box (horizontal line in middle)
        float tapeY = boxTop + perspectiveOffset + boxSize * 0.4f;
        canvas.drawRect(
                boxLeft + perspectiveOffset,
                tapeY,
                boxRight + perspectiveOffset,
                tapeY + 12,
                boxTapePaint
        );
    }

    private void drawText(Canvas canvas) {
        String text = "请将箱子放置到指定位置";
        float textY = platformY + 120;
        textPaint.setAlpha(255);
        canvas.drawText(text, centerX, textY, textPaint);
    }

    public void startAnimationLoop() {
        if (isAnimating) return;
        isAnimating = true;
        runAnimationCycle();
    }

    public void stopAnimation() {
        isAnimating = false;
        if (placeAnimator != null) placeAnimator.cancel();
        if (fadeOutAnimator != null) fadeOutAnimator.cancel();
        if (fadeInAnimator != null) fadeInAnimator.cancel();
    }

    private void runAnimationCycle() {
        if (!isAnimating) return;

        // Reset state
        boxY = boxStartY;
        boxAlpha = 0f;
        shadowAlpha = 0f;

        // Phase 1: Fade in (200ms)
        fadeInAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeInAnimator.setDuration(200);
        fadeInAnimator.addUpdateListener(animation -> {
            boxAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        fadeInAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isAnimating) return;
                startPlaceAnimation();
            }
        });
        fadeInAnimator.start();
    }

    private void startPlaceAnimation() {
        // Phase 2: Place down with DECELERATION (human placement feel)
        placeAnimator = ValueAnimator.ofFloat(0f, 1f);
        placeAnimator.setDuration(800);
        placeAnimator.setInterpolator(new DecelerateInterpolator(1.5f)); // Slow down at end
        placeAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            boxY = boxStartY + (boxEndY - boxStartY) * progress;
            boxAlpha = 1f;
            // Shadow appears as box gets closer to platform
            shadowAlpha = progress;
            invalidate();
        });
        placeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isAnimating) return;
                // Hold then fade out
                postDelayed(() -> startFadeOutAnimation(), 1500);
            }
        });
        placeAnimator.start();
    }

    private void startFadeOutAnimation() {
        if (!isAnimating) return;

        // Phase 3: Fade out (400ms)
        fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
        fadeOutAnimator.setDuration(400);
        fadeOutAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            boxAlpha = progress;
            shadowAlpha = progress;
            invalidate();
        });
        fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isAnimating) return;
                // Restart cycle after short delay
                postDelayed(() -> runAnimationCycle(), 300);
            }
        });
        fadeOutAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
}
