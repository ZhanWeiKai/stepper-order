/*
Copyright 2016 StepStone Services

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.example.stepperfeedback;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.stepperfeedback.adapter.StepAdapter;
import com.example.stepperfeedback.feedback.StepperFeedbackType;
import com.example.stepperfeedback.viewmodel.StepViewModel;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements StepperLayout.StepperListener {

    private StepperLayout mStepperLayout;

    private static final int[] STEP_COLORS = {
            Color.parseColor("#E3F2FD"), // Light Blue
            Color.parseColor("#E8F5E9"), // Light Green
            Color.parseColor("#FFF3E0"), // Light Orange
            Color.parseColor("#F3E5F5")  // Light Purple
    };

    private static final String[] STEP_TITLES = {
            "Scan QR Code",
            "Step 2",
            "Step 3",
            "Step 4"
    };

    private static final String[] STEP_DESCRIPTIONS = {
            "",  // Step 1 has QR code instead
            "This is the second step.\n\nYou have successfully logged in!\n\nClick NEXT to continue.",
            "You're almost there!\n\nThis is the third step of the stepper.\n\nClick NEXT to finish.",
            "Congratulations!\n\nYou have completed all steps.\n\nClick COMPLETE to finish."
    };

    private static final String BASE_URL = "http://119.91.206.195:8889";
    private static final String TAG = "MainActivity";

    private String currentSessionId;
    private String currentQrCodeUrl;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isPolling = false;
    private ImageView qrCodeImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        setupStepperLayout();
    }

    private void setupStepperLayout() {
        mStepperLayout = findViewById(R.id.stepper_layout);
        mStepperLayout.setListener(this);

        // Set up feedback type (tabs + disabled navigation during progress)
        int feedbackType = StepperFeedbackType.TABS |
                StepperFeedbackType.CONTENT_PROGRESS |
                StepperFeedbackType.DISABLED_BOTTOM_NAVIGATION;

        mStepperLayout.setFeedbackType(feedbackType);

        // Disable tab click navigation - only allow navigation via Next/Back buttons
        mStepperLayout.setTabNavigationEnabled(false);

        // Create and set adapter
        mStepperLayout.setAdapter(new DemoStepAdapter());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPolling = false;
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public void onCompleted(View completeButton) {
        Toast.makeText(this, "Stepper completed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStepSelected(int newStepPosition) {
        Log.d(TAG, "Step selected: " + newStepPosition);
        if (newStepPosition == 0) {
            // Hide Next button on QR code scan step
            mStepperLayout.setNextButtonVisibility(View.GONE);
            // Start QR code login process
            createSessionAndStartPolling();
        } else {
            // Show Next button on other steps
            mStepperLayout.setNextButtonVisibility(View.VISIBLE);
            // Stop polling when moving away from step 1
            isPolling = false;
        }
    }

    @Override
    public void onReturn() {
        Toast.makeText(this, "Return button clicked", Toast.LENGTH_SHORT).show();
    }

    private void createSessionAndStartPolling() {
        executorService.execute(() -> {
            try {
                // Create session
                URL url = new URL(BASE_URL + "/api/qr-login/session/create");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();

                    JSONObject json = new JSONObject(response.toString());
                    currentSessionId = json.getString("sessionId");
                    currentQrCodeUrl = json.getString("qrCodeUrl");

                    Log.d(TAG, "Session created: " + currentSessionId);
                    Log.d(TAG, "QR Code URL: " + currentQrCodeUrl);

                    // Update QR code on UI
                    mainHandler.post(() -> {
                        generateAndDisplayQrCode(currentQrCodeUrl);
                        startPolling();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error creating session", e);
                mainHandler.post(() ->
                    Toast.makeText(MainActivity.this, "Failed to create session", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void generateAndDisplayQrCode(String url) {
        if (qrCodeImageView == null) return;

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 400, 400);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            qrCodeImageView.setImageBitmap(bmp);
            Log.d(TAG, "QR code displayed: " + url);
        } catch (WriterException e) {
            Log.e(TAG, "Error generating QR code", e);
        }
    }

    private void startPolling() {
        isPolling = true;
        pollLoginStatus();
    }

    private void pollLoginStatus() {
        if (!isPolling || currentSessionId == null) return;

        executorService.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/api/qr-login/status/" + currentSessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();

                    JSONObject json = new JSONObject(response.toString());
                    boolean loggedIn = json.getBoolean("loggedIn");

                    Log.d(TAG, "Polling status: " + loggedIn);

                    if (loggedIn) {
                        mainHandler.post(() -> {
                            isPolling = false;
                            Toast.makeText(MainActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            // Move to next step
                            mStepperLayout.proceed();
                        });
                    } else {
                        // Continue polling after 2 seconds
                        mainHandler.postDelayed(this::pollLoginStatus, 2000);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error polling status", e);
                // Retry after 2 seconds
                mainHandler.postDelayed(this::pollLoginStatus, 2000);
            }
        });
    }

    /**
     * Adapter for the demo stepper
     */
    private class DemoStepAdapter implements StepAdapter {

        @Override
        public StepViewModel getViewModel(int position) {
            return new StepViewModel.Builder(MainActivity.this)
                    .setTitle(STEP_TITLES[position])
                    .create();
        }

        @Override
        public View getContentView(int position) {
            if (position == 0) {
                return createQrCodeView();
            } else {
                return createStandardStepView(position);
            }
        }

        private View createQrCodeView() {
            // Create a ScrollView with content
            ScrollView scrollView = new ScrollView(MainActivity.this);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));
            scrollView.setBackgroundColor(STEP_COLORS[0]);

            // Create content container
            LinearLayout contentLayout = new LinearLayout(MainActivity.this);
            contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setGravity(Gravity.CENTER);
            contentLayout.setPadding(48, 48, 48, 48);

            // Create QR code ImageView
            qrCodeImageView = new ImageView(MainActivity.this);
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(400, 400);
            qrParams.gravity = Gravity.CENTER;
            qrCodeImageView.setLayoutParams(qrParams);
            qrCodeImageView.setBackgroundColor(Color.WHITE);
            qrCodeImageView.setPadding(16, 16, 16, 16);

            // Create instruction TextView
            TextView instructionView = new TextView(MainActivity.this);
            instructionView.setText("Please scan QR code to login");
            instructionView.setTextSize(18);
            instructionView.setTextColor(Color.BLACK);
            instructionView.setGravity(Gravity.CENTER);
            instructionView.setPadding(0, 32, 0, 0);

            // Create hint TextView
            TextView hintView = new TextView(MainActivity.this);
            hintView.setText("Use your phone camera to scan the QR code above");
            hintView.setTextSize(14);
            hintView.setTextColor(Color.DKGRAY);
            hintView.setGravity(Gravity.CENTER);
            hintView.setPadding(0, 16, 0, 0);

            contentLayout.addView(qrCodeImageView);
            contentLayout.addView(instructionView);
            contentLayout.addView(hintView);
            scrollView.addView(contentLayout);

            return scrollView;
        }

        private View createStandardStepView(int position) {
            // Create a ScrollView with content
            ScrollView scrollView = new ScrollView(MainActivity.this);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));
            scrollView.setBackgroundColor(STEP_COLORS[position]);

            // Create content container
            LinearLayout contentLayout = new LinearLayout(MainActivity.this);
            contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setGravity(Gravity.CENTER);
            contentLayout.setPadding(48, 48, 48, 48);

            // Create title TextView
            TextView titleView = new TextView(MainActivity.this);
            titleView.setText(STEP_TITLES[position]);
            titleView.setTextSize(24);
            titleView.setTextColor(Color.BLACK);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 32);

            // Create description TextView
            TextView descView = new TextView(MainActivity.this);
            descView.setText(STEP_DESCRIPTIONS[position]);
            descView.setTextSize(16);
            descView.setTextColor(Color.DKGRAY);
            descView.setGravity(Gravity.CENTER);
            descView.setLineSpacing(8, 1);

            contentLayout.addView(titleView);
            contentLayout.addView(descView);
            scrollView.addView(contentLayout);

            return scrollView;
        }

        @Override
        public int getCount() {
            return 4;
        }
    }
}
