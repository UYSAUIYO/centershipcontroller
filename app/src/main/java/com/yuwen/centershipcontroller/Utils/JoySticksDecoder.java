package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.yuwen.centershipcontroller.Socket.MainDeviceSocket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 摇杆控制命令解码器
 * 负责将摇杆的原始输入转换为船舶电机控制命令
 * 使用无锁设计避免线程阻塞
 * @author yuwen
 */
public class JoySticksDecoder {
    private static final String TAG = "JoySticksDecoder";

    // 摇杆输入结构
    private static class JoystickInput {
        float x;
        float y;
        long timestamp;

        JoystickInput(float x, float y) {
            this.x = x;
            this.y = y;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // 控制命令结构
    private static class ControlCommand {
        int leftThrust;
        int rightThrust;
        int leftDirection;
        int rightDirection;
        int leftEnable;
        int rightEnable;
        boolean isZeroCommand;
        long timestamp;

        ControlCommand(int leftThrust, int rightThrust, int leftDirection, int rightDirection,
                       int leftEnable, int rightEnable, boolean isZeroCommand) {
            this.leftThrust = leftThrust;
            this.rightThrust = rightThrust;
            this.leftDirection = leftDirection;
            this.rightDirection = rightDirection;
            this.leftEnable = leftEnable;
            this.rightEnable = rightEnable;
            this.isZeroCommand = isZeroCommand;
            this.timestamp = System.currentTimeMillis();
        }

        // 创建零命令
        static ControlCommand createZeroCommand() {
            return new ControlCommand(0, 0, 1, 1, 1, 1, true);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            ControlCommand that = (ControlCommand) obj;
            return leftThrust == that.leftThrust &&
                    rightThrust == that.rightThrust &&
                    leftDirection == that.leftDirection &&
                    rightDirection == that.rightDirection &&
                    leftEnable == that.leftEnable &&
                    rightEnable == that.rightEnable;
        }

        @Override
        public String toString() {
            return "ControlCommand{" +
                    "leftThrust=" + leftThrust +
                    ", rightThrust=" + rightThrust +
                    ", leftDirection=" + leftDirection +
                    ", rightDirection=" + rightDirection +
                    ", leftEnable=" + leftEnable +
                    ", rightEnable=" + rightEnable +
                    ", isZeroCommand=" + isZeroCommand +
                    '}';
        }
    }

    // 使用无锁队列替代带锁的BlockingQueue
    private final ConcurrentLinkedQueue<JoystickInput> inputQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ControlCommand> commandQueue = new ConcurrentLinkedQueue<>();

    // 使用原子引用替代锁
    private final AtomicReference<JoystickInput> latestInput = new AtomicReference<>();
    private final AtomicReference<ControlCommand> latestCommand = new AtomicReference<>(ControlCommand.createZeroCommand());

    private Thread processingThread; // 输入处理线程
    private Thread sendingThread; // 命令发送线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler backgroundHandler;

    // 控制参数
    private float smoothX = 0f; // 平滑后的X轴值
    private float smoothY = 0f; // 平滑后的Y轴值
    private static final float SMOOTH_FACTOR = 0.4f; // 平滑因子，增大以加速响应

    // 配置参数 - 减少延迟
    private static final float CENTER_DEAD_ZONE = 0.2f; // 中心死区半径
    private static final float EDGE_BUFFER_ZONE = 0.1f; // 边缘缓冲区
    private static final long DIRECTION_CHANGE_DELAY = 300; // 方向切换延迟，减少到300ms
    private static final long INPUT_TIMEOUT = 50; // 输入处理超时，减少到50ms
    private static final long COMMAND_TIMEOUT = 50; // 命令发送超时，减少到50ms
    private static final long BUFFER_CLEAR_TIMEOUT = 500; // 缓冲区清空超时，减少到500ms

    // 状态跟踪 - 使用原子变量确保线程安全
    private final AtomicBoolean running = new AtomicBoolean(false); // 运行状态
    private final AtomicBoolean isDirectionChanging = new AtomicBoolean(false); // 方向变化状态
    private final AtomicBoolean emergencyForceSend = new AtomicBoolean(false); // 紧急强制发送标志
    private volatile long directionChangeTimestamp = 0; // 方向变化时间戳
    private volatile long lastInputTime = 0; // 上次输入时间
    private volatile long lastCommandTime = 0; // 上次命令发送时间
    private volatile boolean hasReachedEdge = false; // 是否已到达边缘

    // 震动相关参数
    private Context context;
    private Vibrator vibrator;
    private static final long DIRECTION_CHANGE_VIBRATION = 150; // 方向切换震动时长(毫秒)
    private static final long START_STOP_VIBRATION = 50; // 开始/停止震动时长(毫秒)
    private static final long EDGE_VIBRATION = 40; // 到达边缘震动时长(毫秒)

    // 性能监控相关
    private volatile long totalInputInterval = 0;
    private volatile long minInputInterval = Long.MAX_VALUE;
    private volatile long maxInputInterval = 0;
    private volatile int inputCount = 0;

    private volatile long totalProcessTime = 0;
    private volatile long maxProcessTime = 0;
    private volatile int processCount = 0;

    private volatile long totalSendInterval = 0;
    private volatile long minSendInterval = Long.MAX_VALUE;
    private volatile long maxSendInterval = 0;
    private volatile int sendCount = 0;

    // 单例模式
    private static JoySticksDecoder instance;

    public static synchronized JoySticksDecoder getInstance() {
        if (instance == null) {
            instance = new JoySticksDecoder();
        }
        return instance;
    }

    private JoySticksDecoder() {
        // 创建后台线程Handler
        HandlerThread handlerThread = new HandlerThread("JoystickProcessingThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
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
     * 启动处理线程
     */
    public void start() {
        if (running.getAndSet(true)) {
            Log.w(TAG, "处理器已在运行中");
            return;
        }

        // 清空所有队列
        inputQueue.clear();
        commandQueue.clear();

        // 重置状态
        smoothX = 0;
        smoothY = 0;
        isDirectionChanging.set(false);
        directionChangeTimestamp = 0;
        lastInputTime = 0;
        lastCommandTime = 0;
        latestCommand.set(ControlCommand.createZeroCommand());
        hasReachedEdge = false;
        emergencyForceSend.set(true); // 启动时强制发送一次命令

        // 重置性能监控变量
        inputCount = 0;
        totalInputInterval = 0;
        minInputInterval = Long.MAX_VALUE;
        maxInputInterval = 0;

        processCount = 0;
        totalProcessTime = 0;
        maxProcessTime = 0;

        sendCount = 0;
        totalSendInterval = 0;
        minSendInterval = Long.MAX_VALUE;
        maxSendInterval = 0;

        // 立即发送一个停止命令，不要等待线程启动
        ControlCommand stopCommand = ControlCommand.createZeroCommand();
        directSendCommand(stopCommand);

        // 启动输入处理线程
        processingThread = new Thread(() -> {
            Log.d(TAG, "输入处理线程已启动");

            while (running.get()) {
                try {
                    processInputs();
                    Thread.sleep(INPUT_TIMEOUT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "输入处理线程被中断");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "输入处理异常: " + e.getMessage(), e);
                }
            }

            Log.d(TAG, "输入处理线程已退出");
        });

        // 启动命令发送线程
        sendingThread = new Thread(() -> {
            Log.d(TAG, "命令发送线程已启动");

            while (running.get()) {
                try {
                    processCommands();
                    Thread.sleep(COMMAND_TIMEOUT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "命令发送线程被中断");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "命令发送异常: " + e.getMessage(), e);
                }
            }

            Log.d(TAG, "命令发送线程已退出");
        });

        // 设置线程优先级为最高
        processingThread.setPriority(Thread.MAX_PRIORITY);
        sendingThread.setPriority(Thread.MAX_PRIORITY);

        // 启动线程
        processingThread.start();
        sendingThread.start();

        Log.d(TAG, "摇杆控制处理器已启动");
    }

    /**
     * 停止处理线程
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            Log.w(TAG, "处理器已停止");
            return;
        }

        // 先直接发送停止命令，不等线程处理
        ControlCommand stopCommand = ControlCommand.createZeroCommand();
        directSendCommand(stopCommand);

        // 中断线程
        if (processingThread != null) {
            processingThread.interrupt();
            processingThread = null;
        }

        if (sendingThread != null) {
            sendingThread.interrupt();
            sendingThread = null;
        }

        // 清空队列
        inputQueue.clear();
        commandQueue.clear();

        // 输出最终性能统计
        if (inputCount > 0) {
            long avgInputInterval = totalInputInterval / inputCount;
            Log.i(TAG, String.format("最终输入统计: 次数=%d, 平均间隔=%dms, 最小=%dms, 最大=%dms",
                    inputCount, avgInputInterval, minInputInterval, maxInputInterval));
        }

        if (processCount > 0) {
            long avgProcessTime = totalProcessTime / processCount;
            Log.i(TAG, String.format("最终处理统计: 次数=%d, 平均耗时=%dms, 最大=%dms",
                    processCount, avgProcessTime, maxProcessTime));
        }

        if (sendCount > 0) {
            long avgSendInterval = totalSendInterval / sendCount;
            Log.i(TAG, String.format("最终发送统计: 次数=%d, 平均间隔=%dms, 最小=%dms, 最大=%dms",
                    sendCount, avgSendInterval, minSendInterval, maxSendInterval));
        }

        Log.d(TAG, "摇杆控制处理器已停止");
    }

    /**
     * 强制发送当前命令，跳过队列
     */
    public void forceSendCommand() {
        emergencyForceSend.set(true);
        // 使用后台线程处理，避免主线程阻塞
        backgroundHandler.post(() -> {
            Log.d(TAG, "强制发送控制命令已触发");

            // 获取当前命令
            ControlCommand command = latestCommand.get();
            if (command == null) {
                command = ControlCommand.createZeroCommand();
            }

            // 发送命令
            directSendCommand(command);
        });
    }

    /**
     * 更新摇杆输入值（由JoystickView调用）
     * 无锁设计，不会阻塞主线程
     * @param x 标准化X轴值 [-1.0, 1.0]
     * @param y 标准化Y轴值 [-1.0, 1.0]
     */
    public void updateJoystickValues(float x, float y) {
        if (!running.get()) {
            return; // 如果系统未运行，忽略输入
        }

        // 创建新的输入对象
        JoystickInput input = new JoystickInput(x, y);

        // 使用后台线程处理，而不是主线程
        backgroundHandler.post(() -> {
            // 计算输入间隔
            long currentTime = System.currentTimeMillis();
            if (lastInputTime > 0) {
                long interval = currentTime - lastInputTime;
                inputCount++;
                totalInputInterval += interval;

                if (interval < minInputInterval) minInputInterval = interval;
                if (interval > maxInputInterval) maxInputInterval = interval;

                if (inputCount % 100 == 0) {
                    long avgInterval = totalInputInterval / inputCount;
                    Log.d(TAG, String.format("输入统计: 次数=%d, 平均间隔=%dms, 最小=%dms, 最大=%dms",
                            inputCount, avgInterval, minInputInterval, maxInputInterval));
                }

                // 如果距离上次发送命令超过1秒，触发紧急强制发送
                if (currentTime - lastCommandTime > 1000) {
                    emergencyForceSend.set(true);
                    Log.w(TAG, "检测到命令发送延迟，触发紧急强制发送");
                }
            }

            // 更新时间和最新输入
            lastInputTime = currentTime;
            latestInput.set(input);

            // 添加到输入队列，保持队列不超过10个元素
            inputQueue.offer(input);
            if (inputQueue.size() > 10) {
                inputQueue.poll(); // 移除最旧的输入
            }
        });
    }

    /**
     * 处理输入队列中的数据
     */
    private void processInputs() {
        // 记录处理开始时间
        long startTime = System.currentTimeMillis();

        // 获取最新输入
        JoystickInput input = latestInput.get();
        if (input == null) {
            // 如果超时未收到输入，则生成零命令
            long currentTime = System.currentTimeMillis();
            if (lastInputTime > 0 && (currentTime - lastInputTime) > BUFFER_CLEAR_TIMEOUT) {
                // 生成并添加零命令
                ControlCommand zeroCommand = ControlCommand.createZeroCommand();
                commandQueue.offer(zeroCommand);
                lastInputTime = 0; // 重置输入时间
                Log.d(TAG, "输入超时，生成零命令");
            }
            return;
        }

        // 清空输入队列
        inputQueue.clear();

        // 应用平滑算法
        float normalizedX = input.x;
        float normalizedY = input.y;
        smoothX += (normalizedX - smoothX) * SMOOTH_FACTOR;
        smoothY += (normalizedY - smoothY) * SMOOTH_FACTOR;

        // 死区处理
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
                    mainHandler.post(() -> vibrate(EDGE_VIBRATION));
                }
            } else {
                // 离开边缘状态
                hasReachedEdge = false;
            }
        }

        // 计算控制命令
        ControlCommand command = calculateCommand(smoothX, smoothY);

        // 获取上次命令进行比较
        ControlCommand lastCommand = latestCommand.get();

        // 检查命令是否与上次相同
        if (lastCommand == null || !command.equals(lastCommand) || emergencyForceSend.getAndSet(false)) {
            // 更新最新命令
            latestCommand.set(command);

            // 添加到命令队列
            commandQueue.offer(command);

            // 检测开始/停止移动状态变化
            if (lastCommand != null && lastCommand.isZeroCommand != command.isZeroCommand) {
                mainHandler.post(() -> vibrate(START_STOP_VIBRATION));
            }

            Log.d(TAG, "生成新命令: " + command);
        }

        // 记录处理时间
        long processTime = System.currentTimeMillis() - startTime;
        processCount++;
        totalProcessTime += processTime;
        if (processTime > maxProcessTime) maxProcessTime = processTime;

        if (processCount % 50 == 0) {
            long avgProcessTime = totalProcessTime / processCount;
            Log.d(TAG, String.format("处理统计: 次数=%d, 平均耗时=%dms, 最大=%dms",
                    processCount, avgProcessTime, maxProcessTime));
        }
    }

    /**
     * 根据当前平滑后的输入值计算控制命令
     */
    private ControlCommand calculateCommand(float x, float y) {
        // 计算总推力强度 (0-100)
        float length = (float) Math.hypot(x, y);
        int thrustPower = (int) (length * 100);

        // 限制推力范围
        thrustPower = Math.min(100, Math.max(0, thrustPower));

        // 检查是否为零输入状态
        if (thrustPower < 5) {
            return ControlCommand.createZeroCommand();
        }

        // 确定方向 (Y值决定前进/后退)
        boolean isForward = y >= 0;
        int leftDirection = isForward ? 1 : 0;
        int rightDirection = isForward ? 1 : 0;

        // 获取上次命令
        ControlCommand lastCommand = latestCommand.get();
        if (lastCommand == null) {
            lastCommand = ControlCommand.createZeroCommand();
        }

        // 检测方向变化
        if ((isForward && (lastCommand.leftDirection == 0 || lastCommand.rightDirection == 0)) ||
                (!isForward && (lastCommand.leftDirection == 1 || lastCommand.rightDirection == 1))) {

            // 首次检测到方向变化
            if (!isDirectionChanging.getAndSet(true)) {
                directionChangeTimestamp = System.currentTimeMillis();

                // 触发方向切换震动反馈
                mainHandler.post(() -> vibrate(DIRECTION_CHANGE_VIBRATION));

                // 返回电机停止命令
                Log.d(TAG, "检测到方向变化，电机暂停，等待" + DIRECTION_CHANGE_DELAY + "ms");
                return ControlCommand.createZeroCommand();
            }
            // 如果正在等待方向切换
            else if (System.currentTimeMillis() - directionChangeTimestamp < DIRECTION_CHANGE_DELAY) {
                // 继续保持电机停止状态
                return ControlCommand.createZeroCommand();
            }
            // 方向切换延迟结束
            else {
                isDirectionChanging.set(false);
                Log.d(TAG, "方向切换完成: " + (isForward ? "前进" : "后退"));
            }
        } else {
            // 没有方向变化，重置标志
            isDirectionChanging.set(false);
        }

        // 计算左右推进器的不同推力
        int leftThrust, rightThrust;

        if (Math.abs(x) < 0.1) {
            // 直线前进或后退
            leftThrust = thrustPower;
            rightThrust = thrustPower;
        } else if (x < 0) {
            // 向左转 (减弱左推进器，保持右推进器)
            float turnRatio = 1 + x; // 从1.0到0.0
            leftThrust = (int) (thrustPower * turnRatio);
            rightThrust = thrustPower;
        } else {
            // 向右转 (减弱右推进器，保持左推进器)
            float turnRatio = 1 - x; // 从1.0到0.0
            leftThrust = thrustPower;
            rightThrust = (int) (thrustPower * turnRatio);
        }

        // 确保推力在有效范围内
        leftThrust = Math.min(100, Math.max(0, leftThrust));
        rightThrust = Math.min(100, Math.max(0, rightThrust));

        // 创建命令对象，电机使能为0（开启）
        return new ControlCommand(leftThrust, rightThrust, leftDirection, rightDirection, 0, 0, false);
    }

    /**
     * 处理命令队列中的数据
     */
    private void processCommands() {
        // 检查紧急强制发送标志
        if (emergencyForceSend.getAndSet(false)) {
            // 获取当前命令
            ControlCommand command = latestCommand.get();
            if (command == null) {
                command = ControlCommand.createZeroCommand();
            }

            // 直接发送命令
            sendControlCommand(command);
            Log.d(TAG, "紧急发送命令: " + command);
            return;
        }

        // 如果队列为空
        if (commandQueue.isEmpty()) {
            // 检查是否需要发送超时零命令
            long currentTime = System.currentTimeMillis();
            ControlCommand lastCmd = latestCommand.get();

            if (lastCommandTime > 0 && lastCmd != null &&
                    !lastCmd.isZeroCommand &&
                    (currentTime - lastCommandTime) > BUFFER_CLEAR_TIMEOUT) {
                // 超时发送零命令
                ControlCommand zeroCommand = ControlCommand.createZeroCommand();
                sendControlCommand(zeroCommand);
                Log.d(TAG, "命令超时，发送零命令");
            }
            return;
        }

        // 取最新的命令，清空队列
        ControlCommand command = null;
        while (!commandQueue.isEmpty()) {
            command = commandQueue.poll();
        }

        // 发送命令
        if (command != null) {
            sendControlCommand(command);
        }
    }

    /**
     * 直接发送控制命令到WebSocket，跳过处理线程
     */
    private void directSendCommand(ControlCommand command) {
        if (command == null) {
            return;
        }

        // 更新最新命令
        latestCommand.set(command);

        // 更新上次发送时间
        lastCommandTime = System.currentTimeMillis();

        // 创建控制命令JSON
        JsonObject shipMotorObject = new JsonObject();
        shipMotorObject.addProperty("CH1", command.leftThrust);
        shipMotorObject.addProperty("DIR1", command.leftDirection);
        shipMotorObject.addProperty("EN1", command.leftEnable);
        shipMotorObject.addProperty("CH2", command.rightThrust);
        shipMotorObject.addProperty("DIR2", command.rightDirection);
        shipMotorObject.addProperty("EN2", command.rightEnable);

        JsonObject commandObject = new JsonObject();
        commandObject.add("SHIPMOTRO", shipMotorObject);

        final String commandJson = commandObject.toString();

        // 通过主线程发送WebSocket消息
        mainHandler.post(() -> {
            // 检查WebSocket连接状态
            MainDeviceSocket socketManager = MainDeviceSocket.getInstance();
            if (socketManager != null && socketManager.isConnected()) {
                boolean success = WebSocketManager.getInstance().sendMessage(commandJson);

                // 更新发送统计
                sendCount++;
                if (success) {
                    Log.d(TAG, "直接发送控制命令成功: " + commandJson);
                } else {
                    Log.e(TAG, "直接发送控制命令失败: " + commandJson);
                }
            } else {
                Log.w(TAG, "WebSocket未连接，无法直接发送控制命令");
            }
        });
    }

    /**
     * 发送控制命令到WebSocket
     */
    private void sendControlCommand(ControlCommand command) {
        if (command == null) {
            return;
        }

        // 计算发送间隔
        long currentTime = System.currentTimeMillis();
        if (lastCommandTime > 0) {
            long interval = currentTime - lastCommandTime;
            totalSendInterval += interval;

            if (interval < minSendInterval) minSendInterval = interval;
            if (interval > maxSendInterval) maxSendInterval = interval;

            if (sendCount % 20 == 0) {
                long avgInterval = totalSendInterval / sendCount;
                Log.d(TAG, String.format("发送统计: 次数=%d, 平均间隔=%dms, 最小=%dms, 最大=%dms",
                        sendCount, avgInterval, minSendInterval, maxSendInterval));
            }

            // 如果距离上次发送命令超过1秒，记录警告
            if (interval > 1000) {
                Log.w(TAG, "命令发送间隔过长: " + interval + "ms");
            }
        }

        // 更新最新命令和时间
        latestCommand.set(command);
        lastCommandTime = currentTime;

        // 创建控制命令JSON
        JsonObject shipMotorObject = new JsonObject();
        shipMotorObject.addProperty("CH1", command.leftThrust);
        shipMotorObject.addProperty("DIR1", command.leftDirection);
        shipMotorObject.addProperty("EN1", command.leftEnable);
        shipMotorObject.addProperty("CH2", command.rightThrust);
        shipMotorObject.addProperty("DIR2", command.rightDirection);
        shipMotorObject.addProperty("EN2", command.rightEnable);

        JsonObject commandObject = new JsonObject();
        commandObject.add("SHIPMOTRO", shipMotorObject);

        final String commandJson = commandObject.toString();

        // 通过主线程发送WebSocket消息
        mainHandler.post(() -> {
            // 检查WebSocket连接状态
            MainDeviceSocket socketManager = MainDeviceSocket.getInstance();
            if (socketManager != null && socketManager.isConnected()) {
                boolean success = WebSocketManager.getInstance().sendMessage(commandJson);
                sendCount++;

                if (success) {
                    Log.d(TAG, "控制命令已发送: " + commandJson);
                } else {
                    Log.e(TAG, "控制命令发送失败");
                    // 发送失败时，标记强制重试
                    emergencyForceSend.set(true);
                }
            } else {
                Log.w(TAG, "WebSocket未连接，无法发送控制命令");
            }
        });
    }
}
