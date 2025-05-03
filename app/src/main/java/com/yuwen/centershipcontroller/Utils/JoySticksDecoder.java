package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.yuwen.centershipcontroller.Socket.MainDeviceSocket;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 摇杆控制命令解码器
 * 负责将摇杆的原始输入转换为船舶电机控制命令
 * @author yuwen
 */
public class JoySticksDecoder {
    private static final String TAG = "JoySticksDecoder";

    // 控制参数
    private float normalizedX = 0f; // 标准化X轴值 [-1.0, 1.0]
    private float normalizedY = 0f; // 标准化Y轴值 [-1.0, 1.0]

    // 平滑处理后的控制参数
    private float smoothX = 0f;
    private float smoothY = 0f;
    private static final float SMOOTH_FACTOR = 0.3f; // 平滑因子，值越小平滑效果越强

    // 输出控制值
    private int leftThrust = 0;    // 左推进器推力 [0-100]
    private int rightThrust = 0;   // 右推进器推力 [0-100]
    private int leftDirection = 1; // 左推进器方向 (1:正向，0:反向)
    private int rightDirection = 1; // 右推进器方向 (1:正向，0:反向)
    private int leftEnable = 1;    // 左推进器使能 (0:开启，1:关闭) - 初始为关闭状态
    private int rightEnable = 1;   // 右推进器使能 (0:开启，1:关闭) - 初始为关闭状态

    // 安全控制参数
    private boolean isDirectionChanging = false;
    private long directionChangeTimestamp = 0;
    private static final long DIRECTION_CHANGE_DELAY = 500; // 方向切换延迟(毫秒)

    // 死区设置
    private static final float CENTER_DEAD_ZONE = 0.2f; // 中心死区半径
    private static final float EDGE_BUFFER_ZONE = 0.1f; // 边缘缓冲区

    // 采样与控制间隔
    private static final long COMMAND_INTERVAL = 300; // 控制命令发送间隔(毫秒)
    private Timer commandTimer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 命令状态跟踪
    private String lastCommandJson = ""; // 上次发送的命令
    private boolean isZeroCommand = true; // 是否处于零输入状态
    private boolean zeroCommandSent = false; // 是否已发送停止命令
    private boolean wasMoving = false; // 上一状态是否在移动
    private long lastCommandTime = 0; // 上次发送命令时间
    private static final long MAX_COMMAND_DELAY = 500; // 最大命令延迟(毫秒)

    // 处理状态锁
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isSending = new AtomicBoolean(false);

    // 采样读数缓冲
    private float sampledX = 0;
    private float sampledY = 0;
    private boolean hasNewSample = false;
    private boolean forceNextSend = false; // 强制下次发送标志

    // 震动相关参数
    private Context context;
    private Vibrator vibrator;
    private static final long DIRECTION_CHANGE_VIBRATION = 150; // 方向切换震动时长(毫秒)
    private static final long START_STOP_VIBRATION = 50; // 开始/停止震动时长(毫秒)
    private static final long EDGE_VIBRATION = 40; // 到达边缘震动时长(毫秒)
    private boolean hasReachedEdge = false; // 是否已到达边缘

    // 调试计数
    private int processCount = 0;
    private int sendCount = 0;

    // 单例模式
    private static JoySticksDecoder instance;

    public static synchronized JoySticksDecoder getInstance() {
        if (instance == null) {
            instance = new JoySticksDecoder();
        }
        return instance;
    }

    private JoySticksDecoder() {
        // 私有构造函数
    }

    /**
     * 初始化震动器
     * @param context 应用上下文
     */
    public void init(Context context) {
        this.context = context;

        // 初始化震动器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } catch (Exception e) {
                Log.e(TAG, "无法获取VibratorManager: " + e.getMessage());
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "设备不支持震动功能");
        } else {
            Log.d(TAG, "震动器初始化成功");
        }
    }

    /**
     * 触发震动
     * @param duration 震动时长(毫秒)
     */
    private void vibrate(long duration) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
            Log.d(TAG, "触发震动: " + duration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "震动触发失败: " + e.getMessage());
        }
    }

    /**
     * 启动控制命令定时发送
     */
    public void start() {
        if (commandTimer != null) {
            stop();
        }

        // 重置状态
        resetControls();
        zeroCommandSent = false;
        hasReachedEdge = false;
        wasMoving = false;
        processCount = 0;
        sendCount = 0;
        lastCommandTime = 0;
        forceNextSend = true; // 确保启动时发送初始命令

        // 发送初始零命令
        sendControlCommand();

        commandTimer = new Timer();
        commandTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 检查上次命令发送的时间，如果超过最大延迟，强制发送下一个命令
                long currentTime = System.currentTimeMillis();
                if (lastCommandTime > 0 && (currentTime - lastCommandTime) > MAX_COMMAND_DELAY) {
                    forceNextSend = true;
                    Log.w(TAG, "检测到命令发送延迟过长，强制发送下一个命令");
                }

                // 如果已经在处理中，则跳过此周期
                if (isProcessing.getAndSet(true)) {
                    Log.d(TAG, "跳过处理：正在进行中");
                    return;
                }

                try {
                    processSamples();
                    processCount++;
                    if (processCount % 10 == 0) {
                        Log.i(TAG, "处理统计: 处理=" + processCount + ", 发送=" + sendCount);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理样本时出错: " + e.getMessage(), e);
                } finally {
                    isProcessing.set(false);
                }
            }
        }, COMMAND_INTERVAL, COMMAND_INTERVAL);

        Log.d(TAG, "摇杆控制命令采样处理器已启动，间隔: " + COMMAND_INTERVAL + "ms");
    }

    /**
     * 处理采样数据，计算和发送控制命令
     */
    private void processSamples() {
        // 读取当前采样值，无论是否有新样本
        float currentX, currentY;
        synchronized (this) {
            currentX = sampledX;
            currentY = sampledY;
            hasNewSample = false;
        }

        // 保存旧状态用于比较
        boolean wasZeroCommand = isZeroCommand;
        float oldSmoothX = smoothX;
        float oldSmoothY = smoothY;

        // 应用平滑处理 - 即使没有新样本也应用平滑
        normalizedX = currentX;
        normalizedY = currentY;
        applySmoothing();

        // 计算值变化是否显著
        boolean significantChange = Math.abs(smoothX - oldSmoothX) > 0.05 ||
                Math.abs(smoothY - oldSmoothY) > 0.05;

        // 计算控制参数
        calculateThrust();

        // 检查是否需要发送命令:
        // 1. 强制发送标志
        // 2. 非零命令且未发送过停止命令
        // 3. 之前不是零命令但现在是(状态变化)
        // 4. 有显著变化的非零命令
        if (forceNextSend ||
                (!isZeroCommand) ||
                (wasZeroCommand != isZeroCommand) ||
                (significantChange && !isZeroCommand)) {

            sendControlCommand();

            // 如果是零命令则标记已发送
            if (isZeroCommand) {
                zeroCommandSent = true;
            }

            // 重置强制发送标志
            forceNextSend = false;
        }

        // 检测开始移动和停止移动的状态变化
        boolean isMovingNow = !isZeroCommand;
        if (wasMoving != isMovingNow) {
            wasMoving = isMovingNow;

            // 在主线程触发震动
            mainHandler.post(() -> {
                vibrate(START_STOP_VIBRATION);
            });

            Log.d(TAG, "移动状态变化: " + (isMovingNow ? "开始移动" : "停止移动"));
        }
    }

    /**
     * 应用平滑算法处理输入值
     */
    private void applySmoothing() {
        // 使用线性插值实现平滑过渡
        smoothX += (normalizedX - smoothX) * SMOOTH_FACTOR;
        smoothY += (normalizedY - smoothY) * SMOOTH_FACTOR;

        // 应用死区处理
        float length = (float) Math.hypot(smoothX, smoothY);
        if (length < CENTER_DEAD_ZONE) {
            smoothX = 0;
            smoothY = 0;
        } else {
            // 边缘缓冲处理
            float maxAllowedLength = 1.0f - EDGE_BUFFER_ZONE;
            if (length > maxAllowedLength) {
                float scaleFactor = maxAllowedLength / length;
                smoothX *= scaleFactor;
                smoothY *= scaleFactor;

                // 检测是否到达边缘，只震动一次
                if (!hasReachedEdge) {
                    hasReachedEdge = true;
                    mainHandler.post(() -> {
                        vibrate(EDGE_VIBRATION);
                    });
                }
            } else {
                // 离开边缘状态
                hasReachedEdge = false;
            }
        }
    }

    /**
     * 停止控制命令发送
     */
    public void stop() {
        if (commandTimer != null) {
            commandTimer.cancel();
            commandTimer = null;
            Log.d(TAG, "摇杆控制命令发送器已停止");
        }

        // 停止时发送一次停止命令
        resetControls();
        zeroCommandSent = false;  // 重置状态以便可以发送新的停止命令
        forceNextSend = true;     // 强制发送停止命令
        sendControlCommand();
    }

    /**
     * 更新摇杆输入值（由JoystickView调用）
     * 此方法不会立即处理，只是记录当前值供定时器采样
     * @param x 标准化X轴值 [-1.0, 1.0]
     * @param y 标准化Y轴值 [-1.0, 1.0]
     */
    public void updateJoystickValues(float x, float y) {
        synchronized (this) {
            sampledX = x;
            sampledY = y;
            hasNewSample = true;
        }
    }

    /**
     * 计算推力控制参数
     */
    private void calculateThrust() {
        // 使用平滑后的值计算
        // 计算总推力强度 (0-100)
        float length = (float) Math.hypot(smoothX, smoothY);
        int thrustPower = (int) (length * 100);

        // 限制推力范围
        thrustPower = Math.min(100, Math.max(0, thrustPower));

        // 检查是否为零输入状态
        if (thrustPower < 5) {
            if (!isZeroCommand) {
                isZeroCommand = true;
                zeroCommandSent = false; // 需要发送一次零命令
            }

            leftThrust = 0;
            rightThrust = 0;
            leftEnable = 1;  // 关闭
            rightEnable = 1; // 关闭
            return;
        } else {
            isZeroCommand = false;
        }

        // 确定方向 (Y值决定前进/后退)
        boolean isForward = smoothY >= 0;

        // 检测方向变化
        if ((isForward && (leftDirection == 0 || rightDirection == 0)) ||
                (!isForward && (leftDirection == 1 || rightDirection == 1))) {

            // 如果是首次检测到方向变化
            if (!isDirectionChanging) {
                isDirectionChanging = true;
                directionChangeTimestamp = System.currentTimeMillis();

                // 先停止电机
                leftEnable = 1;  // 关闭左推进器
                rightEnable = 1; // 关闭右推进器
                leftThrust = 0;
                rightThrust = 0;

                // 触发方向切换震动反馈
                mainHandler.post(() -> {
                    vibrate(DIRECTION_CHANGE_VIBRATION);
                });

                Log.d(TAG, "检测到方向变化，电机暂停，等待" + DIRECTION_CHANGE_DELAY + "ms");
                return;
            }
            // 如果正在等待方向切换
            else if (System.currentTimeMillis() - directionChangeTimestamp < DIRECTION_CHANGE_DELAY) {
                // 继续保持电机停止状态
                return;
            }
            // 方向切换延迟结束
            else {
                // 更新方向并重新启用电机
                leftDirection = isForward ? 1 : 0;
                rightDirection = isForward ? 1 : 0;
                isDirectionChanging = false;
                Log.d(TAG, "方向切换完成: " + (isForward ? "前进" : "后退"));
            }
        }

        // 启用电机
        leftEnable = 0;  // 开启
        rightEnable = 0; // 开启

        // 根据X轴方向差异计算左右推进器的不同推力
        // 负X值表示向左，正X值表示向右
        if (Math.abs(smoothX) < 0.1) {
            // 直线前进或后退
            leftThrust = thrustPower;
            rightThrust = thrustPower;
        } else if (smoothX < 0) {
            // 向左转 (减弱左推进器，保持右推进器)
            float turnRatio = 1 + smoothX; // 从1.0到0.0
            leftThrust = (int) (thrustPower * turnRatio);
            rightThrust = thrustPower;
        } else {
            // 向右转 (减弱右推进器，保持左推进器)
            float turnRatio = 1 - smoothX; // 从1.0到0.0
            leftThrust = thrustPower;
            rightThrust = (int) (thrustPower * turnRatio);
        }

        // 确保推力在有效范围内
        leftThrust = Math.min(100, Math.max(0, leftThrust));
        rightThrust = Math.min(100, Math.max(0, rightThrust));

        // 减少日志输出频率
        if (processCount % 5 == 0) {
            Log.d(TAG, "推力计算结果: 左=" + leftThrust + ", 右=" + rightThrust +
                    ", 方向=" + (isForward ? "前进" : "后退"));
        }
    }

    /**
     * 重置控制参数
     */
    private void resetControls() {
        leftThrust = 0;
        rightThrust = 0;
        leftEnable = 1;  // 关闭
        rightEnable = 1; // 关闭
        isDirectionChanging = false;
        smoothX = 0;
        smoothY = 0;
        normalizedX = 0;
        normalizedY = 0;
        sampledX = 0;
        sampledY = 0;
        isZeroCommand = true;
        hasReachedEdge = false;
    }

    /**
     * 生成并发送控制命令
     */
    private void sendControlCommand() {
        // 避免并发发送
        if (isSending.getAndSet(true)) {
            Log.w(TAG, "跳过命令发送：已有发送正在进行");
            return;
        }

        try {
            // 创建控制命令JSON
            JsonObject shipMotorObject = new JsonObject();
            shipMotorObject.addProperty("CH1", leftThrust);
            shipMotorObject.addProperty("DIR1", leftDirection);
            shipMotorObject.addProperty("EN1", leftEnable);
            shipMotorObject.addProperty("CH2", rightThrust);
            shipMotorObject.addProperty("DIR2", rightDirection);
            shipMotorObject.addProperty("EN2", rightEnable);

            JsonObject commandObject = new JsonObject();
            commandObject.add("SHIPMOTRO", shipMotorObject);

            final String commandJson = commandObject.toString();

            // 检查是否与上次命令相同，避免重复发送完全相同的命令
            if (commandJson.equals(lastCommandJson) && isZeroCommand && !forceNextSend) {
                Log.d(TAG, "跳过重复的零命令");
                return;
            }

            // 保存本次命令
            lastCommandJson = commandJson;

            // 记录发送时间
            lastCommandTime = System.currentTimeMillis();

            // 通过主线程发送WebSocket消息
            mainHandler.post(() -> {
                try {
                    // 检查WebSocket连接状态
                    MainDeviceSocket socketManager = MainDeviceSocket.getInstance();
                    if (socketManager.isConnected()) {
                        boolean success = WebSocketManager.getInstance().sendMessage(commandJson);
                        sendCount++;

                        if (success) {
                            Log.d(TAG, "控制命令已发送: " + commandJson);
                        } else {
                            Log.e(TAG, "控制命令发送失败");
                            forceNextSend = true; // 如果发送失败，强制下次重试
                        }
                    } else {
                        Log.w(TAG, "WebSocket未连接，无法发送控制命令");
                    }
                } finally {
                    isSending.set(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "发送命令时出错: " + e.getMessage(), e);
            isSending.set(false);
            forceNextSend = true; // 发生异常，强制下次重试
        }
    }
}
