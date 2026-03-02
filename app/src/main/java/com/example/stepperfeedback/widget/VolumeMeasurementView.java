package com.example.stepperfeedback.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class VolumeMeasurementView extends View {

    // Colors
    private static final int COLOR_BOX_TOP = Color.parseColor("#F5D547");
    private static final int COLOR_BOX_FRONT = Color.parseColor("#E5B91B");
    private static final int COLOR_BOX_SIDE = Color.parseColor("#C9A018");
    private static final int COLOR_BOX_TAPE = Color.parseColor("#8B7355");
    private static final int COLOR_SCAN_LINE = Color.parseColor("#00BCD4");
    private static final int COLOR_SCAN_GLOW = Color.parseColor("#4000BCD4");
    private static final int COLOR_DIMENSION_LINE = Color.parseColor("#2196F3");
    private static final int COLOR_DIMENSION_TEXT = Color.parseColor("#1565C0");
    private static final int COLOR_RESULT_BG = Color.parseColor("#F5F5F5");
    private static final int COLOR_RESULT_TEXT = Color.parseColor("#424242");
    private static final int COLOR_PRICE = Color.parseColor("#4CAF50");

    // Box dimensions (fixed values)
    private static final int BOX_WIDTH = 30;  // cm
    private static final int BOX_HEIGHT = 25; // cm
    private static final int BOX_DEPTH = 20;  // cm
    private static final int BOX_VOLUME = BOX_WIDTH * BOX_HEIGHT * BOX_DEPTH; // cm³
    private static final String BOX_PRICE = "¥25.00";

    // Box drawing size
    private int boxSize = 120;
    private float perspectiveOffset;

    // Animation state
    private float scanLineY;
    private boolean isMeasuring = true;
    private boolean showResults = false;
    private float resultAlpha = 0f;

    // Positions
    private float centerX;
    private float centerY;
    private float boxLeft, boxRight, boxTop, boxBottom;

    // Paints
    private Paint boxTopPaint;
    private Paint boxFrontPaint;
    private Paint boxSidePaint;
    private Paint boxTapePaint;
    private Paint scanLinePaint;
    private Paint scanGlowPaint;
    private Paint dimensionLinePaint;
    private Paint dimensionTextPaint;
    private Paint resultBgPaint;
    private Paint resultTextPaint;
    private Paint priceTextPaint;
    private Paint measuringTextPaint;

    // Animation
    private ValueAnimator scanAnimator;
    private ValueAnimator fadeOutAnimator;
    private ValueAnimator fadeInResultAnimator;
    private boolean isAnimating = false;

    public VolumeMeasurementView(Context context) {
        this(context, null);
    }

    public VolumeMeasurementView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VolumeMeasurementView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boxTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxTopPaint.setColor(COLOR_BOX_TOP);

        boxFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxFrontPaint.setColor(COLOR_BOX_FRONT);

        boxSidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxSidePaint.setColor(COLOR_BOX_SIDE);

        boxTapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxTapePaint.setColor(COLOR_BOX_TAPE);

        scanLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanLinePaint.setColor(COLOR_SCAN_LINE);
        scanLinePaint.setStrokeWidth(4);
        scanLinePaint.setStyle(Paint.Style.STROKE);
        scanLinePaint.setPathEffect(new DashPathEffect(new float[]{15, 8}, 0));

        scanGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanGlowPaint.setColor(COLOR_SCAN_GLOW);
        scanGlowPaint.setStrokeWidth(20);

        dimensionLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionLinePaint.setColor(COLOR_DIMENSION_LINE);
        dimensionLinePaint.setStrokeWidth(2);
        dimensionLinePaint.setStyle(Paint.Style.STROKE);

        dimensionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionTextPaint.setColor(COLOR_DIMENSION_TEXT);
        dimensionTextPaint.setTextSize(36);
        dimensionTextPaint.setTextAlign(Paint.Align.CENTER);

        resultBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        resultBgPaint.setColor(COLOR_RESULT_BG);
        resultBgPaint.setStyle(Paint.Style.FILL);
        resultBgPaint.setShadowLayer(8, 0, 4, Color.parseColor("#20000000"));

        resultTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        resultTextPaint.setColor(COLOR_RESULT_TEXT);
        resultTextPaint.setTextSize(40);
        resultTextPaint.setTextAlign(Paint.Align.CENTER);

        priceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        priceTextPaint.setColor(COLOR_PRICE);
        priceTextPaint.setTextSize(48);
        priceTextPaint.setTextAlign(Paint.Align.CENTER);
        priceTextPaint.setFakeBoldText(true);

        measuringTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        measuringTextPaint.setColor(COLOR_DIMENSION_TEXT);
        measuringTextPaint.setTextSize(42);
        measuringTextPaint.setTextAlign(Paint.Align.CENTER);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // For shadow
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        centerX = w / 2f;
        centerY = h / 2f - 40;

        perspectiveOffset = boxSize * 0.15f;
        boxLeft = centerX - boxSize / 2f;
        boxRight = centerX + boxSize / 2f;
        boxTop = centerY - boxSize / 2f;
        boxBottom = centerY + boxSize / 2f;

        scanLineY = boxTop - 60;

        startMeasurementAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw box
        drawBox(canvas);

        if (isMeasuring) {
            // Draw scan line
            drawScanLine(canvas);
            // Draw measuring text
            drawMeasuringText(canvas);
        }

        if (showResults) {
            // Draw dimension lines and labels
            drawDimensions(canvas);
            // Draw result card
            drawResultCard(canvas);
        }
    }

    private void drawBox(Canvas canvas) {
        float p = perspectiveOffset;

        // Box top surface
        Path topPath = new Path();
        topPath.moveTo(boxLeft, boxTop);
        topPath.lineTo(boxRight, boxTop);
        topPath.lineTo(boxRight + p, boxTop + p);
        topPath.lineTo(boxLeft + p, boxTop + p);
        topPath.close();
        canvas.drawPath(topPath, boxTopPaint);

        // Box front surface
        Path frontPath = new Path();
        frontPath.moveTo(boxLeft + p, boxTop + p);
        frontPath.lineTo(boxRight + p, boxTop + p);
        frontPath.lineTo(boxRight + p, boxBottom + p);
        frontPath.lineTo(boxLeft + p, boxBottom + p);
        frontPath.close();
        canvas.drawPath(frontPath, boxFrontPaint);

        // Box right side surface
        Path sidePath = new Path();
        sidePath.moveTo(boxRight, boxTop);
        sidePath.lineTo(boxRight + p, boxTop + p);
        sidePath.lineTo(boxRight + p, boxBottom + p);
        sidePath.lineTo(boxRight, boxBottom);
        sidePath.close();
        canvas.drawPath(sidePath, boxSidePaint);

        // Tape on box
        float tapeY = boxTop + p + boxSize * 0.35f;
        canvas.drawRect(boxLeft + p, tapeY, boxRight + p, tapeY + 10, boxTapePaint);
    }

    private void drawScanLine(Canvas canvas) {
        // Draw glow effect
        canvas.drawLine(boxLeft - 40, scanLineY, boxRight + perspectiveOffset + 40, scanLineY, scanGlowPaint);
        // Draw scan line
        canvas.drawLine(boxLeft - 40, scanLineY, boxRight + perspectiveOffset + 40, scanLineY, scanLinePaint);
    }

    private void drawMeasuringText(Canvas canvas) {
        String text = "Measuring...";
        canvas.drawText(text, centerX, boxBottom + 100, measuringTextPaint);
    }

    private void drawDimensions(Canvas canvas) {
        float alpha = resultAlpha;
        dimensionLinePaint.setAlpha((int) (alpha * 255));
        dimensionTextPaint.setAlpha((int) (alpha * 255));

        float p = perspectiveOffset;

        // Height dimension (left side, vertical)
        float heightX = boxLeft - 50;
        canvas.drawLine(heightX, boxTop + p, heightX, boxBottom + p, dimensionLinePaint);
        // Arrows
        canvas.drawLine(heightX - 10, boxTop + p + 10, heightX, boxTop + p, dimensionLinePaint);
        canvas.drawLine(heightX + 10, boxTop + p + 10, heightX, boxTop + p, dimensionLinePaint);
        canvas.drawLine(heightX - 10, boxBottom + p - 10, heightX, boxBottom + p, dimensionLinePaint);
        canvas.drawLine(heightX + 10, boxBottom + p - 10, heightX, boxBottom + p, dimensionLinePaint);
        // Label
        canvas.save();
        canvas.rotate(-90, heightX - 25, (boxTop + boxBottom) / 2 + p);
        canvas.drawText(BOX_HEIGHT + " cm", heightX - 25, (boxTop + boxBottom) / 2 + p, dimensionTextPaint);
        canvas.restore();

        // Width dimension (top, horizontal)
        float widthY = boxTop - 30;
        canvas.drawLine(boxLeft, widthY, boxRight, widthY, dimensionLinePaint);
        canvas.drawLine(boxLeft + 10, widthY - 10, boxLeft, widthY, dimensionLinePaint);
        canvas.drawLine(boxLeft + 10, widthY + 10, boxLeft, widthY, dimensionLinePaint);
        canvas.drawLine(boxRight - 10, widthY - 10, boxRight, widthY, dimensionLinePaint);
        canvas.drawLine(boxRight - 10, widthY + 10, boxRight, widthY, dimensionLinePaint);
        canvas.drawText(BOX_WIDTH + " cm", centerX, widthY - 15, dimensionTextPaint);

        // Depth dimension (right side, diagonal)
        float depthStartX = boxRight + p + 20;
        float depthStartY = boxBottom + p + 20;
        float depthEndX = boxRight + p + 50;
        float depthEndY = boxBottom + p + 50;
        canvas.drawLine(depthStartX, depthStartY, depthEndX, depthEndY, dimensionLinePaint);
        // Label
        canvas.drawText(BOX_DEPTH + " cm", depthEndX + 50, depthEndY, dimensionTextPaint);
    }

    private void drawResultCard(Canvas canvas) {
        resultBgPaint.setAlpha((int) (resultAlpha * 255));
        resultTextPaint.setAlpha((int) (resultAlpha * 255));
        priceTextPaint.setAlpha((int) (resultAlpha * 255));

        float cardLeft = centerX - 180;
        float cardTop = boxBottom + 130;
        float cardRight = centerX + 180;
        float cardBottom = cardTop + 130;

        // Draw card background
        RectF cardRect = new RectF(cardLeft, cardTop, cardRight, cardBottom);
        canvas.drawRoundRect(cardRect, 16, 16, resultBgPaint);

        // Volume text
        String volumeText = "Volume: " + String.format("%,d", BOX_VOLUME) + " cm³";
        canvas.drawText(volumeText, centerX, cardTop + 50, resultTextPaint);

        // Price text
        canvas.drawText(BOX_PRICE, centerX, cardTop + 105, priceTextPaint);
    }

    private void startMeasurementAnimation() {
        if (isAnimating) return;
        isAnimating = true;

        // Scan line animation (loop for 3 seconds)
        scanAnimator = ValueAnimator.ofFloat(boxTop - 80, boxBottom + perspectiveOffset + 40);
        scanAnimator.setDuration(1500);
        scanAnimator.setRepeatCount(1); // 2 cycles = 3 seconds
        scanAnimator.addUpdateListener(animation -> {
            scanLineY = (float) animation.getAnimatedValue();
            invalidate();
        });
        scanAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isAnimating) return;
                isMeasuring = false;
                showResults = true;
                startFadeInResultAnimation();
            }
        });
        scanAnimator.start();
    }

    private void startFadeInResultAnimation() {
        fadeInResultAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeInResultAnimator.setDuration(500);
        fadeInResultAnimator.addUpdateListener(animation -> {
            resultAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        fadeInResultAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Results shown, animation complete
            }
        });
        fadeInResultAnimator.start();
    }

    public void stopAnimation() {
        isAnimating = false;
        if (scanAnimator != null) scanAnimator.cancel();
        if (fadeOutAnimator != null) fadeOutAnimator.cancel();
        if (fadeInResultAnimator != null) fadeInResultAnimator.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
}
