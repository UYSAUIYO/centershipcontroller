package com.yuwen.centershipcontroller;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.yuwen.centershipcontroller.Utils.UserSettings;
import com.yuwen.centershipcontroller.Views.DeviceInfoCard;
import com.yuwen.centershipcontroller.Views.JoystickView;
import com.yuwen.centershipcontroller.Utils.Utils;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author Yuwen
 */
public class MainActivity extends AppCompatActivity implements LocationSource, AMapLocationListener {
    //请求权限码
    private static final int REQUEST_PERMISSIONS = 9527;
    private static final int REQUEST_CAMERA_PERMISSION = 9528; // 新增摄像头权限请求码
    private MapView mMapView;//声明一个地图视图对象
    private AMap aMap;

    private DeviceInfoCard deviceInfoCard;

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
        deviceInfoCard = findViewById(R.id.device_card);
        deviceInfoCard.setDeviceTitle("人工湖001"); // 确保传入的字符串是有效的 BufferType 枚举值
        deviceInfoCard.setBatteryLevel(50);
        deviceInfoCard.setBatteryStatus(false);
    }

    /**
     * 初始化地图
     * @param savedInstanceState
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { 
            // Android 13+ 使用 READ_MEDIA_IMAGES/VIDEO 替代旧权限
            perms = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            perms = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        if (EasyPermissions.hasPermissions(this, perms)) {
            // 权限已授予，可执行相关操作
            Toast.makeText(this, "相机权限已就绪", Toast.LENGTH_SHORT).show();
            // 调用摄像头功能...
        } else {
            EasyPermissions.requestPermissions(this, "需要访问相机和媒体文件", REQUEST_CAMERA_PERMISSION, perms);
        }
    }

    /**
     * 示例：在设置菜单点击时请求权限
     */
    public void onSettingClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        
        // 示例触发权限请求（实际应根据业务场景调用）
        requestCameraPermission(); 
    }

    private void showUserAgreementDialog() {
        new AlertDialog.Builder(this)
                .setTitle("用户协议")
                .setMessage("请阅读并同意我们的用户协议...")
                .setPositiveButton("同意", (dialog, which) -> {
                    // 用户同意后的操作
                    Toast.makeText(MainActivity.this, "感谢您的同意!", Toast.LENGTH_SHORT).show();
                    //定位隐私政策同意
                    AMapLocationClient.updatePrivacyShow(this,true,true);
                    AMapLocationClient.updatePrivacyAgree(this,true);
                    //地图隐私政策同意
                    MapsInitializer.updatePrivacyShow(this,true,true);
                    MapsInitializer.updatePrivacyAgree(this,true);
                    //搜索隐私政策同意
                    ServiceSettings.updatePrivacyShow(this,true,true);
                    ServiceSettings.updatePrivacyAgree(this,true);
                })
                .setNegativeButton("不同意", (dialog, which) -> {
                    // 用户不同意，可以选择关闭应用
                    Toast.makeText(MainActivity.this, "您必须同意用户协议才能使用本应用", Toast.LENGTH_LONG).show();
                    finish();
                })
                .setCancelable(false) // 防止用户通过返回键取消对话框
                .show();
    }

    // 如果需要将控制值传递到其他组件，可以添加这样的方法

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
     * 请求权限结果
     * @param requestCode
     * @param permissions
     * @param grantResults
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
    private LatLng currentLatLng;//当前定位
    /**
     * 监听定位回调
     * @param aMapLocation
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
                currentLatLng = new LatLng(latitude, longitude);

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
        mMapView.onDestroy(); // 确保调用 onDestroy 方法
        Log.d("MapLifecycle", "MapView onDestroy called.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume(); // 确保调用 onResume 方法
        Log.d("MapLifecycle", "MapView onResume called.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause(); // 确保调用 onPause 方法
        Log.d("MapLifecycle", "MapView onPause called.");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);//保存地图当前的状态
        Log.d("MapLifecycle", "MapView onSaveInstanceState called.");
    }
}