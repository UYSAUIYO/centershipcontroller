package com.yuwen.centershipcontroller.Socket;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuwen.centershipcontroller.Component.CustomDialog;
import com.yuwen.centershipcontroller.Utils.WebSocketManager;
import com.yuwen.centershipcontroller.Views.DeviceInfoCard;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主设备WebSocket管理类，负责WebSocket的连接、状态管理和通信
 * @author yuwen
 */
public class MainDeviceSocket {
    private static final String TAG = "MainDeviceSocket";
    private static MainDeviceSocket instance;
    private final WebSocketManager webSocketManager;
    private final ExecutorService socketThread;
    private final Handler mainHandler;
    private Context context;
    private DeviceInfoCard deviceInfoCard;
    private String roomId = "";
    private static final String DEVICE_ID = "h832h9eh29h"; // 本设备ID
    private static final String IDENTITY = "MAIN_DEVICES"; // 本设备类型
    private ConnectionStatusListener connectionStatusListener;
    private boolean isConnected = false;

    // 船舶设备信息
    private final List<ShipDevice> shipDevices = new ArrayList<>();

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

    /**
     * 连接状态监听接口
     */
    public interface ConnectionStatusListener {
        /**
         * 连接状态变化
         * @param isConnected 是否已连接
         */
        void onConnectionStatusChanged(boolean isConnected);
    }

    // 私有构造函数
    private MainDeviceSocket() {
        webSocketManager = WebSocketManager.getInstance();
        socketThread = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
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
     * 初始化，设置上下文和设备信息卡片
     */
    public void init(Context context, DeviceInfoCard deviceInfoCard) {
        this.context = context;
        this.deviceInfoCard = deviceInfoCard;
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
        socketThread.execute(() -> {
            // 添加WebSocket连接处理逻辑
            webSocketManager.setConnectionCallback(new WebSocketManager.ConnectionCallback() {
                @Override
                public void onConnected(String message) {
                    Log.d(TAG, "WebSocket连接成功: " + message);
                    // 更新连接状态
                    isConnected = true;
                    notifyConnectionStatusChanged();

                    // 连接成功后，发送设备身份信息
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
            webSocketManager.setMessageListener(this::processMessage);
        });
    }

    /**
     * 通知连接状态变化
     */
    private void notifyConnectionStatusChanged() {
        if (connectionStatusListener != null) {
            mainHandler.post(() -> connectionStatusListener.onConnectionStatusChanged(isConnected));
        }
    }

    /**
     * 处理收到的WebSocket消息
     */
    private void processMessage(String message) {
        try {
            JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();

            // 处理房间消息
            if (jsonObject.has("type") && "room".equals(jsonObject.get("type").getAsString())) {
                handleRoomMessage(jsonObject);
            }

            // 处理房间信息消息
            else if (jsonObject.has("type") && "room_info".equals(jsonObject.get("type").getAsString())) {
                handleRoomInfoMessage(jsonObject);
            }

            // 可以添加其他消息类型的处理...

        } catch (Exception e) {
            Log.e(TAG, "处理消息出错: " + e.getMessage());
        }
    }

    /**
     * 处理房间消息
     */
    private void handleRoomMessage(JsonObject jsonObject) {
        try {
            if (jsonObject.has("room_id")) {
                roomId = jsonObject.get("room_id").getAsString();
                Log.d(TAG, "收到房间ID: " + roomId);

                // 收到房间ID后，查询房间信息
                socketThread.execute(this::queryRoomInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理房间消息出错: " + e.getMessage());
        }
    }

    /**
     * 处理房间信息消息
     */
    private void handleRoomInfoMessage(JsonObject jsonObject) {
        try {
            if (jsonObject.has("room_id")) {
                roomId = jsonObject.get("room_id").getAsString();

                // 清空之前的设备列表
                shipDevices.clear();

                // 解析客户端列表
                if (jsonObject.has("clients") && jsonObject.get("clients").isJsonArray()) {
                    JsonArray clientsArray = jsonObject.getAsJsonArray("clients");

                    for (JsonElement clientElement : clientsArray) {
                        JsonObject clientObject = clientElement.getAsJsonObject();
                        String deviceId = clientObject.has("device_id") ?
                                clientObject.get("device_id").getAsString() : "";
                        String identity = clientObject.has("identity") ?
                                clientObject.get("identity").getAsString() : "";

                        // 添加到船舶设备列表
                        if ("SHIP_DEVICES".equals(identity)) {
                            shipDevices.add(new ShipDevice(deviceId, identity));
                        }
                    }

                    // 在主线程更新UI并显示对话框
                    mainHandler.post(this::showDeviceInfoDialog);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理房间信息消息出错: " + e.getMessage());
        }
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
            Log.e(TAG, "构建身份信息JSON失败: " + e.getMessage());
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
            Log.e(TAG, "构建查询JSON失败: " + e.getMessage());
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
            // 创建对话框
            CustomDialog dialog = getCustomDialog();
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "显示对话框出错: " + e.getMessage());
        }
    }

    @NonNull
    private CustomDialog getCustomDialog() {
        CustomDialog dialog = new CustomDialog(context);
        dialog.setTitle("设备连接成功");

        // 构建消息内容
        StringBuilder messageBuilder = getStringBuilder();

        dialog.setMessage(messageBuilder.toString());
        dialog.showSuccessIcon();
        dialog.hideNegativeButton();

        // 点击确定关闭对话框
        dialog.setPositiveButton("确定", v -> {
            try {
                dialog.dismiss();
                updateDeviceInfoCard();
            } catch (Exception e) {
                Log.e(TAG, "关闭对话框出错: " + e.getMessage());
            }
        });

        dialog.setCancelable(false);
        return dialog;
    }

    @NonNull
    private StringBuilder getStringBuilder() {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("已成功连接到服务器\n");
        messageBuilder.append("房间号: ").append(roomId).append("\n\n");

        if (shipDevices.isEmpty()) {
            messageBuilder.append("未检测到船舶设备");
        } else {
            messageBuilder.append("检测到以下船舶设备:\n");
            for (ShipDevice device : shipDevices) {
                messageBuilder.append("设备ID: ").append(device.getDeviceId()).append("\n");
                messageBuilder.append("设备类型: ").append(device.getDeviceType()).append("\n");
            }
        }
        return messageBuilder;
    }


    /**
     * 更新设备信息卡片
     */
    private void updateDeviceInfoCard() {
        if (deviceInfoCard != null) {
            // 设置本设备ID
            String displayDeviceId = roomId + "+" + DEVICE_ID;
            deviceInfoCard.setDeviceId(displayDeviceId);

            // 如果有船舶设备，显示船舶设备的其他信息
            if (!shipDevices.isEmpty()) {
                ShipDevice shipDevice = shipDevices.get(0);
                if ("SHIP_DEVICES".equals(shipDevice.getIdentity())) {
                    deviceInfoCard.setDeviceType("CL-0089");
                }
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
        isConnected = false;
        notifyConnectionStatusChanged();
    }
}
