package com.yuwen.centershipcontroller.Socket;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuwen.centershipcontroller.MainActivity;

/**
 * 船舶设备Socket通信类
 * 处理WebSocket接收到的GPS坐标数据
 *
 * @author yuwen
 */
public class ShipDevicesSocket {
    private static final String TAG = "ShipDevicesSocket";
    private static ShipDevicesSocket instance;
    private MainActivity mainActivity;
    private double nmeaValue;

    private ShipDevicesSocket() {
        // 私有构造方法
    }

    /**
     * 获取单例实例
     */
    public static synchronized ShipDevicesSocket getInstance() {
        if (instance == null) {
            instance = new ShipDevicesSocket();
        }
        return instance;
    }

    /**
     * 初始化与MainActivity的连接
     */
    public void init(MainActivity activity) {
        this.mainActivity = activity;
        Log.d(TAG, "初始化ShipDevicesSocket，已连接到MainActivity");
    }

    /**
     * 处理WebSocket消息
     * 特别处理包含GPS坐标的消息
     *
     * @param message WebSocket接收到的消息
     */
    public void processMessage(String message) {
        if (message == null || message.isEmpty()) {
            Log.e(TAG, "收到空消息");
            return;
        }

        try {
            // 尝试解析为JSON
            JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();

            // 检查是否包含GPS数据
            if (jsonObject.has("GPS") && jsonObject.get("GPS").isJsonObject()) {
                JsonObject gpsObject = jsonObject.getAsJsonObject("GPS");

                // 检查是否包含经纬度数据
                if (gpsObject.has("E") && gpsObject.has("N")) {
                    double longitude = gpsObject.get("E").getAsDouble();
                    double latitude = gpsObject.get("N").getAsDouble();
                    String time = gpsObject.has("time") ? gpsObject.get("time").getAsString() : "";

                    // 处理NMEA格式的经纬度 (DDMM.MMMM 格式)
                    // 需要转换为度数格式 (DD.DDDDDD)
                    double lon = convertNMEAToDecimal(longitude);
                    double lat = convertNMEAToDecimal(latitude);

                    Log.d(TAG, "收到GPS坐标: 纬度=" + lat + ", 经度=" + lon + ", 时间=" + time);

                    // 更新地图位置
                    if (mainActivity != null) {
                        mainActivity.updateMapLocation(lat, lon);
                    } else {
                        Log.e(TAG, "MainActivity未初始化，无法更新地图位置");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理GPS消息出错: " + e.getMessage(), e);
        }
    }

    /**
     * 将NMEA格式的经纬度转换为十进制度数
     * NMEA格式: DDMM.MMMM (度分格式)
     * 十进制度数: DD.DDDDDD
     *
     * @param nmeaValue NMEA格式的经纬度值
     * @return 十进制度数
     */
    public double convertNMEAToDecimal(double nmeaValue) {
        this.nmeaValue = nmeaValue;
        // 获取度数部分 (DD)
        int degrees = (int) (nmeaValue / 100);

        // 获取分钟部分 (MM.MMMM)
        double minutes = nmeaValue - (degrees * 100);

        // 转换为十进制度数 (DD + MM.MMMM/60)
        return degrees + (minutes / 60.0);
    }

    /**
     * 重置状态
     */
    public void reset() {
        // 目前没有状态需要重置
    }
}
