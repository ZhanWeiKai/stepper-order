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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.stepperfeedback.adapter.StepAdapter;
import com.example.stepperfeedback.feedback.StepperFeedbackType;
import com.example.stepperfeedback.measurement.ObjectMeasurer;
import com.example.stepperfeedback.measurement.callback.CalibrationCallback;
import com.example.stepperfeedback.measurement.callback.MeasureCallback;
import com.example.stepperfeedback.measurement.model.BaselineResult;
import com.example.stepperfeedback.measurement.model.MeasureError;
import com.example.stepperfeedback.measurement.model.MeasureResult;
import com.example.stepperfeedback.viewmodel.StepViewModel;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.List;

public class MainActivity extends AppCompatActivity implements StepperLayout.StepperListener {

    private static final String TAG = "MainActivity123";

    // Stepper
    private StepperLayout mStepperLayout;
    private com.example.stepperfeedback.widget.HomeView mHomeView;
    private boolean mStepperStarted = false;

    // USB & Measurement
    private UsbManager usbManager;
    private UsbSerialPort usbSerialPort;
    private ObjectMeasurer measurer;
    private static final String ACTION_USB_PERMISSION = "com.example.stepperfeedback.USB_PERMISSION";

    // Step views (keep references for updates)
    private ProgressBar calibrationProgressBar;
    private TextView calibrationStatusText;
    private Button calibrationButton;
    private Button measureButton;
    private TextView measureResultText;
    private com.example.stepperfeedback.widget.VolumeMeasurementView volumeMeasurementView;

    // State
    private boolean usbConnected = false;
    private boolean measurerStarted = false;

    private static final int[] STEP_COLORS = {
            Color.parseColor("#E3F2FD"), // Light Blue - Calibration
            Color.parseColor("#E8F5E9"), // Light Green - Box Placement
            Color.parseColor("#FFF3E0"), // Light Orange - Volume Measurement
            Color.parseColor("#F3E5F5")  // Light Purple - Complete
    };

    private static final String[] STEP_TITLES = {
            "校准桌面",
            "放置包裹",
            "体积测量",
            "完成"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerUsbReceiver();

        setupHomeView();
    }

    private void setupHomeView() {
        mHomeView = findViewById(R.id.home_view);
        mHomeView.setOnStartClickListener(() -> {
            mHomeView.setVisibility(View.GONE);
            mStepperLayout.setVisibility(View.VISIBLE);
            mStepperStarted = true;

            setupStepperLayout();
            // Auto start USB connection
            autoStartUsbAndMeasurer();
        });

        mStepperLayout = findViewById(R.id.stepper_layout);
        mStepperLayout.setListener(this);
    }

    /**
     * Auto start USB connection and measurer in sequence
     */
    private void autoStartUsbAndMeasurer() {
        showLoadingDialog("正在连接USB设备...");
        onConnectUsb();
    }

    private void showLoadingDialog(String message) {
        // Could implement a loading dialog here
        Log.d(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideLoadingDialog() {
        // Hide loading dialog
    }

    // ==================== USB Connection ====================

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device);
                        }
                    } else {
                        Log.d(TAG, "USB permission denied");
                        Toast.makeText(context, "USB权限被拒绝", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB device attached");
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB device detached");
                usbConnected = false;
                if (measurer != null) {
                    measurer.release();
                    measurer = null;
                }
            }
        }
    };

    private void onConnectUsb() {
        Log.d(TAG, "onConnectUsb");

        // 使用 UsbSerialProber 查找所有 USB 串口设备
        List<com.hoho.android.usbserial.driver.UsbSerialDriver> drivers =
                com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers == null || drivers.isEmpty()) {
            Toast.makeText(this, "未找到 USB 串口设备", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No USB serial devices found");
            return;
        }

        // 使用第一个驱动
        com.hoho.android.usbserial.driver.UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();

        Log.d(TAG, "Found USB device: " + device.getDeviceName() +
                " VID=" + Integer.toHexString(device.getVendorId()) +
                " PID=" + Integer.toHexString(device.getProductId()));

        if (usbManager.hasPermission(device)) {
            connectToDevice(device);
        } else {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), flags);
            usbManager.requestPermission(device, permissionIntent);
            Toast.makeText(this, "请授权 USB 权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(UsbDevice device) {
        try {
            // 重新获取驱动
            List<com.hoho.android.usbserial.driver.UsbSerialDriver> drivers =
                    com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

            if (drivers == null || drivers.isEmpty()) {
                Toast.makeText(this, "授权后未找到串口驱动", Toast.LENGTH_SHORT).show();
                return;
            }

            // 找到对应设备的驱动
            com.hoho.android.usbserial.driver.UsbSerialDriver driver = null;
            for (com.hoho.android.usbserial.driver.UsbSerialDriver d : drivers) {
                if (d.getDevice().getDeviceId() == device.getDeviceId()) {
                    driver = d;
                    break;
                }
            }
            if (driver == null) {
                driver = drivers.get(0);
            }

            if (driver.getPorts().isEmpty()) {
                Toast.makeText(this, "驱动无可用串口端口", Toast.LENGTH_SHORT).show();
                return;
            }

            // 打开连接
            android.hardware.usb.UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
            if (connection == null) {
                Toast.makeText(this, "无法打开 USB 设备", Toast.LENGTH_SHORT).show();
                return;
            }

            usbSerialPort = driver.getPorts().get(0);
            usbSerialPort.open(connection);
            usbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            usbConnected = true;
            Log.d(TAG, "USB connected successfully");
            Toast.makeText(this, "USB连接成功", Toast.LENGTH_SHORT).show();

            // Auto start measurer after USB connected
            mainHandler.postDelayed(this::onStartMeasurer, 500);

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect USB", e);
            Toast.makeText(this, "USB连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onStartMeasurer() {
        Log.d(TAG, "onStartMeasurer");

        if (!usbConnected || usbSerialPort == null) {
            Toast.makeText(this, "请先连接USB", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            measurer = ObjectMeasurer.builder()
                    .setUsbSerialPort(usbSerialPort)
                    .setFov(70f, 60f)
                    .setTiltAngle(45f)
                    .setThreshold(50f)
                    .setSampleWindow(10, 500)
                    .setStableCount(10)
                    .build();

            // 使用带回调的 start 方法
            if (calibrationStatusText != null) {
                calibrationStatusText.setText("正在配置传感器...");
            }
            if (calibrationButton != null) {
                calibrationButton.setText("配置中...");
                calibrationButton.setEnabled(false);
            }

            measurer.start(new ObjectMeasurer.SensorConfigCallback() {
                @Override
                public void onProgress(String message) {
                    mainHandler.post(() -> {
                        if (calibrationStatusText != null) {
                            calibrationStatusText.setText(message);
                        }
                        Log.d(TAG, "Sensor config progress: " + message);
                    });
                }

                @Override
                public void onSuccess() {
                    mainHandler.post(() -> {
                        measurerStarted = true;
                        Log.d(TAG, "Measurer started successfully");
                        Toast.makeText(MainActivity.this, "传感器配置完成", Toast.LENGTH_SHORT).show();

                        // Enable calibration button
                        if (calibrationButton != null) {
                            calibrationButton.setEnabled(true);
                            calibrationButton.setClickable(true);
                            calibrationButton.setText("开始校准");
                            Log.d(TAG, "Calibration button ENABLED");
                        }
                        if (calibrationStatusText != null) {
                            calibrationStatusText.setText("传感器已就绪，点击按钮开始校准");
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        Log.e(TAG, "Failed to start measurer: " + error);
                        Toast.makeText(MainActivity.this, "传感器配置失败: " + error, Toast.LENGTH_SHORT).show();
                        if (calibrationButton != null) {
                            calibrationButton.setText("配置失败");
                        }
                        if (calibrationStatusText != null) {
                            calibrationStatusText.setText("传感器配置失败: " + error);
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to start measurer", e);
            Toast.makeText(this, "测量器启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Stepper Setup ====================

    private void setupStepperLayout() {
        mStepperLayout.setListener(this);

        int feedbackType = StepperFeedbackType.TABS |
                StepperFeedbackType.CONTENT_PROGRESS |
                StepperFeedbackType.DISABLED_BOTTOM_NAVIGATION;

        mStepperLayout.setFeedbackType(feedbackType);
        mStepperLayout.setTabNavigationEnabled(false);
        mStepperLayout.setBackButtonVisibility(View.GONE);

        mStepperLayout.setAdapter(new DemoStepAdapter());
    }

    @Override
    public void onCompleted(View completeButton) {
        Toast.makeText(this, "测量完成!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStepSelected(int newStepPosition) {
        Log.d(TAG, "Step selected: " + newStepPosition);

        if (newStepPosition == 0) {
            // Calibration step - hide next until calibrated
            mStepperLayout.setNextButtonVisibility(View.GONE);
        } else {
            mStepperLayout.setNextButtonVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onReturn() {
        Toast.makeText(this, "返回", Toast.LENGTH_SHORT).show();
    }

    // ==================== Step Views ====================

    private View createCalibrationView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(STEP_COLORS[0]);

        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(48, 48, 48, 48);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText("桌面校准");
        titleView.setTextSize(24);
        titleView.setTextColor(Color.BLACK);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 32);

        // Status text
        calibrationStatusText = new TextView(this);
        calibrationStatusText.setText("正在连接USB设备...");
        calibrationStatusText.setTextSize(16);
        calibrationStatusText.setTextColor(Color.DKGRAY);
        calibrationStatusText.setGravity(Gravity.CENTER);
        calibrationStatusText.setPadding(0, 0, 0, 24);

        // Progress bar
        calibrationProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        calibrationProgressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48
        ));
        calibrationProgressBar.setMax(10);
        calibrationProgressBar.setProgress(0);
        calibrationProgressBar.setVisibility(View.GONE);

        // Debug info text
        final TextView debugText = new TextView(this);
        debugText.setText("调试信息: 等待中...");
        debugText.setTextSize(12);
        debugText.setTextColor(Color.GRAY);
        debugText.setGravity(Gravity.CENTER);
        debugText.setPadding(0, 16, 0, 16);

        // Calibration button
        calibrationButton = new Button(this);
        calibrationButton.setText("等待USB连接...");
        calibrationButton.setEnabled(false);
        calibrationButton.setClickable(true);
        calibrationButton.setFocusable(true);
        calibrationButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams btnParams = (LinearLayout.LayoutParams) calibrationButton.getLayoutParams();
        btnParams.topMargin = 32;
        calibrationButton.setLayoutParams(btnParams);

        // 添加触摸监听器来调试
        calibrationButton.setOnTouchListener((v, event) -> {
            Log.d(TAG, "Button onTouch: " + event.getAction());
            return false;  // 不消费事件，让它继续传递
        });

        calibrationButton.setOnClickListener(v -> {
            Toast.makeText(this, "按钮被点击了!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Calibration button clicked! measurer=" + measurer + ", measurerStarted=" + measurerStarted);

            if (measurer == null || !measurerStarted) {
                Toast.makeText(this, "测量器未启动", Toast.LENGTH_SHORT).show();
                return;
            }

            if (measurer.hasBaseline()) {
                // Already calibrated, proceed
                Toast.makeText(this, "已校准，进入下一步", Toast.LENGTH_SHORT).show();
                mStepperLayout.proceed();
                return;
            }

            // Start calibration
            Toast.makeText(this, "开始校准...", Toast.LENGTH_SHORT).show();
            calibrationButton.setEnabled(false);
            calibrationButton.setText("校准中...");
            calibrationProgressBar.setVisibility(View.VISIBLE);
            calibrationProgressBar.setProgress(0);
            calibrationStatusText.setText("正在校准，请保持桌面清空...\n正在采集数据帧...");
            debugText.setText("调试: 调用 calibrateBaseline()");

            measurer.calibrateBaseline(new CalibrationCallback() {
                @Override
                public void onProgress(int current, int total, float avgDiff) {
                    mainHandler.post(() -> {
                        calibrationProgressBar.setMax(total);
                        calibrationProgressBar.setProgress(current);
                        calibrationStatusText.setText(String.format("校准进度: %d/%d\n稳定帧收集中...", current, total));
                        debugText.setText(String.format("调试: 进度 %d/%d, avgDiff=%.2f", current, total, avgDiff));
                        Log.d(TAG, String.format("Calibration progress: %d/%d, avgDiff=%.2f", current, total, avgDiff));
                    });
                }

                @Override
                public void onSuccess(BaselineResult result) {
                    mainHandler.post(() -> {
                        calibrationStatusText.setText("校准完成！中心深度: " + (int)result.centerDepth + "mm");
                        calibrationProgressBar.setVisibility(View.GONE);
                        calibrationButton.setText("已校准 ✓");
                        calibrationButton.setEnabled(true);
                        debugText.setText("调试: 校准成功!");

                        // Enable next button
                        mStepperLayout.setNextButtonVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "校准成功!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Calibration SUCCESS: centerDepth=" + result.centerDepth);
                    });
                }

                @Override
                public void onError(MeasureError error) {
                    mainHandler.post(() -> {
                        calibrationStatusText.setText("校准失败: " + error.message);
                        calibrationProgressBar.setVisibility(View.GONE);
                        calibrationButton.setText("重新校准");
                        calibrationButton.setEnabled(true);
                        debugText.setText("调试: 错误 - " + error.message);
                        Toast.makeText(MainActivity.this, "校准失败: " + error.message, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Calibration ERROR: " + error.message);
                    });
                }
            });
        });

        // Hint text
        TextView hintView = new TextView(this);
        hintView.setText("请确保桌面上没有放置任何物体");
        hintView.setTextSize(14);
        hintView.setTextColor(Color.GRAY);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(0, 24, 0, 0);

        container.addView(titleView);
        container.addView(calibrationStatusText);
        container.addView(calibrationProgressBar);
        container.addView(debugText);
        container.addView(calibrationButton);
        container.addView(hintView);
        scrollView.addView(container);

        return scrollView;
    }

    private View createBoxPlacementView() {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundColor(STEP_COLORS[1]);

        com.example.stepperfeedback.widget.BoxPlacementView boxView =
                new com.example.stepperfeedback.widget.BoxPlacementView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        boxView.setLayoutParams(params);

        container.addView(boxView);
        return container;
    }

    private View createVolumeMeasurementView() {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundColor(STEP_COLORS[2]);

        // Add VolumeMeasurementView
        volumeMeasurementView = new com.example.stepperfeedback.widget.VolumeMeasurementView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        volumeMeasurementView.setLayoutParams(params);

        // Add measure button
        Button measureButton = new Button(this);
        measureButton.setText("开始测量");
        measureButton.setTextSize(16);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = 24;
        btnParams.bottomMargin = 48;
        measureButton.setLayoutParams(btnParams);

        measureButton.setOnClickListener(v -> {
            if (measurer == null || !measurer.hasBaseline()) {
                Toast.makeText(MainActivity.this, "请先完成校准", Toast.LENGTH_SHORT).show();
                return;
            }

            measureButton.setEnabled(false);
            measureButton.setText("测量中...");

            // 开始测量动画
            volumeMeasurementView.startMeasure();

            Log.d(TAG, "开始调用 measureOnce...");

            // 调用 SDK measureOnce 接口
            measurer.measureOnce(new MeasureCallback() {
                @Override
                public void onProgress(int current, int total, MeasureResult intermediate) {
                    Log.d(TAG, String.format("测量进度: %d/%d, 中间结果: W=%.1f, L=%.1f, H=%.1f",
                            current, total, intermediate.width, intermediate.length, intermediate.height));
                }

                @Override
                public void onSuccess(MeasureResult result) {
                    mainHandler.post(() -> {
                        Log.d(TAG, String.format("测量成功! W=%.1f, L=%.1f, H=%.1f mm, validPixels=%d",
                                result.width, result.length, result.height, result.validPixelCount));

                        // 将 SDK 测量结果设置到视图
                        volumeMeasurementView.setResult(result.width, result.length, result.height);
                        measureButton.setEnabled(true);
                        measureButton.setText("重新测量");

                        Toast.makeText(MainActivity.this,
                                String.format("测量完成: %.0f×%.0f×%.0f mm", result.width, result.length, result.height),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(MeasureError error) {
                    mainHandler.post(() -> {
                        Log.e(TAG, "测量失败: " + error.code + " - " + error.message);
                        measureButton.setEnabled(true);
                        measureButton.setText("重新测量");

                        // 停止动画
                        volumeMeasurementView.stopAnimation();

                        Toast.makeText(MainActivity.this, "测量失败: " + error.message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        container.addView(volumeMeasurementView);
        container.addView(measureButton);
        return container;
    }

    private View createCompleteView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(STEP_COLORS[3]);

        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(48, 48, 48, 48);

        TextView titleView = new TextView(this);
        titleView.setText("测量完成！");
        titleView.setTextSize(24);
        titleView.setTextColor(Color.BLACK);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 32);

        TextView descView = new TextView(this);
        descView.setText("所有步骤已完成。\n\n点击完成按钮结束。");
        descView.setTextSize(16);
        descView.setTextColor(Color.DKGRAY);
        descView.setGravity(Gravity.CENTER);
        descView.setLineSpacing(8, 1);

        container.addView(titleView);
        container.addView(descView);
        scrollView.addView(container);

        return scrollView;
    }

    // ==================== Adapter ====================

    private class DemoStepAdapter implements StepAdapter {

        @Override
        public StepViewModel getViewModel(int position) {
            return new StepViewModel.Builder(MainActivity.this)
                    .setTitle(STEP_TITLES[position])
                    .create();
        }

        @Override
        public View getContentView(int position) {
            switch (position) {
                case 0:
                    return createCalibrationView();
                case 1:
                    return createBoxPlacementView();
                case 2:
                    return createVolumeMeasurementView();
                case 3:
                    return createCompleteView();
                default:
                    return createCompleteView();
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister receiver", e);
        }

        if (measurer != null) {
            measurer.release();
            measurer = null;
        }
    }
}
