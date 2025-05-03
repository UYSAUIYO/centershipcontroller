package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.yuwen.centershipcontroller.Component.DeviceInfoCard;

/**
 * 二维码扫描结果处理工具类
 *
 * @author yuwen
 */
public class QRCodeResultHandler {
    private static final String TAG = "QRCodeResultHandler";
    private static final String CONNECT_URL_KEY = "connect_url";
    private final QRResultCallback callback;
    private final Context context;
    private DeviceInfoCard deviceInfoCard;

    public QRCodeResultHandler(Context context, QRResultCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * 设置设备信息卡片
     *
     * @param deviceInfoCard 设备信息卡片
     */
    public void setDeviceInfoCard(DeviceInfoCard deviceInfoCard) {
        this.deviceInfoCard = deviceInfoCard;
    }

    /**
     * 处理扫描结果
     *
     * @param result 扫描的二维码内容
     */
    public void processResult(String result) {
        Log.d(TAG, "处理扫描结果: " + result);
        if (result == null || result.isEmpty()) {
            notifyError();
            return;
        }
        // 尝试解析为JSON
        try {
            JsonObject jsonObject = JsonParser.parseString(result).getAsJsonObject();
            // 检查是否包含connect_url字段
            if (jsonObject.has(CONNECT_URL_KEY)) {
                String wsUrl = jsonObject.get(CONNECT_URL_KEY).getAsString();
                Log.d(TAG, "WebSocket URL: " + wsUrl);
                if (wsUrl != null && !wsUrl.isEmpty()) {
                    // 连接WebSocket
                    connectWebSocket(wsUrl);
                    return;
                }
            }
            // JSON格式但没有connect_url字段
            notifyResult(result);
        } catch (JsonSyntaxException e) {
            // 不是JSON格式，直接返回结果
            notifyResult(result);
        }
    }

    /**
     * 连接WebSocket
     *
     * @param url WebSocket URL
     */
    // 修改WebSocket连接处理部分
    private void connectWebSocket(String url) {
        // 确保URL格式正确
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            url = "ws://" + url;
        }
        Log.d(TAG, "正在连接WebSocket: " + url);
        // 获取WebSocketManager实例
        WebSocketManager webSocketManager = WebSocketManager.getInstance();

        // 如果已经连接到同一个URL，则直接触发成功回调
        if (webSocketManager.isConnected() && url.equals(webSocketManager.getCurrentUrl())) {
            Log.d(TAG, "WebSocket已连接到该URL，直接返回成功");
            if (callback != null) {
                callback.onConnectionSuccess("连接成功");
            }
            return;
        }
        // 设置连接回调
        webSocketManager.setConnectionCallback(new WebSocketManager.ConnectionCallback() {
            @Override
            public void onConnected(String message) {
                Log.d(TAG, "WebSocket连接成功，通知UI: " + message);
                if (callback != null) {
                    callback.onConnectionSuccess(message);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "WebSocket连接失败: " + errorMessage);
                if (callback != null) {
                    callback.onConnectionFailure(errorMessage);
                }
            }
        });
        // 通知正在处理
        if (callback != null) {
            callback.onProcessing("正在连接到服务器...");
        }
        // 断开可能存在的连接
        webSocketManager.disconnect();

        // 连接WebSocket
        webSocketManager.connect(url);
    }


    /**
     * 通知普通结果
     */
    private void notifyResult(String result) {
        if (callback != null) {
            callback.onNormalResult(result);
        }
    }

    /**
     * 通知错误
     */
    private void notifyError() {
        if (callback != null) {
            callback.onError("扫描结果为空");
        }
    }

    /**
     * 二维码结果回调接口
     */
    public interface QRResultCallback {
        /**
         * 普通扫描结果回调（非WebSocket连接）
         */
        void onNormalResult(String result);

        /**
         * 正在处理连接
         */
        void onProcessing(String message);

        /**
         * WebSocket连接成功回调
         */
        void onConnectionSuccess(String message);

        /**
         * WebSocket连接失败回调
         */
        void onConnectionFailure(String error);

        /**
         * 错误回调
         */
        void onError(String error);
    }
}
