package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 用户设置管理类，用于保存和读取用户配置项
 * @author yuwen
 */
public class UserSettings {
    private static final String PREFS_NAME = "CenterShipControllerPrefs";
    private static final String KEY_FIRST_RUN = "first_run";

    // 滤波器设置
    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_FILTER_ALPHA = "filter_alpha";
    private static final String KEY_DIRECTION_CHANGE_DELAY = "direction_change_delay";
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";

    // 默认值
    private static final int DEFAULT_FILTER_TYPE = 1; // 默认使用快速滤波
    private static final float DEFAULT_FILTER_ALPHA = 0.7f; // 默认中等灵敏度
    private static final long DEFAULT_DIRECTION_CHANGE_DELAY = 150; // 默认150ms
    private static final boolean DEFAULT_VIBRATION_ENABLED = true; // 默认启用振动

    private final SharedPreferences preferences;

    public UserSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 检查是否首次运行
     * @return 是否首次运行
     */
    public boolean checkFirstRun() {
        boolean isFirstRun = preferences.getBoolean(KEY_FIRST_RUN, true);
        if (isFirstRun) {
            // 首次运行，存储默认设置
            preferences.edit()
                    .putBoolean(KEY_FIRST_RUN, false)
                    .putInt(KEY_FILTER_TYPE, DEFAULT_FILTER_TYPE)
                    .putFloat(KEY_FILTER_ALPHA, DEFAULT_FILTER_ALPHA)
                    .putLong(KEY_DIRECTION_CHANGE_DELAY, DEFAULT_DIRECTION_CHANGE_DELAY)
                    .putBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
                    .apply();
        }
        return isFirstRun;
    }

    /**
     * 获取滤波器类型
     * @return 滤波器类型 0=无滤波, 1=快速滤波, 2=卡尔曼滤波
     */
    public int getFilterType() {
        return preferences.getInt(KEY_FILTER_TYPE, DEFAULT_FILTER_TYPE);
    }

    /**
     * 设置滤波器类型
     * @param filterType 滤波器类型
     */
    public void setFilterType(int filterType) {
        preferences.edit().putInt(KEY_FILTER_TYPE, filterType).apply();
    }

    /**
     * 获取滤波器灵敏度
     * @return 滤波系数
     */
    public float getFilterAlpha() {
        return preferences.getFloat(KEY_FILTER_ALPHA, DEFAULT_FILTER_ALPHA);
    }

    /**
     * 设置滤波器灵敏度
     * @param alpha 滤波系数，范围[0.1, 1.0]
     */
    public void setFilterAlpha(float alpha) {
        // 确保alpha在有效范围内
        float validAlpha = Math.max(0.1f, Math.min(1.0f, alpha));
        preferences.edit().putFloat(KEY_FILTER_ALPHA, validAlpha).apply();
    }

    /**
     * 获取方向变化延迟
     * @return 延迟毫秒数
     */
    public long getDirectionChangeDelay() {
        return preferences.getLong(KEY_DIRECTION_CHANGE_DELAY, DEFAULT_DIRECTION_CHANGE_DELAY);
    }

    /**
     * 设置方向变化延迟
     * @param delayMs 延迟毫秒数
     */
    public void setDirectionChangeDelay(long delayMs) {
        preferences.edit().putLong(KEY_DIRECTION_CHANGE_DELAY, delayMs).apply();
    }

    /**
     * 获取振动是否启用
     * @return 是否启用振动
     */
    public boolean getVibrationEnabled() {
        return preferences.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED);
    }

    /**
     * 设置振动是否启用
     * @param enabled 是否启用振动
     */
    public void setVibrationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }
}
