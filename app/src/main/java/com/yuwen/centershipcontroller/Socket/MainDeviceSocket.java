package com.yuwen.centershipcontroller.Socket;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuwen.centershipcontroller.Component.CustomDialog;
import com.yuwen.centershipcontroller.Component.DeviceInfoCard;
import com.yuwen.centershipcontroller.Utils.WebSocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主设备WebSocket管理类，负责WebSocket的连接、状态管理和通信
 *
 * @author yuwen
 */
public class MainDeviceSocket {
    private static final String TAG = "MainDeviceSocket";
    private static final String DEVICE_ID = "h832h9eh29h"; // 本设备ID
    private static final String IDENTITY = "MAIN_DEVICES"; // 本设备类型
    // 心跳机制相关变量
    private static final int HEARTBEAT_INTERVAL = 5000; // 心跳间隔10秒
    private static final int HEARTBEAT_INITIAL_DELAY = 5000; // 初始延迟5秒
    private static MainDeviceSocket instance;
    private final WebSocketManager webSocketManager;
    private final ExecutorService socketThread;
    private final Handler mainHandler;
    // 船舶设备信息
    private final List<ShipDevice> shipDevices = new ArrayList<>();
    private Context context;
    private DeviceInfoCard deviceInfoCard;
    private String roomId = "";
    private ConnectionStatusListener connectionStatusListener;
    private boolean isConnected = false;
    private CustomDialog statusDialog;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private boolean heartbeatActive = false;

    // 私有构造函数
    // 私有构造函数
    private MainDeviceSocket() {
        webSocketManager = WebSocketManager.getInstance();
        socketThread = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // 初始化心跳处理器
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    sendHeartbeat();
                    heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
                }
            }
        };
    }

    /**
     * 获取单例实例
     */
    public static synchronized MainDeviceSocket getInstance() {
        if (instance == null) {
            instance = new MainDeviceSocket();
        }
        return instance;
    }

    /**
     * 开始心跳检测
     */
    private void startHeartbeat() {
        // 确保不重复启动
        stopHeartbeat();

        Log.d(TAG, "心跳检测将在 " + HEARTBEAT_INITIAL_DELAY + "ms 后开始");
        heartbeatActive = true;
        // 延迟5秒开始第一次心跳
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INITIAL_DELAY);
    }

    /**
     * 停止心跳检测
     */
    private void stopHeartbeat() {
        if (heartbeatActive) {
            Log.d(TAG, "停止心跳检测");
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            heartbeatActive = false;
        }
    }

    /**
     * 发送心跳查询
     */
    private void sendHeartbeat() {
        Log.d(TAG, "发送心跳查询");
        queryRoomInfo();
    }

    /**
     * 初始化，设置上下文和设备信息卡片
     */
    public void init(Context context, DeviceInfoCard deviceInfoCard) {
        this.context = context;
        this.deviceInfoCard = deviceInfoCard;
        Log.d(TAG, "初始化MainDeviceSocket，context: " + context + ", deviceInfoCard: " + deviceInfoCard);
    }

    /**
     * 设置连接状态监听器
     */
    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.connectionStatusListener = listener;
        // 立即通知当前状态
        if (listener != null) {
            listener.onConnectionStatusChanged(isConnected);
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 开始处理WebSocket连接和通信
     */
    public void start() {
        Log.d(TAG, "启动WebSocket连接处理");

        socketThread.execute(() -> {
            // 检查WebSocket是否已连接
            boolean wasAlreadyConnected = webSocketManager.isConnected();
            Log.d(TAG, "WebSocket连接状态检查: " + (wasAlreadyConnected ? "已连接" : "未连接"));

            // 添加WebSocket连接处理逻辑
            webSocketManager.setConnectionCallback(new WebSocketManager.ConnectionCallback() {
                @Override
                public void onConnected(String message) {
                    Log.d(TAG, "WebSocket连接成功: " + message);
                    // 更新连接状态
                    isConnected = true;
                    notifyConnectionStatusChanged();

                    // 连接成功后，立即发送设备身份信息
                    Log.d(TAG, "连接成功，发送设备身份信息");
                    sendIdentityInfo();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "WebSocket连接失败: " + errorMessage);
                    // 更新连接状态
                    isConnected = false;
                    notifyConnectionStatusChanged();
                }
            });

            // 添加消息监听器
            webSocketManager.setMessageListener(message -> {
                Log.d(TAG, "收到WebSocket消息，准备处理: " + message);
                processMessage(message);
            });

            // 如果WebSocket已经连接，则手动触发连接成功回调
            if (wasAlreadyConnected) {
                Log.d(TAG, "WebSocket已经连接，手动触发连接处理流程");
                isConnected = true;
                notifyConnectionStatusChanged();
                // 发送设备身份信息
                sendIdentityInfo();
            }
        });
    }

    /**
     * 通知连接状态变化
     */
    private void notifyConnectionStatusChanged() {
        if (connectionStatusListener != null) {
            mainHandler.post(() -> {
                try {
                    connectionStatusListener.onConnectionStatusChanged(isConnected);
                    Log.d(TAG, "已通知连接状态变化: " + isConnected);
                } catch (Exception e) {
                    Log.e(TAG, "通知连接状态变化出错: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 处理收到的WebSocket消息
     */
    private void processMessage(String message) {
        Log.d(TAG, "开始处理WebSocket消息: " + message);
        try {
            JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
            // 处理连接成功消息
            if (jsonObject.has("type") && "connection".equals(jsonObject.get("type").getAsString())) {
                if (jsonObject.has("message") && "Connected successfully".equals(jsonObject.get("message").getAsString())) {
                    Log.d(TAG, "检测到连接成功消息，发送设备身份信息");
                    sendIdentityInfo();
                    return;
                }
            }
            // 处理房间消息
            if (jsonObject.has("type") && "room".equals(jsonObject.get("type").getAsString())) {
                handleRoomMessage(jsonObject);
                return;
            }
            // 处理房间信息消息
            if (jsonObject.has("type") && "room_info".equals(jsonObject.get("type").getAsString())) {
                handleRoomInfoMessage(jsonObject);
                return;
            }
            // 未知消息类型
            Log.d(TAG, "收到未处理的消息类型: " + message);
        } catch (Exception e) {
            Log.e(TAG, "处理消息出错: " + e.getMessage(), e);
        }
    }

    /**
     * 处理房间消息
     */
    private void handleRoomMessage(JsonObject jsonObject) {
        Log.d(TAG, "处理房间消息: " + jsonObject);
        try {
            if (jsonObject.has("room_id")) {
                roomId = jsonObject.get("room_id").getAsString();
                Log.d(TAG, "收到房间ID: " + roomId);

                // 收到房间ID后，立即查询房间信息
                Log.d(TAG, "开始查询房间信息");
                queryRoomInfo();
            }
        } catch (Exception e) {
            Log.e(TAG, "处理房间消息出错: " + e.getMessage(), e);
        }
    }

    /**
     * 处理房间信息消息
     */
    private void handleRoomInfoMessage(JsonObject jsonObject) {
        Log.d(TAG, "处理房间信息消息: " + jsonObject);
        try {
            if (jsonObject.has("room_id")) {
                roomId = jsonObject.get("room_id").getAsString();
                Log.d(TAG, "设置房间ID: " + roomId);
                // 检查是否满足连接条件
                boolean hasValidConnection = false;
                boolean hasShipDevice = false;
                int totalClients = 0;

                // 获取客户端总数
                if (jsonObject.has("total_clients")) {
                    totalClients = jsonObject.get("total_clients").getAsInt();
                    Log.d(TAG, "房间内客户端总数: " + totalClients);
                }
                // 清空之前的设备列表
                shipDevices.clear();
                // 解析客户端列表
                if (jsonObject.has("clients") && jsonObject.get("clients").isJsonArray()) {
                    JsonArray clientsArray = jsonObject.getAsJsonArray("clients");
                    Log.d(TAG, "客户端数量: " + clientsArray.size());
                    for (JsonElement clientElement : clientsArray) {
                        JsonObject clientObject = clientElement.getAsJsonObject();
                        String deviceId = clientObject.has("device_id") ?
                                clientObject.get("device_id").getAsString() : "";
                        String identity = clientObject.has("identity") ?
                                clientObject.get("identity").getAsString() : "";
                        Log.d(TAG, "检测到客户端: deviceId=" + deviceId + ", identity=" + identity);
                        // 检查是否有船舶设备
                        if ("SHIP_DEVICES".equals(identity)) {
                            hasShipDevice = true;
                            shipDevices.add(new ShipDevice(deviceId, identity));
                            Log.d(TAG, "添加船舶设备: " + deviceId);
                        }
                    }
                }

                // 判断连接条件：客户端数量>=2且存在船舶设备
                hasValidConnection = (totalClients >= 2) && hasShipDevice;

                if (!hasValidConnection) {
                    // 不满足连接条件，断开WebSocket连接
                    Log.d(TAG, "船舶设备已断开，断开WebSocket连接");
                    disconnect();

                    // 显示断联弹窗
                    showDisconnectDialog();

                    // 重置UI组件状态
                    resetUIComponents();

                    return;
                }
                // 在主线程更新UI并显示对话框（仅在首次连接或重新连接时）
                if (shipDevices.size() > 0 && !heartbeatActive) {
                    mainHandler.post(() -> {
                        try {
                            // 1. 先更新设备信息卡
                            updateDeviceInfoCard();
                            // 2. 然后显示连接成功对话框
                            showDeviceInfoDialog();
                            Log.d(TAG, "已更新UI和显示对话框");

                            // 3. 启动心跳检测
                            startHeartbeat();
                        } catch (Exception e) {
                            Log.e(TAG, "更新UI过程中出错: " + e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理房间信息消息出错: " + e.getMessage(), e);
        }
    }

    /**
     * 显示断开连接对话框
     */
    private void showDisconnectDialog() {
        if (context == null) {
            Log.e(TAG, "上下文为空，无法显示对话框");
            return;
        }
        // 检查上下文是否是Activity并且已经结束
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                Log.e(TAG, "Activity已结束，无法显示对话框");
                return;
            }
        }
        mainHandler.post(() -> {
            try {
                // 先关闭任何已存在的对话框
                dismissCurrentDialog();
                // 创建新对话框
                statusDialog = new CustomDialog(context);
                statusDialog.setTitle("连接断开");
                statusDialog.setMessage("船舶设备已断开连接，请检查设备状态后重新扫码连接。");
                statusDialog.showErrorIcon();
                statusDialog.hideNegativeButton();
                // 点击确定关闭对话框
                statusDialog.setPositiveButton("确定", v -> {
                    try {
                        dismissCurrentDialog();
                    } catch (Exception e) {
                        Log.e(TAG, "关闭对话框出错: " + e.getMessage(), e);
                    }
                });
                statusDialog.setCancelable(false);
                statusDialog.show();
                Log.d(TAG, "断开连接对话框已显示");
            } catch (Exception e) {
                Log.e(TAG, "显示对话框出错: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 重置UI组件状态
     */
    private void resetUIComponents() {
        mainHandler.post(() -> {
            try {
                if (deviceInfoCard != null) {
                    // 重置设备信息卡
                    deviceInfoCard.setDeviceTitle("主控设备");
                    deviceInfoCard.setDeviceType("管理端");
                    deviceInfoCard.setDeviceId("未连接");
                    deviceInfoCard.setWorkStatus("未连接");
                    deviceInfoCard.setWorkArea("未分配");
                    deviceInfoCard.setBatteryLevel(100);
                    deviceInfoCard.setBatteryStatus(true);
                    deviceInfoCard.setButtonText("连接设备");
                }
                // 更新连接状态
                isConnected = false;
                notifyConnectionStatusChanged();

                Log.d(TAG, "UI组件已重置为初始状态");
            } catch (Exception e) {
                Log.e(TAG, "重置UI组件出错: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 发送设备身份信息
     */
    private void sendIdentityInfo() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("device_id", DEVICE_ID);
            jsonObject.put("identity", IDENTITY);

            String message = jsonObject.toString();
            Log.d(TAG, "发送设备身份信息: " + message);

            webSocketManager.sendMessage(message);
        } catch (JSONException e) {
            Log.e(TAG, "构建身份信息JSON失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询房间信息
     */
    private void queryRoomInfo() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "query_room");

            String message = jsonObject.toString();
            Log.d(TAG, "查询房间信息: " + message);

            webSocketManager.sendMessage(message);
        } catch (JSONException e) {
            Log.e(TAG, "构建查询JSON失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭当前显示的对话框
     */
    private void dismissCurrentDialog() {
        if (statusDialog != null && statusDialog.isShowing()) {
            try {
                statusDialog.dismiss();
                statusDialog = null;
                Log.d(TAG, "关闭当前对话框");
            } catch (Exception e) {
                Log.e(TAG, "关闭对话框出错: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 显示设备信息对话框
     */
    private void showDeviceInfoDialog() {
        if (context == null) {
            Log.e(TAG, "上下文为空，无法显示对话框");
            return;
        }

        // 检查上下文是否是Activity并且已经结束
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                Log.e(TAG, "Activity已结束，无法显示对话框");
                return;
            }
        }

        try {
            // 先关闭任何已存在的对话框
            dismissCurrentDialog();

            // 创建新对话框
            statusDialog = new CustomDialog(context);
            statusDialog.setTitle("设备连接成功");

            // 构建消息内容
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("已成功连接到服务器\n");
            messageBuilder.append("设备服务号：").append(roomId).append("\n\n");

            if (shipDevices.isEmpty()) {
                messageBuilder.append("未检测到船舶设备");
            } else {
                messageBuilder.append("检测到以下船舶设备:\n");
                for (ShipDevice device : shipDevices) {
                    messageBuilder.append("设备ID: ").append(device.getDeviceId().toUpperCase()).append("\n");
                    messageBuilder.append("设备类型: ").append(device.getDeviceType()).append("\n");
                }
            }

            statusDialog.setMessage(messageBuilder.toString());
            statusDialog.showSuccessIcon();
            statusDialog.hideNegativeButton();

            // 点击确定关闭对话框
            statusDialog.setPositiveButton("确定", v -> {
                try {
                    dismissCurrentDialog();
                } catch (Exception e) {
                    Log.e(TAG, "关闭对话框出错: " + e.getMessage(), e);
                }
            });

            statusDialog.setCancelable(false);
            statusDialog.show();

            Log.d(TAG, "设备信息对话框已显示");
        } catch (Exception e) {
            Log.e(TAG, "显示对话框出错: " + e.getMessage(), e);
        }
    }

    /**
     * 更新设备信息卡片
     */
    private void updateDeviceInfoCard() {
        if (deviceInfoCard != null) {
            try {
                // 设置本设备ID
                String displayDeviceId = roomId + DEVICE_ID;
                deviceInfoCard.setDeviceId(displayDeviceId.toUpperCase());

                // 设置设备标题和工作状态
                deviceInfoCard.setDeviceTitle("主控设备");
                deviceInfoCard.setWorkStatus("已连接");

                // 更新按钮文本
                deviceInfoCard.setButtonText("已连接");

                // 如果有船舶设备，显示船舶设备的其他信息
                if (!shipDevices.isEmpty()) {
                    ShipDevice shipDevice = shipDevices.get(0);
                    if ("SHIP_DEVICES".equals(shipDevice.getIdentity())) {
                        deviceInfoCard.setDeviceType("CL-0089");
                    }
                }

                Log.d(TAG, "设备信息卡片已更新: ID=" + displayDeviceId);
            } catch (Exception e) {
                Log.e(TAG, "更新设备信息卡片出错: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "设备信息卡片为空，无法更新");
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        // 停止心跳
        stopHeartbeat();

        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
        isConnected = false;
        notifyConnectionStatusChanged();
        Log.d(TAG, "WebSocket连接已断开");
    }

    /**
     * 连接状态监听接口
     */
    public interface ConnectionStatusListener {
        /**
         * 连接状态变化
         *
         * @param isConnected 是否已连接
         */
        void onConnectionStatusChanged(boolean isConnected);
    }

    /**
     * 船舶设备类
     */
    public static class ShipDevice {
        String deviceId;
        String identity;
        String deviceType;

        public ShipDevice(String deviceId, String identity) {
            this.deviceId = deviceId;
            this.identity = identity;

            // 根据identity确定设备型号
            if ("SHIP_DEVICES".equals(identity)) {
                this.deviceType = "CL-0089";
            } else {
                this.deviceType = "未知型号";
            }
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getIdentity() {
            return identity;
        }

        public String getDeviceType() {
            return deviceType;
        }
    }
}
