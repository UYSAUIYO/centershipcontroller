package com.yuwen.centershipcontroller;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.ServiceSettings;
import com.yuwen.centershipcontroller.Activity.QR_codeScannerActivity;
import com.yuwen.centershipcontroller.Activity.SettingsActivity;
import com.yuwen.centershipcontroller.Component.CustomDialog;
import com.yuwen.centershipcontroller.Component.DeviceInfoCard;
import com.yuwen.centershipcontroller.Socket.MainDeviceSocket;
import com.yuwen.centershipcontroller.Utils.JoySticksDecoder;
import com.yuwen.centershipcontroller.Utils.UserSettings;
import com.yuwen.centershipcontroller.Utils.Utils;
import com.yuwen.centershipcontroller.Views.JoystickView;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * 主活动类，负责核心功能模块：
 * 1. 高德地图集成与定位功能
 * 2. 设备控制与状态监控
 * 3. 用户交互与界面管理
 * 4. 权限请求与生命周期管理
 * 
 * @author Yuwen
 * @version 1.0 2023-10-01
 */
public class MainActivity extends AppCompatActivity implements LocationSource, AMapLocationListener {
    private static final String TAG = "MainActivity";
    
    // 权限请求码定义
    private static final int REQUEST_PERMISSIONS = 9527; // 定位相关权限请求码
    private static final int REQUEST_CAMERA_PERMISSION = 9528; // 摄像头权限请求码
    private static final int REQUEST_QR_SCAN = 9529; // 二维码扫描请求码

    private ActivityResultLauncher<Intent> qrCodeScannerLauncher;

    // 地图相关成员变量
    private MapView mMapView; // 地图视图组件
    private AMap aMap; // 地图控制器

    // 设备信息显示组件
    private DeviceInfoCard deviceInfoCard; // 设备状态信息卡片
    private ImageView connectionStatusLight; // WebSocket连接状态指示灯
    private long lastUpdateTime = 0;
    // 性能指标统计变量
    private int updateCount = 0;
    private long totalInterval = 0;
    private long minInterval = Long.MAX_VALUE;
    private long maxInterval = 0;

    /**
     * Activity创建时的初始化方法
     * 包含以下主要流程：
     * 1. 系统UI样式配置
     * 2. 地图SDK初始化
     * 3. 权限请求处理
     * 4. 地图与设备控制初始化
     * 5. 用户界面组件绑定
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 系统UI样式配置（沉浸式状态栏）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 使用现代API实现沉浸式体验
            try {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController insetsController = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                    insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    // 回退到传统实现方案
                    getWindow().setFlags(
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    );
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
                }
            } catch (Exception e) {
                // 异常处理并回退
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                );
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } else {
            // 兼容旧版本Android
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            );
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        setContentView(R.layout.activity_main);

        // 加载高德地图原生库
        try {
            System.loadLibrary("amap3dmap");
        } catch (UnsatisfiedLinkError e) {
            Log.e("MapError", "Failed to load AMap library: " + e.getMessage());
        }

        requestPermission(); // 发起必要权限请求

        // 隐私政策合规设置
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 定位服务隐私设置
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);

        // 搜索服务隐私设置
        ServiceSettings.updatePrivacyShow(this, true, true);
        ServiceSettings.updatePrivacyAgree(this, true);

        // 地图组件初始化
        initMap(savedInstanceState);

        // 摇杆控制器初始化
        JoystickView joystickView = findViewById(R.id.joystick_view);
        UserSettings userSettings = new UserSettings(this);
        joystickView.setJoystickListener(this::updateControlValues);

        // 初始化震动反馈
        JoySticksDecoder.getInstance().init(this);

        // 首次运行检测
        if (userSettings.checkFirstRun()) {
            showUserAgreementDialog();
        }

        // 设备信息卡初始化
        deviceInfoCard = findViewById(R.id.device_card);
        initDeviceInfoCard();

        // 连接状态指示灯初始化
        connectionStatusLight = findViewById(R.id.status_light);
        updateConnectionStatusLight(false); // 默认为未连接状态

        // WebSocket连接状态监听
        MainDeviceSocket.getInstance().setConnectionStatusListener(
                this::updateConnectionStatusLight
        );
    }


    /**
     * 更新连接状态指示灯
     * @param isConnected 是否已连接
     */
    private void updateConnectionStatusLight(boolean isConnected) {
        runOnUiThread(() -> {
            ImageView statusLight = findViewById(R.id.status_light);
            if (statusLight != null) {
                Log.d(TAG, "更新连接状态指示灯: " + (isConnected ? "已连接" : "未连接"));
                statusLight.setImageResource(
                        isConnected ? R.drawable.green : R.drawable.red
                );
            }
        });
    }

    /**
     * 初始化设备信息卡
     */
    private void initDeviceInfoCard() {
        if (deviceInfoCard != null) {
            deviceInfoCard.setDeviceTitle("主控设备");
            deviceInfoCard.setDeviceType("管理端");
            deviceInfoCard.setDeviceId("未连接");
            deviceInfoCard.setWorkStatus("未连接");
            deviceInfoCard.setWorkArea("未分配");
            deviceInfoCard.setBatteryLevel(100);
            deviceInfoCard.setBatteryStatus(true);

            // 设置卡片操作按钮
            deviceInfoCard.setButtonText("连接设备");
            // 启动二维码扫描
            deviceInfoCard.setActionButtonClickListener(this::onScannerClick);
        }
    }

    /**
     * 初始化地图配置
     * 包含以下关键配置：
     * - 地图隐私协议验证
     * - 地图视图组件初始化
     * - 定位蓝点样式配置
     * - 地图交互设置
     */
    private void initMap(Bundle savedInstanceState) {
        // 地图隐私合规接口调用
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 地图视图初始化
        mMapView = findViewById(R.id.map);
        if (mMapView == null) {
            Log.e("MapError", "MapView is not found in the layout.");
            return;
        }
        mMapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mMapView.getMap();
            if (aMap == null) {
                Log.e("MapError", "Failed to initialize AMap object.");
                return;
            } else {
                Log.d("MapInit", "AMap object initialized successfully.");
            }
        }

        // 地图UI定制配置
        aMap.getUiSettings().setLogoBottomMargin(-100); // 隐藏高德Logo

        // 定位样式配置
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW);
        myLocationStyle.interval(2000);
        myLocationStyle.showMyLocation(true);
        myLocationStyle.anchor(0.5f, 0.5f);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setMyLocationEnabled(true);
        Log.d("MapInit", "MyLocationStyle and MyLocationEnabled set successfully.");
    }

    /**
     * 请求摄像头权限（用于二维码扫描）
     * 支持Android 13+的READ_MEDIA_IMAGES权限
     */
    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    public void requestCameraPermission() {
        String[] perms = new String[]{
                Manifest.permission.CAMERA, 
                Manifest.permission.READ_MEDIA_IMAGES
        };
        if (EasyPermissions.hasPermissions(this, perms)) {
            Toast.makeText(this, "相机权限已就绪", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, QR_codeScannerActivity.class);
            startActivityForResult(intent, REQUEST_QR_SCAN);
        } else {
            EasyPermissions.requestPermissions(this, "需要访问相机和媒体文件", REQUEST_CAMERA_PERMISSION, perms);
        }
    }

    /**
     * 设置菜单点击事件
     */
    public void onSettingClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * 显示用户协议对话框
     */
    private void showUserAgreementDialog() {
        CustomDialog dialog = new CustomDialog(this);
        dialog.setTitle("用户协议");
        dialog.setMessage("请阅读并同意我们的用户协议...");
        dialog.setPositiveButton("同意", v -> {
            Toast.makeText(MainActivity.this, "感谢您的同意!", Toast.LENGTH_SHORT).show();
            AMapLocationClient.updatePrivacyShow(this, true, true);
            AMapLocationClient.updatePrivacyAgree(this, true);
            MapsInitializer.updatePrivacyShow(this, true, true);
            MapsInitializer.updatePrivacyAgree(this, true);
            ServiceSettings.updatePrivacyShow(this, true, true);
            ServiceSettings.updatePrivacyAgree(this, true);
            dialog.dismiss();
        });
        dialog.setNegativeButton("不同意", v -> {
            Toast.makeText(MainActivity.this, "您必须同意用户协议才能使用本应用", Toast.LENGTH_LONG).show();
            finish();
            dialog.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }
    // 在更新控制值显示方法中添加超时检测
    private static final long FORCE_SEND_THRESHOLD = 500; // 如果超过500ms未发送则强制发送

    /**
     * 更新控制值显示
     * 并记录每次调用的时间间隔
     */
    private void updateControlValues(float length, float angle) {
        // 计算本次调用与上次调用的时间间隔
        long currentTime = System.currentTimeMillis();
        long interval = 0;
        if (lastUpdateTime > 0) {
            interval = currentTime - lastUpdateTime;

            // 如果间隔过长，触发紧急发送（使用无阻塞方法）
            if (interval > FORCE_SEND_THRESHOLD) {
                JoySticksDecoder.getInstance().forceSendCommand();
                Log.w(TAG, "检测到UI更新延迟，触发紧急发送: " + interval + "ms");
            }
        }
        lastUpdateTime = currentTime;

        // 记录日志，包含时间间隔信息
        Log.d("ControlValues", "长度：" + length + ", 角度：" + angle + ", 调用间隔：" + interval + "ms");

        // 更新UI显示
        TextView lengthText = findViewById(R.id.textView);
        TextView angleText = findViewById(R.id.textView2);
        if (lengthText != null) {
            lengthText.setText(String.valueOf(length));
        }
        if (angleText != null) {
            angleText.setText(String.valueOf(angle));
        }
        // 更新性能统计相关变量（确保不会阻塞UI线程）
        updatePerformanceMetrics(interval);
        // 提取JoystickView的归一化坐标并传递给JoySticksDecoder
        JoystickView joystickView = findViewById(R.id.joystick_view);
        if (joystickView != null) {
            float normalizedX = joystickView.getNormalizedX();
            float normalizedY = joystickView.getNormalizedY();
            // 使用无阻塞方法更新JoySticksDecoder控制参数
            JoySticksDecoder.getInstance().updateJoystickValues(normalizedX, normalizedY);
        }
    }

    /**
     * 更新性能统计指标
     *
     * @param interval 本次调用间隔(毫秒)
     */
    private void updatePerformanceMetrics(long interval) {
        if (interval <= 0) return;

        updateCount++;
        totalInterval += interval;

        // 更新最大和最小间隔
        if (interval < minInterval) {
            minInterval = interval;
        }
        if (interval > maxInterval) {
            maxInterval = interval;
        }

        // 每30次更新打印一次统计信息
        if (updateCount % 30 == 0) {
            double avgInterval = (double) totalInterval / updateCount;
            Log.i("PerformanceStats", String.format(
                    "摇杆控制统计: 调用次数=%d, 平均间隔=%.2fms, 最小间隔=%dms, 最大间隔=%dms",
                    updateCount, avgInterval, minInterval, maxInterval
            ));
        }
    }


    /**
     * 处理活动结果回调
     * 当启动其他活动（例如二维码扫描）并返回结果时，该方法被调用
     *
     * @param requestCode 请求码，用于标识启动的活动
     * @param resultCode  结果码，表示活动结果的状态
     * @param data        返回的数据，可能为null
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 日志记录：输出请求码和结果码，用于调试和跟踪
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        // 检查请求码和结果码以确定是否从二维码扫描返回且操作成功
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK) {
            // 扫码成功，开始处理WebSocket连接
            Log.d(TAG, "扫码成功，开始处理WebSocket连接");

            // 初始化震动反馈（如果尚未初始化）
            JoySticksDecoder.getInstance().init(this);

            // 立即更新UI以反馈连接状态
            updateConnectionStatusLight(true);

            // 初始化MainDeviceSocket并传递deviceInfoCard
            MainDeviceSocket mainDeviceSocket = getDeviceSocket();
            mainDeviceSocket.init(this, deviceInfoCard);

            // 启动WebSocket连接处理
            mainDeviceSocket.start();
        }
    }


    /**
     * 获取设备套接字实例，并配置连接状态监听器
     * <p>
     * 该方法主要负责获取MainDeviceSocket的实例，并设置连接状态监听器以处理WebSocket连接状态的变化
     * 当WebSocket连接状态发生变化时，通过日志记录当前连接状态，并调用方法更新连接状态指示
     * 如果连接成功，将启动摇杆控制命令发送；如果断开连接，将停止摇杆控制命令发送
     *
     * @return MainDeviceSocket的实例，用于设备通信
     */
    @NonNull
    private MainDeviceSocket getDeviceSocket() {
        // 获取MainDeviceSocket的单例实例
        MainDeviceSocket mainDeviceSocket = MainDeviceSocket.getInstance();
        // 设置连接状态监听器
        mainDeviceSocket.setConnectionStatusListener(isConnected -> {
            // 记录WebSocket连接状态变化
            Log.d(TAG, "WebSocket连接状态变化: " + (isConnected ? "已连接" : "未连接"));
            // 更新连接状态指示
            updateConnectionStatusLight(isConnected);
            // 在WebSocket连接成功时启动摇杆控制命令发送
            if (isConnected) {
                // 确保初始化震动功能
                JoySticksDecoder.getInstance().init(this);
                // 启动摇杆控制命令发送
                JoySticksDecoder.getInstance().start();
            } else {
                // 在WebSocket断开连接时停止摇杆控制命令发送
                JoySticksDecoder.getInstance().stop();
            }
        });
        // 返回MainDeviceSocket实例
        return mainDeviceSocket;
    }

    /**
     * 请求权限结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //设置权限请求结果
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * 动态请求权限
     */
    @AfterPermissionGranted(REQUEST_PERMISSIONS)
    private void requestPermission() {
        String[] permissions = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        if (EasyPermissions.hasPermissions(this, permissions)) {
            // true 有权限 开始定位
            Utils.showMsg(MainActivity.this, "已获得权限，可以定位啦！");
            // 新增：重新启用地图定位功能
            if (aMap != null) {
                aMap.setMyLocationEnabled(true);
            }
        } else {
            // false 无权限
            EasyPermissions.requestPermissions(this, "需要权限", REQUEST_PERMISSIONS, permissions);
        }
    }

    private LocationSource.OnLocationChangedListener mListener;//声明位置监听
    private AMapLocationClient mlocationClient;//声明定位客户端

    /**
     * 位置定位激活方法
     * 创建并启动定位客户端，设置定位模式为高精度模式
     */
    @Override
    public void activate(LocationSource.OnLocationChangedListener listener) {
        mListener = listener;
        if (mlocationClient == null) {
            try {
                AMapLocationClientOption mLocationOption = new AMapLocationClientOption();
                mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                mlocationClient = new AMapLocationClient(this);
                mlocationClient.setLocationListener(this);
                mlocationClient.setLocationOption(mLocationOption);
                mlocationClient.startLocation();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            mlocationClient.startLocation();
        }
    }

    /**
     * 停止定位服务
     * 释放定位客户端资源
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mlocationClient != null) {
            mlocationClient.stopLocation();
            mlocationClient.onDestroy();
        }
        mlocationClient = null;
    }

    private boolean isFirstLoc = true;//判断是否第一次定位

    /**
     * 监听定位回调
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (mListener != null && aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                // 定位成功回调信息，设置相关消息
                double latitude = aMapLocation.getLatitude(); // 获取纬度
                double longitude = aMapLocation.getLongitude(); // 获取经度
                String address = aMapLocation.getAddress(); // 获取地址信息

                // 更新当前定位点
                //当前定位
                LatLng currentLatLng = new LatLng(latitude, longitude);

                // 判断定位点是否在当前地图显示范围内
                if (aMap != null && !aMap.getProjection().getVisibleRegion().latLngBounds.contains(currentLatLng)) {
                    // 如果定位点不在当前地图显示范围内，则更新地图中心点
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentLatLng, 3); // 设置缩放级别为最大值
                    aMap.moveCamera(cameraUpdate);
                }

                // 如果是第一次定位，通知监听器
                if (isFirstLoc) {
                    mListener.onLocationChanged(aMapLocation);
                    isFirstLoc = false;
                }

                // 添加详细的日志输出
                Log.d("AmapLocation", "Location updated: Lat=" + latitude + ", Lng=" + longitude + ", Address=" + address);
            } else {
                // 显示错误信息
                Log.e("AmapError", "location Error, ErrCode:"
                        + aMapLocation.getErrorCode() + ", errInfo:"
                        + aMapLocation.getErrorInfo());
            }
        }
    }

    /**
     * 生命周期-onDestroy
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 记录最终性能统计
        if (updateCount > 0) {
            double avgInterval = (double) totalInterval / updateCount;
            Log.i("PerformanceStats", String.format(
                    "最终摇杆控制统计: 调用次数=%d, 平均间隔=%.2fms, 最小间隔=%dms, 最大间隔=%dms",
                    updateCount, avgInterval, minInterval, maxInterval
            ));
        }

        // 断开WebSocket连接
        MainDeviceSocket.getInstance().disconnect();
        // 停止控制命令发送
        JoySticksDecoder.getInstance().stop();
        if (mMapView != null) {
            mMapView.onDestroy(); // 确保调用 onDestroy 方法
        }
        Log.d("MapLifecycle", "MapView onDestroy called.");
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume(); // 确保调用 onResume 方法
        }
        Log.d("MapLifecycle", "MapView onResume called.");
        // 检查WebSocket连接状态并更新指示灯
        boolean isConnected = MainDeviceSocket.getInstance().isConnected();
        Log.d(TAG, "页面恢复，WebSocket连接状态: " + (isConnected ? "已连接" : "未连接"));
        updateConnectionStatusLight(isConnected);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) {
            mMapView.onPause(); // 确保调用 onPause 方法
        }
        Log.d("MapLifecycle", "MapView onPause called.");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) {
            mMapView.onSaveInstanceState(outState);//保存地图当前的状态
        }
        Log.d("MapLifecycle", "MapView onSaveInstanceState called.");
    }

    /**
     * 扫描二维码按钮点击事件
     */
    public void onScannerClick(View view) {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            // 摄像头权限已授予，可以启动扫描器
            Intent intent = new Intent(this, QR_codeScannerActivity.class);
            // 使用传统的方式启动活动
            startActivityForResult(intent, REQUEST_QR_SCAN);
        } else {
            // 请求摄像头权限
            requestCameraPermission();
        }
    }

}
