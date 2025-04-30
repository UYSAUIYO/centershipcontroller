package com.yuwen.centershipcontroller.Component;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.yuwen.centershipcontroller.R;

/**
 * @author yuwen
 */
public class CustomDialog extends Dialog {

    private final TextView titleText;
    private final TextView messageText;
    private final Button positiveButton;
    private final Button negativeButton;
    private final ImageView statusIcon;

    public CustomDialog(Context context) {
        super(context, R.style.TransparentDialogTheme);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_custom);

        // 设置对话框宽度为屏幕宽度的 80%
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.5);
            window.setAttributes(layoutParams);
        }

        // 初始化UI组件
        titleText = findViewById(R.id.dialog_title);
        messageText = findViewById(R.id.dialog_message);
        positiveButton = findViewById(R.id.dialog_positive_button);
        negativeButton = findViewById(R.id.dialog_negative_button);
        statusIcon = findViewById(R.id.dialog_status_icon);

        // 默认按钮点击事件：关闭对话框
        positiveButton.setOnClickListener(v -> dismiss());
        negativeButton.setOnClickListener(v -> dismiss());
    }

    /**
     * 设置标题
     */
    public void setTitle(String title) {
        titleText.setText(title);
    }

    /**
     * 设置消息
     */
    public void setMessage(String message) {
        messageText.setText(message);
    }

    /**
     * 设置成功图标
     */
    public void showSuccessIcon() {
        statusIcon.setImageResource(R.drawable.check_circle_24px);
        statusIcon.setVisibility(View.VISIBLE);
    }

    /**
     * 设置错误图标
     */
    public void showErrorIcon() {
        statusIcon.setImageResource(R.drawable.error_24px);
        statusIcon.setVisibility(View.VISIBLE);
    }

    /**
     * 设置加载图标
     */
    public void showLoadingIcon() {
        statusIcon.setImageResource(R.drawable.sync_24px);
        statusIcon.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏图标
     */
    public CustomDialog hideIcon() {
        statusIcon.setVisibility(View.GONE);
        return this;
    }

    /**
     * 设置确认按钮
     */
    public void setPositiveButton(String text, View.OnClickListener listener) {
        positiveButton.setText(text);
        positiveButton.setOnClickListener(listener);
        positiveButton.setVisibility(View.VISIBLE);
    }

    /**
     * 设置取消按钮
     */
    public CustomDialog setNegativeButton(String text, View.OnClickListener listener) {
        negativeButton.setText(text);
        negativeButton.setOnClickListener(listener);
        negativeButton.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * 隐藏取消按钮
     */
    public CustomDialog hideNegativeButton() {
        negativeButton.setVisibility(View.GONE);
        return this;
    }

    /**
     * 自动关闭对话框
     *
     * @param delayMillis 延迟时间（毫秒）
     */
    public void autoDismiss(long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(this::dismiss,delayMillis);
    }
}


