package com.yuwen.centershipcontroller.Views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.yuwen.centershipcontroller.R;

public class DeviceInfoCard extends CardView {

    private ImageView deviceImage;
    private TextView deviceTitle;
    private TextView deviceId;
    private TextView deviceType;
    private TextView workArea;
    private TextView workStatus;
    private ImageView batteryIcon;
    private Button actionButton;

    // 构造函数
    public DeviceInfoCard(@NonNull Context context) {
        super(context);
        init(context, null);
    }
    public DeviceInfoCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    public DeviceInfoCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.device_info_card, this, true);

        // 初始化视图
        deviceImage = findViewById(R.id.device_image);
        deviceTitle = findViewById(R.id.device_title);
        deviceType = findViewById(R.id.device_type);  // 初始化新增的设备类型TextView
        deviceId = findViewById(R.id.device_id);
        workArea = findViewById(R.id.work_area);
        workStatus = findViewById(R.id.work_status);
        batteryIcon = findViewById(R.id.battery_icon);
        actionButton = findViewById(R.id.action_button);

        // 如果有自定义属性，解析它们
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.DeviceInfoCard);

            // 设置设备图片
            Drawable deviceDrawable = typedArray.getDrawable(R.styleable.DeviceInfoCard_deviceImage);
            if (deviceDrawable != null) {
                deviceImage.setImageDrawable(deviceDrawable);
            }

            // 设置标题
            String title = typedArray.getString(R.styleable.DeviceInfoCard_deviceTitle);
            if (title != null) {
                deviceTitle.setText(title);
            }

            // 设置设备类型 - 新增处理
            String type = typedArray.getString(R.styleable.DeviceInfoCard_deviceType);
            if (type != null) {
                deviceType.setText("设备类型：" + type);
            }

            // 设置设备ID
            String id = typedArray.getString(R.styleable.DeviceInfoCard_deviceId);
            if (id != null) {
                deviceId.setText("设备号：" + id);
            }

            typedArray.recycle();
        }
    }

    // 公共方法，用于设置设备图片
    public void setDeviceImage(Drawable drawable) {
        deviceImage.setImageDrawable(drawable);
    }

    public void setDeviceImage(@DrawableRes int resId) {
        deviceImage.setImageResource(resId);
    }

    // 设置设备名称
    public void setDeviceTitle(String title) {
        deviceTitle.setText(title);
    }

    // 设置设备ID
    public void setDeviceId(String id) {
        deviceId.setText("设备号：" + id);
    }

    // 设置工作区域
    public void setWorkArea(String area) {
        workArea.setText(area);
    }

    // 设置工作状态
    public void setWorkStatus(String status) {
        workStatus.setText(status);
    }

    // 设置电池图标
    public void setBatteryIcon(Drawable drawable) {
        batteryIcon.setImageDrawable(drawable);
    }

    public void setBatteryIcon(@DrawableRes int resId) {
        batteryIcon.setImageResource(resId);
    }

    // 设置按钮文本
    public void setButtonText(String text) {
        actionButton.setText(text);
    }
    public void setDeviceType(String type) {
        deviceType.setText("设备类型：" + type);
    }

    // 设置按钮点击监听器
    public void setActionButtonClickListener(OnClickListener listener) {
        actionButton.setOnClickListener(listener);
    }
}
