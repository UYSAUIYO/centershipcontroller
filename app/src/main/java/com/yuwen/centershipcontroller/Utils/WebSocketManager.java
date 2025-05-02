package com.yuwen.centershipcontroller.Utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket连接管理类，负责处理二维码扫描后的WebSocket连接
 * @author yuwen
 */
public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private WebSocket webSocket;
    private final OkHttpClient client;
    private ConnectionCallback connectionCallback;
    private MessageListener messageListener;

    private static final class InstanceHolder {
        static final WebSocketManager INSTANCE = new WebSocketManager();
    }

    // 单例模式
    public static WebSocketManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private WebSocketManager() {
        // 初始化OkHttp客户端
        client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 设置连接回调
     * @param callback 连接回调接口
     */
    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    /**
     * 设置消息监听器
     * @param listener 消息监听器接口
     */
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * 发送WebSocket消息
     * @param message 要发送的消息
     * @return 是否发送成功
     */
    public boolean sendMessage(String message) {
        if (webSocket != null) {
            return webSocket.send(message);
        } else {
            Log.e(TAG, "WebSocket未连接，无法发送消息");
            return false;
        }
    }

    // 添加一个方法来获取当前连接状态
    public boolean isConnected() {
        return webSocket != null;
    }

    // 添加获取当前连接URL的方法
    private String currentUrl = "";
    public String getCurrentUrl() {
        return currentUrl;
    }

    // 修改connect方法，保存URL
    public void connect(String serverUrl) {
        // 确保URL格式正确
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            serverUrl = "ws://" + serverUrl;
        }

        this.currentUrl = serverUrl;
        Log.d(TAG, "开始连接WebSocket: " + serverUrl);

        // 创建请求
        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        // 连接WebSocket
        webSocket = client.newWebSocket(request, new WebSocketHandler());
    }


    /**
     * 断开WebSocket连接
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(NORMAL_CLOSURE_STATUS, "用户关闭连接");
            webSocket = null;
        }
    }

    /**
     * WebSocket连接监听器
     */
    private class WebSocketHandler extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            Log.d(TAG, "WebSocket连接已打开");
            // 连接成功，通知回调
            if (connectionCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        connectionCallback.onConnected("连接成功"));
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            Log.d(TAG, "收到WebSocket消息: " + text);
            // 将消息转发给消息监听器
            if (messageListener != null) {
                messageListener.onMessage(text);
            }

            try {
                // 解析JSON消息
                JsonObject jsonObject = JsonParser.parseString(text).getAsJsonObject();

                // 检查是否为连接成功消息
                if (jsonObject.has("type") && "connection".equals(jsonObject.get("type").getAsString())) {
                    if (jsonObject.has("message") && "Connected successfully".equals(jsonObject.get("message").getAsString())) {
                        // 连接成功，通知回调
                        if (connectionCallback != null) {
                            new Handler(Looper.getMainLooper()).post(() ->
                                    connectionCallback.onConnected("连接成功"));
                        }
                    }
                }
            } catch (JsonIOException e) {
                Log.e(TAG, "解析WebSocket消息出错: " + e.getMessage());
            }
        }


        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            Log.d(TAG, "收到二进制消息");
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket正在关闭: " + code + ", " + reason);
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket已关闭: " + code + ", " + reason);
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket连接失败: " + t.getMessage());

            // 通知连接失败
            if (connectionCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        connectionCallback.onFailure("连接失败: " + t.getMessage()));
            }
        }
    }

    /**
     * WebSocket连接回调接口
     */
    public interface ConnectionCallback {
        /**
         * 连接成功回调
         * @param message 成功消息
         */
        void onConnected(String message);

        /**
         * 连接失败回调
         * @param errorMessage 错误信息
         */
        void onFailure(String errorMessage);
    }

    /**
     * WebSocket消息监听器接口
     */
    public interface MessageListener {
        /**
         * 收到消息回调
         * @param text 消息内容
         */
        void onMessage(String text);
    }
}
