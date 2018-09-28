package com.speedata.sc100_r6;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.test.yinlianbarcode.entity.QrEntity;
import com.example.test.yinlianbarcode.interfaces.OnBackListener;
import com.example.test.yinlianbarcode.utils.Logcat;
import com.example.test.yinlianbarcode.utils.ScanUtils;
import com.example.test.yinlianbarcode.utils.ValidationUtils;
import com.honeywell.barcode.ActiveCamera;
import com.honeywell.barcode.HSMDecodeComponent;
import com.honeywell.barcode.HSMDecodeResult;
import com.honeywell.barcode.HSMDecoder;
import com.honeywell.camera.CameraManager;
import com.honeywell.camera.HSMCameraPreview;
import com.honeywell.plugins.decode.DecodeResultListener;
import com.speedata.libutils.DataConversionUtils;
import com.speedata.r6lib.IMifareManager;
import com.speedata.r6lib.IR6Manager;
import com.speedata.r6lib.R6Manager;
import com.speedata.sc100_r6.spdata.been.IcCardBeen;
import com.speedata.sc100_r6.spdata.been.TCommInfo;
import com.speedata.sc100_r6.spdata.utils.DataUtils;
import com.speedata.sc100_r6.spdata.utils.HEX;
import com.speedata.sc100_r6.spdata.utils.PlaySound;
import com.speedata.sc100_r6.spdata.utils.TLV;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import speedatacom.a3310libs.PsamManager;
import speedatacom.a3310libs.inf.IPsam;

import static com.android.hflibs.Mifare_native.AUTH_TYPEA;
import static com.honeywell.barcode.Symbology.CODE128;
import static com.honeywell.barcode.Symbology.CODE39;
import static com.honeywell.barcode.Symbology.CODE93;
import static com.honeywell.barcode.Symbology.EAN13;
import static com.honeywell.barcode.Symbology.QR;

public class MainActivity extends AppCompatActivity implements DecodeResultListener {
    /**
     * 选择PPSE支付环境
     */
    private final byte[] fuhe_tlv = {0x00, (byte) 0xA4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e,
            0x44, 0x44, 0x46, 0x30, 0x31};
    /**
     * 读ic卡应用下公共应用基本信息文件指令 15文件
     */
    private final byte[] ic_read_file = {0x00, (byte) 0xB0, (byte) 0x95, 0x00, 0x00};

    /**
     * 获取PSAM卡终端机编号指令
     */
    private final byte[] psam1_get_id = {0x00, (byte) 0xB0, (byte) 0x96, 0x00, 0x06};

    /**
     * 交通部
     */
    private final byte[] psam2_select_dir = {0x00, (byte) 0xA4, 0x00, 0x00, 0x02, (byte) 0x80, 0x11};
    /**
     * 住建部
     */
    private final byte[] psamzhujian_select_dir = {0x00, (byte) 0xA4, 0x00, 0x00, 0x02, (byte) 0x10, 0x01};
    /**
     * 读取psam卡17文件
     */
    private final byte[] psam3_get_index = {0x00, (byte) 0xB0, (byte) 0x97, 0x00, 0x01};
    /**
     * 返回正确结果
     */
    private final byte[] APDU_RESULT_SUCCESS = {(byte) 0x90, 0x00};
    /**
     * 返回错误结果
     */
    private final byte[] APDU_RESULT_FAILE = {(byte) 0x62, (byte) 0x83};
    /**
     * 选择电子钱包应用
     */
    private final byte[] ic_file = {0x00, (byte) 0xA4, 0x04, 0x00, 0x08, (byte) 0xA0, 0x00, 0x00, 0x06, 0x32, 0x01, 0x01, 0x05};
    private String tag = "sc100R6";
    /**
     * 定义对象
     */
    private IR6Manager cpuAR6Manager;
    /**
     * 定义对象
     */
    private IMifareManager mifareR6Manager;
    private IPsam iPsam;

    private byte flag;
    private byte[] resultBytes;
    private int isExpense = -1;
    private byte[] systemTime;
    private byte[] deviceCode;//终端编号
    private byte[] psamKey;//秘钥索引
    private int CAPP = 0; //普通交易（CAPP=0）或复合交易（CAPP=1）
    private byte[] cardId = new byte[8];
    private byte[] city = new byte[2];
    private byte[] file15_8 = new byte[8];
    private byte[] blance = new byte[4];
    private byte[] ATC = new byte[2];
    private byte[] keyVersion = new byte[4];
    private byte[] rondomCpu = new byte[4];
    private byte[] psamAtc = new byte[4];
    private byte[] mac1 = new byte[4];

    /**
     * 复合消费
     */
    private CheckBox mCheckbox;
    private TextView mTvTitle;
    private TextView mTvCircuit;
    private TextView mTvPrice;
    private TextView mTvBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去掉Activity上面的状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //去掉虚拟按键全屏显示
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
//        //点击屏幕不再显示
        this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav
                        // bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);

        setContentView(R.layout.bus_layout);
        initView();
        //注册系统时间广播 只能动态注册
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(receiver, filter);
        PlaySound.initSoundPool(this);
        intDev();
        initScanBards();
    }

    /**
     * 更新时间
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_TICK)) {
                mTvTitle.setText("产品编号：0123456789   " + DataUtils.getNowTime());
            }
        }
    };


    private void intDev() {
        //选择卡类型为cpuA卡。同理cpuB卡为：CPUB
        cpuAR6Manager = R6Manager.getInstance(R6Manager.CardType.CPUA);//选择卡类型：CPUA
        int a = cpuAR6Manager.InitDevice();
        if (a != 0) {
            Log.e(tag, "intDev:  cpua初始化失败");
            mTvPrice.setTextSize(65);
            mTvPrice.setText("R6CPU初始化失败！");
        }
        mifareR6Manager = R6Manager.getMifareInstance(R6Manager.CardType.MIFARE);//选择卡类型：15693}
        a = mifareR6Manager.InitDev();
        if (a != 0) {
            Log.e(tag, "intDev:  MIFARE初始化失败");
            mTvPrice.setTextSize(65);
            mTvPrice.setText("R6MIFARE初始化失败！");
        }
        iPsam = PsamManager.getPsamIntance();
        try {
            iPsam.initDev(this);
            iPsam.resetDev();
            SystemClock.sleep(2500);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (psam1Init() != 0) {
            PlaySound.play(PlaySound.erro,3);
            mTvPrice.setTextSize(65);
            mTvPrice.setText("PSAM1初始化失败！");
        }
        if (psam2Init() != 0) {
            PlaySound.play(PlaySound.erro,3);
            mTvPrice.setTextSize(65);
            mTvPrice.setText("PSAM2初始化失败！");
        }
        startTimer();
    }

    private void initR6() {
        if (cpuAR6Manager != null) {
            int a = cpuAR6Manager.InitDevice();
            if (a != 0) {
                Log.e(tag, "intDev:  cpua初始化失败");
                return;
            }
        }
    }

    private byte[] ICSendAPDU(byte[] cmd) {

        byte[] result = cpuAR6Manager.ExecCmdInput(cmd);//发送指令
        if (result == null) {
            Log.e(tag, "write指令返回失败失败 , ");
            return null;
        }
        return result;
    }


    private byte[] PsamSenAPDU(IPsam.PowerType types, byte[] cmd) throws UnsupportedEncodingException {
        byte[] re = iPsam.WriteCmd(cmd, types);
        return re;
    }

    @Override
    protected void onStop() {
        hsmDecodeComponent.enableScanning(false);
        hsmDecodeComponent.dispose();
        HSMDecoder.disposeInstance();

        super.onStop();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        unregisterReceiver(receiver);

        cpuAR6Manager.ReleaseDevice();

        mifareR6Manager.ReleaseDev();
    }

    private void initView() {
        mCheckbox = findViewById(R.id.checkbox);
        mCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    CAPP = 1;
                } else {
                    CAPP = 0;
                }
            }
        });
        mTvTitle = findViewById(R.id.tv_title);
        mTvCircuit = findViewById(R.id.tv_circuit);
        mTvPrice = findViewById(R.id.tv_price);
        mTvBalance = findViewById(R.id.tv_balance);
        mTvTitle.setText("产品编号：0123456789   " + DataUtils.getNowTime());
    }

    private Timer timer = new Timer();

    private void startTimer() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (snUid == null) {
                    isExpense = -1;
                    resultBytes = mifareR6Manager.SearchCardSak();
                    if (resultBytes == null) {
                        isExpense = 1;
                        Log.e(tag, "M1ICCard: 获取UID失败");
                        return;
                    }
                    cardType = resultBytes[0];
                    snUid = cutBytes(resultBytes, 1, resultBytes.length - 1);
                    if ((cardType & 0x20) == 0) {
                        if (cardType == 0x18 || cardType == 0x38) {//70卡
                            Log.i(tag, "ddd:  z执行M1卡 ");
                            M1ICCard();
                        } else {//50卡
                            M1ICCard();
                        }
                    } else {//CPU卡
                        Log.i(tag, "ddd:  z执行CPU卡 ");
                        ICExpance();
                    }
                } else {
                    if (isExpense == 1) {//m1卡检测
                        int a = mifareR6Manager.HaltCard();
                        if (a != 0) {
                            return;
                        }
                        a = mifareR6Manager.ActiveCard(snUid);
                        if (a == 0) {
                            Log.d(tag, "卡片在");
                            return;
                        } else {
                            snUid = null;
                            Log.d(tag, "卡片不在");
                            return;
                        }
                    }
                }
            }
        }, 10, 50);

    }


    private byte[] CpuASearchCardSaks() {
        byte[] result = cpuAR6Manager.SearchCard();
        while (result == null) {
            result = cpuAR6Manager.SearchCard();
            Log.i("ssssss", "CpuASearchCardSaks:  SearchCardSearchCardSearchCardSearchCard ");
        }
        return result;
    }

    /**
     * psam 初始化流程
     */
    private int psam1Init() {
        try {
            resultBytes = iPsam.PsamPower(IPsam.PowerType.Psam1);
            if (resultBytes == null) {
                Log.e(tag, "psamInit: psam1 上电失败 ");
                return 1;
            }
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, psam1_get_id);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:  psam读16文件错误 ");
                return 1;
            }
            Log.d(tag, "交通部16文件：" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(cutBytes(resultBytes, resultBytes.length - 2, 2), APDU_RESULT_SUCCESS)) {
                Log.e(tag, "交通部获取终端编号错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            deviceCode = cutBytes(resultBytes, 0, resultBytes.length - 2);//终端机编号
            Log.d(tag, "====交通部PSAM卡终端机编号==== " + DataConversionUtils.byteArrayToString(deviceCode));
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, psam2_select_dir);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:psam 8011 错误");
                return 1;
            }
            Log.d(tag, "===交通部80 11 ====" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(new byte[]{0x61, 0x1D}, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(resultBytes, 1, GET_RESPONSE, 4, 1);
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, GET_RESPONSE);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:psam 8011 错误");
                return 1;
            }
            Log.d(tag, "===交通部80 11 ====" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, psam3_get_index);
            if (resultBytes == null) {
                Log.d(tag, "交通部切换00B0错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            Log.d(tag, "===交通部读17文件获取秘钥索引===" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "交通部获取秘钥索引错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            psamKey = cutBytes(resultBytes, 0, 1);
            Log.d(tag, "交通部秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            //切换等待读消费卡
            return 0;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * psam m1psam初始化
     */
    private int psam2Init() {
        try {
            resultBytes = iPsam.PsamPower(IPsam.PowerType.Psam2);
            if (resultBytes == null) {
                Log.e(tag, "psamInit: psam2 上电失败 ");
                return 1;
            }
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, psam1_get_id);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:  住建部M1 psam读16文件错误 ");
                return 1;
            }
            Log.d(tag, "住建部M1 16文件：" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(cutBytes(resultBytes, resultBytes.length - 2, 2), APDU_RESULT_SUCCESS)) {
                Log.e(tag, "住建部M1获取终端编号错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            deviceCode = cutBytes(resultBytes, 0, resultBytes.length - 2);//终端机编号
            Log.d(tag, "====住建部M1PSAM卡终端机编号==== " + DataConversionUtils.byteArrayToString(deviceCode));
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, psamzhujian_select_dir);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:psam 1001 错误");
                return 1;
            }
            Log.d(tag, "===住建部M110 01 ====" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(new byte[]{0x61, 0x28}, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "住建部M1切换10 01错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(resultBytes, 1, GET_RESPONSE, 4, 1);
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, GET_RESPONSE);
            if (resultBytes == null) {
                Log.e(tag, "psamInit:psam 8011 错误");
                return 1;
            }
            Log.d(tag, "===住建部M110 01 ====" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "住建部M1切换10 01错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }

            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, psam3_get_index);
            if (resultBytes == null) {
                Log.d(tag, "住建部M1切换00B0错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            Log.d(tag, "===住建部M1读17文件获取秘钥索引===" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, "住建部M1获取秘钥索引错误:" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return 1;
            }
            psamKey = cutBytes(resultBytes, 0, 1);
            Log.d(tag, "住建部M1秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            //切换等待读消费卡
            return 0;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return 1;
        }
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                  int blances = (int) msg.obj;
                    mTvPrice.setTextSize(40);
                    mTvPrice.setText("票价：0.02元");
                    mTvBalance.setVisibility(View.VISIBLE);
                    PlaySound.play(PlaySound.dang, 0);
                    mTvBalance.setText("余额：" + (double) blances / 100 + "元");
                    handler.postDelayed(runnable, 3000);
                    break;
                case 2:

                    mTvPrice.setTextSize(40);
                    mTvPrice.setText("票价：0.01元");
                    mTvBalance.setVisibility(View.VISIBLE);
                    PlaySound.play(PlaySound.dang, 0);
                    mTvBalance.setText("余额：" + ((DataConversionUtils.byteArrayToInt(icCardBeen.getPurOriMoney(), false) - DataConversionUtils.byteArrayToInt(icCardBeen.getPurSub())) / 100) + "元");
                    handler.postDelayed(runnable, 3000);
                    break;
                case 3:
                    break;
                default:
                    break;
            }
        }
    };
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mTvBalance.setVisibility(View.GONE);
            mTvPrice.setTextSize(65);
            mTvPrice.setText("票价：2元");
        }
    };
    private long times = 0;

    private void ICExpance() {
        try {
            byte[] id = CpuASearchCardSaks();
            if (id == null) {
                Log.i("stw", "CPUA寻卡失败 ");
                initR6();
                isExpense = 1;
                return;
            }
            //读卡操作后，可转化为String型便于查看
            resultBytes = cpuAR6Manager.ReadCard();
            if (resultBytes == null) {
                Log.e(tag, "CPUA读卡失败 , ");
                initR6();
                isExpense = 1;
                return;
            }
            Log.d(tag, "fuhexiaofei: 消费记录 tlv 3031 send " + DataConversionUtils.byteArrayToString(fuhe_tlv));
            resultBytes = ICSendAPDU(fuhe_tlv);
            Log.d("ssssssss", "ICExpance1111: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            if (resultBytes == null) {
                initR6();
                Log.e(tag, "消费记录 tlv 3031 error ");
                isExpense = 1;
                return;
            }
            byte[] testTlv = cutBytes(resultBytes, 0, resultBytes.length - 2);

            if (Arrays.equals(cutBytes(resultBytes, resultBytes.length - 2, 2), APDU_RESULT_FAILE)) {//黑名单

            } else if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                Log.e(tag, "消费记录 tlv 3031 return :" + DataConversionUtils.byteArrayToString(testTlv));
            }
            Log.d("ssssssss", "ICExpance1...22222: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();

            boolean isFlag = true;
            List<String> listTlv = new ArrayList<>();
            TLV.anaTagSpeedata(testTlv, listTlv);
            systemTime = getDateTime();//获取交易时间
            Log.d("ssssssss", "ICExpance2222: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            for (int i = 0; i < listTlv.size(); i++) {
                //判断解析出来的tlv 61目录里是否 是否存在A000000632010105
                if (listTlv.get(i).equals("A000000632010105")) {
                    isFlag = false;
                    String select_ic = "00A4040008" + listTlv.get(i);
                    Log.d(tag, "test: 解析到TLV发送0105 send：" + select_ic);
                    byte[] selectDianziqianbao = DataConversionUtils.HexString2Bytes(select_ic);
                    resultBytes = ICSendAPDU(selectDianziqianbao);//选择电子钱包应用
                    break;
                }
            }

            if (isFlag) {
                Log.d(tag, "test: 默认发送 0105 send ：" + DataConversionUtils.byteArrayToString(ic_file));
                resultBytes = ICSendAPDU(ic_file);//选择电子钱包应用
            }

            if (resultBytes == null) {
                initR6();
                isExpense = 1;
                return;
            }

            Log.d(tag, "test: 0105 return： " + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "===读15文件 === sebd" + DataConversionUtils.byteArrayToString(ic_read_file));
            Log.d("ssssssss", "ICExpance1...@@@@@@@@@@: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            //读应用下公共应用基本信息文件指令
            resultBytes = ICSendAPDU(ic_read_file);
            if (resultBytes == null) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d("ssssssss", "ICExpance1...#######: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            Log.d(tag, "===IC读15文件 === retur:" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                initR6();
                isExpense = 1;
                return;
            }
            //02313750FFFFFFFF0201 031049906000000000062017010820991231000090000000000000000000
            System.arraycopy(resultBytes, 12, cardId, 0, 8);
            System.arraycopy(resultBytes, 0, file15_8, 0, 8);
            System.arraycopy(resultBytes, 2, city, 0, 2);
            Log.d(tag, "===卡应用序列号 ===" + DataConversionUtils.byteArrayToString(cardId));
            Log.d("ssssssss", "ICExpance1...@@@@@@@@@@: " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            //读17文件
            byte[] IC_READ17_FILE = {0x00, (byte) 0xB0, (byte) 0x97, 0x00, 0x00};
            Log.d(tag, "test: 读17文件 00b0 send:" + DataConversionUtils.byteArrayToString(IC_READ17_FILE));
            resultBytes = ICSendAPDU(IC_READ17_FILE);
            if (resultBytes == null) {
                initR6();
                isExpense = 1;
                return;
            }
            Log.d(tag, "===IC读17文件 === return:" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "test: IC读1E文件 00b2 send  00B201F400");
            resultBytes = ICSendAPDU(DataConversionUtils.HexString2Bytes("00B201F400"));
            if (resultBytes == null) {
                initR6();
                isExpense = 1;
                return;
            }
            Log.d(tag, "test: IC读1E文件 00b2 return：" + DataConversionUtils.byteArrayToString(resultBytes) + "\n");
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "===805c IC余额===  send :805C030204");
            resultBytes = ICSendAPDU(DataConversionUtils.HexString2Bytes("805C030204"));
            if (resultBytes == null) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "===IC余额 (805c)===  return  :" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }

            byte[] INIT_IC_FILE = initICcard();
            Log.d(tag, "===IC卡初始化=== 8050 send   :" + DataConversionUtils.byteArrayToString(INIT_IC_FILE));
            resultBytes = ICSendAPDU(INIT_IC_FILE);
            if (resultBytes == null) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "===IC卡初始化=== 8050  return:" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            System.arraycopy(resultBytes, 0, blance, 0, 4);
            System.arraycopy(resultBytes, 4, ATC, 0, 2);
            System.arraycopy(resultBytes, 6, keyVersion, 0, 4);
            flag = resultBytes[10];
            System.arraycopy(resultBytes, 11, rondomCpu, 0, 4);
            Log.d(tag, "===余额:  " + DataConversionUtils.byteArrayToString(blance));
            Log.d(tag, "===CPU卡脱机交易序号:  " + DataConversionUtils.byteArrayToString(ATC));
            Log.d(tag, "===密钥版本 : " + (int) flag);
            Log.d(tag, "===随机数 : " + DataConversionUtils.byteArrayToString(rondomCpu));
            byte[] psam_mac1 = initSamForPurchase();
            Log.d(tag, "===获取MAC1 8070  send===" + DataConversionUtils.byteArrayToString(psam_mac1));
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, psam_mac1);
            if (resultBytes == null) {
                isExpense = 1;
                initR6();
                return;
            }
            byte[] getResponse = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(resultBytes, 1, getResponse, 4, 1);
            Log.d("ssssssss", "******* " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam1, getResponse);

            Log.d(tag, "===获取MAC1 8070 return:" + DataConversionUtils.byteArrayToString(resultBytes) + "   " + resultBytes);
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d("ssssssss", "############# " + (System.currentTimeMillis() - times));
            times = System.currentTimeMillis();
            praseMAC1(resultBytes);
//            //80dc
//            String ss = "80DC00F030060000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
//            Log.d(tag, ss.length() + "===更新1E文件 80dc  send===" + ss);
//            resultBytes = ICSendAPDU(DataConversionUtils.HexString2Bytes(ss));
//            if (resultBytes == null) {
//                isExpense = 1;
//                initR6();
//                return;
//            }
//            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
//                Log.d(tag, "===更新1E文件 return===" + DataConversionUtils.byteArrayToString(resultBytes));
//                cpuAR6Manager.InitDevice();
//                isExpense = 1;
//                initR6();
//                return;
//            }

            byte[] cmd = getIcPurchase();
            Log.d(tag, "===IC卡 8054消费发送===" + DataConversionUtils.byteArrayToString(cmd));
            resultBytes = ICSendAPDU(cmd);
            if (resultBytes == null) {
                isExpense = 1;
                initR6();
                return;
            }
            Log.d(tag, "===IC 卡 8054消费返回===" + DataConversionUtils.byteArrayToString(resultBytes));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                isExpense = 1;
                initR6();
                return;
            }
            byte[] mac2 = cutBytes(resultBytes, 0, 8);
            byte[] PSAM_CHECK_MAC2 = checkPsamMac2(mac2);

//            Log.d(tag, "===psam卡 8072校验 send===: " + DataConversionUtils.byteArrayToString(PSAM_CHECK_MAC2) + "微智结果：" + resultBytes);
//             resultBytes =ICSendAPDU(PSAM_CHECK_MAC2);
//            if (resultBytes == null) {
//                isExpense = 1;
//                initR6();
//                return;
//            }
//            Log.d(tag, "===psam卡 8072校验返回===: " + DataConversionUtils.byteArrayToString(resultBytes));
//            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
//                isExpense = 1;
//                initR6();
//                return;
//            }
            isExpense = 1;
            initR6();
            Log.d("ssssssss", "ICExpance33333: " + (System.currentTimeMillis() - times));
            handler.sendMessage(handler.obtainMessage(1, DataConversionUtils.byteArrayToInt(blance)));
            Log.d("times", "ICExpance:=====  消费完成=======");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            initR6();
            isExpense = 1;
        }
    }


    /**
     * 用户卡(IC卡)交易初始化指令 8050指令
     *
     * @return 8050指令
     */
    private byte[] initICcard() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("805001020B");
        if (CAPP == 1) {
            stringBuilder.replace(5, 6, "3");
        }
        stringBuilder.append(DataConversionUtils.byteArrayToString(psamKey)).append("00000002").append(DataConversionUtils.byteArrayToString(deviceCode)).append("0F");
        return DataConversionUtils.HexString2Bytes(stringBuilder.toString());
    }

    /**
     * PSAM 卡产生MAC1指令 8070
     *
     * @return 返回结果为：XXXXXXXX（终端脱机交易序号）XXXXXXXX（mac1）
     */
    private byte[] initSamForPurchase() {
        byte[] cmd = new byte[42];
        cmd[0] = (byte) 0x80;
        cmd[1] = 0x70;
        cmd[2] = 0x00;
        cmd[3] = 0x00;
        cmd[4] = 0x24;
        System.arraycopy(rondomCpu, 0, cmd, 5, 4);
        System.arraycopy(ATC, 0, cmd, 9, 2);
        //交易金额
        cmd[11] = 0x00;
        cmd[12] = 0x00;
        cmd[13] = 0x00;
        cmd[14] = 0x02;
        if (CAPP == 1) {//是否为复合消费
            cmd[15] = (byte) 0x09;
        } else {
            cmd[15] = (byte) 0x06;
        }
        System.arraycopy(systemTime, 0, cmd, 16, 7);//系统时间
        cmd[23] = 0x01;
        cmd[24] = 0x00;//arithmeticFlog
        System.arraycopy(cardId, 0, cmd, 25, 8);// 8 ->35字节
        System.arraycopy(file15_8, 0, cmd, 33, 8);// 2 ->36字节
        cmd[41] = (byte) 0x08;

        return cmd;
    }

    /**
     * 用户卡扣款指令[终端向用户卡发送] 8054
     *
     * @return 8054指令
     */
    private byte[] getIcPurchase() {
        byte[] cmd = new byte[21];
        cmd[0] = (byte) 0x80;
        cmd[1] = (byte) 0x54;
        cmd[2] = (byte) 0x01;
        cmd[3] = (byte) 0x00;
        cmd[4] = (byte) 0x0F;
        System.arraycopy(psamAtc, 0, cmd, 5, 4);
        //psamAtc 4
        System.arraycopy(systemTime, 0, cmd, 9, 7);//系统时间

        System.arraycopy(mac1, 0, cmd, 16, 4);
        cmd[20] = 0x08;
        return cmd;
    }

    /**
     * PSAM卡校验MAC2指令[终端向PSAM卡发送]：8072 000004 XXXXXXXX（MAC2 用户卡8054指令返回）
     *
     * @param data 8070返回 mac2
     * @return 返回 8072校验mac2
     */
    private byte[] checkPsamMac2(byte[] data) {
        String psam_mac2 = "8072000004" + DataConversionUtils.byteArrayToString(cutBytes(data, 4, 4));
        return DataConversionUtils.HexString2Bytes(psam_mac2);
    }

    /**
     * 获取psam mac1
     *
     * @param data
     */
    private void praseMAC1(byte[] data) {
        if (data.length <= 2) {
            Log.e(tag, "===获取MAC1失败===" + HEX.bytesToHex(data));
            return;
        }
        System.arraycopy(data, 0, psamAtc, 0, 4);
        System.arraycopy(data, 4, mac1, 0, 4);
    }

    /**
     * 获取系统时间
     *
     * @return
     */
    private byte[] getDateTime() {
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");//可以方便地修改日期格式
        String currentTime = dateFormat.format(now);
        // 赋值当前日期和时间
        byte[] nowTimes = DataConversionUtils.HexString2Bytes(currentTime);
        return nowTimes;
    }

    /**
     * 截取数组
     *
     * @param bytes  被截取数组
     * @param start  被截取数组开始截取位置
     * @param length 新数组的长度
     * @return 新数组
     */
    public static byte[] cutBytes(byte[] bytes, int start, int length) {
        byte[] res = new byte[length];
        System.arraycopy(bytes, start, res, 0, length);
        return res;
    }
    //*********************************************mifare卡消费


    private TCommInfo CInfoZ, CInfoF, CInfo;
    private byte[] snUid = null;
    private IcCardBeen icCardBeen = new IcCardBeen();
    private byte RcdBlkIndex[] = {12, 13, 14, 16, 17, 18, 20, 21, 22};//所有“交易记录”块
    private byte[] secF;//扇区标识符
    private byte[][] lodkey = new byte[16][6]; //保存读第0扇区 01块返回的 秘钥
    private byte[] actRemaining;
    private int fErr = -1;
    private byte cardType;

    private void M1ICCard() {
        times = System.currentTimeMillis();
        CInfoF = new TCommInfo();
        CInfoZ = new TCommInfo();
        CInfo = new TCommInfo();
        try {
            Log.d(tag, "M1ICCard:m1卡消费开始 ");
            //读取非接卡 SN(UID)信息
//            resultBytes = mifareR6Manager.SearchCard();
//            resultBytes = mifareR6Manager.SearchCardSak();
//
//            if (resultBytes == null) {
//                isExpense = 1;
//                Log.e(TAG, "M1ICCard: 获取UID失败");
//                return;
//            }
//            cardType = resultBytes[0];
//            snUid = cutBytes(resultBytes, 1, resultBytes.length - 1);
//            snUid = resultBytes;
            if (snUid == null) {
                Log.d(tag, "M1ICCard: getUID==" + HEX.bytesToHex(snUid));
                return;
            }
            icCardBeen.setSnr(snUid);
            Log.d(tag, "M1ICCard: getUID==" + HEX.bytesToHex(snUid));
            byte[] key = new byte[6];
            System.arraycopy(snUid, 0, key, 0, 4);
            System.arraycopy(snUid, 0, key, 4, 2);

            //认证1扇区第4块
            int a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 4, key);
            if (a != 0) {
                Log.e(tag, "M1ICCard: 认证认证1扇区第4块失败");
                isExpense = 1;
                return;
            }
            resultBytes = mifareR6Manager.ReadBlock(4);
            if (resultBytes == null) {
                isExpense = 1;
                Log.e(tag, "M1ICCard: 读取1扇区第4块失败");
                return;
            }
            byte[] bytes04 = resultBytes;
            Log.d(tag, "M1ICCard: 读取1扇区第4块返回：" + HEX.bytesToHex(bytes04));

            icCardBeen.setIssueSnr(cutBytes(bytes04, 0, 8));
            icCardBeen.setCityNr(cutBytes(bytes04, 0, 2));
            icCardBeen.setVocCode(cutBytes(bytes04, 2, 2));
            icCardBeen.setIssueCode(cutBytes(bytes04, 4, 4));
            icCardBeen.setMackNr(cutBytes(bytes04, 8, 4));
            icCardBeen.setfStartUse(cutBytes(bytes04, 12, 1));
            icCardBeen.setCardType(cutBytes(bytes04, 13, 1));//卡类型判断表格中没有return
            icCardBeen.setfBlackCard(0);//黑名单
            switch (icCardBeen.getfStartUse()[0]) {//判断启用标志
                case (byte) 0x01://未启用
                    Log.e(tag, "M1ICCard: 启用标志未启用");
                    isExpense = 1;
                    return;
                case (byte) 0x02://正常
                    // TODO: 2018/8/29
                    break;
                case (byte) 0x03://停用
                    Log.e(tag, "M1ICCard: 启用标志停用");
                    isExpense = 1;
                    return;
                case (byte) 0x04://黑名单
                    Log.e(tag, "M1ICCard: 启用标志黑名单");
                    isExpense = 1;
                    icCardBeen.setfBlackCard(1);
                    return;
                default:
                    break;
            }

            //读1扇区05块数据
            resultBytes = mifareR6Manager.ReadBlock(5);
            if (resultBytes == null) {
                isExpense = 1;
                Log.e(tag, "M1ICCard: 读1扇区05块数据失败");
                return;
            }
            byte[] bytes05 = resultBytes;
            Log.d(tag, "M1ICCard: 读1扇区05块数据：" + HEX.bytesToHex(bytes05));
            icCardBeen.setIssueDate(cutBytes(bytes05, 0, 4));
            icCardBeen.setEndUserDate(cutBytes(bytes05, 4, 4));
            icCardBeen.setStartUserDate(cutBytes(bytes05, 8, 4));

            //读1扇区06块数据
            resultBytes = mifareR6Manager.ReadBlock(6);
            if (resultBytes == null) {
                Log.e(tag, "M1ICCard: 读1扇区06块数据失败");
                isExpense = 1;
                return;
            }
            byte[] bytes06 = resultBytes;
            Log.d(tag, "M1ICCard: 读1扇区06块数据返回：" + HEX.bytesToHex(bytes06));
            icCardBeen.setPurIncUtc(cutBytes(bytes06, 0, 6));//转UTC时间
            icCardBeen.setPurIncMoney(cutBytes(bytes06, 9, 2));

            //第0扇区 01块认证
            a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 1, new byte[]{(byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5});
            if (a != 0) {
                Log.e(tag, "M1ICCard: 第0扇区01块认证失败");
                isExpense = 1;
                return;
            }
            resultBytes = mifareR6Manager.ReadBlock(1);//读第0扇区第一块秘钥
            if (resultBytes == null) {
                Log.e(tag, "M1ICCard: 读第0扇区01块失败");
                isExpense = 1;
                return;
            }

            byte[] bytes01 = resultBytes;
            Log.d(tag, "M1ICCard: 读第0扇区01块：" + HEX.bytesToHex(bytes01));
            //扇区标识符
            secF = bytes01;
            //算秘钥指令
            String sendCmd = "80FC010110" + HEX.bytesToHex(icCardBeen.getCityNr()) + DataConversionUtils.byteArrayToString(icCardBeen.getSnr()) + HEX.bytesToHex(cutBytes(icCardBeen.getIssueSnr(), 6, 2)) + HEX.bytesToHex(icCardBeen.getMackNr())
                    + HEX.bytesToHex(cutBytes(secF, 2, 2)) + HEX.bytesToHex(cutBytes(secF, 6, 2));
            Log.d(tag, "M1ICCard:psam计算秘钥指令 ：" + sendCmd);
            //psam卡计算秘钥
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, DataConversionUtils.HexString2Bytes(sendCmd));
            if (resultBytes == null) {
                Log.e(tag, "M1ICCard: psam计算秘钥指令错误");
                isExpense = 1;
                return;
            }
            if (!Arrays.equals(new byte[]{0x61, 0x18}, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.e(tag, "M1ICCard: psam计算秘钥指令错误");
                isExpense = 1;
                return;
            }

            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(resultBytes, 1, GET_RESPONSE, 4, 1);
            resultBytes = PsamSenAPDU(IPsam.PowerType.Psam2, GET_RESPONSE);
            if (resultBytes == null) {
                Log.e(tag, "错误");
                return;
            }
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(resultBytes, resultBytes.length - 2, 2))) {
                Log.d(tag, " psam计算秘钥指令错误非9000" + DataConversionUtils.byteArrayToString(cutBytes(resultBytes, resultBytes.length - 2, 2)));
                return;
            }
            byte[] result = cutBytes(resultBytes, 0, resultBytes.length - 2);
            Log.d(tag, "M1ICCard: psam计算秘钥返回：" + HEX.bytesToHex(result));
            //3/4/5扇区秘钥相同
            lodkey[2] = cutBytes(result, 0, 6);//第2扇区秘钥
            lodkey[3] = cutBytes(result, 6, 6);//第3扇区秘钥
            lodkey[4] = cutBytes(result, 6, 6);//第4扇区秘钥
            lodkey[5] = cutBytes(result, 6, 6);//第5扇区秘钥
            lodkey[6] = cutBytes(result, 12, 6);//第6扇区秘钥
            lodkey[7] = cutBytes(result, 18, 6);//第7扇区秘钥

            //第6扇区24 块认证
            byte[] lodKey6 = lodkey[6];
            a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 24, lodKey6);
            if (a != 0) {
                Log.e(tag, "M1ICCard: 第6扇区24 块认证错误");
                isExpense = 1;
                return;
            }
            //读6扇区第24块
            resultBytes = mifareR6Manager.ReadBlock(24);
            if (resultBytes == null) {
                Log.e(tag, "M1ICCard: 读6扇区第24块失败");
                isExpense = 1;
                return;
            }
            byte[] bytes24 = resultBytes;

//            System.arraycopy(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00}, 0, bytes24, 0, 8);
//            System.arraycopy(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 0, bytes24, 8, 7);
//            a = mifareR6Manager.WriteBlock(24, bytes24);
//            a =mifareR6Manager.WriteBlock(25, bytes24);
//            resultBytes = mifareR6Manager.ReadBlock(24);
//            if (resultBytes == null) {
//                Log.e(TAG, "M1ICCard: 读6扇区第24块失败");
//                isExpense = 1;
//                return;
//            }
//            bytes24 = resultBytes;

            Log.d(tag, "M1ICCard: 读6扇区第24块返回：" + HEX.bytesToHex(bytes24));
            byte[] dtZ = bytes24;
            byte chk = 0;
            for (int i = 0; i < 16; i++) {//异或操作
                chk ^= dtZ[i];
            }
            //判断8-15是否都等于0xff
            if (Arrays.equals(cutBytes(dtZ, 8, 7),
                    new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}) && chk == 0) {
                CInfoZ.fValid = 1;
            }
            if (Arrays.equals(cutBytes(dtZ, 0, 8), new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff})) {
                CInfoZ.fValid = 0;
            }
            if (dtZ[0] > 8) {
                CInfoZ.fValid = 0;
            }
            CInfoZ.cPtr = dtZ[0];//交易记录指针
            CInfoZ.iPurCount = cutBytes(dtZ, 1, 2);//钱包计数,2,3
            CInfoZ.fProc = dtZ[3];//进程标志
            CInfoZ.iYueCount = cutBytes(dtZ, 4, 2);
            CInfoZ.fBlack = dtZ[6];
            CInfoZ.fFileNr = dtZ[7];
            //副本  有效性
            //读6扇区第25块
            resultBytes = mifareR6Manager.ReadBlock(25);
            if (resultBytes == null) {
                Log.e(tag, "M1ICCard:读6扇区第25块失败 ");
                isExpense = 1;
                return;
            }
            byte[] bytes25 = resultBytes;
            byte[] dtF = bytes25;
            Log.d(tag, "M1ICCard:读6扇区第25块: " + DataConversionUtils.byteArrayToString(bytes25));
            for (int i = 0; i < 16; i++) {
                chk ^= dtF[i];
            }
            if (Arrays.equals(cutBytes(dtF, 8, 7),
                    new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,}) && chk == 0) {
                CInfoF.fValid = 1;
            }
            if (Arrays.equals(cutBytes(dtF, 0, 8), new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff})) {
                CInfoF.fValid = 0;
            }
            if (dtF[0] > 8) {
                CInfoF.fValid = 0;
            }
            CInfoF.cPtr = dtF[0];
            CInfoF.iPurCount = cutBytes(dtF, 1, 2);
            CInfoF.fProc = dtF[3];
            CInfoF.iYueCount = cutBytes(dtF, 4, 2);
            CInfoF.fBlack = dtF[6];
            CInfoF.fFileNr = dtF[7];

            if (CInfoZ.fValid == 1) {
                CInfo = CInfoZ;
            } else if (CInfoF.fValid == 1) {
                CInfo = CInfoF;
            } else {
                Log.e(tag, "M1ICCard: 24 25块有效标志错误 返回0");
                isExpense = 1;
                CInfo = CInfoF;
//                return;
            }

            if ((CInfoZ.fValid == 1 && (CInfoZ.fBlack == 4)) || (CInfoF.fValid == 1 && (CInfoF.fBlack == 4))) {
                icCardBeen.setfBlackCard(1);//黑名单 报语音
                Log.e(tag, "M1ICCard: 黑名单");
                isExpense = 1;
                //語音 黑名單
                return;
            }
            if (!BackupManage(8)) {
                isExpense = 1;
                return;
            }
            if (!writeCardRcd()) {
                isExpense = 1;
                return;
            }
            Log.d("ssssssss", "M1卡time: " + (System.currentTimeMillis() - times));
            handler.sendMessage(handler.obtainMessage(2));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            isExpense = 1;
            return;
        }
    }

    private boolean BackupManage(int blk) {
        //第二扇区08 块认证

        byte[] lodKey2 = lodkey[blk / 4];
        int a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKey2);
        if (a != 0) {
            Log.e(tag, "M1ICCard:认证2扇区第8块失败：");
            isExpense = 1;
            return false;
        }
        //读2扇区第9块
        resultBytes = mifareR6Manager.ReadBlock(9);
        if (resultBytes == null) {
            Log.e(tag, "M1ICCard: 读2扇区第9块失败");
            isExpense = 1;
            return false;
        }
        byte[] bytes09 = resultBytes;
        Log.d(tag, "M1ICCard:读2扇区第9块返回： " + HEX.bytesToHex(bytes09));

        //读2扇区第10块
        //读2扇区第10块
        resultBytes = mifareR6Manager.ReadBlock(10);
        if (resultBytes == null) {
            Log.e(tag, "M1ICCard:读2扇区第10块失败：");
            isExpense = 1;
            return false;
        }
        byte[] bytes10 = resultBytes;
        Log.d(tag, "M1ICCard:读2扇区第10块返回： " + HEX.bytesToHex(bytes10));

        if (ValueBlockValid(bytes09)) {
            Log.d(tag, "M1ICCard: 2区09块过");
            //判断2区9块10块数据是否一致
            if (!Arrays.equals(bytes09, bytes10)) {
                a = mifareR6Manager.restoreBlock(9);
                if (a != 0) {
                    isExpense = 1;
                    Log.e(tag, "M1ICCard:restoreBlock失败");
                }
                a = mifareR6Manager.transferBlock(10);
                if (a != 0) {
                    Log.e(tag, "M1ICCard:transferBlock失败");
                    isExpense = 1;
                }
            }
        } else {
            if (ValueBlockValid(bytes10)) {
                Log.d(tag, "M1ICCard: 2区10块过 ");
                if (!Arrays.equals(bytes10, bytes09)) {
                    bytes09 = bytes10;
                    a = mifareR6Manager.restoreBlock(10);
                    if (a != 0) {
                        Log.e(tag, "M1ICCard:restoreBlock失败");
                        isExpense = 1;
                        return false;
                    }
                    a = mifareR6Manager.transferBlock(9);
                    if (a != 0) {
                        Log.e(tag, "M1ICCard:transferBlock失败");
                        isExpense = 1;
                        return false;
                    }
                }
            } else {
                isExpense = 1;
                Log.d(tag, "M1ICCard: 2区10块错返回 ");
                return false;
            }
        }
        //原额
        byte[] yue09 = cutBytes(bytes09, 0, 4);
        icCardBeen.setPurOriMoney(yue09);
        icCardBeen.setPurSub(new byte[]{0x00, 0x00, 0x00, 0x01});//定义消费金额
        Log.d(tag, "M1ICCard: " + HEX.bytesToHex(yue09));
        return true;
    }

    /**
     * 钱包/月票正本或副本余额有效性检测
     *
     * @param dts
     * @return
     */
    public boolean ValueBlockValid(byte[] dts) {//
        int i;
        for (i = 4; i < 12; i++) {// 钱包/月票原码反码比较
            if (dts[i - 4] != ~dts[i]) {
                return false;// 不相符返回假‘。；
            }
        }
        for (i = 13; i < 16; i++) {// 钱包/月票校验字正反码比较
            if (dts[i - 1] != ~dts[i]) {
                return false;// 不相符返回假
            }
        }
        return true;                                            // 钱包/月票正副本有效，返回真
    }


    public boolean writeCardRcd() {
        //step 0
        CInfo.fFileNr = secF[2];//文件标识
        if (CInfo.cPtr > 8) {
            CInfo.cPtr = 0;
        }
        int blk = RcdBlkIndex[CInfo.cPtr];//当前交易记录块
        Log.d(tag, "writeCardRcd: 当前交易记录块：" + blk);

        CInfo.cPtr = (byte) (CInfo.cPtr == 8 ? 0 : CInfo.cPtr + 1);//
        byte[] ulDevUTC = DataConversionUtils.HexString2Bytes(DataUtils.getUTCtimes());//获取UTC时间
        byte[] RcdToCard = new byte[16]; //写卡指令
        System.arraycopy(ulDevUTC, 0, RcdToCard, 0, 4);
        System.arraycopy(icCardBeen.getPurOriMoney(), 0, RcdToCard, 4, 4);//获取消费前原额
        System.arraycopy(icCardBeen.getPurSub(), 1, RcdToCard, 8, 3);//获取本次消费金额
        RcdToCard[11] = 1;
        //设备号写死
        RcdToCard[12] = 0x64;
        RcdToCard[13] = 0x10;
        RcdToCard[14] = 0x00;
        RcdToCard[15] = 0x01;

        CInfo.fProc = 1;//进程标志
        Log.d(tag, "writeCardRcd: 本次交易记录指令：" + HEX.bytesToHex(RcdToCard));
        int count = DataConversionUtils.byteArrayToInt(CInfo.iPurCount) + 1;
        byte[] result = new byte[2];
        result[0] = (byte) ((count >> 8) & 0xFF);
        result[1] = (byte) (count & 0xFF);
        CInfo.iPurCount = result;

        for (; ; ) {
            //step 1 改写24 25块数据
            if (!Modify_InfoArea(24)) {
                Log.e(tag, "writeCardRcd: 改写24块错误");
                return false;
            }
            //step 2
            byte[] lodKeys = lodkey[blk / 4];
            int a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
            if (a != 0) {
                return false;
            }
            //写卡  将消费记录写入消费记录区
            a = mifareR6Manager.WriteBlock(blk, RcdToCard);

            if (a != 0) {
                Log.e(tag, "writeCardRcd: 将消费记录写入消费记录区错误 块为" + blk);
                return false;
            }
            //消费记录区读取
            resultBytes = mifareR6Manager.ReadBlock(blk);
            if (resultBytes == null) {
                Log.e(tag, "writeCardRcd: 读取消费记录区错误");
                return false;
            }
            byte[] RcdInCard = resultBytes;
            Log.d(tag, "writeCardRcd: 读当前消费记录区数据：" + HEX.bytesToHex(RcdInCard));
            if (!Arrays.equals(RcdInCard, RcdToCard)) {
                Log.e(tag, "writeCardRcd: 读数据不等于消费返回错误");
                return false;
            }
            byte[] bytes = new byte[16];
            if (Arrays.equals(RcdInCard, bytes)) {//判断是否 读回==00
                Log.e(tag, "writeCardRcd: 读数据不等于消费返回0错误");
                return false;
            }

            //step 3
//            PrepareRecord(tCardOpDu.ucSec == 2 ? 1 : 3);   1代表 钱包灰记录 3 月票灰记录
            fErr = 1;
            if (!Modify_InfoArea(25)) {
                Log.e(tag, "writeCardRcd: 改写25块错误");
                return false;             // 改写25块，不成功退出
            }
            //step 4
            //认证2扇区8块
            lodKeys = lodkey[2];
            a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 8, lodKeys);
            if (a != 0) {
                return false;
            }

            //执行消费 将消费金额带入
            int purSub = DataConversionUtils.byteArrayToInt(icCardBeen.getPurSub());
            a = mifareR6Manager.DecrementBlockValue(9, purSub);
            if (a != 0) {
                Log.e(tag, "writeCardRcd: 执行消费错误");
                return false;
            }
            //执行 读出 现在原额
            resultBytes = mifareR6Manager.ReadBlock(9);
            if (resultBytes == null) {
                Log.e(tag, "writeCardRcd: 读原额错误");
                return false;
            }
            byte[] dtZ = resultBytes;
            byte[] tempV = cutBytes(resultBytes, 0, 4);//本次消费后的原额
            Log.d(tag, "writeCardRcd:正本读09块返回：" + HEX.bytesToHex(dtZ));
            //判断消费前金额-消费金额=消费后金额
            if (DataConversionUtils.byteArrayToInt(icCardBeen.getPurOriMoney(), false) - purSub != DataConversionUtils.byteArrayToInt(tempV, false)) {
                return false;
            }
            //step 6 将9块消费后  需要确认
            a = mifareR6Manager.restoreBlock(9);
            if (a != 0) {
                Log.e(tag, "M1ICCard:restoreBlock失败");
                return false;
            }
            a = mifareR6Manager.transferBlock(10);
            if (a != 0) {
                Log.e(tag, "M1ICCard:transferBlock失败");
                return false;
            }

            resultBytes = mifareR6Manager.ReadBlock(10);
            if (resultBytes == null) {
                Log.e(tag, "writeCardRcd: 读10块错误");
                return false;
            }
            byte[] dtF = resultBytes;//第10块数据 （副本）
            Log.d(tag, "writeCardRcd: 副本读10块返回：" + HEX.bytesToHex(dtF));
            if (!Arrays.equals(dtF, dtZ)) {
                Log.d(tag, "writeCardRcd: 正副本判断返回");
                return false;
            }
            //step 7
            CInfo.fProc += 1;
            if (!Modify_InfoArea(24)) {
                Log.e(tag, "writeCardRcd: 改写24错误");
                return false;
            }
            //step 8
            fErr = 0;
            if (!Modify_InfoArea(25)) {
                Log.e(tag, "writeCardRcd: 改写25错误");
                return false;
            }
            break;
        }
        if (fErr == 1) {
            //添加灰记录 报语音请重刷
            return false;
        }
        //添加正常交易记录 报语音显示界面
        return true;
    }

    /**
     * 改写24 25块数据
     *
     * @param blk 块号
     * @return
     */
    private boolean Modify_InfoArea(int blk) {
        byte[] info = new byte[16];
        byte[] tpdt = new byte[16];
        byte chk;
        int i;
        info[0] = CInfo.cPtr;
        System.arraycopy(CInfo.iPurCount, 0, info, 1, 2);
        info[3] = CInfo.fProc;
        System.arraycopy(CInfo.iYueCount, 0, info, 4, 2);
        info[6] = CInfo.fBlack;
        info[7] = CInfo.fFileNr;

        System.arraycopy(new byte[]{(byte) 0xff, (byte) 0xff, (byte)
                0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 0, info, 8, 7);
        for (chk = 0, i = 0; i < 15; i++) {
            chk ^= info[i];
        }
        info[15] = chk;

        //认证6扇区24块
        byte[] lodKeys = lodkey[6];
        int a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
        if (a != 0) {
            Log.e(tag, "Modify_InfoArea: 认证6扇区24块失败");
            return false;
        }
        a = mifareR6Manager.WriteBlock(blk, info);
        if (a != 0) {
            Log.e(tag, "Modify_InfoArea: 写6扇区24块错误");
            return false;
        }
        resultBytes = mifareR6Manager.ReadBlock(blk);
        if (resultBytes == null) {
            Log.e(tag, "Modify_InfoArea: 读6扇区24块错误");
            return false;
        }
        tpdt = resultBytes;
        if (!Arrays.equals(info, tpdt)) {
            Log.e(tag, "Modify_InfoArea: ");
            return false;
        }

        return true;
    }

    public void TEST(int blk) {
        ///////////////////////Start Consuming//////////////////////////
        int fJudge = 0;
        if ((CInfoZ.fValid) == 1 && (CInfoF.fValid) == 1 &&
                ((CInfoZ.fProc & 0x01) == 0) && ((CInfoF.fProc & 0x01) == 0)) {

        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 1) &&
                ((CInfoF.fProc & 0x01) == 0)) {
            if (!Modify_InfoArea(24)) {
                return;
            }
        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 1) &&
                ((CInfoZ.fProc & 0x01) == 1) && ((CInfoF.fProc & 0x01) == 0)) {

            CInfo = CInfoF;
            if (!Modify_InfoArea(24)) {
                return;
            }
        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 0) && ((CInfoZ.fProc & 0x01) == 1)) {
            CInfo.cPtr = (byte) (CInfoZ.cPtr == 0 ? 8 : (CInfoZ.cPtr - 1));
            CInfo.fProc = (byte) (CInfo.fProc + 1);
            if (CInfoZ.fProc == 1) {
                int count = DataConversionUtils.byteArrayToInt(CInfoZ.iPurCount) - 1;
                byte[] result = new byte[2];
                result[0] = (byte) ((count >> 8) & 0xFF);
                result[1] = (byte) (count & 0xFF);
                CInfo.iPurCount = result;

            } else {
                int count = DataConversionUtils.byteArrayToInt(CInfoZ.iYueCount) - 1;
                byte[] result = new byte[2];
                result[0] = (byte) ((count >> 8) & 0xFF);
                result[1] = (byte) (count & 0xFF);
                CInfoZ.iYueCount = result;
            }
            if (!Modify_InfoArea(25)) {
                return;
            }
            if (!Modify_InfoArea(24)) {
                return;
            }
        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 1) && ((CInfoZ.fProc & 0x01) == 1) && ((CInfoF.fProc & 0x01) == 1)) {
            fJudge = 1;
        } else if ((CInfoZ.fValid == 0) && (CInfoF.fValid == 1) && ((CInfoF.fProc & 0x01) == 1)) {
            CInfo.fProc = (byte) (CInfo.fProc + 1);
            if (!Modify_InfoArea(24)) return;
            if (!Modify_InfoArea(25)) return;
        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 1) && ((CInfoZ.fProc & 0x01) == 0) && ((CInfoF.fProc & 0x01) == 1)) {
            if (!Modify_InfoArea(25)) return;
        } else if ((CInfoZ.fValid == 1) && (CInfoF.fValid == 0) && ((CInfoZ.fProc & 0x01) == 0)) {
            if (!Modify_InfoArea(25)) return;
        } else {
            return;
        }
/////////////////////////////////////////////////////////////

        if (fJudge == 1) {
            byte CardRcdDateTime[] = new byte[8];
            byte[] CardRcdOriMoney = new byte[4];
            byte[] CardRcdSub = new byte[4];
            int CardRcdSec = 0;//代表扇区 2为钱包区 7为月票区
            blk = RcdBlkIndex[CInfo.cPtr == 0 ? 8 : CInfo.cPtr - 1];

            byte[] lodKeys = lodkey[blk / 4];
            int a = mifareR6Manager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
            if (a != 0) {
                return;
            }
            resultBytes = mifareR6Manager.ReadBlock(blk);
            if (resultBytes == null) {
                return;
            }
            byte[] RcdInCard = resultBytes;
            //todo utc时间转bcd时间 UTCtoBCDTime(ArrToVar( & RcdInCard[0], 4), CardRcdDateTime);
            byte[] UTCTimes = cutBytes(RcdInCard, 0, 4);
            CardRcdOriMoney = cutBytes(RcdInCard, 4, 4);
            CardRcdSub = cutBytes(RcdInCard, 8, 3);
            if (RcdInCard[11] == 0x02) {
                CardRcdSec = 7;
            } else {
                CardRcdSec = 2;
            }
            if (!BackupManage(CardRcdSec)) { //比对 9 块10块
                return;  //_dt and _backup are all wrong
            }
            //原额倒叙 操作
            actRemaining = icCardBeen.getPurOriMoney();
            if (CardRcdSec == 2) {//Pur{
                icCardBeen.setPurOriMoney(actRemaining);
            }
            if (Arrays.equals(CardRcdOriMoney, icCardBeen.getPurOriMoney())) {
                CInfo.cPtr = (byte) (CInfoZ.cPtr == 0 ? 8 : CInfoZ.cPtr - 1);
                int count = DataConversionUtils.byteArrayToInt(CInfoZ.iPurCount) - 1;
                byte[] result = new byte[2];
                result[0] = (byte) ((count >> 8) & 0xFF);
                result[1] = (byte) (count & 0xFF);
                CInfo.iPurCount = result;
                CInfo.fProc = (byte) (CInfo.fProc + 1);
                if (!Modify_InfoArea(25)) return;
                if (!Modify_InfoArea(24)) return;
            } else if (DataConversionUtils.byteArrayToInt(CardRcdOriMoney) - DataConversionUtils.byteArrayToInt(CardRcdSub) == DataConversionUtils.byteArrayToInt(icCardBeen.getPurOriMoney())) {
                CInfo.fProc = (byte) (CInfo.fProc + 1);
                if (!Modify_InfoArea(24)) return;
                if (!Modify_InfoArea(25)) return;
            } else {
                CInfo.fProc = (byte) (CInfo.fProc + 1);
                if (!Modify_InfoArea(25)) return;
                if (!Modify_InfoArea(24)) return;
            }
        } else {
            CInfo.fProc = (byte) (CInfo.fProc + 1);
            if (!Modify_InfoArea(25)) return;
            if (!Modify_InfoArea(24)) return;
        }

    }


    private HSMDecoder hsmDecoder;
    private HSMDecodeComponent hsmDecodeComponent = null;

    private void initScanBards() {

        hsmDecoder = HSMDecoder.getInstance(this);
        hsmDecoder.addResultListener(this);
        hsmDecoder.enableSymbology(QR);
        hsmDecoder.enableAimer(true);
        hsmDecoder.setAimerColor(Color.RED);
        hsmDecoder.setOverlayText("ceshi");
        hsmDecoder.setOverlayTextColor(Color.RED);
        hsmDecoder.enableSound(true);
        //初始为默认前置摄像头扫码
        hsmDecoder.setActiveCamera(ActiveCamera.REAR_FACING);//后置 摄像头
        CameraManager cameraManager = CameraManager.getInstance(MainActivity.this);
        hsmDecodeComponent = new HSMDecodeComponent(MainActivity.this);
        cameraManager.closeCamera();

        ScanUtils.activateScan(this, new OnBackListener() {
            @Override
            public void onBack() {
                hsmDecoder.enableSymbology(QR);
                hsmDecoder.enableSymbology(QR);
                hsmDecoder.enableSymbology(QR);
                cameraManager.openCamera();
                hsmDecodeComponent.enableScanning(true);
                Toast.makeText(MainActivity.this, "激活成功！", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                cameraManager.openCamera();
                hsmDecodeComponent.enableScanning(true);
                Toast.makeText(MainActivity.this, "激活失败！" + e.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onHSMDecodeResult(HSMDecodeResult[] hsmDecodeResults) {
        try {
            byte[] barcodeDataBytes = hsmDecodeResults[0].getBarcodeDataBytes();
            Log.i("kkkkkk", "onHSMDecodeResult: ");
            String qr = new String(barcodeDataBytes);
            QrEntity qrEntity = new QrEntity(qr);
            try {
                boolean validation = ValidationUtils.validation(qrEntity);
                Logcat.d(validation);
                if (validation) {
                    Toast.makeText(this, "验证通过", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "验证失败", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
