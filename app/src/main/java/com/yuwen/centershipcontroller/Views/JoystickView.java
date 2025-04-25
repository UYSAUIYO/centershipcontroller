package com.yuwen.centershipcontroller.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.yuwen.centershipcontroller.R;

public class JoystickView extends View {
    private float centerX;
    private float centerY;
    private float thumbX;
    private float thumbY;
    private float maxDistance;

    private float normalizedLength = 0; // -100 到 +100
    private float normalizedAngle = 0;  // -100 到 +100

    private Paint backgroundPaint;
    private Paint thumbPaint;
    private Paint debugPaint;

    private Drawable backgroundDrawable;
    private Drawable thumbDrawable;

    private JoystickListener listener;

    public interface JoystickListener {
        void onJoystickMoved(float length, float angle);
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
            e.printStackTrace();
        }

        // 设置透明度
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 计算中心点和最大距离
        centerX = w / 2f;
        centerY = h / 2f;
        maxDistance = Math.min(w, h) / 3f; // 最大移动距离为视图宽高的1/3

        // 初始化遥杆位置为中心点
        thumbX = centerX;
        thumbY = centerY;
    }

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
                float deltaY = newY - centerY;
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                // 限制在最大距离内
                if (distance > maxDistance) {
                    float ratio = maxDistance / distance;
                    deltaX *= ratio;
                    deltaY *= ratio;
                    distance = maxDistance;
                }

                // 更新拇指位置
                thumbX = centerX + deltaX;
                thumbY = centerY + deltaY;

                // 计算标准化长度(-100 到 +100)
                normalizedLength = (distance / maxDistance) * 100;

                // 计算角度(-180 到 +180度)
                float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

                // 将角度转换为相对于Y轴的，并标准化到-100到+100
                // 当手指在左边时为负，右边时为正
                if (angle >= -90 && angle <= 90) {
                    // 右半边
                    normalizedAngle = (angle / 90) * 100;
                } else {
                    // 左半边 (-180到-90) 或 (90到180)
                    if (angle > 90) {
                        normalizedAngle = ((180 - angle) / 90) * -100;
                    } else {
                        normalizedAngle = ((angle + 180) / 90) * -100;
                    }
                }

                // 通知监听器
                if (listener != null) {
                    listener.onJoystickMoved(normalizedLength, normalizedAngle);
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

    @Override
    protected void onDraw(Canvas canvas) {
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
            int left = (int)(thumbX - thumbSize / 2);
            int top = (int)(thumbY - thumbSize / 2);
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
        canvas.drawText("Length: " + String.format("%.1f", normalizedLength), 20, 40, textPaint);
        canvas.drawText("Angle: " + String.format("%.1f", normalizedAngle), 20, 80, textPaint);
    }

    public float getNormalizedLength() {
        return normalizedLength;
    }

    public float getNormalizedAngle() {
        return normalizedAngle;
    }
}
