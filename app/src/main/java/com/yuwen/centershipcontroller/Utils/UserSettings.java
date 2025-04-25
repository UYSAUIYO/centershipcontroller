package com.yuwen.centershipcontroller.Utils;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.yuwen.centershipcontroller.MainActivity;

/**
 * @author yuwen404
 */
public class UserSettings {
    // SharedPreferences的key常量
    private static final String PREF_NAME = "app_settings";
    private static final String KEY_FIRST_RUN = "is_first_run";

    private final SharedPreferences preferences;

    public UserSettings(Context context) {
        // 获取SharedPreferences实例
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 检查应用是否是首次运行
     * @return 如果是首次运行返回true，否则返回false
     */
    public boolean checkFirstRun() {
        // 默认值为true，表示首次运行
        boolean isFirstRun = preferences.getBoolean(KEY_FIRST_RUN, true);

        // 如果是首次运行，标记为非首次运行并保存
        if (isFirstRun) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(KEY_FIRST_RUN, false);
            editor.apply();
        }

        return isFirstRun;
    }

    /**
     * 重置首次运行状态（用于测试或重置应用）
     */
    public void resetFirstRunState() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_FIRST_RUN, true);
        editor.apply();
    }
}
