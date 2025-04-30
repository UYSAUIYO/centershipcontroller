package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * 二维码扫描结果处理工具类
 * @author yuwen
 */
public class QRCodeResultHandler {
    private static final String TAG = "QRCodeResultHandler";
    private static final String CONNECT_URL_KEY = "connect_url";

    private final QRResultCallback callback;

    public QRCodeResultHandler(Context context, QRResultCallback callback) {
        this.callback = callback;
    }

    /**
     * 处理扫描结果
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
     * @param url WebSocket URL
     */
    private void connectWebSocket(String url) {
        Log.d(TAG, "正在连接WebSocket: " + url);

        if (callback != null) {
            callback.onProcessing("正在连接服务器...");
        }

        WebSocketManager.getInstance().connect(url, new WebSocketManager.ConnectionCallback() {
            @Override
            public void onConnected(String message) {
                Log.d(TAG, "WebSocket连接成功: " + message);
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

