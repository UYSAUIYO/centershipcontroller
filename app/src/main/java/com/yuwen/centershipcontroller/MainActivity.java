package com.yuwen.centershipcontroller;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
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
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.ServiceSettings;
import com.yuwen.centershipcontroller.Utils.UserSettings;
import com.yuwen.centershipcontroller.Views.JoystickView;

import com.yuwen.centershipcontroller.Utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author Yuwen
 */
public class MainActivity extends AppCompatActivity implements LocationSource, AMapLocationListener {
    //请求权限码
    private static final int REQUEST_PERMISSIONS = 9527;
    private MapView mMapView;//声明一个地图视图对象
    private AMap aMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    /**
     * 初始化地图
     * @param savedInstanceState
     */
    private void initMap(Bundle savedInstanceState) {
        // 隐私合规接口
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 获取地图视图对象
        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState); // 确保调用 onCreate 方法
        if (aMap == null) {
            aMap = mMapView.getMap();
        }

        // 设置定位蓝点样式
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_MAP_ROTATE);
        myLocationStyle.interval(2000);
        myLocationStyle.showMyLocation(true);
        myLocationStyle.anchor(0.5f, 0.5f).myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setMyLocationEnabled(true);
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

    private void updateControlValues(float length, float angle) {
        TextView lengthText = findViewById(R.id.textView);
        TextView angleText = findViewById(R.id.textView2);

        if (lengthText != null) {
            lengthText.setText("Length: " + String.format("%.1f", length));
        }

        if (angleText != null) {
            angleText.setText("Angle: " + String.format("%.1f", angle));
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
            //true 有权限 开始定位
            Toast.makeText(MainActivity.this, "已获得权限，可以定位啦！", Toast.LENGTH_LONG).show();
            Utils.showMsg(MainActivity.this,"已获得权限，可以定位啦！");
        } else {
            //false 无权限
            EasyPermissions.requestPermissions(this, "需要权限", REQUEST_PERMISSIONS, permissions);
        }
    }

    private LocationSource.OnLocationChangedListener mListener;//声明位置监听
    private AMapLocationClient mlocationClient;//声明定位客户端
    private AMapLocationClientOption mLocationOption;//声明定位参数配置选项
    /**
     * 激活定位
     */
    @Override
    public void activate(LocationSource.OnLocationChangedListener listener) {
        mListener = listener;
        if (mlocationClient == null) {
            //初始化定位
            try {
                mLocationOption = new AMapLocationClientOption();//初始化定位参数
                mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);//设置为高精度定位模式
                mlocationClient = new AMapLocationClient(this);//声明定位客户端
                mlocationClient.setLocationListener(this);//设置定位回调监听
                mlocationClient.setLocationOption(mLocationOption);//设置定位参数
                mlocationClient.startLocation();//启动定位
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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
                //定位成功回调信息，设置相关消息
                aMapLocation.getLocationType();//获取当前定位结果来源，如网络定位结果，详见官方定位类型表
                aMapLocation.getLatitude();//获取纬度
                aMapLocation.getLongitude();//获取经度
                aMapLocation.getAccuracy();//获取精度信息
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = new Date(aMapLocation.getTime());
                df.format(date);//定位时间
                aMapLocation.getAddress();//地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。
                aMapLocation.getCountry();//国家信息
                aMapLocation.getProvince();//省信息
                aMapLocation.getCity();//城市信息
                aMapLocation.getDistrict();//城区信息
                aMapLocation.getStreet();//街道信息
                aMapLocation.getStreetNum();//街道门牌号信息
                aMapLocation.getCityCode();//城市编码
                aMapLocation.getAdCode();//地区编码
                // 是否第一次定位
                if (isFirstLoc) {
                    aMap.moveCamera(CameraUpdateFactory.zoomTo(16));//设置缩放级别
                    currentLatLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()); //获取当前定位
                    aMap.moveCamera(CameraUpdateFactory.changeLatLng(currentLatLng));//移动到定位点
                    //点击定位按钮 能够将地图的中心移动到定位点
                    mListener.onLocationChanged(aMapLocation);
                    isFirstLoc = false;
                }

            } else {
                //显示错误信息
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
    }
    /**
     * 生命周期-onResume
     */
    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume(); // 确保调用 onResume 方法
    }
    /**
     * 生命周期-onPause
     */
    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause(); // 确保调用 onPause 方法
    }
    /**
     * 生命周期-onSaveInstanceState
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);//保存地图当前的状态
    }




}
