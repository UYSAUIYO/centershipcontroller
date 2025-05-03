package com.yuwen.centershipcontroller.Component;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.yuwen.centershipcontroller.R;

/**
 * 自定义对话框
 *
 * @author yuwen
 */
public class CustomDialog extends Dialog {
    private final Context context;
    private TextView titleTextView;
    private TextView messageTextView;
    private Button positiveButton;
    private Button negativeButton;
    private ImageView iconImageView;
    private Handler autoDismissHandler;
    private Runnable autoDismissRunnable;

    public CustomDialog(Context context) {
        super(context);
        this.context = context;
        init();
    }

    private void init() {
        // 使用自定义布局
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom, null);
        setContentView(view);

        // 设置默认宽度为屏幕的 80%
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            // 80% 屏幕宽度
            params.width = (int) (displayMetrics.widthPixels * 0.5f);
            window.setAttributes(params);
        }

        // 获取视图引用
        titleTextView = view.findViewById(R.id.dialog_title);
        messageTextView = view.findViewById(R.id.dialog_message);
        positiveButton = view.findViewById(R.id.dialog_positive_button);
        negativeButton = view.findViewById(R.id.dialog_negative_button);
        iconImageView = view.findViewById(R.id.dialog_status_icon);

        // 设置默认按钮
        positiveButton.setOnClickListener(v -> dismiss());
        negativeButton.setOnClickListener(v -> dismiss());

        // 初始化自动关闭处理器
        autoDismissHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 设置标题
     */
    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    /**
     * 设置消息内容
     */
    public void setMessage(String message) {
        messageTextView.setText(message);
    }

    /**
     * 设置确定按钮
     */
    public void setPositiveButton(String text, View.OnClickListener listener) {
        positiveButton.setText(text);
        positiveButton.setOnClickListener(listener);
    }

    /**
     * 设置取消按钮
     */
    public void setNegativeButton(String text, View.OnClickListener listener) {
        negativeButton.setText(text);
        negativeButton.setOnClickListener(listener);
        negativeButton.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏取消按钮
     */
    public void hideNegativeButton() {
        negativeButton.setVisibility(View.GONE);
    }

    /**
     * 显示成功图标
     */
    public void showSuccessIcon() {
        iconImageView.setImageResource(R.drawable.check_circle_24px);
        iconImageView.setVisibility(View.VISIBLE);
    }

    /**
     * 显示错误图标
     */
    public void showErrorIcon() {
        iconImageView.setImageResource(R.drawable.error_24px);
        iconImageView.setVisibility(View.VISIBLE);
    }

    /**
     * 显示加载图标
     */
    public void showLoadingIcon() {
        iconImageView.setImageResource(R.drawable.sync_24px);
        iconImageView.setVisibility(View.VISIBLE);
    }

    /**
     * 设置自动关闭时间（毫秒）
     * 安全版本，检查Activity是否已结束
     */
    public void autoDismiss(int delayMillis) {
        // 先取消之前的自动关闭任务
        if (autoDismissRunnable != null) {
            autoDismissHandler.removeCallbacks(autoDismissRunnable);
        }

        // 创建新的自动关闭任务
        autoDismissRunnable = () -> {
            try {
                // 检查上下文是否是Activity并且是否已结束
                if (context instanceof Activity) {
                    Activity activity = (Activity) context;
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        if (isShowing()) {
                            dismiss();
                        }
                    }
                } else {
                    // 如果不是Activity上下文，直接尝试关闭
                    if (isShowing()) {
                        dismiss();
                    }
                }
            } catch (Exception e) {
                // 忽略可能的异常
            }
        };

        // 延迟执行
        autoDismissHandler.postDelayed(autoDismissRunnable, delayMillis);
    }

    @Override
    public void dismiss() {
        try {
            // 检查窗口是否仍然附加在窗口管理器上
            if (getWindow() != null && getWindow().getDecorView().getWindowToken() != null) {
                super.dismiss();
            }
        } catch (Exception e) {
            // 忽略可能的异常
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 取消所有等待的消息
        if (autoDismissHandler != null) {
            autoDismissHandler.removeCallbacksAndMessages(null);
        }
    }
}
