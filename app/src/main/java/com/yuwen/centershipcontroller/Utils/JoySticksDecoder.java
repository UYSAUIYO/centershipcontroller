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

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.yuwen.centershipcontroller.Socket.MainDeviceSocket;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 摇杆控制命令解码器 - 高性能低延迟版本
 * 负责将摇杆的原始输入转换为船舶电机控制命令
 * 使用无锁设计和高效线程模型
 * @author yuwen (优化版)
 */
public class JoySticksDecoder {
    private static final String TAG = "JoySticksDecoder";
    private static final boolean DEBUG = true; // 发布时设为false，减少日志输出

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
    // 配置参数 - 高性能设置
    private static final float CENTER_DEAD_ZONE = 0.05f; // 减小死区以提高响应性

    // 使用原子引用替代锁
    private final AtomicReference<JoystickInput> latestInput = new AtomicReference<>();
    private final AtomicReference<ControlCommand> latestCommand = new AtomicReference<>(ControlCommand.createZeroCommand());
    private static final float EDGE_BUFFER_ZONE = 0.05f; // 边缘缓冲区
    private static final long BUFFER_CLEAR_TIMEOUT = 300; // 缓冲区清空超时
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long DIRECTION_CHANGE_VIBRATION = 100; // 减少震动时间
    private static final long START_STOP_VIBRATION = 40;
    private static final long EDGE_VIBRATION = 30;
    private static long DIRECTION_CHANGE_DELAY = 150; // 进一步减少延迟
    // 移除冗余线程，使用优化的线程模型
    private final HandlerThread controllerThread;
    private final Handler controllerHandler;
    // 添加振动执行器，避免在主线程振动
    private final ExecutorService vibrationExecutor = Executors.newSingleThreadExecutor();
    // 添加命令缓存，预计算常用命令
    private final Map<String, ControlCommand> commandCache = new ConcurrentHashMap<>();
    // 状态跟踪 - 使用原子变量确保线程安全
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean isDirectionChanging = new AtomicBoolean(false);
    private final AtomicBoolean emergencyForceSend = new AtomicBoolean(false);
    // 初始化性能监控器
    private final PerformanceMonitor inputMonitor = new PerformanceMonitor("输入处理");
    private final PerformanceMonitor filterMonitor = new PerformanceMonitor("滤波处理");
    private final PerformanceMonitor commandGenMonitor = new PerformanceMonitor("命令生成");

    // 震动相关参数
    private Context context;
    private Vibrator vibrator;
    private final PerformanceMonitor sendMonitor = new PerformanceMonitor("命令发送");
    private final PerformanceMonitor fullPathMonitor = new PerformanceMonitor("全路径延迟");
    // JSON缓存
    private final Map<String, String> jsonCache = new ConcurrentHashMap<>();
    // 添加快速滤波器
    private Utils.LowLatencyFilter inputFilter;
    private volatile long directionChangeTimestamp = 0;
    private volatile long lastInputTime = 0;
    private volatile long lastCommandTime = 0;
    private volatile boolean hasReachedEdge = false;
    private JoySticksDecoder() {
        // 创建高优先级控制线程
        controllerThread = new HandlerThread("JoystickController", Thread.MAX_PRIORITY);
        controllerThread.start();
        controllerHandler = new Handler(controllerThread.getLooper());

        // 初始化快速滤波器 - 根据设备性能选择最佳参数
        inputFilter = new Utils.FastLagFilter(0.5f);

        // 预计算常用命令
        initCommandCache();
    }

    // 单例模式
    private static JoySticksDecoder instance;

    public static synchronized JoySticksDecoder getInstance() {
        if (instance == null) {
            instance = new JoySticksDecoder();
        }
        return instance;
    }

    @NonNull
    private static String getJson(@NonNull ControlCommand command) {
        JsonObject shipMotorObject = new JsonObject();
        shipMotorObject.addProperty("CH1", command.leftThrust);
        shipMotorObject.addProperty("DIR1", command.leftDirection);
        shipMotorObject.addProperty("EN1", command.leftEnable);
        shipMotorObject.addProperty("CH2", command.rightThrust);
        shipMotorObject.addProperty("DIR2", command.rightDirection);
        shipMotorObject.addProperty("EN2", command.rightEnable);

        JsonObject commandObject = new JsonObject();
        commandObject.add("SHIPMOTRO", shipMotorObject);

        String json = commandObject.toString();
        return json;
    }

    /**
     * 预计算并缓存常用命令组合
     */
    private void initCommandCache() {
        // 缓存零命令
        commandCache.put("zero", ControlCommand.createZeroCommand());

        // 缓存常用前进命令 (10%-100%, 每10%一档)
        for (int power = 10; power <= 100; power += 10) {
            String key = "forward_" + power;
            commandCache.put(key, new ControlCommand(power, power, 1, 1, 0, 0, false));
        }

        // 缓存常用后退命令
        for (int power = 10; power <= 100; power += 10) {
            String key = "backward_" + power;
            commandCache.put(key, new ControlCommand(power, power, 0, 0, 0, 0, false));
        }

        // 缓存常用转向命令 (左转和右转，几个常用的转向比例)
        int[] turns = {25, 50, 75};
        for (int power = 30; power <= 100; power += 30) {
            for (int turn : turns) {
                // 左转命令 (降低左侧推力)
                int leftPower = power * (100 - turn) / 100;
                String leftKey = "left_" + power + "_" + turn;
                commandCache.put(leftKey, new ControlCommand(leftPower, power, 1, 1, 0, 0, false));

                // 右转命令 (降低右侧推力)
                int rightPower = power * (100 - turn) / 100;
                String rightKey = "right_" + power + "_" + turn;
                commandCache.put(rightKey, new ControlCommand(power, rightPower, 1, 1, 0, 0, false));
            }
        }
    }

    /**
     * 初始化震动器
     * @param context 应用上下文
     */
    public void init(Context context) {
        this.context = context;

        try {
            // 初始化震动器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.w(TAG, "设备不支持震动功能");
            }
        } catch (Exception e) {
            Log.e(TAG, "震动器初始化失败: " + e.getMessage());
        }
    }

    /**
     * 触发震动 - 移至后台线程执行
     * @param duration 震动时长(毫秒)
     */
    private void vibrate(long duration) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        // 在专用线程执行震动，避免主线程阻塞
        vibrationExecutor.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            } catch (Exception e) {
                Log.e(TAG, "震动触发失败: " + e.getMessage());
            }
        });
    }

    /**
     * 启动控制处理
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // 已在运行中
        }

        // 重置状态
        inputFilter.reset();
        isDirectionChanging.set(false);
        directionChangeTimestamp = 0;
        lastInputTime = 0;
        lastCommandTime = 0;
        latestCommand.set(ControlCommand.createZeroCommand());
        hasReachedEdge = false;
        emergencyForceSend.set(true);

        // 重置性能监控
        inputMonitor.reset();
        filterMonitor.reset();
        commandGenMonitor.reset();
        sendMonitor.reset();
        fullPathMonitor.reset();

        // 立即发送一个停止命令
        try {
            ControlCommand stopCommand = commandCache.get("zero");
            if (stopCommand == null) {
                stopCommand = ControlCommand.createZeroCommand();
            }
            directSendCommand(stopCommand);
        } catch (Exception e) {
            Log.e(TAG, "发送停止命令失败: " + e.getMessage());
        }

        Log.i(TAG, "摇杆控制处理器已启动");
    }

    /**
     * 停止控制处理
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return; // 已停止
        }

        try {
            // 发送停止命令
            ControlCommand stopCommand = commandCache.get("zero");
            if (stopCommand == null) {
                stopCommand = ControlCommand.createZeroCommand();
            }
            directSendCommand(stopCommand);

            // 输出最终性能统计
            Log.i(TAG, "摇杆控制处理器性能统计:");
            Log.i(TAG, inputMonitor.getStats());
            Log.i(TAG, filterMonitor.getStats());
            Log.i(TAG, commandGenMonitor.getStats());
            Log.i(TAG, sendMonitor.getStats());
            Log.i(TAG, fullPathMonitor.getStats());

        } catch (Exception e) {
            Log.e(TAG, "停止处理时发生错误: " + e.getMessage());
        }

        Log.i(TAG, "摇杆控制处理器已停止");
    }

    public void forceSendCommand() {
        emergencyForceSend.set(true);
        controllerHandler.post(() -> {
            try {
                ControlCommand command = latestCommand.get();
                if (command == null) {
                    command = commandCache.get("zero");
                    if (command == null) {
                        command = ControlCommand.createZeroCommand();
                    }
                }
                directSendCommand(command);
            } catch (Exception e) {
                Log.e(TAG, "强制发送命令异常: " + e.getMessage());
            }
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
        final JoystickInput input = new JoystickInput(x, y);
        final long inputTime = System.currentTimeMillis();

        // 在专用控制线程处理，优化响应速度
        controllerHandler.post(() -> {
            try {
                // 记录全路径起始时间
                fullPathMonitor.recordSample(0); // 仅作标记

                // 记录输入间隔
                long currentTime = System.currentTimeMillis();
                inputMonitor.recordSample(lastInputTime > 0 ? currentTime - lastInputTime : 0);
                lastInputTime = currentTime;

                // 更新最新输入
                latestInput.set(input);

                // 直接处理输入，减少延迟
                processInputImmediate(input, inputTime);
            } catch (Exception e) {
                Log.e(TAG, "处理输入异常: " + e.getMessage());
            }
        });
    }

    /**
     * 立即处理输入，减少延迟
     */
    private void processInputImmediate(JoystickInput input, long startTime) {
        try {
            // 记录滤波开始时间
            long filterStart = System.currentTimeMillis();

            // 应用滤波器
            float[] filtered = inputFilter.update(input.x, input.y);
            float filteredX = filtered[0];
            float filteredY = filtered[1];

            // 记录滤波耗时
            filterMonitor.recordSample(System.currentTimeMillis() - filterStart);

            // 死区处理
            float length = (float) Math.hypot(filteredX, filteredY);
            if (length < CENTER_DEAD_ZONE) {
                filteredX = 0;
                filteredY = 0;
                inputFilter.reset();
            } else {
                // 边缘缓冲处理
                float maxAllowedLength = 1.0f - EDGE_BUFFER_ZONE;
                if (length > maxAllowedLength) {
                    float scaleFactor = maxAllowedLength / length;
                    filteredX *= scaleFactor;
                    filteredY *= scaleFactor;

                    // 检测是否到达边缘，只震动一次
                    if (!hasReachedEdge) {
                        hasReachedEdge = true;
                        vibrate(EDGE_VIBRATION);
                    }
                } else {
                    // 离开边缘状态
                    hasReachedEdge = false;
                }
            }

            // 记录命令生成开始时间
            long commandStart = System.currentTimeMillis();

            // 计算控制命令
            ControlCommand command = calculateCommand(filteredX, filteredY);

            // 记录命令生成耗时
            commandGenMonitor.recordSample(System.currentTimeMillis() - commandStart);

            // 获取上次命令进行比较
            ControlCommand lastCommand = latestCommand.get();

            // 检查命令是否与上次相同或需要强制发送
            if (lastCommand == null || !command.equals(lastCommand) || emergencyForceSend.getAndSet(false)) {
                // 更新最新命令
                latestCommand.set(command);

                // 检测开始/停止移动状态变化
                if (lastCommand != null && lastCommand.isZeroCommand != command.isZeroCommand) {
                    vibrate(START_STOP_VIBRATION);
                }

                // 直接发送命令
                long sendStart = System.currentTimeMillis();
                directSendCommand(command);
                sendMonitor.recordSample(System.currentTimeMillis() - sendStart);

                // 记录全路径延迟
                if (startTime > 0) {
                    fullPathMonitor.recordSample(System.currentTimeMillis() - startTime);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理输入时发生异常: " + e.getMessage());
        }
    }

    /**
     * 根据输入值计算最佳控制命令
     * 优先使用缓存的命令，减少计算开销
     */
    private ControlCommand calculateCommand(float x, float y) {
        try {
            // 计算总推力强度 (0-100)
            float length = (float) Math.hypot(x, y);
            int thrustPower = (int) (length * 100);

            // 限制推力范围
            thrustPower = Math.min(100, Math.max(0, thrustPower));

            // 检查是否为零输入状态 - 降低阈值提高响应性
            if (thrustPower < 3) {
                return commandCache.getOrDefault("zero", ControlCommand.createZeroCommand());
            }

            // 确定方向 (Y值决定前进/后退)
            boolean isForward = y >= 0;

            // 尝试从缓存获取命令
            if (Math.abs(x) < 0.1) {
                // 直线前进/后退，尝试使用缓存
                int roundedPower = Math.round(thrustPower / 10f) * 10; // 舍入到最近的10的倍数
                String key = isForward ? "forward_" + roundedPower : "backward_" + roundedPower;
                ControlCommand cachedCommand = commandCache.get(key);
                if (cachedCommand != null) {
                    return cachedCommand;
                }
            } else {
                // 转向，尝试使用缓存
                int roundedPower = Math.round(thrustPower / 30f) * 30; // 舍入到最近的30的倍数
                int turnPercent = Math.round(Math.abs(x) * 100 / 4) * 25; // 转向百分比(25, 50, 75)
                if (turnPercent > 75) turnPercent = 75;
                String key = (x < 0 ? "left_" : "right_") + roundedPower + "_" + turnPercent;
                ControlCommand cachedCommand = commandCache.get(key);
                if (cachedCommand != null) {
                    if (!isForward) {
                        // 需要反向，重新计算而不使用缓存
                        // 由于反向命令较少用，不单独缓存
                    } else {
                        return cachedCommand;
                    }
                }
            }

            // 缓存未命中，计算实际命令
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
                    vibrate(DIRECTION_CHANGE_VIBRATION);
                    return commandCache.getOrDefault("zero", ControlCommand.createZeroCommand());
                }
                // 如果正在等待方向切换
                else if (System.currentTimeMillis() - directionChangeTimestamp < DIRECTION_CHANGE_DELAY) {
                    return commandCache.getOrDefault("zero", ControlCommand.createZeroCommand());
                }
                // 方向切换延迟结束
                else {
                    isDirectionChanging.set(false);
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
        } catch (Exception e) {
            Log.e(TAG, "计算命令错误: " + e.getMessage());
            return commandCache.getOrDefault("zero", ControlCommand.createZeroCommand());
        }
    }

    /**
     * 直接发送控制命令到WebSocket
     */
    private void directSendCommand(ControlCommand command) {
        if (command == null) {
            return;
        }

        try {
            // 更新最新命令
            latestCommand.set(command);

            // 更新上次发送时间
            lastCommandTime = System.currentTimeMillis();

            // 创建控制命令JSON
            final String commandJson = createCommandJson(command);

            // 通过主线程发送WebSocket消息
            mainHandler.post(() -> {
                try {
                    // 检查WebSocket连接状态
                    MainDeviceSocket socketManager = MainDeviceSocket.getInstance();
                    if (socketManager != null && socketManager.isConnected()) {
                        boolean success = WebSocketManager.getInstance().sendMessage(commandJson);

                        if (!success && DEBUG) {
                            Log.e(TAG, "发送控制命令失败");
                            // 发送失败时，标记强制重试
                            emergencyForceSend.set(true);
                        }
                    } else if (DEBUG) {
                        Log.w(TAG, "WebSocket未连接");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "发送消息异常: " + e.getMessage());
                    emergencyForceSend.set(true);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "准备发送命令异常: " + e.getMessage());
        }
    }

    /**
     * 创建命令JSON字符串
     * 预构建常用的JSON模板，减少实时构建开销
     */
    private String createCommandJson(ControlCommand command) {
        // 缓存常用命令的JSON表示
        String cacheKey = getCacheKey(command);
        String cachedJson = getCommandJsonFromCache(cacheKey);
        if (cachedJson != null) {
            return cachedJson;
        }

        // 如果缓存未命中，创建新的JSON
        try {
            String json = getJson(command);

            // 缓存这个JSON以便将来使用
            cacheCommandJson(cacheKey, json);

            return json;
        } catch (Exception e) {
            Log.e(TAG, "创建命令JSON异常: " + e.getMessage());
            return "{\"SHIPMOTRO\":{\"CH1\":0,\"DIR1\":1,\"EN1\":1,\"CH2\":0,\"DIR2\":1,\"EN2\":1}}"; // 零命令的默认JSON
        }
    }

    private String getCacheKey(@NonNull ControlCommand command) {
        return command.leftThrust + "_" + command.rightThrust + "_" +
                command.leftDirection + "_" + command.rightDirection + "_" +
                command.leftEnable + "_" + command.rightEnable;
    }

    private String getCommandJsonFromCache(String key) {
        return jsonCache.get(key);
    }

    private void cacheCommandJson(String key, String json) {
        // 限制缓存大小
        if (jsonCache.size() > 50) {
            // 如果缓存过大，清除一半
            if (jsonCache.size() > 100) {
                jsonCache.clear();
            } else {
                // 这里可以用更复杂的策略来清除最不常用的项
                // 但为了简单起见，我们只保留固定命令的缓存
                String[] keysToKeep = {"0_0_1_1_1_1"}; // 零命令的键
                Map<String, String> tempCache = new HashMap<>();
                for (String keyToKeep : keysToKeep) {
                    String value = jsonCache.get(keyToKeep);
                    if (value != null) {
                        tempCache.put(keyToKeep, value);
                    }
                }
                jsonCache.clear();
                jsonCache.putAll(tempCache);
            }
        }
        jsonCache.put(key, json);
    }

    /**
     * 设置滤波器类型
     * 允许在运行时切换不同的滤波算法以适应不同场景
     *
     * @param type  滤波器类型: 0=无滤波, 1=快速滤波(默认), 2=卡尔曼滤波
     * @param alpha 滤波系数(对于FastLagFilter), 范围[0.1, 1.0], 值越大响应越快
     */
    public void setFilterType(int type, float alpha) {
        if (!running.get()) {
            switch (type) {
                case 0:
                    inputFilter = new Utils.NoFilter();
                    break;
                case 1:
                    inputFilter = new Utils.FastLagFilter(alpha);
                    break;
                case 2:
                    inputFilter = new Utils.KalmanFilter();
                    break;
                default:
                    inputFilter = new Utils.FastLagFilter(0.5f);
                    break;
            }
            Log.i(TAG, "滤波器已更改为: " + inputFilter.getClass().getSimpleName());
        } else {
            Log.w(TAG, "不能在运行时更改滤波器类型，请先停止处理器");
        }
    }

    /**
     * 获取延迟统计信息
     *
     * @return 延迟统计的字符串表示
     */
    public String getPerformanceStats() {
        StringBuilder stats = new StringBuilder("性能统计:\n");
        stats.append(fullPathMonitor.getStats()).append("\n");
        stats.append(inputMonitor.getStats()).append("\n");
        stats.append(filterMonitor.getStats()).append("\n");
        stats.append(commandGenMonitor.getStats()).append("\n");
        stats.append(sendMonitor.getStats());
        return stats.toString();
    }

    /**
     * 重置性能统计
     */
    public void resetPerformanceStats() {
        inputMonitor.reset();
        filterMonitor.reset();
        commandGenMonitor.reset();
        sendMonitor.reset();
        fullPathMonitor.reset();
        Log.i(TAG, "性能统计已重置");
    }

    /**
     * 设置方向切换延迟
     *
     * @param delayMs 延迟毫秒数 (推荐范围: 50-200ms)
     */
    public void setDirectionChangeDelay(long delayMs) {
        long delay = Math.max(50, Math.min(200, delayMs));
        DIRECTION_CHANGE_DELAY = delay;
        Log.i(TAG, "方向切换延迟已设置为: " + delay + "ms");
    }

    /**
     * 清除命令和JSON缓存
     * 在内存紧张时调用
     */
    public void clearCaches() {
        commandCache.clear();
        jsonCache.clear();
        // 重新初始化必要的缓存项
        initCommandCache();
        Log.i(TAG, "命令和JSON缓存已清除");
    }

    /**
     * 检查当前是否有零命令在发送
     *
     * @return 如果最近的命令是零命令则返回true
     */
    public boolean isIdle() {
        ControlCommand cmd = latestCommand.get();
        return cmd == null || cmd.isZeroCommand;
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
        public int hashCode() {
            int result = leftThrust;
            result = 31 * result + rightThrust;
            result = 31 * result + leftDirection;
            result = 31 * result + rightDirection;
            result = 31 * result + leftEnable;
            result = 31 * result + rightEnable;
            return result;
        }

        @NonNull
        @Override
        public String toString() {
            if (!DEBUG) return "[Command]"; // 减少日志开销

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

    // 性能监控相关
    private static class PerformanceMonitor {
        private final String name;
        // 最近5个样本的移动平均
        private final long[] recentSamples = new long[5];
        private long totalTime = 0;
        private long maxTime = 0;
        private long minTime = Long.MAX_VALUE;
        private int count = 0;
        private int sampleIndex = 0;

        PerformanceMonitor(String name) {
            this.name = name;
        }

        void recordSample(long time) {
            if (time <= 0) return;

            totalTime += time;
            count++;

            if (time > maxTime) maxTime = time;
            if (time < minTime) minTime = time;

            // 更新移动平均
            recentSamples[sampleIndex] = time;
            sampleIndex = (sampleIndex + 1) % recentSamples.length;

            // 每100个样本输出一次统计
            if (DEBUG && count % 100 == 0) {
                long avg = totalTime / count;
                long recentAvg = calculateRecentAverage();
                Log.d(TAG, String.format("%s统计: 计数=%d, 平均=%dms, 最近平均=%dms, 最小=%dms, 最大=%dms",
                        name, count, avg, recentAvg, minTime, maxTime));
            }
        }

        /**
         * 计算最近样本的平均值
         * 该方法旨在处理一组最近的样本数据，计算它们的平均值
         * 主要解决了如何有效计算一组非负样本数据的平均值问题，忽略无效（非正）样本
         *
         * @return 最近样本的平均值如果所有样本都是无效的，则返回0
         */
        long calculateRecentAverage() {
            // 初始化样本之和
            long sum = 0;
            // 初始化有效样本计数
            int validCount = 0;
            // 遍历所有最近的样本
            for (long sample : recentSamples) {
                // 如果样本值大于0，即为有效样本
                if (sample > 0) {
                    // 将有效样本累加到总和中
                    sum += sample;
                    // 有效样本计数增加
                    validCount++;
                }
            }
            // 如果有有效样本，则返回平均值；否则返回0
            return validCount > 0 ? sum / validCount : 0;
        }


        void reset() {
            totalTime = 0;
            maxTime = 0;
            minTime = Long.MAX_VALUE;
            count = 0;
            Arrays.fill(recentSamples, 0);
            sampleIndex = 0;
        }

        @NonNull
        String getStats() {
            if (count == 0) return name + ": 无数据";

            long avg = totalTime / Math.max(1, count);
            long recentAvg = calculateRecentAverage();
            return String.format("%s: 计数=%d, 平均=%dms, 最近平均=%dms, 最小=%dms, 最大=%dms",
                    name, count, avg, recentAvg, minTime, maxTime);
        }
    }
}

