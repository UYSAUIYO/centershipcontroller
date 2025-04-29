package com.yuwen.centershipcontroller.Views;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.yuwen.centershipcontroller.R;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author yuwen
 */
public class QR_codeScanner extends AppCompatActivity {
    private static final String TAG = "QR_codeScanner";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int GALLERY_PERMISSION_REQUEST_CODE = 1002;

    // 摄像头相关成员变量
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Handler backgroundHandler;
    private HandlerThread handlerThread;
    private String cameraId;
    private boolean hasFlash = false;
    private Size previewSize;
    private ImageReader imageReader;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private boolean processingBarcode = false;

    // UI组件
    private SurfaceView cameraPreview;
    private QRCodeScannerView scannerView;
    private Button flashButton;
    private Button scanButton;
    private Button exitButton;
    private TextView scanResultText;

    // 闪光灯状态
    private boolean isFlashOn = false;

    // 图片选择
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code_scanner);

        // 初始化UI组件
        cameraPreview = findViewById(R.id.camera_preview);
        scannerView = findViewById(R.id.scanner_view);
        flashButton = findViewById(R.id.flash_button);
        scanButton = findViewById(R.id.scan_button);
        exitButton = findViewById(R.id.exit_button);
        scanResultText = findViewById(R.id.scan_result_text);

        // 检查设备是否支持闪光灯
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0]; // 默认使用后置摄像头
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != null &&
                    Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));

            // 设置闪光灯按钮可用性
            flashButton.setEnabled(hasFlash);
            if (!hasFlash) {
                flashButton.setAlpha(0.5f);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "获取摄像头特性失败", e);
            flashButton.setEnabled(false);
            flashButton.setAlpha(0.5f);
        }

        // 闪光灯按钮点击事件
        flashButton.setOnClickListener(v -> toggleFlash());

        // 图库选择器
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                            decodeBitmap(bitmap);
                        } catch (Exception e) {
                            Log.e(TAG, "图片加载失败", e);
                            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 相册选择按钮
        scanButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, GALLERY_PERMISSION_REQUEST_CODE);
            } else {
                galleryLauncher.launch("image/*");
            }
        });

        // 退出按钮点击事件
        exitButton.setOnClickListener(v -> finish());

        // 请求摄像头权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            setupCameraSurface();
        }
    }

    /**
     * 设置摄像头预览Surface
     */
    private void setupCameraSurface() {
        SurfaceHolder holder = cameraPreview.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                // 检查Surface是否有效，并且确保在UI线程之后启动相机
                if (holder.getSurface() != null && holder.getSurface().isValid()) {
                    // 确保在启动预览前先启动后台线程
                    startBackgroundThread();
                    openCamera();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                // 如果Surface尺寸变化，可能需要重新配置预览
                if (cameraCaptureSession != null) {
                    try {
                        // 停止当前会话
                        cameraCaptureSession.stopRepeating();
                        // 使用新的Surface尺寸创建预览请求
                        createCameraPreviewSession();
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "相机预览更新失败", e);
                    }
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                closeCamera(); // Surface销毁处理
            }
        });
    }

    /**
     * 打开摄像头
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("获取相机锁超时");
            }

            if (cameraId == null) {
                cameraId = manager.getCameraIdList()[0]; // 默认使用后置摄像头
            }

            // 获取相机特性
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("无法获取相机预览配置");
            }

            // 选择合适的预览尺寸
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));

            // 创建图像读取器用于实时扫描
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            // 检查权限并打开摄像头
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "相机访问错误", e);
            Toast.makeText(this, "无法访问相机，请确认权限设置", Toast.LENGTH_SHORT).show();
            cameraOpenCloseLock.release();
        } catch (SecurityException e) {
            Log.e(TAG, "相机权限错误", e);
            Toast.makeText(this, "缺少相机权限", Toast.LENGTH_SHORT).show();
            cameraOpenCloseLock.release();
        } catch (InterruptedException e) {
            Log.e(TAG, "相机打开中断", e);
            cameraOpenCloseLock.release();
        }
    }

    /**
     * 相机状态回调
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "相机已打开");
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "相机已断开连接");
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "相机打开失败，错误代码: " + error);
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            Toast.makeText(QR_codeScanner.this, "相机打开失败，请重试", Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    /**
     * 创建预览会话
     */
    private void createCameraPreviewSession() {
        try {
            if (cameraDevice == null) {
                Log.e(TAG, "相机设备为空，无法创建预览会话");
                return;
            }

            SurfaceHolder holder = cameraPreview.getHolder();
            Surface previewSurface = holder.getSurface();

            // 检查Surface是否有效
            if (previewSurface == null || !previewSurface.isValid()) {
                Log.e(TAG, "预览Surface无效");
                return;
            }

            // 创建预览请求
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface()); // 添加图像读取器Surface

            // 设置自动对焦模式
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // 设置闪光灯初始状态
            if (hasFlash) {
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        isFlashOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            }

            // 创建相机捕获会话
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "相机预览会话已配置");
                            if (cameraDevice == null) {
                                return; // 相机已关闭
                            }

                            cameraCaptureSession = session;
                            try {
                                // 启动预览
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "启动预览失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "相机预览会话配置失败");
                            Toast.makeText(QR_codeScanner.this, "相机预览配置失败", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "相机会话错误", e);
        }
    }

    private long lastProcessTimestamp = 0;
    private static final long PROCESS_INTERVAL_MS = 500; // 每500毫秒处理一次

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
        long currentTime = System.currentTimeMillis();

        // 如果已经在处理中或者距离上次处理时间不足，跳过此帧
        if (processingBarcode || (currentTime - lastProcessTimestamp < PROCESS_INTERVAL_MS)) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                image.close();
            }
            return;
        }

        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }

            processingBarcode = true;
            lastProcessTimestamp = currentTime;

            // 将图像转换为字节数组
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            // 获取图像尺寸
            int width = image.getWidth();
            int height = image.getHeight();

            // 处理图像数据
            processImageData(data, width, height);

        } catch (Exception e) {
            Log.e(TAG, "处理相机图像出错", e);
        } finally {
            if (image != null) {
                image.close();
            }
            processingBarcode = false;
        }
    };


    /**
     * 处理图像数据
     */
    private void processImageData(byte[] data, int width, int height) {
        try {
            // 获取相机预览的中心位置
            int centerX = width / 2;
            int centerY = height / 2;

            // 计算扫描区域（使用固定的扫描框大小，而不是依赖UI上的扫描框）
            int scanSize = Math.min(width, height) / 2; // 扫描区域为宽高中较小值的一半

            int left = centerX - scanSize / 2;
            int top = centerY - scanSize / 2;
            int scanWidth = scanSize;
            int scanHeight = scanSize;

            // 确保扫描区域在有效范围内
            left = Math.max(0, left);
            top = Math.max(0, top);
            scanWidth = Math.min(scanWidth, width - left);
            scanHeight = Math.min(scanHeight, height - top);

            // 确保所有值都为正数且在图像边界内
            if (left < 0 || top < 0 || scanWidth <= 0 || scanHeight <= 0 ||
                    left + scanWidth > width || top + scanHeight > height) {
                Log.e(TAG, "扫描区域无效: left=" + left + ", top=" + top +
                        ", width=" + scanWidth + ", height=" + scanHeight);
                return;
            }

            // 日志记录实际扫描区域，帮助调试
            Log.d(TAG, "扫描区域: left=" + left + ", top=" + top +
                    ", width=" + scanWidth + ", height=" + scanHeight +
                    ", 图像大小: " + width + "x" + height);

            // 将Y数据转换为YUV亮度源
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height, left, top, scanWidth, scanHeight, false);

            // 创建二进制位图
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            // 配置解码选项
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

            // 使用集合存储支持的格式
            List<BarcodeFormat> formats = new ArrayList<>();
            formats.add(BarcodeFormat.QR_CODE);
            // 暂时只支持QR码，减少处理时间和错误可能
            // formats.add(BarcodeFormat.DATA_MATRIX);
            // formats.add(BarcodeFormat.CODE_128);
            // formats.add(BarcodeFormat.EAN_13);

            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            // 创建多格式读取器
            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);

            // 尝试解码
            Result result = reader.decode(bitmap);

            // 处理扫描结果
            if (result != null) {
                String text = result.getText();
                if (text != null && !text.isEmpty()) {
                    // 在主线程更新UI
                    runOnUiThread(() -> handleScanResult(text));
                }
            }
        } catch (NotFoundException ignored) {
            // 未找到二维码，正常情况，不需处理
        } catch (Exception e) {
            Log.e(TAG, "扫描二维码出错: " + e.getMessage(), e);
        }
    }



    /**
     * 解码位图中的二维码
     */
    private void decodeBitmap(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            com.google.zxing.RGBLuminanceSource source = new com.google.zxing.RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();

            // 使用集合存储支持的格式
            List<BarcodeFormat> formats = new ArrayList<>();
            formats.add(BarcodeFormat.QR_CODE);
            // 也可以添加其他格式支持
            formats.add(BarcodeFormat.DATA_MATRIX);
            formats.add(BarcodeFormat.CODE_128);

            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);

            try {
                Result result = reader.decode(binaryBitmap);
                handleScanResult(result.getText());
            } catch (NotFoundException e) {
                Toast.makeText(this, "未找到二维码", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "解码位图失败", e);
            Toast.makeText(this, "图片解析失败", Toast.LENGTH_SHORT).show();
        }
    }



    /**
     * 切换闪光灯状态
     */
    private void toggleFlash() {
        if (!hasFlash) {
            Toast.makeText(this, "设备不支持闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cameraDevice == null || cameraCaptureSession == null || previewRequestBuilder == null) {
            Log.e(TAG, "相机未初始化，无法控制闪光灯");
            return;
        }

        try {
            // 切换闪光灯状态
            isFlashOn = !isFlashOn;

            // 更新闪光灯模式
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

            // 更新预览请求
            cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);

            // 更新闪光灯按钮图标
            runOnUiThread(() -> {
                flashButton.setCompoundDrawablesWithIntrinsicBounds(0,
                        isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off, 0, 0);
            });

            Log.d(TAG, "闪光灯状态已切换为: " + (isFlashOn ? "开启" : "关闭"));
        } catch (CameraAccessException e) {
            Log.e(TAG, "切换闪光灯失败", e);
            Toast.makeText(this, "闪光灯控制失败", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 处理扫描结果
     */
    private void handleScanResult(String result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        // 防止重复处理同一个结果
        if (processingBarcode) {
            return;
        }

        processingBarcode = true;

        // 播放成功声音
        try {
            android.media.MediaPlayer mediaPlayer = android.media.MediaPlayer.create(this, R.raw.notification_default_ringtone);
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> mp.release());
        } catch (Exception e) {
            Log.e(TAG, "播放提示音失败", e);
        }

        // 显示结果
        scanResultText.setText("扫描结果: " + result);
        scanResultText.setVisibility(View.VISIBLE);

        // 震动反馈
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(200);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "震动反馈失败", e);
        }

        // 延迟2秒后隐藏结果，继续扫描
        new Handler().postDelayed(() -> {
            scanResultText.setVisibility(View.GONE);
            processingBarcode = false; // 允许继续扫描
        }, 2000);

        // 可选：根据扫描结果进行处理或跳转
        // 这里可以添加自定义的处理逻辑，例如打开网页、解析特定格式等

        // 打印日志
        Log.d(TAG, "扫描到二维码: " + result);

        // 如果需要将结果传回调用活动，可以添加如下代码：
        // Intent resultIntent = new Intent();
        // resultIntent.putExtra("SCAN_RESULT", result);
        // setResult(Activity.RESULT_OK, resultIntent);
        // finish();
    }


    /**
     * 选择最优的预览尺寸
     */
    /**
     * 选择最优的预览尺寸
     */
    private Size chooseOptimalSize(Size[] choices) {
        // 默认为较小的尺寸，避免性能问题
        if (choices == null || choices.length == 0) {
            return new Size(640, 480);
        }

        // 先筛选出宽高比接近16:9或4:3，且不大于1080p的尺寸
        List<Size> goodSizes = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() <= 1920 && size.getHeight() <= 1080) {
                float ratio = (float) size.getWidth() / size.getHeight();
                // 允许16:9或4:3的宽高比（带一点误差范围）
                if ((Math.abs(ratio - 16.0f/9.0f) < 0.2f) || (Math.abs(ratio - 4.0f/3.0f) < 0.2f)) {
                    goodSizes.add(size);
                }
            }
        }

        if (goodSizes.isEmpty()) {
            // 如果没有找到合适的宽高比，则选择不超过1080p的最大尺寸
            Size result = choices[0];
            for (Size size : choices) {
                if (size.getWidth() <= 1920 && size.getHeight() <= 1080 &&
                        size.getWidth() * size.getHeight() > result.getWidth() * result.getHeight()) {
                    result = size;
                }
            }
            return result;
        } else {
            // 在合适宽高比的尺寸中，选择分辨率适中的（不要太大也不要太小）
            // 优先选640x480左右的尺寸，它对扫描来说通常足够了
            Size bestSize = goodSizes.get(0);
            int bestDiff = Integer.MAX_VALUE;

            for (Size size : goodSizes) {
                int diff = Math.abs(size.getWidth() - 640) + Math.abs(size.getHeight() - 480);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestSize = size;
                }
            }

            return bestSize;
        }
    }


    // 生命周期管理
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        // 如果Surface已创建，则重新启动相机预览
        if (cameraPreview.getHolder().getSurface() != null &&
                cameraPreview.getHolder().getSurface().isValid()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * 启动后台线程
     */
    private void startBackgroundThread() {
        // 确保先停止之前的线程
        stopBackgroundThread();

        handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        Log.d(TAG, "后台处理线程已启动");
    }

    /**
     * 停止后台线程
     */
    private void stopBackgroundThread() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
                handlerThread = null;
                backgroundHandler = null;
                Log.d(TAG, "后台处理线程已停止");
            } catch (InterruptedException e) {
                Log.e(TAG, "线程关闭错误", e);
            }
        }
    }

    /**
     * 关闭摄像头
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            Log.d(TAG, "相机资源已释放");
        } catch (InterruptedException e) {
            Log.e(TAG, "关闭相机中断", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCameraSurface();
            } else {
                Toast.makeText(this, "需要摄像头权限才能使用扫码功能", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == GALLERY_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
