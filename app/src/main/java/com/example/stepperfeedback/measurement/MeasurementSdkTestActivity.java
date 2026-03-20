package com.example.stepperfeedback.measurement;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.view.ViewParent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.measurement.callback.CalibrationCallback;
import com.example.stepperfeedback.measurement.callback.ContinuousDepthCallback;
import com.example.stepperfeedback.measurement.callback.ContinuousMeasureCallback;
import com.example.stepperfeedback.measurement.callback.DepthCallback;
import com.example.stepperfeedback.measurement.callback.MeasureCallback;
import com.example.stepperfeedback.measurement.model.BaselineResult;
import com.example.stepperfeedback.measurement.model.MeasureError;
import com.example.stepperfeedback.measurement.model.MeasureResult;
import com.example.stepperfeedback.measurement.model.MeasureStatus;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调用 {@link ObjectMeasurer} 各公开接口的调试页：一按钮一接口，日志区展示成功/失败。
 */
public class MeasurementSdkTestActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION =
            "com.example.stepperfeedback.measurement.USB_PERMISSION";

    /** 与传感器固件一致时可改为 921600 等 */
    private static final int USB_BAUD_RATE = 115200;

    private TextView logView;
    private LinearLayout buttonContainer;

    private UsbManager usbManager;
    private android.hardware.usb.UsbDeviceConnection usbConnection;
    private UsbSerialPort serialPort;
    private ObjectMeasurer measurer;

    private final AtomicInteger continuousDepthFrames = new AtomicInteger(0);
    private final AtomicInteger continuousMeasureUpdates = new AtomicInteger(0);

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) {
                return;
            }
            UsbDevice device = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                    : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) {
                runOnUiThread(() -> openUsbAfterPermission(device));
            } else {
                runOnUiThread(() -> logFail("USB", "用户拒绝或未授予 USB 权限"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_sdk_test);
        setTitle("ObjectMeasurer SDK 测试");

        logView = findViewById(R.id.text_log);
        buttonContainer = findViewById(R.id.button_container);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbPermissionReceiver, filter);
        }

        buildButtons();
        logLine("提示：先点「连接 USB」，再依次 start → 深度/校准/测量。");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (Exception ignored) {
        }
        releaseUsbAndMeasurer();
    }

    private void buildButtons() {
        addBtn("连接 USB（请求权限并创建 ObjectMeasurer）", this::onConnectUsb);
        addBtn("start()", this::onStartMeasurer);
        addBtn("stop()", this::onStopMeasurer);
        addBtn("release() + 关闭串口", this::onRelease);
        addBtn("getDepthData (单次)", this::onGetDepthOnce);
        addBtn("getDepthDataContinuous", this::onGetDepthContinuous);
        addBtn("stopDepthData()", this::onStopDepthData);
        addBtn("calibrateBaseline()", this::onCalibrate);
        addBtn("hasBaseline()", this::onHasBaseline);
        addBtn("getBaseline()", this::onGetBaseline);
        addBtn("clearBaseline()", this::onClearBaseline);
        addBtn("measureOnce()", this::onMeasureOnce);
        addBtn("startContinuousMeasure()", this::onStartContinuousMeasure);
        addBtn("stopMeasure()", this::onStopMeasure);
        addBtn("getState()", this::onGetState);
        addBtn("setNoiseMask (测试 1 像素)", this::onSetNoiseMask);
        addBtn("clearNoiseMask()", this::onClearNoiseMask);
    }

    private void addBtn(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable t) {
                logFail(label, t.getMessage() != null ? t.getMessage() : t.toString());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        buttonContainer.addView(b, lp);
    }

    private void onConnectUsb() {
        releaseUsbAndMeasurer();
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers == null || drivers.isEmpty()) {
            logFail("连接 USB", "未发现 USB 串口设备，请接入后重试");
            return;
        }
        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();
        if (!usbManager.hasPermission(device)) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), flags);
            usbManager.requestPermission(device, pi);
            logLine("已弹出 USB 权限请求…");
            return;
        }
        openUsbAfterPermission(device);
    }

    private void openUsbAfterPermission(UsbDevice device) {
        try {
            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (drivers == null || drivers.isEmpty()) {
                logFail("连接 USB", "授权后未找到串口驱动（设备已拔掉？）");
                return;
            }
            UsbSerialDriver driver = null;
            for (UsbSerialDriver d : drivers) {
                if (d.getDevice().getDeviceId() == device.getDeviceId()) {
                    driver = d;
                    break;
                }
            }
            if (driver == null) {
                driver = drivers.get(0);
            }
            if (driver.getPorts().isEmpty()) {
                logFail("连接 USB", "驱动无可用串口端口");
                return;
            }
            usbConnection = usbManager.openDevice(driver.getDevice());
            if (usbConnection == null) {
                logFail("连接 USB", "openDevice 失败");
                return;
            }
            serialPort = driver.getPorts().get(0);
            serialPort.open(usbConnection);
            serialPort.setParameters(USB_BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // 等待设备稳定
            Thread.sleep(500);

            // 清空缓冲区
            byte[] tmp = new byte[4096];
            while (serialPort.read(tmp, 50) > 0) {
            }

            // 配置传感器 (发送 AT 命令)
            logLine("正在配置传感器...");
            sendAtCommand("AT+ISP=0\r\n");  // 先停止数据流
            Thread.sleep(500);  // 参考代码用500ms
            sendAtCommand("AT+BINN=1\r\n"); // 二进制模式
            Thread.sleep(200);
            sendAtCommand("AT+UNIT=0\r\n"); // 单位
            Thread.sleep(200);
            sendAtCommand("AT+FPS=15\r\n"); // 帧率
            Thread.sleep(200);
            sendAtCommand("AT+DISP=6\r\n"); // 显示模式
            Thread.sleep(200);
            sendAtCommand("AT+SAVE\r\n");   // 保存配置
            Thread.sleep(500);
            sendAtCommand("AT+ISP=1\r\n");  // 启动数据流!
            Thread.sleep(2000);  // 参考代码用2000ms，等待数据流稳定
            logLine("传感器配置完成");

            measurer = ObjectMeasurer.builder()
                    .setUsbSerialPort(serialPort)
                    .build();
            logOk("连接 USB", "已打开端口，波特率=" + USB_BAUD_RATE + "，ObjectMeasurer 已 build()");
        } catch (Exception e) {
            releaseUsbAndMeasurer();
            logFail("连接 USB", e.getMessage());
        }
    }

    private void sendAtCommand(String cmd) {
        try {
            serialPort.write(cmd.getBytes(), 500);
            Thread.sleep(100);
            byte[] resp = new byte[256];
            int len = serialPort.read(resp, 500);
            if (len > 0) {
                String response = new String(resp, 0, len).trim();
                logLine("AT: " + cmd.trim() + " -> " + response);
            } else {
                logLine("AT: " + cmd.trim() + " (无响应)");
            }
        } catch (Exception e) {
            logLine("AT命令失败: " + cmd.trim() + " - " + e.getMessage());
        }
    }

    private void releaseUsbAndMeasurer() {
        continuousDepthFrames.set(0);
        continuousMeasureUpdates.set(0);
        if (measurer != null) {
            try {
                measurer.release();
            } catch (Exception ignored) {
            }
            measurer = null;
        }
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (Exception ignored) {
            }
            serialPort = null;
        }
        if (usbConnection != null) {
            try {
                usbConnection.close();
            } catch (Exception ignored) {
            }
            usbConnection = null;
        }
    }

    private void requireMeasurer(String op) {
        if (measurer == null) {
            logFail(op, "请先「连接 USB」");
            throw new IllegalStateException("no measurer");
        }
    }

    private void onStartMeasurer() {
        requireMeasurer("start");
        measurer.start();
        logOk("start()", "已调用");
    }

    private void onStopMeasurer() {
        requireMeasurer("stop");
        measurer.stop();
        logOk("stop()", "已调用");
    }

    private void onRelease() {
        releaseUsbAndMeasurer();
        logOk("release", "已释放测量器并关闭串口");
    }

    private void onGetDepthOnce() {
        requireMeasurer("getDepthData");
        measurer.getDepthData(new DepthCallback() {
            @Override
            public void onDepthData(float[][] depthMatrix, long timestamp) {
                float center = depthMatrix[50][50];
                runOnUiThread(() -> logOk("getDepthData",
                        "中心[50,50]=" + center + "mm, ts=" + timestamp));
            }

            @Override
            public void onError(MeasureError error) {
                runOnUiThread(() -> logFail("getDepthData", error.toString()));
            }
        });
    }

    private void onGetDepthContinuous() {
        requireMeasurer("getDepthDataContinuous");
        continuousDepthFrames.set(0);
        measurer.getDepthDataContinuous(new ContinuousDepthCallback() {
            @Override
            public void onDepthData(float[][] depthMatrix, long timestamp) {
                int n = continuousDepthFrames.incrementAndGet();
                if (n == 1 || n % 20 == 0) {
                    float c = depthMatrix[50][50];
                    runOnUiThread(() -> logOk("连续深度",
                            "帧#" + n + " 中心=" + c + "mm ts=" + timestamp));
                }
            }

            @Override
            public void onError(MeasureError error) {
                runOnUiThread(() -> logFail("getDepthDataContinuous", error.toString()));
            }
        });
        logOk("getDepthDataContinuous", "已开始（每 20 帧打一条日志）");
    }

    private void onStopDepthData() {
        requireMeasurer("stopDepthData");
        measurer.stopDepthData();
        logOk("stopDepthData()", "已调用，累计帧≈" + continuousDepthFrames.get());
    }

    private void onCalibrate() {
        requireMeasurer("calibrateBaseline");
        measurer.calibrateBaseline(new CalibrationCallback() {
            @Override
            public void onProgress(int current, int total, float avgDifference) {
                runOnUiThread(() -> logLine(
                        "[校准进度] " + current + "/" + total + " avgDiff=" + avgDifference));
            }

            @Override
            public void onSuccess(BaselineResult result) {
                runOnUiThread(() -> logOk("calibrateBaseline", result.toString()));
            }

            @Override
            public void onError(MeasureError error) {
                runOnUiThread(() -> logFail("calibrateBaseline", error.toString()));
            }
        });
    }

    private void onHasBaseline() {
        requireMeasurer("hasBaseline");
        boolean h = measurer.hasBaseline();
        logOk("hasBaseline()", String.valueOf(h));
    }

    private void onGetBaseline() {
        requireMeasurer("getBaseline");
        BaselineResult b = measurer.getBaseline();
        if (b == null) {
            logFail("getBaseline()", "返回 null（尚无基线）");
        } else {
            logOk("getBaseline()", b.toString());
        }
    }

    private void onClearBaseline() {
        requireMeasurer("clearBaseline");
        measurer.clearBaseline();
        logOk("clearBaseline()", "已调用");
    }

    private void onMeasureOnce() {
        requireMeasurer("measureOnce");
        measurer.measureOnce(new MeasureCallback() {
            @Override
            public void onProgress(int current, int total, MeasureResult intermediate) {
                runOnUiThread(() -> logLine(
                        "[测量进度] " + current + "/" + total + " " + intermediate.toString()));
            }

            @Override
            public void onSuccess(MeasureResult result) {
                runOnUiThread(() -> logOk("measureOnce", result.getFormattedResult()));
            }

            @Override
            public void onError(MeasureError error) {
                runOnUiThread(() -> logFail("measureOnce", error.toString()));
            }
        });
    }

    private void onStartContinuousMeasure() {
        requireMeasurer("startContinuousMeasure");
        continuousMeasureUpdates.set(0);
        measurer.startContinuousMeasure(new ContinuousMeasureCallback() {
            @Override
            public void onResult(MeasureResult result) {
                int n = continuousMeasureUpdates.incrementAndGet();
                if (n == 1 || n % 5 == 0) {
                    runOnUiThread(() -> logOk("连续测量 onResult (#" + n + ")", result.toString()));
                }
            }

            @Override
            public void onStatusChanged(MeasureStatus status) {
                runOnUiThread(() -> logLine("[MeasureStatus] " + status.toString()));
            }

            @Override
            public void onError(MeasureError error) {
                runOnUiThread(() -> logFail("startContinuousMeasure", error.toString()));
            }
        });
        logOk("startContinuousMeasure", "已开始");
    }

    private void onStopMeasure() {
        requireMeasurer("stopMeasure");
        measurer.stopMeasure();
        logOk("stopMeasure()", "已调用，onResult 次数≈" + continuousMeasureUpdates.get());
    }

    private void onGetState() {
        requireMeasurer("getState");
        ObjectMeasurer.State s = measurer.getState();
        logOk("getState()", s.name());
    }

    private void onSetNoiseMask() {
        requireMeasurer("setNoiseMask");
        boolean[][] mask = new boolean[100][100];
        mask[50][50] = true;
        measurer.setNoiseMask(mask);
        logOk("setNoiseMask", "已设置 mask[50][50]=true");
    }

    private void onClearNoiseMask() {
        requireMeasurer("clearNoiseMask");
        measurer.clearNoiseMask();
        logOk("clearNoiseMask()", "已调用");
    }

    private void logLine(String msg) {
        if (!canUpdateUi()) {
            return;
        }
        String line = System.currentTimeMillis() % 100000 + " | " + msg + "\n";
        logView.append(line);
        scrollLogToEnd();
    }

    private boolean canUpdateUi() {
        if (isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed();
    }

    private void logOk(String api, String detail) {
        logLine("✓ " + api + " → " + detail);
    }

    private void logFail(String api, String detail) {
        logLine("✗ " + api + " → " + detail);
        if (canUpdateUi()) {
            Toast.makeText(this, api + ": " + detail, Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollLogToEnd() {
        logView.post(() -> {
            ViewParent p = logView.getParent();
            if (p instanceof android.widget.ScrollView) {
                ((android.widget.ScrollView) p).fullScroll(android.view.View.FOCUS_DOWN);
            }
        });
    }
}

