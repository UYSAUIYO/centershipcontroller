package com.yuwen.centershipcontroller.Views;

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

import java.util.LinkedList;
import java.util.Queue;

public class JoystickView extends View {
    private float centerX;
    private float centerY;
    private float thumbX;
    private float thumbY;
    private float maxDistance;

    private int normalizedLength = 0; // -100 到 +100
    private int normalizedAngle = 0;  // -100 到 +100

    private Paint backgroundPaint;
    private Paint thumbPaint;
    private Paint debugPaint;

    private Drawable backgroundDrawable;
    private Drawable thumbDrawable;

    private JoystickListener listener;

    public interface JoystickListener {
        void onJoystickMoved(int length, int angle);
    }

    public JoystickView(Context context) {
        super(context);
        init(context);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private float deadZoneRadius; // 新增死区范围变量
    private float edgeDeadZoneRadius; // 新增边缘死区范围变量

    private void init(Context context) {
        // 初始化绘制工具
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.GRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(Color.BLUE);
        thumbPaint.setStyle(Paint.Style.FILL);

        debugPaint = new Paint();
        debugPaint.setColor(Color.RED);
        debugPaint.setStrokeWidth(5);
        debugPaint.setStyle(Paint.Style.STROKE);

        // 加载图片资源
        try {
            backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.joystick_background);
            thumbDrawable = ContextCompat.getDrawable(context, R.drawable.joystick_2);
        } catch (Exception e) {
            Log.e("JoystickView", "Failed to load drawable resources", e);
        }

        // 设置透明度
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 初始化死区范围，默认为最大距离的 10%
        deadZoneRadius = 0;
        edgeDeadZoneRadius = 0; // 初始化边缘死区范围
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 计算中心点和最大距离
        centerX = w / 2f;
        centerY = h / 2f;
        maxDistance = Math.min(w, h) / 3f; // 最大移动距离为视图宽高的1/3

        // 初始化死区范围
        deadZoneRadius = maxDistance * 0.1f;
        edgeDeadZoneRadius = maxDistance * 0.1f; // 初始化边缘死区范围

        // 初始化遥杆位置为中心点
        thumbX = centerX;
        thumbY = centerY;
    }

    private Queue<Float> signalQueue = new LinkedList<>(); // 新增信号缓存队列
    private long lastOutputTime = 0; // 新增上次输出时间戳
    private static final long filterInterval = 300; // 定义滤波时间间隔为300ms

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 计算触摸点相对于中心的偏移
                float newX = event.getX();
                float newY = event.getY();

                // 计算距离和角度
                float deltaX = newX - centerX;
                float deltaY = centerY - newY; // 注意：y轴方向反转以匹配屏幕坐标系

                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                // 判断是否在死区内
                if (distance <= deadZoneRadius) {
                    thumbX = centerX;
                    thumbY = centerY;
                    normalizedLength = 0;
                    normalizedAngle = 0;
                } else {
                    // 限制在最大距离内并更新拇指位置
                    if (distance > maxDistance) {
                        float ratio = maxDistance / distance;
                        deltaX *= ratio;
                        deltaY *= ratio;
                        distance = maxDistance;
                    }
                    thumbX = centerX + deltaX;
                    thumbY = centerY - deltaY; // y轴方向反转以匹配屏幕坐标系

                    // 计算标准化长度(-100 到 +100)
                    normalizedLength = (int) ((distance / maxDistance) * 100); // 直接赋值为整数

                    // 使用 Math.atan2 计算角度，并调整为 [-180, 180] 范围
                    float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
                    if (angle < 0) {
                        angle += 360; // 转换为 [0, 360]
                    }
                    if (angle > 180) {
                        angle -= 360; // 转换为 [-180, 180]
                    }

                    // 将角度标准化到 [-100, 100]
                    normalizedAngle = (int) ((angle / 180) * 100); // 直接赋值为整数
                }

                // 将计算结果添加到信号队列
                signalQueue.add((float) normalizedLength);
                signalQueue.add((float) normalizedAngle);

                // 获取当前时间
                long currentTime = System.currentTimeMillis();

                // 判断是否超过300ms
                if (currentTime - lastOutputTime >= filterInterval) {
                    // 调用滤波算法计算平滑值
                    int filteredLength = (int) applyFilter(signalQueue);
                    int filteredAngle = (int) applyFilter(signalQueue);

                    // 通知监听器平滑后的值
                    if (listener != null) {
                        listener.onJoystickMoved(filteredLength, filteredAngle);
                    }

                    // 更新上次输出时间并清空队列
                    lastOutputTime = currentTime;
                    signalQueue.clear();
                }

                invalidate(); // 重绘视图
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 重置到中心
                thumbX = centerX;
                thumbY = centerY;
                normalizedLength = 0;
                normalizedAngle = 0;

                if (listener != null) {
                    listener.onJoystickMoved(0, 0);
                }

                invalidate(); // 重绘视图
                return true;
        }

        return super.onTouchEvent(event);
    }

    // 新增滤波方法
    private float applyFilter(Queue<Float> queue) {
        if (queue.isEmpty()) {
            return 0;
        }
        float sum = 0;
        for (float value : queue) {
            sum += value;
        }
        return sum / queue.size(); // 返回平均值
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 绘制背景
        if (backgroundDrawable != null) {
            int left = (int)(centerX - maxDistance * 1.2f);
            int top = (int)(centerY - maxDistance * 1.2f);
            int right = (int)(centerX + maxDistance * 1.2f);
            int bottom = (int)(centerY + maxDistance * 1.2f);
            backgroundDrawable.setBounds(left, top, right, bottom);
            backgroundDrawable.draw(canvas);
        } else {
            // 备选绘制
            canvas.drawCircle(centerX, centerY, maxDistance * 1.2f, backgroundPaint);
        }

        // 绘制遥杆
        if (thumbDrawable != null) {
            int thumbSize = (int)(maxDistance * 0.5f);
            int left = (int)(thumbX - (float) thumbSize / 2);
            int top = (int)(thumbY - (float) thumbSize / 2);
            int right = left + thumbSize;
            int bottom = top + thumbSize;
            thumbDrawable.setBounds(left, top, right, bottom);
            thumbDrawable.draw(canvas);
        } else {
            // 备选绘制
            canvas.drawCircle(thumbX, thumbY, maxDistance * 0.25f, thumbPaint);
        }

        // 绘制长度和角度文本
        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        canvas.drawText("Length: " + normalizedLength, 20, 40, textPaint); // 使用整数值
        canvas.drawText("Angle: " + normalizedAngle, 20, 80, textPaint); // 使用整数值
    }

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    public float getNormalizedLength() {
        return normalizedLength;
    }

    public float getNormalizedAngle() {
        return normalizedAngle;
    }
}