package com.yuwen.centershipcontroller.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.yuwen.centershipcontroller.R;

/**
 * @author yuwen
 */
public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        // 设置 ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("设置");
        }

        // 添加"操作设置"入口
        setupOperationSettings();
    }

    private void setupOperationSettings() {
        // 在常规设置部分添加操作设置入口
        RelativeLayout settingDataUsage = findViewById(R.id.setting_opration_usage);
        settingDataUsage.setOnClickListener(v -> {
            // 跳转到操作设置页面
            Intent intent = new Intent(SettingsActivity.this, OperationSettingsActivity.class);
            startActivity(intent);
        });

        // 修改文本显示
        // 注意：您可能需要修改布局文件中的这个文本显示
        // 如果不想修改布局，这里可以动态修改文本
        try {
            View dataUsageView = findViewById(R.id.setting_opration_usage);
            if (dataUsageView != null) {
                View textView = dataUsageView.findViewById(android.R.id.text1);
                if (textView instanceof android.widget.TextView) {
                    ((android.widget.TextView) textView).setText("操作设置");
                }
            }
        } catch (Exception e) {
            // 忽略异常，保持原有文本
        }
    }

    // 处理返回按钮点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // 返回上一个 Activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
