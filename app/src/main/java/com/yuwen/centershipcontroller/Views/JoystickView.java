package com.yuwen.centershipcontroller.Views;

import static androidx.core.math.MathUtils.clamp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.yuwen.centershipcontroller.R;

/**
 * @author yuwen
 */
public class JoystickView extends View {
    // 坐标系定义
    private static final float MIN_OUTPUT = -1.0f;
    private static final float MAX_OUTPUT = 1.0f;
    private static final float MAX_THRUST_RATIO = 0.30f; // 最大推力比例，对应30%的最大值
    private static final long UPDATE_INTERVAL = 100; // 新增常量：更新间隔时间（单位：毫秒）
    private final Paint debugPaint = new Paint();
    // 交互参数
    private final float deadZoneRatio = 0.1f;  // 死区占最大半径的比例
    private final float edgeZoneRatio = 0.05f; // 边缘缓冲比例
    // 控件几何参数
    private float centerX;
    private float centerY;
    private float thumbX;  // 新增坐标声明
    private float thumbY;
    private float thumbRadius;
    private float maxDistance;
    // 输出值
    private float normalizedX = 0f; // [-1.0, 1.0]
    private float normalizedY = 0f; // [-1.0, 1.0]
    // 绘制工具
    private Drawable backgroundDrawable;
    private Drawable thumbDrawable;
    private JoystickListener listener;
    private long lastUpdateTime = 0; // 新增变量：记录上次更新时间

    public JoystickView(Context context) {
        super(context);
        init(context);
    }


    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // 初始化图形资源
        try {
            backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.joystick_background);
            thumbDrawable = ContextCompat.getDrawable(context, R.drawable.joystick_2);

            // 添加资源检查，避免在绘制时出现空指针异常
            if (backgroundDrawable == null || thumbDrawable == null) {
                Log.w("JoystickView", "无法加载一个或多个所需资源");
            }
        } catch (Exception e) {
            Log.e("JoystickView", "资源加载错误", e);
        }
        // 调试绘制配置
        debugPaint.setColor(Color.RED);
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(4);

        // 初始化thumb位置
        thumbX = 0;
        thumbY = 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 计算几何参数
        centerX = w / 2f;
        centerY = h / 2f;
        maxDistance = Math.min(w, h) * 0.3f; // 最大移动距离
        thumbRadius = maxDistance * 0.2f;    // 摇杆头尺寸
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                processTouch(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetPosition();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void processTouch(float touchX, float touchY) {
        // 获取当前时间
        long currentTime = System.currentTimeMillis();
        // 检查是否超过更新间隔
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
            return; // 如果未超过间隔时间，则直接返回，不进行更新
        }
        lastUpdateTime = currentTime; // 更新上次更新时间

        // 计算原始偏移量
        float deltaX = touchX - centerX;
        float deltaY = touchY - centerY;

        // 计算极坐标
        float distance = (float) Math.hypot(deltaX, deltaY);
        float angle = (float) Math.atan2(deltaY, deltaX);

        // 应用死区处理
        if (distance < maxDistance * deadZoneRatio) {
            resetPosition();
            return;
        }

        // 应用边缘缓冲
        float effectiveDistance = Math.min(distance, maxDistance * (1 - edgeZoneRatio));

        // 标准化输出
        normalizedX = (effectiveDistance / maxDistance) * (float) Math.cos(angle);
        normalizedY = (effectiveDistance / maxDistance) * (float) Math.sin(angle);
        
        // 应用最大推力限制
        float magnitude = (float) Math.hypot(normalizedX, normalizedY);
        if (magnitude > MAX_THRUST_RATIO) {
            float scaleFactor = MAX_THRUST_RATIO / magnitude;
            normalizedX *= scaleFactor;
            normalizedY *= scaleFactor;
        }

        // 约束输出范围
        normalizedX = clamp(normalizedX, MIN_OUTPUT, MAX_OUTPUT);
        normalizedY = clamp(normalizedY, MIN_OUTPUT, MAX_OUTPUT);

        // 更新摇杆位置
        updateThumbPosition();
        notifyListener();
        invalidate();
    }

    private void updateThumbPosition() {
        float oldX = thumbX;
        float oldY = thumbY;

        float scaledX = normalizedX * maxDistance;
        float scaledY = normalizedY * maxDistance;
        thumbX = centerX + scaledX;
        thumbY = centerY + scaledY;

        // 只有在位置发生变化时才重绘
        if (oldX != thumbX || oldY != thumbY) {
            invalidate();
        }
    }

    private void resetPosition() {
        normalizedX = 0f;
        normalizedY = 0f;
        thumbX = centerX;
        thumbY = centerY;
        notifyListener();
        invalidate();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onJoystickChanged(normalizedX, normalizedY);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // 绘制背景
        drawBackground(canvas);

        // 绘制摇杆
        if (thumbDrawable != null) {
            int left = (int) (thumbX - thumbRadius);
            int top = (int) (thumbY - thumbRadius);
            thumbDrawable.setBounds(left, top,
                    left + (int) (thumbRadius * 2),
                    top + (int) (thumbRadius * 2));
            thumbDrawable.draw(canvas);
        }

        // 调试绘制
        canvas.drawCircle(centerX, centerY, maxDistance * deadZoneRatio, debugPaint);
        canvas.drawCircle(centerX, centerY, maxDistance * (1 - edgeZoneRatio), debugPaint);
    }

    private void drawBackground(Canvas canvas) {
        if (backgroundDrawable != null) {
            int left = (int) (centerX - maxDistance);
            int top = (int) (centerY - maxDistance);
            backgroundDrawable.setBounds(left, top,
                    left + (int) (maxDistance * 2),
                    top + (int) (maxDistance * 2));
            backgroundDrawable.draw(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        listener = null;
    }

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    public float getNormalizedX() {
        return normalizedX;
    }

    public float getNormalizedY() {
        return normalizedY;
    }

    // 监听接口
    public interface JoystickListener {
        void onJoystickChanged(float x, float y);
    }
}