package com.yuwen.centershipcontroller.Utils;


import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;



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
     * 连接到WebSocket服务器
     * @param url WebSocket连接URL
     * @param callback 连接回调
     */
    public void connect(String url, ConnectionCallback callback) {
        if (url == null || url.isEmpty()) {
            if (callback != null) {
                callback.onFailure("无效的连接地址");
            }
            return;
        }

        // 设置回调
        this.connectionCallback = callback;

        // 断开之前的连接
        disconnect();

        // 创建WebSocket请求
        Request request = new Request.Builder()
                .url(url)
                .build();

        // 连接WebSocket
        webSocket = client.newWebSocket(request, new WebSocketHandler());

        Log.d(TAG, "正在连接WebSocket: " + url);
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
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            Log.d(TAG, "收到消息: " + text);
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

                // 这里可以添加其他消息类型的处理

            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON解析错误: " + e.getMessage());
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            Log.d(TAG, "收到二进制消息");
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, @NonNull String reason) {
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
}
