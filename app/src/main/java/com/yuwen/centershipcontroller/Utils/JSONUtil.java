package com.yuwen.centershipcontroller.Utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * @author yuwen
 */
public class JSONUtil {
    private static final Gson gson = new Gson();

    /**
     * 将对象转换为JSON字符串
     */
    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    /**
     * 将JSON字符串解析为对象
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            return null; // 解析失败返回null
        }
    }

    public static boolean isValidJson(String json) {
        if (json == null) return false;
        try {
            Object obj = gson.fromJson(json, Object.class); // 显式接收返回值
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }


    /**
     * 从JSON字符串获取指定字段的值
     */
    public static String getField(String json, String fieldName) {
        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            return jsonObject.has(fieldName) ? jsonObject.get(fieldName).getAsString() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}