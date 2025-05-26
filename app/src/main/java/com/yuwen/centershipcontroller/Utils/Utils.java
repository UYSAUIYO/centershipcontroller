package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.widget.Toast;

/**
 * 工具类，包含各种通用功能
 * @author yuwen404
 */
public class Utils {
    //时间计数器，最多只能到99小时，如需要更大小时数需要改改方法
    public static String showTimeCount(long time) {
        System.out.println("time="+time);
        if(time >= 360000000){
            return "00:00:00";
        }
        String timeCount = "";
        long hours = time/3600000;
        String hour = "0" + hours;
        System.out.println("hour="+hour);
        hour = hour.substring(hour.length()-2);
        System.out.println("hour2="+hour);

        long minuet = (time-hours*3600000)/(60000);
        String minue = "0" + minuet;
        System.out.println("minue="+minue);
        minue = minue.substring(minue.length()-2);
        System.out.println("minue2="+minue);

        long secc = (time-hours*3600000-minuet*60000)/1000;
        String sec = "0" + secc;
        System.out.println("sec="+sec);
        sec = sec.substring(sec.length()-2);
        System.out.println("sec2="+sec);
        timeCount = hour + ":" + minue + ":" + sec;
        System.out.println("timeCount="+timeCount);
        return timeCount;
    }
    /**
     * Toast提示
     * @param msg 提示内容
     */
    public static void showMsg(Context context, String msg){
        Toast.makeText(context,msg,Toast.LENGTH_SHORT).show();
    }

    /**
     * 限制值在指定范围内
     * @param value 需要限制的值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 超低延迟滤波器接口
     * 定义通用滤波器接口，使不同滤波算法可以互换使用
     */
    public interface LowLatencyFilter {
        /**
         * 重置滤波器状态
         */
        void reset();

        /**
         * 更新一个坐标轴的值
         * @param value 输入值
         * @return 滤波后的值
         */
        float update(float value);

        /**
         * 同时更新两个坐标的值
         * @param x X轴输入值
         * @param y Y轴输入值
         * @return 包含滤波后[x,y]的数组
         */
        float[] update(float x, float y);
    }

    /**
     * 一阶滞后滤波器(First-Order Lag Filter)
     * 极低计算复杂度，适合对实时性要求高的控制场景
     */
    public static class FastLagFilter implements LowLatencyFilter {
        // 滤波系数，范围[0,1]：0表示完全使用上次值，1表示完全使用当前值
        private final float alpha;

        // 上一次滤波结果
        private float lastX = 0;
        private float lastY = 0;

        // 滤波器是否初始化
        private boolean initialized = false;

        /**
         * 创建快速滞后滤波器
         * @param alpha 滤波系数[0,1]，值越小滤波效果越强，但响应越慢
         */
        public FastLagFilter(float alpha) {
            // 确保alpha在有效范围内
            this.alpha = clamp(alpha, 0.01f, 1.0f);
        }

        /**
         * 创建默认参数的快速滤波器
         */
        public FastLagFilter() {
            // 默认使用0.3的系数，平衡了平滑性和响应速度
            this(0.3f);
        }

        @Override
        public void reset() {
            lastX = 0;
            lastY = 0;
            initialized = false;
        }

        @Override
        public float update(float value) {
            // 如果是第一次调用，直接返回输入值
            if (!initialized) {
                lastX = value;
                initialized = true;
                return value;
            }

            // 一阶滞后滤波公式: y(n) = alpha*x(n) + (1-alpha)*y(n-1)
            // 只有一次乘法和一次加法，极低计算复杂度
            lastX = alpha * value + (1 - alpha) * lastX;
            return lastX;
        }

        @Override
        public float[] update(float x, float y) {
            float filteredX = update(x);
            // 使用相同公式处理Y轴
            if (!initialized) {
                lastY = y;
                initialized = true;
                return new float[]{filteredX, y};
            }

            lastY = alpha * y + (1 - alpha) * lastY;
            return new float[]{filteredX, lastY};
        }
    }

    /**
     * 无滤波直通器 - 用于完全禁用滤波，获得最低延迟
     */
    public static class NoFilter implements LowLatencyFilter {
        @Override
        public void reset() {
            // 无状态，不需要重置
        }

        @Override
        public float update(float value) {
            // 直接返回输入值，无滤波
            return value;
        }

        @Override
        public float[] update(float x, float y) {
            // 直接返回输入值，无滤波
            return new float[]{x, y};
        }
    }

    /**
     * 卡尔曼滤波器 - 更高精度的滤波，但计算复杂度较高
     * 适用于需要精确控制且能接受一定延迟的场景
     */
    public static class KalmanFilter implements LowLatencyFilter {
        // 状态估计值
        private float stateX = 0;
        private float stateY = 0;

        // 估计误差协方差
        private float errorCovarianceX = 1.0f;
        private float errorCovarianceY = 1.0f;

        // 过程噪声协方差
        private final float processNoise;

        // 测量噪声协方差
        private final float measurementNoise;

        /**
         * 创建卡尔曼滤波器
         */
        public KalmanFilter(float processNoise, float measurementNoise) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
        }

        /**
         * 创建默认参数的卡尔曼滤波器
         */
        public KalmanFilter() {
            this(0.01f, 0.1f);
        }

        @Override
        public void reset() {
            stateX = 0;
            stateY = 0;
            errorCovarianceX = 1.0f;
            errorCovarianceY = 1.0f;
        }

        @Override
        public float update(float measurement) {
            // 预测阶段
            errorCovarianceX = errorCovarianceX + processNoise;

            // 更新阶段
            float kalmanGain = errorCovarianceX / (errorCovarianceX + measurementNoise);
            stateX = stateX + kalmanGain * (measurement - stateX);
            errorCovarianceX = (1 - kalmanGain) * errorCovarianceX;

            return stateX;
        }

        @Override
        public float[] update(float x, float y) {
            float filteredX = update(x);

            // 对Y轴执行相同操作
            errorCovarianceY = errorCovarianceY + processNoise;
            float kalmanGain = errorCovarianceY / (errorCovarianceY + measurementNoise);
            stateY = stateY + kalmanGain * (y - stateY);
            errorCovarianceY = (1 - kalmanGain) * errorCovarianceY;

            return new float[]{filteredX, stateY};
        }
    }
}
