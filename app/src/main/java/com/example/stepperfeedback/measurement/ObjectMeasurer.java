package com.example.stepperfeedback.measurement;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.example.stepperfeedback.measurement.callback.CalibrationCallback;
import com.example.stepperfeedback.measurement.callback.ContinuousDepthCallback;
import com.example.stepperfeedback.measurement.callback.ContinuousMeasureCallback;
import com.example.stepperfeedback.measurement.callback.DepthCallback;
import com.example.stepperfeedback.measurement.callback.MeasureCallback;
import com.example.stepperfeedback.measurement.internal.DynamicThresholdManager;
import com.example.stepperfeedback.measurement.internal.MedianFilter;
import com.example.stepperfeedback.measurement.internal.ObjectMeasurerConfig;
import com.example.stepperfeedback.measurement.internal.StabilityDetector;
import com.example.stepperfeedback.measurement.model.BaselineResult;
import com.example.stepperfeedback.measurement.model.MeasureError;
import com.example.stepperfeedback.measurement.model.MeasureResult;
import com.example.stepperfeedback.measurement.model.MeasureStatus;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 物体尺寸测量器
 *
 * 使用流程：
 * 1. 创建实例并配置
 * 2. 调用 start() 开始接收数据
 * 3. 调用 calibrateBaseline() 校准基线
 * 4. 调用 measureOnce() 或 startContinuousMeasure() 测量物体
 * 5. 调用 stop() / release() 释放资源
 */
public class ObjectMeasurer {

    private static final String TAG = "ObjectMeasurer123";

    // ========== 配置 ==========
    private final ObjectMeasurerConfig config;
    private final UsbSerialPort usbSerialPort;

    // ========== 内部组件 ==========
    private final DynamicThresholdManager thresholdManager;
    private StabilityDetector calibrateStabilityDetector;
    private StabilityDetector measureStabilityDetector;

    // ========== 状态 ==========
    private State state = State.IDLE;
    private float[][] baselineDepth;
    private float pixelSizeX;
    private float pixelSizeY;

    // ========== 采样相关 ==========
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler samplingHandler = new Handler(Looper.getMainLooper());
    private Runnable samplingRunnable;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Object bufferLock = new Object();

    // 窗口采样
    private final List<float[][]> windowFrames = new ArrayList<>();
    private final List<Float> windowMaxDepthDiffs = new ArrayList<>();
    private int windowFrameCount = 0;
    private float[][] latestMedianDepth;

    // 回调
    private CalibrationCallback calibrationCallback;
    private MeasureCallback measureCallback;
    private ContinuousMeasureCallback continuousMeasureCallback;
    private ContinuousDepthCallback continuousDepthCallback;
    private DepthCallback singleDepthCallback;

    // 校准帧累积
    private final List<float[][]> calibrationFrames = new ArrayList<>();
    private int calibrateStableCount = 0;

    // 噪声掩码
    private boolean[][] noiseMask;

    // ========== 运行标志 ==========
    private volatile boolean running = false;
    private volatile byte[] latestDepthData;

    /**
     * 状态枚举
     */
    public enum State {
        IDLE,           // 空闲
        CALIBRATING,    // 校准中
        CALIBRATED,     // 已校准
        MEASURING,      // 测量中
        ERROR           // 错误
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UsbSerialPort usbSerialPort;
        private float fovHorizontal = 70f;
        private float fovVertical = 60f;
        private float tiltAngle = 45f;
        private float threshold = 50f;
        private int samplesPerWindow = 10;
        private long sampleIntervalMs = 500;
        private int stableCountCalibrate = 10;
        private int stableCountMeasure = 10;

        public Builder setUsbSerialPort(UsbSerialPort port) {
            this.usbSerialPort = port;
            return this;
        }

        public Builder setFov(float horizontalDeg, float verticalDeg) {
            this.fovHorizontal = horizontalDeg;
            this.fovVertical = verticalDeg;
            return this;
        }

        public Builder setTiltAngle(float angleDeg) {
            this.tiltAngle = angleDeg;
            return this;
        }

        public Builder setThreshold(float thresholdMm) {
            this.threshold = thresholdMm;
            return this;
        }

        public Builder setSampleWindow(int samplesPerWindow, long intervalMs) {
            this.samplesPerWindow = samplesPerWindow;
            this.sampleIntervalMs = intervalMs;
            return this;
        }

        public Builder setStableCount(int count) {
            this.stableCountCalibrate = count;
            this.stableCountMeasure = count;
            return this;
        }

        public ObjectMeasurer build() {
            if (usbSerialPort == null) {
                throw new IllegalStateException("UsbSerialPort is required");
            }
            ObjectMeasurerConfig config = new ObjectMeasurerConfig.Builder()
                    .setFov(fovHorizontal, fovVertical)
                    .setTiltAngle(tiltAngle)
                    .setObjectThreshold(threshold)
                    .setMaxThreshold(100f)
                    .setSamplesPerWindow(samplesPerWindow)
                    .setSampleInterval(sampleIntervalMs)
                    .setStableCountCalibrate(stableCountCalibrate)
                    .setStableCountMeasure(stableCountMeasure)
                    .setBaselineAvgThreshold(14f)
                    .setDimensionThreshold(20f)
                    .setMinConsecutivePixels(8)
                    .build();
            return new ObjectMeasurer(usbSerialPort, config);
        }
    }

    // ==================== 构造函数 ====================

    private ObjectMeasurer(UsbSerialPort usbSerialPort, ObjectMeasurerConfig config) {
        this.usbSerialPort = usbSerialPort;
        this.config = config;
        this.thresholdManager = new DynamicThresholdManager();
    }

    // ==================== 生命周期 ====================

    /**
     * 开始接收数据
     */
    public void start() {
        if (running) return;
        running = true;
        startUsbReadThread();
        Log.d(TAG, "ObjectMeasurer started");
    }

    /**
     * 停止接收数据
     */
    public void stop() {
        running = false;
        stopWindowSampling();
        Log.d(TAG, "ObjectMeasurer stopped");
    }

    /**
     * 释放所有资源
     */
    public void release() {
        stop();
        mainHandler.removeCallbacksAndMessages(null);
        samplingHandler.removeCallbacksAndMessages(null);
        baselineDepth = null;
        calibrationFrames.clear();
        windowFrames.clear();
        Log.d(TAG, "ObjectMeasurer released");
    }

    // ==================== USB 读取线程 ====================

    private void startUsbReadThread() {
        new Thread(() -> {
            byte[] readBuffer = new byte[10240];
            int totalReadBytes = 0;
            int readCount = 0;
            int zeroReadCount = 0;
            Log.i(TAG, "USB读取线程已启动");
            while (running) {
                try {
                    int len = usbSerialPort.read(readBuffer, 100);
                    if (len > 0) {
                        readCount++;
                        totalReadBytes += len;
                        zeroReadCount = 0;  // 重置计数
                        // 每10次读取打印一次统计
                        if (readCount % 10 == 0) {
                            Log.d(TAG, String.format("USB读取: 第%d次, 本次%d字节, 累计%d字节",
                                readCount, len, totalReadBytes));
                        }
                        // 打印前16字节用于调试
                        if (totalReadBytes < 100) {
                            StringBuilder hex = new StringBuilder();
                            for (int i = 0; i < Math.min(16, len); i++) {
                                hex.append(String.format("%02X ", readBuffer[i] & 0xFF));
                            }
                            Log.d(TAG, "数据前16字节: " + hex.toString());
                        }
                        synchronized (bufferLock) {
                            buffer.write(readBuffer, 0, len);
                            processBuffer();
                        }
                    } else {
                        zeroReadCount++;
                        // 前10次零读取打印一次
                        if (zeroReadCount <= 10) {
                            Log.d(TAG, "USB读取超时(len=0), 第" + zeroReadCount + "次");
                        } else if (zeroReadCount == 50) {
                            Log.w(TAG, "USB连续50次读取无数据，可能传感器未启动");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "USB read error: " + e.getMessage());
                    if (running) {
                        notifyError(MeasureError.of(MeasureError.ErrorCode.USB_READ_FAILED, e.getMessage()));
                    }
                }
            }
            Log.i(TAG, "USB读取线程已停止");
        }, "UsbReadThread").start();
    }

    private void processBuffer() {
        synchronized (bufferLock) {
            byte[] data = buffer.toByteArray();
            int pos = 0;
            final int HEADER_SIZE = 2;
            final int LENGTH_SIZE = 2;
            final int META_SIZE = 16;
            final int CHECKSUM_TAIL = 2;
            final int HEAD_1 = 0x00;
            final int HEAD_2 = 0xFF;

            int bufferLen = data.length;
            int headerFoundCount = 0;
            int payloadOkCount = 0;

            while (pos + HEADER_SIZE + LENGTH_SIZE <= data.length) {
                // 查找帧头 0x00 0xFF
                if ((data[pos] & 0xFF) != HEAD_1 || (data[pos + 1] & 0xFF) != HEAD_2) {
                    pos++;
                    continue;
                }

                headerFoundCount++;

                // 读取载荷长度 (little-endian)
                int payloadLen = (data[pos + 2] & 0xFF) | ((data[pos + 3] & 0xFF) << 8);

                // 验证载荷长度范围 (10000-10050)
                if (payloadLen < 10000 || payloadLen > 10050) {
                    // 只打印前几次无效的载荷长度
                    if (headerFoundCount <= 3) {
                        Log.d(TAG, String.format("找到帧头但载荷长度无效: %d (pos=%d)", payloadLen, pos));
                    }
                    pos++;
                    continue;
                }

                payloadOkCount++;
                Log.d(TAG, String.format("找到有效帧头, payloadLen=%d, pos=%d", payloadLen, pos));

                int frameSize = HEADER_SIZE + LENGTH_SIZE + payloadLen + CHECKSUM_TAIL;

                // 检查是否有完整的帧
                if (pos + frameSize > data.length) {
                    Log.d(TAG, String.format("帧不完整, 需要更多数据: buffer=%d, 需要=%d", bufferLen, pos + frameSize));
                    break;
                }

                // 验证帧尾 0xDD
                int tailPos = pos + frameSize - 1;
                int tailByte = data[tailPos] & 0xFF;
                if (tailByte != 0xDD) {
                    Log.w(TAG, String.format("帧尾验证失败: 0x%02X (期望 0xDD), pos=%d", tailByte, pos));
                    pos++;
                    continue;
                }

                // 提取深度数据: 帧头(2) + 长度(2) + 元数据(16) + 深度(10000)
                int depthStart = pos + HEADER_SIZE + LENGTH_SIZE + META_SIZE;
                if (depthStart + 10000 <= data.length) {
                    byte[] depthData = new byte[10000];
                    System.arraycopy(data, depthStart, depthData, 0, 10000);
                    latestDepthData = depthData;
                    Log.i(TAG, "✓ 成功解析深度帧, 帧大小=" + frameSize);
                }

                // 移除已处理的帧
                int newStart = pos + frameSize;
                if (newStart < data.length) {
                    byte[] remaining = new byte[data.length - newStart];
                    System.arraycopy(data, newStart, remaining, 0, remaining.length);
                    buffer.reset();
                    try {
                        buffer.write(remaining);
                    } catch (Exception ignored) {}
                } else {
                    buffer.reset();
                }
                data = buffer.toByteArray();
                pos = 0;
            }
        }
    }

    // ==================== 深度数据 ====================

    /**
     * 单次获取深度数据
     */
    public void getDepthData(DepthCallback callback) {
        if (!running) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.USB_NOT_CONNECTED));
            return;
        }
        this.singleDepthCallback = callback;
        mainHandler.postDelayed(() -> {
            if (singleDepthCallback == callback && latestDepthData != null) {
                float[][] depthMatrix = convertToDistanceMatrix(latestDepthData);
                callback.onDepthData(depthMatrix, System.currentTimeMillis());
                singleDepthCallback = null;
            } else if (singleDepthCallback == callback) {
                callback.onError(MeasureError.of(MeasureError.ErrorCode.NO_DEPTH_DATA));
                singleDepthCallback = null;
            }
        }, 200);
    }

    /**
     * 连续获取深度数据
     */
    public void getDepthDataContinuous(ContinuousDepthCallback callback) {
        if (!running) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.USB_NOT_CONNECTED));
            return;
        }
        this.continuousDepthCallback = callback;
        startContinuousDepthDelivery();
    }

    /**
     * 停止连续获取深度数据
     */
    public void stopDepthData() {
        continuousDepthCallback = null;
    }

    private void startContinuousDepthDelivery() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (continuousDepthCallback != null && running) {
                    if (latestDepthData != null) {
                        float[][] depthMatrix = convertToDistanceMatrix(latestDepthData);
                        continuousDepthCallback.onDepthData(depthMatrix, System.currentTimeMillis());
                    }
                    mainHandler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    // ==================== 校准 ====================

    /**
     * 开始基线校准
     */
    public void calibrateBaseline(CalibrationCallback callback) {
        if (!running) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.USB_NOT_CONNECTED));
            return;
        }
        if (state == State.CALIBRATING) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.CALIBRATION_IN_PROGRESS));
            return;
        }
        if (state == State.MEASURING) {
            stopMeasure();
        }

        this.calibrationCallback = callback;
        this.state = State.CALIBRATING;
        this.calibrateStabilityDetector = new StabilityDetector(
                config.baselineAvgThreshold, config.stableCountCalibrate);
        calibrationFrames.clear();
        calibrateStableCount = 0;
        baselineDepth = null;

        Log.d(TAG, "开始基线校准");
        startWindowSampling(this::processCalibrationSample);
    }

    private void processCalibrationSample() {
        if (calibrationCallback == null || latestMedianDepth == null) return;

        float[][] currentFrame = copyDepthArray(latestMedianDepth);

        if (baselineDepth == null) {
            baselineDepth = copyDepthArray(currentFrame);
            calibrationFrames.add(copyDepthArray(currentFrame));
            calibrateStableCount = 1;
            notifyCalibrationProgress(1);
        } else {
            float avgDiff = MedianFilter.calculateAverageDifference(baselineDepth, currentFrame);

            if (avgDiff < config.baselineAvgThreshold) {
                calibrateStableCount++;
                calibrationFrames.add(copyDepthArray(currentFrame));
                notifyCalibrationProgress(calibrateStableCount);

                if (calibrateStableCount >= config.stableCountCalibrate) {
                    finishCalibration();
                }
            } else {
                baselineDepth = copyDepthArray(currentFrame);
                calibrationFrames.clear();
                calibrationFrames.add(copyDepthArray(currentFrame));
                calibrateStableCount = 1;
                notifyCalibrationProgress(1);
            }
        }
    }

    private void finishCalibration() {
        stopWindowSampling();

        baselineDepth = MedianFilter.medianFrames(calibrationFrames);
        int frameCount = calibrationFrames.size();
        calibrationFrames.clear();

        float centerDepth = baselineDepth[50][50];
        pixelSizeX = (float) (2.0 * centerDepth * config.tanFovHHalf / 100.0);
        pixelSizeY = (float) (2.0 * centerDepth * config.tanFovVHalf / 100.0);

        state = State.CALIBRATED;
        thresholdManager.reset();

        BaselineResult result = new BaselineResult(
                baselineDepth, centerDepth,
                pixelSizeX, pixelSizeY,
                frameCount, System.currentTimeMillis()
        );

        Log.d(TAG, String.format("校准完成: centerDepth=%.0fmm, pixelSize=%.2f×%.2fmm",
                centerDepth, pixelSizeX, pixelSizeY));

        if (calibrationCallback != null) {
            CalibrationCallback cb = calibrationCallback;
            calibrationCallback = null;
            cb.onSuccess(result);
        }
    }

    private void notifyCalibrationProgress(int current) {
        if (calibrationCallback != null) {
            final int c = current;
            final int total = config.stableCountCalibrate;
            mainHandler.post(() -> {
                if (calibrationCallback != null) {
                    calibrationCallback.onProgress(c, total, 0);
                }
            });
        }
    }

    /**
     * 是否已有基线数据
     */
    public boolean hasBaseline() {
        return baselineDepth != null;
    }

    /**
     * 获取当前基线数据
     */
    public BaselineResult getBaseline() {
        if (baselineDepth == null) return null;
        float centerDepth = baselineDepth[50][50];
        return new BaselineResult(
                baselineDepth, centerDepth,
                pixelSizeX, pixelSizeY,
                0, System.currentTimeMillis()
        );
    }

    /**
     * 清除基线数据
     */
    public void clearBaseline() {
        baselineDepth = null;
        state = State.IDLE;
    }

    // ==================== 测量 ====================

    /**
     * 单次测量
     */
    public void measureOnce(MeasureCallback callback) {
        if (!running) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.USB_NOT_CONNECTED));
            return;
        }
        if (!hasBaseline()) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.CALIBRATION_NOT_READY));
            return;
        }
        if (state == State.MEASURING) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.MEASUREMENT_IN_PROGRESS));
            return;
        }

        this.measureCallback = callback;
        this.state = State.MEASURING;
        this.measureStabilityDetector = new StabilityDetector(
                config.dimensionThreshold, config.stableCountMeasure);
        this.continuousMeasureCallback = null;
        thresholdManager.reset();

        Log.d(TAG, "开始单次测量");
        startWindowSampling(this::processMeasurementSample);
    }

    /**
     * 开始连续测量
     */
    public void startContinuousMeasure(ContinuousMeasureCallback callback) {
        if (!running) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.USB_NOT_CONNECTED));
            return;
        }
        if (!hasBaseline()) {
            callback.onError(MeasureError.of(MeasureError.ErrorCode.CALIBRATION_NOT_READY));
            return;
        }

        this.continuousMeasureCallback = callback;
        this.state = State.MEASURING;
        this.measureStabilityDetector = new StabilityDetector(
                config.dimensionThreshold, config.stableCountMeasure);
        this.measureCallback = null;
        thresholdManager.reset();

        Log.d(TAG, "开始连续测量");
        startWindowSampling(this::processMeasurementSample);
    }

    /**
     * 停止测量
     */
    public void stopMeasure() {
        stopWindowSampling();
        measureCallback = null;
        continuousMeasureCallback = null;
        if (state == State.MEASURING) {
            state = hasBaseline() ? State.CALIBRATED : State.IDLE;
        }
        Log.d(TAG, "测量已停止");
    }

    private void processMeasurementSample() {
        if ((measureCallback == null && continuousMeasureCallback == null) ||
                latestMedianDepth == null || baselineDepth == null) {
            return;
        }

        float[][] currentDepth = copyDepthArray(latestMedianDepth);

        ObjectDimensionCalculator calculator = new ObjectDimensionCalculator(
                baselineDepth, currentDepth,
                config.fovHorizontal, config.fovVertical,
                thresholdManager.getCurrentThreshold(), noiseMask
        );

        ObjectDimensionCalculator.DimensionResult result =
                calculator.calculateDimensionsByCalibratedRatioWithMedianData(pixelSizeX, pixelSizeY);

        MeasureResult measureResult = convertToMeasureResult(result);

        if (result == null || result.validPixelCount == 0) {
            notifyMeasureError(MeasureError.of(MeasureError.ErrorCode.OBJECT_NOT_DETECTED));
            return;
        }

        int xSpan = result.xMax - result.xMin + 1;
        int ySpan = result.yMax - result.yMin + 1;
        DynamicThresholdManager.ThresholdUpdateResult thresholdResult =
                thresholdManager.update(xSpan, ySpan);

        if (continuousMeasureCallback != null) {
            MeasureStatus status = new MeasureStatus(
                    thresholdResult.currentThreshold,
                    thresholdResult.thresholdLocked,
                    thresholdResult.unstableCount,
                    thresholdResult.stableCount
            );
            continuousMeasureCallback.onStatusChanged(status);
        }

        boolean stable = measureStabilityDetector.addAndCheck(
                result.width, result.length, result.height);

        if (measureCallback != null) {
            measureCallback.onProgress(
                    measureStabilityDetector.getStableCount(),
                    config.stableCountMeasure,
                    measureResult
            );

            if (measureStabilityDetector.isStableEnough()) {
                stopWindowSampling();
                state = State.CALIBRATED;

                MeasureResult finalResult = new MeasureResult(
                        measureStabilityDetector.getAverageWidth(),
                        measureStabilityDetector.getAverageLength(),
                        measureStabilityDetector.getAverageHeight(),
                        result.rawPixelCount, result.validPixelCount,
                        result.maxDepthDiff, result.objectSurfaceDepth,
                        result.xMin, result.xMax, result.yMin, result.yMax,
                        true, config.stableCountMeasure,
                        "测量完成",
                        result.dynamicThresholdX, result.dynamicThresholdY,
                        thresholdManager.getCurrentThreshold(), thresholdManager.isThresholdLocked()
                );

                Log.d(TAG, "单次测量完成: " + finalResult.toString());
                measureCallback.onSuccess(finalResult);
                measureCallback = null;
            }
        } else if (continuousMeasureCallback != null) {
            measureResult = new MeasureResult(
                    result.width, result.length, result.height,
                    result.rawPixelCount, result.validPixelCount,
                    result.maxDepthDiff, result.objectSurfaceDepth,
                    result.xMin, result.xMax, result.yMin, result.yMax,
                    stable, measureStabilityDetector.getStableCount(),
                    stable ? "稳定" : "不稳定",
                    result.dynamicThresholdX, result.dynamicThresholdY,
                    thresholdManager.getCurrentThreshold(), thresholdManager.isThresholdLocked()
            );

            continuousMeasureCallback.onResult(measureResult);
        }
    }

    private MeasureResult convertToMeasureResult(ObjectDimensionCalculator.DimensionResult r) {
        if (r == null) {
            return MeasureResult.empty();
        }
        return new MeasureResult(
                r.width, r.length, r.height,
                r.rawPixelCount, r.validPixelCount,
                r.maxDepthDiff, r.objectSurfaceDepth,
                r.xMin, r.xMax, r.yMin, r.yMax,
                false, 0, r.message,
                r.dynamicThresholdX, r.dynamicThresholdY,
                thresholdManager.getCurrentThreshold(), thresholdManager.isThresholdLocked()
        );
    }

    private void notifyMeasureError(MeasureError error) {
        if (measureCallback != null) {
            measureCallback.onError(error);
        } else if (continuousMeasureCallback != null) {
            if (error.code != MeasureError.ErrorCode.OBJECT_NOT_DETECTED) {
                continuousMeasureCallback.onError(error);
            }
        }
    }

    // ==================== 窗口采样 ====================

    private void startWindowSampling(Runnable sampleAction) {
        windowFrames.clear();
        windowMaxDepthDiffs.clear();
        windowFrameCount = 0;

        samplingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running || samplingRunnable != this) return;

                if (latestDepthData == null) {
                    samplingHandler.postDelayed(this, config.sampleFastIntervalMs);
                    return;
                }

                float[][] frame = convertToDistanceMatrix(latestDepthData);
                windowFrames.add(frame);
                windowFrameCount++;

                if (state == State.MEASURING && baselineDepth != null) {
                    float maxDiff = calculateMaxDepthDiff(frame);
                    windowMaxDepthDiffs.add(maxDiff);
                }

                if (windowFrameCount >= config.samplesPerWindow) {
                    if (state == State.MEASURING && !windowMaxDepthDiffs.isEmpty()) {
                        latestMedianDepth = MedianFilter.selectMedianMaxDiffFrame(
                                windowFrames, windowMaxDepthDiffs);
                    } else {
                        latestMedianDepth = MedianFilter.medianFrames(windowFrames);
                    }

                    windowFrames.clear();
                    windowMaxDepthDiffs.clear();
                    windowFrameCount = 0;

                    if (sampleAction != null) {
                        sampleAction.run();
                    }

                    samplingHandler.postDelayed(this, config.sampleIntervalMs);
                } else {
                    samplingHandler.postDelayed(this, config.sampleFastIntervalMs);
                }
            }
        };

        samplingHandler.post(samplingRunnable);
    }

    private void stopWindowSampling() {
        if (samplingRunnable != null) {
            samplingHandler.removeCallbacks(samplingRunnable);
            samplingRunnable = null;
        }
        windowFrames.clear();
        windowMaxDepthDiffs.clear();
        windowFrameCount = 0;
    }

    private float calculateMaxDepthDiff(float[][] frame) {
        float maxDiff = 0;
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                float bDepth = baselineDepth[x][y];
                float cDepth = frame[x][y];
                if (bDepth > 100 && bDepth < 800 && cDepth > 100 && cDepth < 800) {
                    float diff = bDepth - cDepth;
                    if (diff > maxDiff) {
                        maxDiff = diff;
                    }
                }
            }
        }
        return maxDiff;
    }

    // ==================== 工具方法 ====================

    public State getState() {
        return state;
    }

    public void setNoiseMask(boolean[][] mask) {
        this.noiseMask = mask;
    }

    public void clearNoiseMask() {
        this.noiseMask = null;
    }

    private float[][] convertToDistanceMatrix(byte[] depthData) {
        float[][] matrix = new float[100][100];
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                int pixelValue = depthData[x * 100 + y] & 0xFF;
                matrix[x][y] = calculateDistanceMm(pixelValue);
            }
        }
        return matrix;
    }

    private int calculateDistanceMm(int pixelValue) {
        if (pixelValue == 0) return 0;
        double distance = Math.pow(pixelValue / 5.1, 2);
        return (int) Math.round(distance);
    }

    private float[][] copyDepthArray(float[][] source) {
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private void notifyError(MeasureError error) {
        mainHandler.post(() -> {
            if (calibrationCallback != null) {
                calibrationCallback.onError(error);
            } else if (measureCallback != null) {
                measureCallback.onError(error);
            } else if (continuousMeasureCallback != null) {
                continuousMeasureCallback.onError(error);
            }
        });
    }
}
