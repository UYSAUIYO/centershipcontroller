package com.yuwen.centershipcontroller.Activity;

import android.os.Bundle;
import android.view.MenuItem;

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
        
        // 确保使用支持 ActionBar 的主题
        // 在 AndroidManifest.xml 中设置主题为 Theme.AppCompat 或其子主题
        
        // 设置 ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("设置");
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