package com.yuwen.centershipcontroller.Activity;

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
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.yuwen.centershipcontroller.Component.CustomDialog;
import com.yuwen.centershipcontroller.R;
import com.yuwen.centershipcontroller.Utils.QRCodeProcessor;
import com.yuwen.centershipcontroller.Utils.QRCodeResultHandler;
import com.yuwen.centershipcontroller.Utils.WebSocketManager;
import com.yuwen.centershipcontroller.Views.QRCodeScannerView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 二维码扫描主界面
 * 使用Camera2 API实现相机预览和实时二维码识别
 * 包含闪光灯控制、相册扫码、全屏模式等功能
 * @author yuwen
 */
public class QR_codeScannerActivity extends AppCompatActivity {
    // 标签用于日志输出
    private static final String TAG = "QR_codeScanner";
    // 权限请求码
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int GALLERY_PERMISSION_REQUEST_CODE = 1002;

    // 摄像头相关成员变量
    private CameraDevice cameraDevice; // 相机设备实例
    private CameraCaptureSession cameraCaptureSession; // 相机捕获会话
    private CaptureRequest.Builder previewRequestBuilder; // 预览请求构建器
    private Handler backgroundHandler; // 后台线程Handler
    private HandlerThread handlerThread; // 后台线程
    private String cameraId; // 当前使用相机ID
    private boolean hasFlash = false; // 是否支持闪光灯
    private Size previewSize; // 预览分辨率大小
    private ImageReader imageReader; // 图像读取器（用于获取相机原始数据）
    private final Semaphore cameraOpenCloseLock = new Semaphore(1); // 相机开关锁
    private boolean processingBarcode = false; // 正在处理二维码标志

    // UI组件
    private SurfaceView cameraPreview; // 相机预览SurfaceView
    private QRCodeScannerView scannerView; // 自定义扫描框视图
    private CustomDialog processingDialog; // 处理中对话框实例
    private CustomDialog statusDialog; // 状态对话框引用
    private Button flashButton; // 闪光灯按钮
    private Button scanButton; // 扫描按钮（用于相册扫码）
    private Button exitButton; // 退出按钮
    private TextView scanResultText; // 扫描结果文本显示

    // 闪光灯状态
    private boolean isFlashOn = false; // 当前闪光灯开关状态

    // 图片选择相关
    private ActivityResultLauncher<String> galleryLauncher; // 相册选择启动器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏状态栏并设置全屏模式
        Window window = getWindow();
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        WindowCompat.setDecorFitsSystemWindows(window, false);

        setContentView(R.layout.activity_qr_code_scanner);

        // 初始化UI组件
        cameraPreview = findViewById(R.id.camera_preview);
        scannerView = findViewById(R.id.scanner_view);
        flashButton = findViewById(R.id.flash_button);
        scanButton = findViewById(R.id.scan_button);
        exitButton = findViewById(R.id.exit_button);
        scanResultText = findViewById(R.id.scan_result_text);

        // 初始化相机管理器并检查闪光灯支持
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            // 获取默认后置摄像头ID
            cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            // 检查闪光灯支持状态
            hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != null &&
                    Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));

            // 根据闪光灯支持状态设置按钮可用性
            flashButton.setEnabled(hasFlash);
            if (!hasFlash) {
                flashButton.setAlpha(0.5f);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "获取摄像头特性失败", e);
            flashButton.setEnabled(false);
            flashButton.setAlpha(0.5f);
        }

        // 初始化闪光灯按钮点击事件
        flashButton.setOnClickListener(v -> toggleFlash());

        // 初始化相册选择器
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            // 加载用户选择的图片并解码
                            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                            decodeBitmap(bitmap);
                        } catch (Exception e) {
                            Log.e(TAG, "图片加载失败", e);
                            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 相册按钮点击事件
        scanButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // 请求相册权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, GALLERY_PERMISSION_REQUEST_CODE);
            } else {
                // 已授权则启动相册选择
                galleryLauncher.launch("image/*");
            }
        });

        // 退出按钮点击事件
        exitButton.setOnClickListener(v -> finish());

        // 请求相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            setupCameraSurface();
        }
    }

    /**
     * 设置相机预览Surface
     * 包含Surface生命周期回调处理
     */
    private void setupCameraSurface() {
        SurfaceHolder holder = cameraPreview.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                // 在Surface创建时启动相机
                if (holder.getSurface() != null && holder.getSurface().isValid()) {
                    startBackgroundThread();
                    openCamera();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                // Surface尺寸变化时重新配置预览
                if (cameraCaptureSession != null) {
                    try {
                        cameraCaptureSession.stopRepeating();
                        createCameraPreviewSession();
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "相机预览更新失败", e);
                    }
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                // Surface销毁时关闭相机
                closeCamera();
            }
        });
    }

    /**
     * 打开相机设备
     * 包含权限检查、相机特性获取、图像读取器初始化等操作
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            // 获取相机锁防止并发访问
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("获取相机锁超时");
            }

            // 重新获取相机ID（可能为空）
            if (cameraId == null) {
                cameraId = manager.getCameraIdList()[0];
            }

            // 获取相机特性并配置预览参数
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("无法获取相机预览配置");
            }

            // 选择最优预览尺寸
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));

            // 创建图像读取器用于实时处理
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            // 检查权限并打开相机
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
     * 相机设备状态回调
     * 处理相机打开、断开、错误等情况
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
            Toast.makeText(QR_codeScannerActivity.this, "相机打开失败，请重试", Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    /**
     * 创建相机预览会话
     * 配置预览请求并启动预览
     */
    private void createCameraPreviewSession() {
        try {
            if (cameraDevice == null) {
                Log.e(TAG, "相机设备为空，无法创建预览会话");
                return;
            }

            SurfaceHolder holder = cameraPreview.getHolder();
            Surface previewSurface = holder.getSurface();

            // 检查Surface有效性
            if (previewSurface == null || !previewSurface.isValid()) {
                Log.e(TAG, "预览Surface无效");
                return;
            }

            // 创建预览请求
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface());

            // 设置自动对焦模式为连续对焦
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // 设置闪光灯初始状态
            if (hasFlash) {
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        isFlashOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            }

            // 创建捕获会话
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "相机预览会话已配置");
                            if (cameraDevice == null) {
                                return;
                            }

                            cameraCaptureSession = session;
                            try {
                                // 开始预览
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "启动预览失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "相机预览会话配置失败");
                            Toast.makeText(QR_codeScannerActivity.this, "相机预览配置失败", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "相机会话错误", e);
        }
    }

    // 图像处理间隔常量（毫秒）
    private long lastProcessTimestamp = 0;
    private static final long PROCESS_INTERVAL_MS = 500;

    /**
     * 相机图像可用监听器
     * 处理实时获取的相机图像数据
     */
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
        long currentTime = System.currentTimeMillis();

        // 限制处理频率，防止过度处理
        if (processingBarcode || (currentTime - lastProcessTimestamp < PROCESS_INTERVAL_MS)) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                image.close();
            }
            return;
        }

        try (Image image = reader.acquireLatestImage()) {
            if (image == null) {
                return;
            }

            processingBarcode = true;
            lastProcessTimestamp = currentTime;

            // 获取图像数据
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
            processingBarcode = false;
        }
    };

    /**
     * 处理相机图像数据
     * @param data 图像原始字节数据
     * @param width 图像宽度
     * @param height 图像高度
     */
    private void processImageData(byte[] data, int width, int height) {
        try {
            // 计算扫描区域（以屏幕中心为基准）
            int centerX = width / 2;
            int centerY = height / 2;

            // 固定扫描区域大小为屏幕较小边的一半
            int scanSize = Math.min(width, height) / 2;

            int left = centerX - scanSize / 2;
            int top = centerY - scanSize / 2;
            int scanWidth = scanSize;
            int scanHeight = scanSize;

            // 确保扫描区域在有效范围内
            left = Math.max(0, left);
            top = Math.max(0, top);
            scanWidth = Math.min(scanWidth, width - left);
            scanHeight = Math.min(scanHeight, height - top);

            // 边界检查
            if (left < 0 || top < 0 || scanWidth <= 0 || scanHeight <= 0 ||
                    left + scanWidth > width || top + scanHeight > height) {
                Log.e(TAG, "扫描区域无效: left=" + left + ", top=" + top +
                        ", width=" + scanWidth + ", height=" + scanHeight);
                return;
            }

            // 输出调试日志
            Log.d(TAG, "扫描区域: left=" + left + ", top=" + top +
                    ", width=" + scanWidth + ", height=" + scanHeight +
                    ", 图像大小: " + width + "x" + height);

            // 创建ZXing需要的亮度源
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height, left, top, scanWidth, scanHeight, false);

            // 创建二进制位图
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            // 配置解码参数
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

            // 设置支持的条码格式
            List<BarcodeFormat> formats = new ArrayList<>();
            formats.add(BarcodeFormat.QR_CODE); // 仅支持QR码

            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); // 启用深度解码

            // 创建解码器
            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);

            // 执行解码
            Result result = reader.decode(bitmap);

            // 处理解码结果
            if (result != null) {
                String text = result.getText();
                if (text != null && !text.isEmpty()) {
                    runOnUiThread(() -> handleScanResult(text));
                }
            }
        } catch (NotFoundException ignored) {
            // 未找到二维码，正常情况
        } catch (Exception e) {
            Log.e(TAG, "扫描二维码出错: " + e.getMessage(), e);
        }
    }

    /**
     * 解码位图中的二维码
     * @param bitmap 待解码的位图
     */
    private void decodeBitmap(Bitmap bitmap) {
        QRCodeProcessor qrProcessor  = new QRCodeProcessor();
        qrProcessor.decodeBitmap(bitmap, new QRCodeProcessor.DecodeCallback() {
            @Override
            public void onDecodeSuccess(Result result) {
                handleScanResult(result.getText());
            }

            @Override
            public void onDecodeFailed() {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(QR_codeScannerActivity.this, "未找到二维码", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 切换闪光灯状态
     * 需要确保相机已初始化
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
            // 切换状态
            isFlashOn = !isFlashOn;

            // 更新闪光灯模式
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

            // 提交新的预览请求
            cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);

            // 更新按钮图标
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
     * 包含结果展示、震动反馈、声音提示等
     * @param result 扫描结果字符串
     */
    private void handleScanResult(String result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        // 防止重复处理
        if (processingBarcode) {
            return;
        }

        processingBarcode = true;

        // 播放提示音
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.notification_default_ringtone);
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> mp.release());
        } catch (Exception e) {
            Log.e(TAG, "播放提示音失败", e);
        }

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

        // 显示初步结果
        scanResultText.setText("扫描成功，正在处理...");
        scanResultText.setVisibility(View.VISIBLE);

        // 使用结果处理器处理结果
        QRCodeResultHandler resultHandler = new QRCodeResultHandler(this, new QRCodeResultHandler.QRResultCallback() {
            @Override
            public void onNormalResult(String result) {
                // 普通扫描结果处理
                runOnUiThread(() -> {
                    scanResultText.setText("扫描结果: " + result);

                    // 延迟隐藏结果
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        scanResultText.setVisibility(View.GONE);
                        processingBarcode = false;
                    }, 2000);
                });
            }

            @Override
            public void onProcessing(String message) {
                // 连接处理中状态
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        try {
                            if (processingDialog != null && processingDialog.isShowing()) {
                                processingDialog.dismiss();
                            }

                            processingDialog = new CustomDialog(QR_codeScannerActivity.this);
                            processingDialog.setTitle("连接中");
                            processingDialog.setMessage(message);
                            processingDialog.showLoadingIcon();
                            processingDialog.hideNegativeButton();
                            processingDialog.setPositiveButton("取消", v -> {
                                processingDialog.dismiss();
                                WebSocketManager.getInstance().disconnect();
                                finish();
                            });
                            processingDialog.setCancelable(false);
                            processingDialog.show();
                        } catch (Exception e) {
                            Log.e(TAG, "显示处理对话框出错", e);
                        }
                    }
                });
            }

            @Override
            public void onConnectionSuccess(String message) {
                // 连接成功处理
                runOnUiThread(() -> {
                    try {
                        if (processingDialog != null && processingDialog.isShowing()) {
                            processingDialog.dismiss();
                        }
                        Log.d(TAG, "WebSocket连接成功，返回主界面");
                        setResult(RESULT_OK);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "处理连接成功出错", e);
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }

            @Override
            public void onConnectionFailure(String error) {
                // 连接失败处理
                runOnUiThread(() -> {
                    try {
                        if (processingDialog != null && processingDialog.isShowing()) {
                            processingDialog.dismiss();
                        }

                        CustomDialog dialog = new CustomDialog(QR_codeScannerActivity.this);
                        dialog.setTitle("连接失败");
                        dialog.setMessage("无法连接到服务器: " + error);
                        dialog.showErrorIcon();
                        dialog.hideNegativeButton();
                        dialog.setPositiveButton("确定", v -> {
                            dialog.dismiss();
                            processingBarcode = false;
                        });
                        dialog.setCancelable(false);
                        dialog.show();
                    } catch (Exception e) {
                        Log.e(TAG, "显示连接失败对话框出错", e);
                    }
                });
            }

            @Override
            public void onError(String error) {
                // 错误处理
                runOnUiThread(() -> {
                    scanResultText.setText("错误: " + error);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        scanResultText.setVisibility(View.GONE);
                        processingBarcode = false;
                    }, 2000);
                });
            }
        });

        // 开始处理结果
        resultHandler.processResult(result);
    }

    /**
     * 选择最优预览尺寸
     * 优先选择接近4:3或16:9的尺寸
     * @param choices 可选尺寸列表
     * @return 最优尺寸
     */
    private Size chooseOptimalSize(Size[] choices) {
        if (choices == null || choices.length == 0) {
            return new Size(640, 480);
        }

        // 筛选宽高比接近16:9或4:3且不超过1080p的尺寸
        List<Size> goodSizes = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() <= 1920 && size.getHeight() <= 1080) {
                float ratio = (float) size.getWidth() / size.getHeight();
                if ((Math.abs(ratio - 16.0f/9.0f) < 0.2f) || (Math.abs(ratio - 4.0f/3.0f) < 0.2f)) {
                    goodSizes.add(size);
                }
            }
        }

        if (goodSizes.isEmpty()) {
            // 选择不超过1080p的最大尺寸
            Size result = choices[0];
            for (Size size : choices) {
                if (size.getWidth() <= 1920 && size.getHeight() <= 1080 &&
                        size.getWidth() * size.getHeight() > result.getWidth() * result.getHeight()) {
                    result = size;
                }
            }
            return result;
        } else {
            // 选择最接近640x480的尺寸
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

    // 生命周期方法
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        // 重启相机预览
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 安全关闭对话框
        if (processingDialog != null && processingDialog.isShowing()) {
            try {
                processingDialog.dismiss();
            } catch (Exception e) {
                // 忽略异常
            }
        }

        // 释放相机资源
        closeCamera();
        stopBackgroundThread();
        Log.d(TAG, "相机资源已释放");
        Log.d(TAG, "后台处理线程已停止");
    }

    /**
     * 启动后台线程
     */
    private void startBackgroundThread() {
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
     * 关闭相机设备
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