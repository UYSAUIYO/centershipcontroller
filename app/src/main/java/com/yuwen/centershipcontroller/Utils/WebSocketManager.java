package com.yuwen.centershipcontroller.Utils;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;

/**
 * @author yuwen
 */
public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private OkHttpClient client;
    private WebSocket webSocket;
    private WebSocketListener listener;

    public WebSocketManager() {
        this.client = new OkHttpClient();
    }

    /**
     * 连接到指定的WebSocket服务器
     *
     * @param url      WebSocket服务器地址
     * @param listener 回调监听器
     */
    public void connect(String url, WebSocketListener listener) {
        this.listener = listener;
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new SocketListener());
    }

    /**
     * 发送文本消息
     *
     * @param message 消息内容
     */
    public void sendMessage(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        } else {
            Log.e(TAG, "WebSocket is not connected");
        }
    }

    /**
     * 关闭WebSocket连接
     */
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    /**
     * 内部类，用于处理WebSocket事件
     */
    private class SocketListener extends okhttp3.WebSocketListener implements WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket opened");
            if (listener != null) {
                listener.onOpen(webSocket, response);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "Message received: " + text);
            if (listener != null) {
                listener.onMessage(webSocket, text);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing: " + reason);
            if (listener != null) {
                listener.onClosing(webSocket, code, reason);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure: " + t.getMessage());
            if (listener != null) {
                listener.onFailure(webSocket, t, response);
            }
        }
    }

    /**
     * WebSocket事件监听器接口
     */
    public interface WebSocketListener {
        void onOpen(WebSocket webSocket, Response response);

        void onMessage(WebSocket webSocket, String text);

        void onClosing(WebSocket webSocket, int code, String reason);

        void onFailure(WebSocket webSocket, Throwable t, Response response);
    }
}