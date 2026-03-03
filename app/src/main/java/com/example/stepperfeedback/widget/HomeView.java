package com.example.stepperfeedback.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HomeView extends FrameLayout {

    private static final int COLOR_GRADIENT_START = Color.parseColor("#667eea");
    private static final int COLOR_GRADIENT_END = Color.parseColor("#764ba2");
    private static final int COLOR_BUTTON_START = Color.parseColor("#667eea");
    private static final int COLOR_BUTTON_END = Color.parseColor("#764ba2");
    private static final int COLOR_WHITE = Color.WHITE;

    private OnStartClickListener listener;

    public interface OnStartClickListener {
        void onStartClicked();
    }

    public HomeView(Context context) {
        this(context, null);
    }

    public HomeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HomeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Set gradient background
        setBackgroundColor(COLOR_GRADIENT_START);

        // Create main container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        LayoutParams containerParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        container.setLayoutParams(containerParams);

        // Create Logo container (box icon made with text)
        TextView logoIcon = new TextView(context);
        logoIcon.setText("📦");
        logoIcon.setTextSize(80);
        logoIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        iconParams.bottomMargin = 24;
        logoIcon.setLayoutParams(iconParams);

        // Create App name
        TextView appName = new TextView(context);
        appName.setText("Box Scanner");
        appName.setTextSize(32);
        appName.setTextColor(COLOR_WHITE);
        appName.setGravity(Gravity.CENTER);
        appName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.bottomMargin = 16;
        appName.setLayoutParams(nameParams);

        // Create subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText("Scan • Measure • Ship");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.parseColor("#CCFFFFFF"));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.bottomMargin = 80;
        subtitle.setLayoutParams(subtitleParams);

        // Create Start button
        Button startButton = new Button(context);
        startButton.setText("START");
        startButton.setTextSize(18);
        startButton.setTextColor(COLOR_WHITE);
        startButton.setTypeface(null, android.graphics.Typeface.BOLD);
        startButton.setAllCaps(false);
        startButton.setBackgroundColor(Color.TRANSPARENT);
        startButton.setBackgroundResource(android.R.drawable.btn_default);
        startButton.getBackground().setTint(COLOR_BUTTON_START);

        // Set button padding and min dimensions
        startButton.setPadding(60, 20, 60, 20);
        startButton.setMinWidth(200);
        startButton.setMinimumWidth(200);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        startButton.setLayoutParams(buttonParams);

        startButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStartClicked();
            }
        });

        // Add views to container
        container.addView(logoIcon);
        container.addView(appName);
        container.addView(subtitle);
        container.addView(startButton);

        // Add container to this view
        addView(container);

        // Apply gradient background using custom drawing
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        // Draw gradient background
        Paint paint = new Paint();
        LinearGradient gradient = new LinearGradient(
                0, 0, 0, getHeight(),
                COLOR_GRADIENT_START, COLOR_GRADIENT_END,
                Shader.TileMode.CLAMP
        );
        paint.setShader(gradient);
        paint.setDither(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        super.onDraw(canvas);
    }

    public void setOnStartClickListener(OnStartClickListener listener) {
        this.listener = listener;
    }
}
