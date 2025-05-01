package com.yuwen.centershipcontroller.Views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.yuwen.centershipcontroller.R;

/**
 * 自定义设备信息卡片视图组件，继承自FrameLayout。
 * 用于显示设备的基本信息，包括设备图片、名称、ID、类型、工作区域、工作状态和电池状态等。
 * 支持拖拽功能，可以在父布局中自由移动位置。
 * 
 * @author yuwen
 * @version 1.0
 */
public class DeviceInfoCard extends FrameLayout {
    /** 设备图片显示控件 */
    private ImageView deviceImage;
    /** 设备名称显示控件 */
    private TextView deviceTitle;
    /** 设备ID显示控件 */
    private TextView deviceId;
    /** 设备类型显示控件 */
    private TextView deviceType;
    /** 工作区域显示控件 */
    private TextView workArea;
    /** 工作状态显示控件 */
    private TextView workStatus;
    /** 电池图标显示控件 */
    private ImageView batteryIcon;
    /** 操作按钮控件 */
    private Button actionButton;
    /** 电池状态显示控件 */
    private TextView batteryStatus;
    // 构造函数
    public DeviceInfoCard(@NonNull Context context) {
        super(context);
        init(context, null);
    }
    public DeviceInfoCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
        // 添加触摸事件监听器以支持拖动功能
        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 记录初始触摸位置
                    startX = event.getRawX();
                    startY = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    // 计算移动的距离
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    // 更新组件的位置
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) getLayoutParams();
                    params.leftMargin += (int) dx;
                    params.topMargin += (int) dy;
                    setLayoutParams(params);
                    // 更新起始位置
                    startX = event.getRawX();
                    startY = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    // 检测是否为点击事件
                    float moveDistanceX = Math.abs(event.getRawX() - startX);
                    float moveDistanceY = Math.abs(event.getRawY() - startY);
                    if (moveDistanceX < 10 && moveDistanceY < 10) { // 点击阈值
                        performClick(); // 调用 performClick 以支持 Accessibility
                    }
                    break;
            }
            return true; // 消费触摸事件
        });
    }
    public DeviceInfoCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    private void init(Context context, AttributeSet attrs) {
        // 确保父布局是透明的
        setBackgroundColor(Color.TRANSPARENT);

        // 加载布局
        View root = LayoutInflater.from(context).inflate(R.layout.device_info_card, this, true);

        // 给根视图设置圆角背景
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(context.getResources().getDimensionPixelSize(R.dimen.card_corner_radius));
        // 添加阴影效果，API 21及以上支持
        root.setElevation(context.getResources().getDimensionPixelSize(R.dimen.card_elevation));
        root.setBackground(drawable);
        // 初始化视图
        deviceImage = findViewById(R.id.device_image);
        deviceTitle = findViewById(R.id.device_title);
        deviceType = findViewById(R.id.device_type);
        deviceId = findViewById(R.id.device_id);
        workArea = findViewById(R.id.work_area);
        workStatus = findViewById(R.id.work_status);
        batteryIcon = findViewById(R.id.battery_icon);
        actionButton = findViewById(R.id.action_button);
        batteryStatus = findViewById(R.id.battery_type);
        // 如果有自定义属性，解析它们
        if (attrs != null) {
            try (TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.DeviceInfoCard)) {
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
                // 设置设备类型
                String type = typedArray.getString(R.styleable.DeviceInfoCard_deviceType);
                if (type != null) {
                    deviceType.setText(type);
                }
                // 设置设备ID
                String id = typedArray.getString(R.styleable.DeviceInfoCard_deviceId);
                if (id != null) {
                    deviceId.setText(id);
                }
                // 新增：设置工作区域
                String area = typedArray.getString(R.styleable.DeviceInfoCard_workArea);
                if (area != null) {
                    workArea.setText(area);
                }
                // 新增：设置工作状态
                String status = typedArray.getString(R.styleable.DeviceInfoCard_workStatus);
                if (status != null) {
                    workStatus.setText(status);
                }
                if (batteryStatus != null) {
                    batteryStatus.setText(status);
                }
            } catch (Exception e) {
                // 捕获并处理可能的异常（如资源解析失败）
                Log.e("DeviceInfoCard", "Error while setting device image.", e);
            }
        }
    }


    // 公共方法，用于设置设备图片
    /**
     * 设置设备图片
     *
     * @param drawable 要设置的图片Drawable对象
     */
    public void setDeviceImage(Drawable drawable) {
        deviceImage.setImageDrawable(drawable);
    }

    /**
     * 设置设备图片资源
     *
     * @param resId 图片资源ID
     */
    public void setDeviceImage(@DrawableRes int resId) {
        deviceImage.setImageResource(resId);
    }

    // 设置设备名称
    /**
     * 设置设备名称
     *
     * @param title 设备名称
     */
    public void setDeviceTitle(String title) {
        deviceTitle.setText(title);
    }

    // 设置设备ID
    /**
     * 设置设备ID
     *
     * @param id 设备ID
     */
    public void setDeviceId(String id) {
        deviceId.setText(id);
    }

    // 设置工作区域
    /**
     * 设置工作区域
     *
     * @param area 工作区域名称
     */
    public void setWorkArea(String area) {
        workArea.setText(area);
    }

    // 设置工作状态
    /**
     * 设置工作状态
     *
     * @param status 工作状态描述
     */
    public void setWorkStatus(String status) {
        workStatus.setText(status);
    }

    // 设置电池图标
    /**
     * 设置电池图标
     *
     * @param drawable 电池图标Drawable对象
     */
    public void setBatteryIcon(Drawable drawable) {
        batteryIcon.setImageDrawable(drawable);
    }

    /**
     * 设置电池图标资源
     *
     * @param resId 电池图标资源ID
     */
    public void setBatteryIcon(@DrawableRes int resId) {
        batteryIcon.setImageResource(resId);
    }

        /**
     * 根据电量百分比设置电池图标
     * 根据不同的电量级别显示对应的电池图标，电量级别分为10个等级
     *
     * @param level 电量百分比，范围0-100
     * @throws IllegalArgumentException 当电量百分比不在0-100范围内时抛出
     * @throws IllegalStateException 当batteryIcon未初始化时抛出
     */
    public void setBatteryLevel(int level) {
        // 参数校验：电量百分比必须在 0 到 100 之间
        if (level < 0 || level > 100) {
            throw new IllegalArgumentException("电量百分比必须在 0 到 100 之间");
        }

        // 确保 batteryIcon 不为 null，避免 NullPointerException
        if (batteryIcon == null) {
            throw new IllegalStateException("batteryIcon 尚未初始化");
        }

        // 定义电量等级与图标资源的映射关系
        int[] thresholds = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        int[] resources = {
            R.drawable.battery_10,
            R.drawable.battery_20,
            R.drawable.battery_30,
            R.drawable.battery_40,
            R.drawable.battery_50,
            R.drawable.battery_60,
            R.drawable.battery_70,
            R.drawable.battery_80,
            R.drawable.battery_90,
            R.drawable.battery
        };

        // 遍历阈值数组，找到匹配的图标资源
        for (int i = 0; i < thresholds.length; i++) {
            if (level < thresholds[i]) {
                batteryIcon.setImageResource(resources[i]);
                return;
            }
        }

        // 如果电量达到 100%，设置默认图标
        batteryIcon.setImageResource(resources[resources.length - 1]);
    }

    /**
     * 设置电池充电状态
     * 根据充电状态设置电池状态文本和颜色
     *
     * @param isCharging true表示正在充电，false表示放电中
     */
    public void setBatteryStatus(boolean isCharging) {
        if (isCharging) {
            batteryStatus.setText("充电中");
            batteryStatus.setTextColor(Color.GREEN);
        } else {
            batteryStatus.setText("放电中");
            batteryStatus.setTextColor(Color.RED);
        }
    }

    /**
     * 设置操作按钮文本
     *
     * @param text 按钮显示文本
     */
    public void setButtonText(String text) {
        actionButton.setText(text);
    }
    /**
     * 设置设备类型
     *
     * @param type 设备类型描述
     */
    public void setDeviceType(String type) {
        deviceType.setText(type);
    }

    /**
     * 设置操作按钮点击监听器
     *
     * @param listener 按钮点击事件监听器
     */
    public void setActionButtonClickListener(OnClickListener listener) {
        actionButton.setOnClickListener(listener);
    }

    // 新增变量用于记录触摸起始位置
    private float startX;
    private float startY;

}
