package com.yuwen.centershipcontroller.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * @author COLORFUL
 */
public class QRCodeScannerView extends View {
    private final int scanFrameSize = 550; // 扫描框大小
    private Paint scanFramePaint; // 扫描框画笔
    private Paint scanLinePaint; // 扫描线画笔
    private Paint cornerPaint; // 扫描框角画笔
    private Paint maskPaint; // 蒙版画笔
    private Paint textPaint; // 文字画笔
    private int scanLinePosition; // 扫描线位置
    private boolean scanLineGoingDown = true; // 扫描线方向

    public QRCodeScannerView(Context context) {
        super(context);
        init();
    }

    public QRCodeScannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QRCodeScannerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 初始化扫描框画笔
        scanFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanFramePaint.setColor(Color.WHITE);
        scanFramePaint.setStyle(Paint.Style.STROKE);
        scanFramePaint.setStrokeWidth(2);

        // 初始化扫描线画笔
        scanLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanLinePaint.setColor(Color.parseColor("#4CAF50")); // 绿色
        scanLinePaint.setStrokeWidth(6);
        scanLinePaint.setAlpha(180);

        // 初始化扫描框角画笔
        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.parseColor("#4CAF50")); // 绿色
        cornerPaint.setStyle(Paint.Style.STROKE);
        // 角线宽度
        int cornerStrokeWidth = 8;
        cornerPaint.setStrokeWidth(cornerStrokeWidth);

        // 初始化蒙版画笔
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.parseColor("#80000000")); // 半透明黑色

        // 初始化文字画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 计算扫描框的位置
        int left = (width - scanFrameSize) / 2;
        int top = (height - scanFrameSize) / 2;
        int right = left + scanFrameSize;
        int bottom = top + scanFrameSize;
        RectF scanFrame = new RectF(left, top, right, bottom);

        // 绘制蒙版
        canvas.drawRect(0, 0, width, top, maskPaint); // 上
        canvas.drawRect(0, top, left, bottom, maskPaint); // 左
        canvas.drawRect(right, top, width, bottom, maskPaint); // 右
        canvas.drawRect(0, bottom, width, height, maskPaint); // 下

        // 绘制扫描框
        canvas.drawRect(scanFrame, scanFramePaint);

        // 绘制扫描框四角
        // 左上角
        // 角大小
        int cornerSize = 30;
        canvas.drawLine(left, top + cornerSize, left, top, cornerPaint);
        canvas.drawLine(left, top, left + cornerSize, top, cornerPaint);

        // 右上角
        canvas.drawLine(right - cornerSize, top, right, top, cornerPaint);
        canvas.drawLine(right, top, right, top + cornerSize, cornerPaint);

        // 左下角
        canvas.drawLine(left, bottom - cornerSize, left, bottom, cornerPaint);
        canvas.drawLine(left, bottom, left + cornerSize, bottom, cornerPaint);

        // 右下角
        canvas.drawLine(right - cornerSize, bottom, right, bottom, cornerPaint);
        canvas.drawLine(right, bottom - cornerSize, right, bottom, cornerPaint);

        // 绘制扫描线
        if (scanLinePosition == 0) {
            scanLinePosition = top;
            scanLineGoingDown = true;
        }

        // 绘制线性渐变效果
        Path path = new Path();
        path.moveTo(left + 20, scanLinePosition);
        path.lineTo(right - 20, scanLinePosition);
        canvas.drawPath(path, scanLinePaint);

        // 更新扫描线位置
        // 动画速度
        int animationSpeed = 6;
        if (scanLineGoingDown) {
            scanLinePosition += animationSpeed;
            if (scanLinePosition >= bottom) {
                scanLineGoingDown = false;
            }
        } else {
            scanLinePosition -= animationSpeed;
            if (scanLinePosition <= top) {
                scanLineGoingDown = true;
            }
        }

        // 绘制提示文字
        String tipText = "将二维码/条码放入框内，即可自动扫描";
        canvas.drawText(tipText, (float) width / 2, bottom + 80, textPaint);

        // 继续动画
        invalidate();
    }

    // 获取扫描框的RectF，用于扫描区域裁剪
    // 获取扫描框的RectF，用于扫描区域裁剪
    public RectF getScanFrameRect() {
        int width = getWidth();
        int height = getHeight();

        // 计算扫描框的位置
        int left = (width - scanFrameSize) / 2;
        int top = (height - scanFrameSize) / 2;
        int right = left + scanFrameSize;
        int bottom = top + scanFrameSize;

        // 确保返回的矩形区域都是有效的正值
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);

        // 确保宽高为正值
        if (right <= left) right = left + 1;
        if (bottom <= top) bottom = top + 1;

        return new RectF(left, top, right, bottom);
    }

}
