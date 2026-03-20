package com.example.stepperfeedback.measurement;

import android.util.Log;

/**
 * 物体三维尺寸计算器
 *
 * 基于 3D 坐标变换计算放置在桌面上的物体的长、宽、高
 *
 * 原理：
 * 1. 深度图 → 3D 点云（相机坐标系）
 * 2. 45° 旋转变换 → 世界坐标系（垂直向下）
 * 3. 计算物体点云的 AABB（轴对齐包围盒）
 * 4. 从 AABB 获取真实长宽高
 */
public class ObjectDimensionCalculator {

    private static final String TAG = "DimensionCalc";
    private static final int MIN_PROJECTION_COUNT_ABSOLUTE = 10; // 绝对最小阈值（防止噪声）
    private static final float PROJECTION_THRESHOLD_RATIO = 0.7f; // 动态阈值 = maxCount * ratio
    private static final int MIN_CONSECUTIVE_PIXELS = 8;  // 连续像素法: 连续>=8个有效像素才算边界
    private int dynamicThresholdX = MIN_PROJECTION_COUNT_ABSOLUTE; // X方向动态阈值
    private int dynamicThresholdY = MIN_PROJECTION_COUNT_ABSOLUTE; // Y方向动态阈值

    // 45° 旋转相关常量
    private static final double TILT_ANGLE_DEG = 45.0;
    private static final double TILT_ANGLE_RAD = Math.toRadians(TILT_ANGLE_DEG);
    private static final double COS_TILT = Math.cos(TILT_ANGLE_RAD);  // cos(45°) ≈ 0.707
    private static final double SIN_TILT = Math.sin(TILT_ANGLE_RAD);  // sin(45°) ≈ 0.707

    // 输入数据
    private final float[][] baselineDepth;  // 空桌面深度 (mm)
    private final float[][] currentDepth;   // 放置物体后的深度 (mm)
    private final float fovHorizontal;      // 水平视场角 (度)
    private final float fovVertical;        // 垂直视场角 (度)
    private final float threshold;          // 物体检测阈值 (mm)
    private final boolean[][] noiseMask;    // 噪声像素黑名单，true=排除

    // 计算结果
    private final int[][] mask;             // 物体掩码: 1=物体, 0=背景
    private float width;              // 物体宽度 W (mm) - X 方向
    private float length;             // 物体长度 L (mm) - Y 方向
    private float height;             // 物体高度 H (mm) - Z 方向

    // 行列投影（用于噪声过滤）
    private int[] colCount;
    private int[] rowCount;

    // 调试信息
    private int xMin, xMax, yMin, yMax;
    private int objectPixelCount;
    private int validPixelCount;
    private int maxDiffX = -1, maxDiffY = -1;
    private float maxDiffBaselineDepth = 0;
    private float maxDiffCurrentDepth = 0;

    // 用于新计算顺序
    private float maxDepthDiffValue = 0;  // 最大深度差值
    private float baselineDepthAtMaxDiff = 0;  // 最大深度差位置的基线深度

    // 3D 点云存储（世界坐标系）
    private final float[] worldX;  // 物体点云的 X 坐标
    private final float[] worldY;  // 物体点云的 Y 坐标
    private final float[] worldZ;  // 物体点云的 Z 坐标
    private int pointCloudCount;

    /**
     * 构造函数
     */
    public ObjectDimensionCalculator(float[][] baselineDepth, float[][] currentDepth,
                                     float fovHorizontal, float fovVertical, float threshold) {
        this(baselineDepth, currentDepth, fovHorizontal, fovVertical, threshold, null);
    }

    public ObjectDimensionCalculator(float[][] baselineDepth, float[][] currentDepth,
                                     float fovHorizontal, float fovVertical, float threshold,
                                     boolean[][] noiseMask) {
        // 边界检查
        if (baselineDepth == null || baselineDepth.length == 0 ||
            baselineDepth[0] == null || baselineDepth[0].length == 0) {
            throw new IllegalArgumentException("baselineDepth 无效: 不能为null或空数组");
        }
        if (currentDepth == null || currentDepth.length == 0 ||
            currentDepth[0] == null || currentDepth[0].length == 0) {
            throw new IllegalArgumentException("currentDepth 无效: 不能为null或空数组");
        }

        this.baselineDepth = baselineDepth;
        this.currentDepth = currentDepth;
        this.fovHorizontal = fovHorizontal;
        this.fovVertical = fovVertical;
        this.threshold = threshold;
        this.noiseMask = noiseMask;
        this.mask = new int[baselineDepth.length][baselineDepth[0].length];

        // 预分配点云存储空间（最大 100x100 = 10000 点）
        int maxPoints = baselineDepth.length * baselineDepth[0].length;
        this.worldX = new float[maxPoints];
        this.worldY = new float[maxPoints];
        this.worldZ = new float[maxPoints];
    }

    /**
     * 使用校准比例计算物体尺寸（推荐方法）
     *
     * 按照文档要求的流程：
     * 1. 生成 mask
     * 2. 用直接连续像素法确定边界
     * 3. 先计算高度
     * 4. 计算物体表面深度
     * 5. 计算像素尺寸
     * 6. 计算长宽
     *
     * @param pixelSizeX X方向每像素对应的物理尺寸 (mm/像素) - 校准时计算的
     * @param pixelSizeY Y方向每像素对应的物理尺寸 (mm/像素) - 校准时计算的
     * @return 尺寸计算结果
     */
    public DimensionResult calculateDimensionsByCalibratedRatioWithMedianData(
            float pixelSizeX, float pixelSizeY) {

        // Step 1: 生成物体 mask
        generateMask();

        if (objectPixelCount == 0) {
            return new DimensionResult(0, 0, 0, 0, 0, "未检测到物体", -1, -1, -1, -1);
        }

        // Step 2: 使用直接连续像素法确定边界 (按照文档要求)
        int[] bounds = findEdgeByDirectConsecutivePixels();
        xMin = bounds[0];
        xMax = bounds[1];
        yMin = bounds[2];
        yMax = bounds[3];

        // 检查边界是否有效
        if (xMin < 0 || yMin < 0) {
            return new DimensionResult(0, 0, 0, objectPixelCount, 0, "边界检测无效(物体可能太小)", xMin, xMax, yMin, yMax);
        }

        // Step 3: 计算有效像素数（在边界范围内的 mask=1 像素）
        validPixelCount = 0;
        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;

        // 同时计算投影（用于统计）
        colCount = new int[cols];
        rowCount = new int[rows];

        for (int x = xMin; x <= xMax && x < cols; x++) {
            for (int y = yMin; y <= yMax && y < rows; y++) {
                if (mask[x][y] == 1) {
                    validPixelCount++;
                    colCount[x]++;
                    rowCount[y]++;
                }
            }
        }

        if (validPixelCount == 0) {
            return new DimensionResult(0, 0, 0, objectPixelCount, 0, "有效像素为空", xMin, xMax, yMin, yMax);
        }

        // Step 3.5: 计算动态阈值（用于后续过滤）
        calculateDynamicThresholds();

        // Step 4: 先计算高度 (maxDepthDiff × cos(45°))
        calculateHeightByDepthDiff();

        // Step 5: 计算物体表面深度 = 基线深度 - 最大深度差
        // 物体表面深度 = 传感器到物体表面的距离 (比桌面更近)
        float objectSurfaceDepth = baselineDepthAtMaxDiff - maxDepthDiffValue;
        if (objectSurfaceDepth <= 0) {
            // 回退到中心深度
            objectSurfaceDepth = getCenterDepth();
            if (objectSurfaceDepth <= 0 && xMin >= 0 && yMin >= 0) {
                int centerX = (xMin + xMax) / 2;
                int centerY = (yMin + yMax) / 2;
                if (centerX >= 0 && centerX < cols && centerY >= 0 && centerY < rows) {
                    objectSurfaceDepth = currentDepth[centerX][centerY];
                }
            }
        }

        // Step 6: 基于物体表面深度动态计算像素尺寸
        // 物体表面越近，每像素对应的物理尺寸越小
        int xSpan = xMax - xMin + 1;
        int ySpan = yMax - yMin + 1;

        double tanFovHHalf = Math.tan(Math.toRadians(fovHorizontal / 2.0));
        double tanFovVHalf = Math.tan(Math.toRadians(fovVertical / 2.0));
        float dynamicPixelSizeX = (float) (2.0 * objectSurfaceDepth * tanFovHHalf / 100.0);
        float dynamicPixelSizeY = (float) (2.0 * objectSurfaceDepth * tanFovVHalf / 100.0);

        // Step 7: 最后计算长宽
        width = xSpan * dynamicPixelSizeX;
        length = ySpan * dynamicPixelSizeY;

        Log.d(TAG, String.format(
                "═══ 直接连续像素法计算结果 ═══\n" +
                "像素跨度: X=%d, Y=%d\n" +
                "最大深度差: %.1fmm\n" +
                "物体表面深度: %.1fmm\n" +
                "动态像素尺寸: X=%.2f, Y=%.2f mm/像素\n" +
                "动态阈值: X=%d, Y=%d\n" +
                "计算结果: W=%.1fmm, L=%.1fmm, H=%.1fmm",
                xSpan, ySpan, maxDepthDiffValue, objectSurfaceDepth,
                dynamicPixelSizeX, dynamicPixelSizeY, dynamicThresholdX, dynamicThresholdY,
                width, length, height
        ));

        return new DimensionResult(width, length, height, objectPixelCount, validPixelCount,
                "计算成功(直接连续像素法)", xMin, xMax, yMin, yMax,
                maxDepthDiffValue, objectSurfaceDepth, dynamicThresholdX, dynamicThresholdY);
    }

    /**
     * 生成mask
     */
    public void generateMask() {
        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;

        objectPixelCount = 0;

        // 验证 noiseMask 维度
        boolean useNoiseMask = noiseMask != null;
        if (useNoiseMask) {
            if (noiseMask.length != cols || (noiseMask.length > 0 && noiseMask[0].length != rows)) {
                Log.w(TAG, "noiseMask 维度与深度数据不匹配，忽略 noiseMask");
                useNoiseMask = false;
            }
        }

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (useNoiseMask && noiseMask[x][y]) {
                    mask[x][y] = 0;
                    continue;
                }

                float bDepth = baselineDepth[x][y];
                float cDepth = currentDepth[x][y];

                // 基本有效性检查
                if (bDepth <= 0 || cDepth <= 0) {
                    mask[x][y] = 0;
                    continue;
                }

                // baseline 值必须在合理范围内
                if (bDepth < 100 || bDepth > 800) {
                    mask[x][y] = 0;
                    continue;
                }

                // 深度差必须在合理范围内
                float depthDiff = bDepth - cDepth;
                if (depthDiff < 0 || depthDiff > 800) {
                    mask[x][y] = 0;
                    continue;
                }

                if (depthDiff > threshold) {
                    mask[x][y] = 1;
                    objectPixelCount++;
                } else {
                    mask[x][y] = 0;
                }
            }
        }
    }

    /**
     * 计算动态阈值
     */
    private void calculateDynamicThresholds() {
        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;

        int maxColCount = 0, maxRowCount = 0;
        for (int x = 0; x < cols; x++) {
            if (colCount[x] > maxColCount) maxColCount = colCount[x];
        }
        for (int y = 0; y < rows; y++) {
            if (rowCount[y] > maxRowCount) maxRowCount = rowCount[y];
        }

        dynamicThresholdX = Math.max(MIN_PROJECTION_COUNT_ABSOLUTE,
                (int)(maxColCount * PROJECTION_THRESHOLD_RATIO));
        dynamicThresholdY = Math.max(MIN_PROJECTION_COUNT_ABSOLUTE,
                (int)(maxRowCount * PROJECTION_THRESHOLD_RATIO));

        Log.d(TAG, String.format("动态阈值: X=%d, Y=%d (maxCol=%d, maxRow=%d)",
                dynamicThresholdX, dynamicThresholdY, maxColCount, maxRowCount));
    }

    /**
     * 使用深度差异计算高度
     * 同时保存最大深度差值和对应位置的基线深度，供后续计算物体表面深度使用
     */
    private void calculateHeightByDepthDiff() {
        float maxDepthDiff = 0;
        float baselineAtMaxDiff = 0;

        for (int x = xMin; x <= xMax; x++) {
            if (colCount[x] < dynamicThresholdX) continue;  // 动态阈值过滤
            for (int y = yMin; y <= yMax; y++) {
                if (rowCount[y] < dynamicThresholdY) continue;  // 动态阈值过滤
                if (mask[x][y] != 1) continue;

                float diff = baselineDepth[x][y] - currentDepth[x][y];
                if (diff > maxDepthDiff) {
                    maxDepthDiff = diff;
                    baselineAtMaxDiff = baselineDepth[x][y];
                    maxDiffX = x;
                    maxDiffY = y;
                    maxDiffBaselineDepth = baselineDepth[x][y];
                    maxDiffCurrentDepth = currentDepth[x][y];
                }
            }
        }

        // 保存最大深度差和对应基线深度，供后续计算物体表面深度
        maxDepthDiffValue = maxDepthDiff;
        baselineDepthAtMaxDiff = baselineAtMaxDiff;

        // 高度 = 最大深度差 × cos(45°)
        height = maxDepthDiff * (float) COS_TILT;
        if (height < 0) height = 0;
    }

    /**
     * 连续像素法边界检测 - 直接在 mask 上扫描
     *
     * 原理：直接在 mask 上找连续 >=MIN_CONSECUTIVE_PIXELS(8) 个 mask=1 像素
     * 如果某行/列有连续 >=8 个 mask=1，该行/列有效
     * 边界 = min/max(有效行/列)
     *
     * @return [xMin, xMax, yMin, yMax]
     */
    private int[] findEdgeByDirectConsecutivePixels() {
        int cols = mask.length;
        if (cols == 0) {
            return new int[]{-1, -1, -1, -1};
        }
        int rows = mask[0].length;
        if (rows == 0) {
            return new int[]{-1, -1, -1, -1};
        }

        if (cols < MIN_CONSECUTIVE_PIXELS || rows < MIN_CONSECUTIVE_PIXELS) {
            return new int[]{-1, -1, -1, -1};
        }

        // 找有效列：每列有多少个连续的 mask=1
        int xMin = -1, xMax = -1;
        for (int x = 0; x < cols; x++) {
            int consecutive = 0;
            for (int y = 0; y < rows; y++) {
                if (mask[x][y] == 1) {
                    consecutive++;
                    if (consecutive >= MIN_CONSECUTIVE_PIXELS) {
                        // 该列有效
                        if (xMin < 0) xMin = x;
                        xMax = x;
                        break;  // 跳到下一列
                    }
                } else {
                    consecutive = 0;
                }
            }
        }

        // 找有效行：每行有多少个连续的 mask=1
        int yMin = -1, yMax = -1;
        for (int y = 0; y < rows; y++) {
            int consecutive = 0;
            for (int x = 0; x < cols; x++) {
                if (mask[x][y] == 1) {
                    consecutive++;
                    if (consecutive >= MIN_CONSECUTIVE_PIXELS) {
                        // 该行有效
                        if (yMin < 0) yMin = y;
                        yMax = y;
                        break;  // 跳到下一行
                    }
                } else {
                    consecutive = 0;
                }
            }
        }

        // 验证结果有效性
        if (xMin < 0 || yMin < 0) {
            return new int[]{-1, -1, -1, -1};
        }

        Log.d(TAG, String.format("直接连续像素法边界: x[%d-%d] y[%d-%d]", xMin, xMax, yMin, yMax));

        return new int[]{xMin, xMax, yMin, yMax};
    }

    /**
     * 获取中心深度（有效像素的平均深度或边界中心点深度）
     */
    public float getCenterDepth() {
        if (xMin < 0 || yMin < 0) return 0;

        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;

        // 方法1: 计算边界中心点的深度
        int centerX = (xMin + xMax) / 2;
        int centerY = (yMin + yMax) / 2;

        // 方法2: 计算有效像素的平均深度
        float sumDepth = 0;
        int count = 0;

        // 检查 colCount/rowCount 是否已初始化
        boolean useDynamicThreshold = (colCount != null && rowCount != null);

        for (int x = xMin; x <= xMax; x++) {
            if (x >= cols) continue;
            if (useDynamicThreshold && colCount[x] < dynamicThresholdX) continue;  // 动态阈值过滤
            for (int y = yMin; y <= yMax; y++) {
                if (y >= rows) continue;
                if (useDynamicThreshold && rowCount[y] < dynamicThresholdY) continue;  // 动态阈值过滤
                if (mask[x][y] != 1) continue;
                float depth = currentDepth[x][y];
                if (depth > 0) {
                    sumDepth += depth;
                    count++;
                }
            }
        }

        if (count > 0) {
            return sumDepth / count;
        }

        // 如果没有有效像素，返回边界中心点深度（带边界检查）
        if (centerX >= 0 && centerX < cols && centerY >= 0 && centerY < rows) {
            return currentDepth[centerX][centerY];
        }
        return 0;
    }

    // ==================== 统计信息 ====================

    public String getMaskDepthDiffStats() {
        if (objectPixelCount == 0) return "无 mask=1 像素";

        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;
        float minDiff = Float.MAX_VALUE, maxDiff = 0, sumDiff = 0;

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (mask[x][y] == 1) {
                    float diff = baselineDepth[x][y] - currentDepth[x][y];
                    if (diff < minDiff) minDiff = diff;
                    if (diff > maxDiff) maxDiff = diff;
                    sumDiff += diff;
                }
            }
        }

        float avgDiff = sumDiff / objectPixelCount;

        return String.format("Mask=1: %d个, 深度差: min=%.0f max=%.0f avg=%.0f mm",
                objectPixelCount, minDiff, maxDiff, avgDiff);
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ 3D 坐标变换法 ═══\n");
        sb.append(String.format("原始Mask: %d → 有效: %d\n", objectPixelCount, validPixelCount));
        sb.append(String.format("FOV: H=%.0f° V=%.0f°, 倾斜: %.0f°\n", fovHorizontal, fovVertical, TILT_ANGLE_DEG));
        sb.append(String.format("动态阈值: X=%d, Y=%d\n", dynamicThresholdX, dynamicThresholdY));
        sb.append(String.format("点云数量: %d\n", pointCloudCount));
        sb.append("───── AABB 尺寸 ─────\n");
        sb.append(String.format("宽度 W(X): %.1f mm\n", width));
        sb.append(String.format("长度 L(Y): %.1f mm\n", length));
        sb.append(String.format("高度 H(Z): %.1f mm\n", height));
        sb.append("══════════════════════");
        return sb.toString();
    }

    // ==================== Getters ====================

    public int[][] getMask() { return mask; }

    /**
     * 获取colCount数组（用于多帧中值滤波）
     */
    public int[] getColCount() {
        if (colCount == null) return null;
        return colCount.clone();
    }

    /**
     * 获取rowCount数组（用于多帧中值滤波）
     */
    public int[] getRowCount() {
        if (rowCount == null) return null;
        return rowCount.clone();
    }

    /**
     * 仅计算行列投影（供多帧中值滤波使用）
     */
    public void calculateProjectionOnly() {
        int cols = baselineDepth.length;
        int rows = baselineDepth[0].length;

        colCount = new int[cols];
        rowCount = new int[rows];

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (mask[x][y] == 1) {
                    colCount[x]++;
                    rowCount[y]++;
                }
            }
        }
    }

    public int getMinProjectionCount() { return dynamicThresholdX; }
    public float getWidth() { return width; }
    public float getLength() { return length; }
    public float getHeight() { return height; }
    public int getObjectPixelCount() { return objectPixelCount; }
    public int getValidPixelCount() { return validPixelCount; }
    public int getXMin() { return xMin; }
    public int getXMax() { return xMax; }
    public int getYMin() { return yMin; }
    public int getYMax() { return yMax; }
    public int getMaxDiffX() { return maxDiffX; }
    public int getMaxDiffY() { return maxDiffY; }
    public float getMaxDiffBaselineDepth() { return maxDiffBaselineDepth; }
    public float getMaxDiffCurrentDepth() { return maxDiffCurrentDepth; }

    public boolean isValidPixel(int x, int y) {
        // 边界检查
        if (x < 0 || x >= mask.length || y < 0 || y >= mask[0].length) {
            return false;
        }
        if (mask[x][y] != 1) return false;
        if (colCount == null || rowCount == null) return false;
        return colCount[x] >= dynamicThresholdX && rowCount[y] >= dynamicThresholdY;
    }

    public float getDepthDiffAt(int x, int y) {
        if (x < 0 || x >= baselineDepth.length || y < 0 || y >= baselineDepth[0].length) {
            return 0;
        }
        return baselineDepth[x][y] - currentDepth[x][y];
    }

    /**
     * 计算结果封装类
     */
    public static class DimensionResult {
        public final float width;
        public final float length;
        public final float height;
        public final int rawPixelCount;
        public final int validPixelCount;
        public final String message;
        // 有效像素边界范围
        public final int xMin, xMax, yMin, yMax;
        // 新增字段
        public final float maxDepthDiff;         // 最大深度差 (mm)
        public final float objectSurfaceDepth;   // 物体表面深度 (mm)
        public final int dynamicThresholdX;      // 动态阈值 X
        public final int dynamicThresholdY;      // 动态阈值 Y

        public DimensionResult(float width, float length, float height,
                               int rawPixelCount, int validPixelCount, String message,
                               int xMin, int xMax, int yMin, int yMax) {
            this(width, length, height, rawPixelCount, validPixelCount, message,
                    xMin, xMax, yMin, yMax, 0, 0, 0, 0);
        }

        public DimensionResult(float width, float length, float height,
                               int rawPixelCount, int validPixelCount, String message,
                               int xMin, int xMax, int yMin, int yMax,
                               float maxDepthDiff, float objectSurfaceDepth) {
            this(width, length, height, rawPixelCount, validPixelCount, message,
                    xMin, xMax, yMin, yMax, maxDepthDiff, objectSurfaceDepth, 0, 0);
        }

        public DimensionResult(float width, float length, float height,
                               int rawPixelCount, int validPixelCount, String message,
                               int xMin, int xMax, int yMin, int yMax,
                               float maxDepthDiff, float objectSurfaceDepth,
                               int dynamicThresholdX, int dynamicThresholdY) {
            this.width = width;
            this.length = length;
            this.height = height;
            this.rawPixelCount = rawPixelCount;
            this.validPixelCount = validPixelCount;
            this.message = message;
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
            this.maxDepthDiff = maxDepthDiff;
            this.objectSurfaceDepth = objectSurfaceDepth;
            this.dynamicThresholdX = dynamicThresholdX;
            this.dynamicThresholdY = dynamicThresholdY;
        }

        @Override
        public String toString() {
            return String.format("W=%.1fmm, L=%.1fmm, H=%.1fmm | %s", width, length, height, message);
        }
    }
}
