package com.speedata.sc100_r6;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.serialport.DeviceControl;
import android.util.Log;
import android.widget.Toast;

import com.example.test.yinlianbarcode.interfaces.OnBackListener;
import com.example.test.yinlianbarcode.utils.ScanUtils;
import com.honeywell.barcode.ActiveCamera;
import com.honeywell.barcode.HSMDecodeComponent;
import com.honeywell.barcode.HSMDecoder;
import com.honeywell.camera.CameraManager;
import com.speedata.r6lib.IMifareManager;
import com.speedata.r6lib.IR6Manager;
import com.speedata.r6lib.R6Manager;
import com.speedata.sc100_r6.spdata.utils.PlaySound;

import java.io.IOException;

import speedatacom.a3310libs.PsamManager;
import speedatacom.a3310libs.inf.IPsam;

import static com.honeywell.barcode.Symbology.QR;

public class MyAplication extends Application {
    String TAG = "sc100r6";
    /**
     * 定义对象
     */
    private static IR6Manager cpuAR6Manager;
    /**
     * 定义对象
     */
    private static IMifareManager mifareR6Manager;
    private static IPsam iPsam;
    private static HSMDecoder hsmDecoder;
    private HSMDecodeComponent hsmDecodeComponent;


    @Override
    public void onCreate() {
        super.onCreate();
        initDev();
        initScanBards();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Log.d(TAG, "onActivityCreated: " + activity.getLocalClassName());
            }

            @Override
            public void onActivityStarted(Activity activity) {
                Log.d(TAG, "onActivityStarted: " + activity.getLocalClassName());
            }

            @Override
            public void onActivityResumed(Activity activity) {
                Log.d(TAG, "onActivityResumed: " + activity.getLocalClassName());
            }

            @Override
            public void onActivityPaused(Activity activity) {
                Log.d(TAG, "onActivityPaused: " + activity.getLocalClassName());
            }

            @Override
            public void onActivityStopped(Activity activity) {
                Log.d(TAG, "onActivityStopped: " + activity.getLocalClassName());
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                Log.d(TAG, "onActivityDestroyed: " + activity.getLocalClassName());
                hsmDecodeComponent.enableScanning(false);
                hsmDecodeComponent.dispose();
            }
        });

    }

    public static IR6Manager getCpuAR6Manager() {
        return cpuAR6Manager;
    }

    public static IMifareManager getMifareR6Manager() {
        return mifareR6Manager;
    }

    public static HSMDecoder getHSMDecoder() {
        return hsmDecoder;
    }

    public static IPsam getIPSAM() {
        return iPsam;
    }

    /**
     * 初始化 dev
     */
    private void initDev() {
        PlaySound.initSoundPool(this);
        //选择卡类型为cpuA卡。同理cpuB卡为：CPUB
        cpuAR6Manager = R6Manager.getInstance(R6Manager.CardType.CPUA);//选择卡类型：CPUA
        int a = cpuAR6Manager.InitDevice();
        if (a != 0) {
            Log.e(TAG, "intDev:  cpua初始化失败");
        }
        mifareR6Manager = R6Manager.getMifareInstance(R6Manager.CardType.MIFARE);//选择卡类型：15693}
        a = mifareR6Manager.InitDev();
        if (a != 0) {
            Log.e(TAG, "intDev:  MIFARE初始化失败");
        }
        iPsam = PsamManager.getPsamIntance();
        try {
            iPsam.initDev("ttyMT1", 115200, getApplicationContext());
            DeviceControl deviceControl = new DeviceControl(DeviceControl.PowerType.MAIN, 66);
            deviceControl.PowerOnDevice();
            iPsam.resetDev(DeviceControl.PowerType.MAIN, 119);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化银联二维码支付
     */
    private void initScanBards() {
        hsmDecoder = HSMDecoder.getInstance(this);
        hsmDecoder.enableAimer(true);
        hsmDecoder.setAimerColor(Color.RED);
        hsmDecoder.setOverlayText("ceshi");
        hsmDecoder.setOverlayTextColor(Color.RED);
        hsmDecoder.enableSound(true);
        //初始为默认前置摄像头扫码
        hsmDecoder.setActiveCamera(ActiveCamera.FRONT_FACING);// 摄像头
        hsmDecoder.enableSymbology(QR);
        CameraManager cameraManager = CameraManager.getInstance(getApplicationContext());
        hsmDecodeComponent = new HSMDecodeComponent(getApplicationContext());
        cameraManager.closeCamera();

        ScanUtils.activateScan(this, new OnBackListener() {
            @Override
            public void onBack() {
                hsmDecoder.enableSymbology(QR);
                cameraManager.openCamera();
                hsmDecodeComponent.enableScanning(true);
                Toast.makeText(getApplicationContext(), "激活成功！", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                hsmDecoder.enableSymbology(QR);
                cameraManager.openCamera();
                hsmDecodeComponent.enableScanning(true);
                Toast.makeText(getApplicationContext(), "激活失败！", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
