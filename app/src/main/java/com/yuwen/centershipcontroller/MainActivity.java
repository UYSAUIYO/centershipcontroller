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
import com.yuwen.centershipcontroller.Activity.SettingsActivity;
import com.yuwen.centershipcontroller.Component.CustomDialog;
import com.yuwen.centershipcontroller.Socket.MainDeviceSocket;
import com.yuwen.centershipcontroller.Utils.UserSettings;
import com.yuwen.centershipcontroller.Utils.Utils;
import com.yuwen.centershipcontroller.Views.DeviceInfoCard;
import com.yuwen.centershipcontroller.Views.JoystickView;
import com.yuwen.centershipcontroller.Views.QR_codeScanner;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author Yuwen
 */
public class MainActivity extends AppCompatActivity implements LocationSource, AMapLocationListener {
    private static final String TAG = "MainActivity";
    //请求权限码
    private static final int REQUEST_PERMISSIONS = 9527;
    private static final int REQUEST_CAMERA_PERMISSION = 9528; // 新增摄像头权限请求码
    private static final int REQUEST_QR_SCAN = 9529; // WebSocket连接请求码

    private ActivityResultLauncher<Intent> qrCodeScannerLauncher;

    private MapView mMapView;//声明一个地图视图对象
    private AMap aMap;

    private DeviceInfoCard deviceInfoCard;
    private ImageView connectionStatusLight; // 连接状态指示灯

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置透明顶部栏和系统状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 使用 WindowInsetsController 替代过时的 SYSTEM_UI_FLAG
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
                    Log.e("UIError", "WindowInsetsController is null. Falling back to legacy flags.");
                    // 回退到旧的 SYSTEM_UI_FLAG 实现
                    getWindow().setFlags(
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    );
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
                }
            } catch (Exception e) {
                Log.e("UIError", "Failed to set system UI flags: " + e.getMessage());
                // 回退到旧的 SYSTEM_UI_FLAG 实现
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                );
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } else {
            // 兼容低版本 API
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            );
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        setContentView(R.layout.activity_main);

        // 确保加载高德地图的原生库
        try {
            System.loadLibrary("amap3dmap");
        } catch (UnsatisfiedLinkError e) {
            Log.e("MapError", "Failed to load AMap library: " + e.getMessage());
        }

        requestPermission();

        // 地图隐私政策同意
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 定位隐私政策同意
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);

        // 搜索隐私政策同意
        ServiceSettings.updatePrivacyShow(this, true, true);
        ServiceSettings.updatePrivacyAgree(this, true);

        // 初始化地图
        initMap(savedInstanceState);

        // 获取布局中的JoystickView实例
        JoystickView joystickView = findViewById(R.id.joystick_view);
        UserSettings userSettings = new UserSettings(this);
        // 设置监听器
        joystickView.setJoystickListener((length, angle) -> {
            // 这里可以添加其他操作，例如控制机器人或游戏角色
            System.out.println("Length: " + length + ", Angle: " + angle);

            updateControlValues(length, angle);
        });

        if (userSettings.checkFirstRun()) {
            showUserAgreementDialog();
        }

        // 初始化设备信息卡
        deviceInfoCard = findViewById(R.id.device_card);
        initDeviceInfoCard();

        // 初始化连接状态指示灯
        // 连接状态指示灯
        connectionStatusLight = findViewById(R.id.status_light);
        updateConnectionStatusLight(false); // 默认为未连接状态

        // 设置MainDeviceSocket连接状态监听
        MainDeviceSocket.getInstance().setConnectionStatusListener(
                this::updateConnectionStatusLight
        );

    }
    /**
     * 处理二维码扫描结果
     */
    private void onQRScanResult(@Nullable Intent data) {
        if (MainActivity.REQUEST_QR_SCAN == REQUEST_QR_SCAN && android.app.Activity.RESULT_OK == RESULT_OK) {
            Log.d(TAG, "扫码成功，开始处理WebSocket连接");

            // 立即更新UI以反馈连接状态
            updateConnectionStatusLight(true);

            // 初始化MainDeviceSocket并传递deviceInfoCard
            MainDeviceSocket mainDeviceSocket = MainDeviceSocket.getInstance();

            // 设置连接状态监听器
            mainDeviceSocket.setConnectionStatusListener(isConnected -> {
                Log.d(TAG, "WebSocket连接状态变化: " + (isConnected ? "已连接" : "未连接"));
                updateConnectionStatusLight(isConnected);
            });

            mainDeviceSocket.init(this, deviceInfoCard);

            // 启动WebSocket连接处理
            mainDeviceSocket.start();
        }
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
     * 初始化地图
     */
    private void initMap(Bundle savedInstanceState) {
        // 确保地图隐私合规接口已调用
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 获取地图视图对象
        mMapView = findViewById(R.id.map);
        if (mMapView == null) {
            Log.e("MapError", "MapView is not found in the layout.");
            return;
        }
        mMapView.onCreate(savedInstanceState); // 确保调用 onCreate 方法
        if (aMap == null) {
            aMap = mMapView.getMap();
            if (aMap == null) {
                Log.e("MapError", "Failed to initialize AMap object.");
                return;
            } else {
                Log.d("MapInit", "AMap object initialized successfully.");
            }
        }

        // 隐藏高德地图Logo（通过调整边距实现）
        aMap.getUiSettings().setLogoBottomMargin(-100); // 负值将Logo移出屏幕下边界

        // 设置定位蓝点样式
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
     * 动态请求摄像头权限（兼容不同Android版本）
     */
    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    public void requestCameraPermission() {
        String[] perms;
        // Android 13+ 使用 READ_MEDIA_IMAGES/VIDEO 替代旧权限
        perms = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // 权限已授予，可执行相关操作
            Toast.makeText(this, "相机权限已就绪", Toast.LENGTH_SHORT).show();
            // 使用传统的方式启动活动
            Intent intent = new Intent(this, QR_codeScanner.class);
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

    /**
     * 更新控制值显示
     */
    private void updateControlValues(int length, int angle) {
        TextView lengthText = findViewById(R.id.textView);
        TextView angleText = findViewById(R.id.textView2);

        if (lengthText != null) {
            lengthText.setText("Length: " + String.format("%d", length));
        }

        if (angleText != null) {
            angleText.setText("Angle: " + String.format("%d", angle));
        }
    }

    /**
     * 处理二维码扫描结果
     */
    // 在MainActivity.java中添加或修改以下方法

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK) {
            Log.d(TAG, "扫码成功，开始处理WebSocket连接");

            // 立即更新UI以反馈连接状态
            updateConnectionStatusLight(true);

            // 初始化MainDeviceSocket并传递deviceInfoCard
            MainDeviceSocket mainDeviceSocket = MainDeviceSocket.getInstance();

            // 设置连接状态监听器
            mainDeviceSocket.setConnectionStatusListener(isConnected -> {
                Log.d(TAG, "WebSocket连接状态变化: " + (isConnected ? "已连接" : "未连接"));
                updateConnectionStatusLight(isConnected);
            });

            mainDeviceSocket.init(this, deviceInfoCard);

            // 启动WebSocket连接处理
            mainDeviceSocket.start();
        }
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
     * 激活定位
     */
    @Override
    public void activate(LocationSource.OnLocationChangedListener listener) {
        mListener = listener;
        if (mlocationClient == null) {
            // 初始化定位
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
            // 新增：确保定位服务已启动
            mlocationClient.startLocation();
        }
    }

    /**
     * 停止定位
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
        // 断开WebSocket连接
        MainDeviceSocket.getInstance().disconnect();

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
            Intent intent = new Intent(this, QR_codeScanner.class);
            // 使用传统的方式启动活动
            startActivityForResult(intent, REQUEST_QR_SCAN);
        } else {
            // 请求摄像头权限
            requestCameraPermission();
        }
    }

}
