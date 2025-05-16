package com.yuwen.centershipcontroller.Activity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yuwen.centershipcontroller.R;
import com.yuwen.centershipcontroller.Utils.JoySticksDecoder;
import com.yuwen.centershipcontroller.Utils.UserSettings;

/**
 * 操作设置页面，包含摇杆滤波器设置等
 * @author yuwen
 */
public class OperationSettingsActivity extends AppCompatActivity {

    private UserSettings settings;
    private TextView filterTypeValueText;
    private TextView filterSensitivityValueText;
    private TextView directionChangeValueText;
    private Switch vibrationSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operation_settings);

        // 设置ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("操作设置");
        }

        // 初始化UserSettings
        settings = new UserSettings(this);

        // 初始化视图组件
        initViews();

        // 加载已保存的设置
        loadSettings();

        // 设置点击事件
        setupClickListeners();
    }

    private void initViews() {
        filterTypeValueText = findViewById(R.id.text_filter_type_value);
        filterSensitivityValueText = findViewById(R.id.text_filter_sensitivity_value);
        directionChangeValueText = findViewById(R.id.text_direction_change_value);
        vibrationSwitch = findViewById(R.id.switch_vibration);
    }

    private void loadSettings() {
        // 加载滤波器类型
        int filterType = settings.getFilterType();
        updateFilterTypeText(filterType);

        // 加载滤波器灵敏度
        float filterAlpha = settings.getFilterAlpha();
        updateFilterSensitivityText(filterAlpha);

        // 加载方向变化延迟
        long directionChangeDelay = settings.getDirectionChangeDelay();
        directionChangeValueText.setText(directionChangeDelay + "ms");

        // 加载振动设置
        boolean vibrationEnabled = settings.getVibrationEnabled();
        vibrationSwitch.setChecked(vibrationEnabled);
    }

    private void setupClickListeners() {
        // 滤波器类型选择
        findViewById(R.id.setting_filter_type).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterTypeDialog();
            }
        });

        // 滤波器灵敏度选择
        findViewById(R.id.setting_filter_sensitivity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterSensitivityDialog();
            }
        });

        // 方向变化延迟选择
        findViewById(R.id.setting_direction_change_delay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectionChangeDelayDialog();
            }
        });

        // 振动开关
        vibrationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setVibrationEnabled(isChecked);
            }
        });
    }

    private void showFilterTypeDialog() {
        int currentFilterType = settings.getFilterType();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择滤波器类型");

        final String[] filterTypes = {"无滤波", "快速滤波", "卡尔曼滤波"};
        builder.setSingleChoiceItems(filterTypes, currentFilterType, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存设置
                settings.setFilterType(which);
                updateFilterTypeText(which);

                // 应用设置
                applyFilterSettings();

                dialog.dismiss();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showFilterSensitivityDialog() {
        float currentAlpha = settings.getFilterAlpha();
        int selectedIndex = 2; // 默认中等灵敏度

        if (currentAlpha <= 0.3f) {
            selectedIndex = 0; // 低灵敏度
        } else if (currentAlpha <= 0.6f) {
            selectedIndex = 1; // 中低灵敏度
        } else if (currentAlpha <= 0.7f) {
            selectedIndex = 2; // 中等灵敏度
        } else if (currentAlpha <= 0.85f) {
            selectedIndex = 3; // 中高灵敏度
        } else {
            selectedIndex = 4; // 高灵敏度
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择滤波器灵敏度");

        final String[] sensitivities = {"低", "中低", "中等", "中高", "高"};
        final float[] alphaValues = {0.3f, 0.5f, 0.7f, 0.85f, 1.0f};

        builder.setSingleChoiceItems(sensitivities, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存设置
                float newAlpha = alphaValues[which];
                settings.setFilterAlpha(newAlpha);
                updateFilterSensitivityText(newAlpha);

                // 应用设置
                applyFilterSettings();

                dialog.dismiss();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDirectionChangeDelayDialog() {
        long currentDelay = settings.getDirectionChangeDelay();
        int selectedIndex = 1; // 默认中等延迟

        if (currentDelay <= 100) {
            selectedIndex = 0;
        } else if (currentDelay <= 150) {
            selectedIndex = 1;
        } else if (currentDelay <= 200) {
            selectedIndex = 2;
        } else {
            selectedIndex = 3;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择方向变化延迟");

        final String[] delays = {"100ms (较快)", "150ms (默认)", "200ms (较慢)", "250ms (缓慢)"};
        final long[] delayValues = {100, 150, 200, 250};

        builder.setSingleChoiceItems(delays, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存设置
                long newDelay = delayValues[which];
                settings.setDirectionChangeDelay(newDelay);
                directionChangeValueText.setText(newDelay + "ms");

                // 应用设置
                JoySticksDecoder.getInstance().setDirectionChangeDelay(newDelay);

                dialog.dismiss();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void updateFilterTypeText(int filterType) {
        String[] filterTypes = {"无滤波", "快速滤波", "卡尔曼滤波"};
        if (filterType >= 0 && filterType < filterTypes.length) {
            filterTypeValueText.setText(filterTypes[filterType]);
        }
    }

    private void updateFilterSensitivityText(float alpha) {
        String sensitivity;
        if (alpha <= 0.3f) {
            sensitivity = "低";
        } else if (alpha <= 0.6f) {
            sensitivity = "中低";
        } else if (alpha <= 0.7f) {
            sensitivity = "中等";
        } else if (alpha <= 0.85f) {
            sensitivity = "中高";
        } else {
            sensitivity = "高";
        }
        filterSensitivityValueText.setText(sensitivity);
    }

    private void applyFilterSettings() {
        int filterType = settings.getFilterType();
        float filterAlpha = settings.getFilterAlpha();

        // 应用滤波器设置到JoySticksDecoder
        JoySticksDecoder.getInstance().setFilterType(filterType, filterAlpha);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // 返回上一个 Activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
