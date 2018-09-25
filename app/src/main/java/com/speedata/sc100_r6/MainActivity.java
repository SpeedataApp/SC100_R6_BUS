package com.speedata.sc100_r6;

import android.os.Bundle;
import android.os.SystemClock;
import android.serialport.SerialPort;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.speedata.libutils.DataConversionUtils;
import com.speedata.r6lib.IMifareManager;
import com.speedata.r6lib.IR6Manager;
import com.speedata.r6lib.R6Manager;
import com.speedata.sc100_r6.spdata.been.IcCardBeen;
import com.speedata.sc100_r6.spdata.been.TCommInfo;
import com.speedata.sc100_r6.spdata.utils.DataUtils;

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

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private String TAG = "sc100R6";
    private IR6Manager mIR6Manager;//定义对象
    private IMifareManager mifareManager;//定义对象
    private IPsam iPsam;
    //选择PPSE支付环境
    private final byte[] fuhe_tlv = {0x00, (byte) 0xA4, 0x04, 0x00, 0x0e, 0x32, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x59, 0x53, 0x2e,
            0x44, 0x44, 0x46, 0x30, 0x31};
    //读ic卡应用下公共应用基本信息文件指令 15文件
    private final byte[] ic_read_file = {0x00, (byte) 0xB0, (byte) 0x95, 0x00, 0x00};
    //选择卡类型为Mifare卡。

    //获取PSAM卡终端机编号指令
    private final byte[] psam1_get_id = {0x00, (byte) 0xB0, (byte) 0x96, 0x00, 0x06};
    //交通部
    private final byte[] psam2_select_dir = {0x00, (byte) 0xA4, 0x00, 0x00, 0x02, (byte) 0x80, 0x11};
    //住建部
    private final byte[] psamzhujian_select_dir = {0x00, (byte) 0xA4, 0x00, 0x00, 0x02, (byte) 0x10, 0x01};
    //读取psam卡17文件
    private final byte[] psam3_get_index = {0x00, (byte) 0xB0, (byte) 0x97, 0x00, 0x01};
    private final byte[] APDU_RESULT_SUCCESS = {(byte) 0x90, 0x00};//返回正确结果
    private final byte[] APDU_RESULT_FAILE = {(byte) 0x62, (byte) 0x83};//返回错误结果
    //选择电子钱包应用
    private final byte[] ic_file = {0x00, (byte) 0xA4, 0x04, 0x00, 0x08, (byte) 0xA0, 0x00, 0x00, 0x06, 0x32, 0x01, 0x01, 0x05};
    private byte flag;
    private byte[] retvalue;
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
    private byte[] PSAM_ATC = new byte[4];
    private byte[] MAC1 = new byte[4];
    /**
     * Hello World!
     */
    private SerialPort serialPort;
    private int fd;
    private TextView tvShowMsg;
    /**
     * psam初始化
     */
    private Button mBtnPsam;
    /**
     * 消费
     */
    private Button mBtnIc;
    /**
     * M1
     */
    private Button mBtnM1;
    /**
     * 复合消费
     */
    private CheckBox mCheckbox;
    private LinearLayout mLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        intDev();
    }

    private void intDev() {
        //选择卡类型为cpuA卡。同理cpuB卡为：CPUB
        mIR6Manager = R6Manager.getInstance(R6Manager.CardType.CPUA);//选择卡类型：CPUA
        mIR6Manager.InitDevice();
        mifareManager = R6Manager.getMifareInstance(R6Manager.CardType.MIFARE);//选择卡类型：15693
        mifareManager.InitDev();
        iPsam = PsamManager.getPsamIntance();
        try {
            iPsam.initDev(this);
            iPsam.resetDev();
            SystemClock.sleep(2500);
        } catch (IOException e) {
            e.printStackTrace();
        }
        psamInit();
        psamM1Init();

        startTimer();
    }


    private byte[] ICSendAPDU(byte[] cmd) {

        byte[] result = mIR6Manager.ExecCmdInput(cmd);//发送指令
        if (result == null) {
            Log.e(TAG, "write指令返回失败失败 , ");
            return null;
        }
        return result;
    }

    private int MifareWriteBlock(int block, byte[] data) {
        int re = mifareManager.WriteBlock(block, data);
        return re;
    }

    private byte[] PsamSenAPDU(IPsam.PowerType types, byte[] cmd) throws UnsupportedEncodingException {
        byte[] re = iPsam.WriteCmd(cmd, types);
        return re;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIR6Manager.ReleaseDevice();
        mifareManager.ReleaseDev();
    }

    private void initView() {
        tvShowMsg = findViewById(R.id.tv_show);
        mBtnPsam = findViewById(R.id.btn_psam);
        mBtnPsam.setOnClickListener(this);
        mBtnIc = findViewById(R.id.btn_ic);
        mBtnIc.setOnClickListener(this);
        mBtnM1 = findViewById(R.id.btn_m1);
        mBtnM1.setOnClickListener(this);
        mCheckbox = findViewById(R.id.checkbox);
        mLayout = findViewById(R.id.layout);
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
    }

    //    private SerialManager mSerialManager;
//    private ByteBuffer mInputBuffer;
//    private ByteBuffer mOutputBuffer;
//    private SerialPort mSerialPort;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
            case R.id.btn_psam:
                psamInit();
                break;
            case R.id.btn_ic:
                ICExpance();
//                retvalue = ICSendAPDU(ic_read_file);
//                if (retvalue==null) {
//                    isExpense = 1;
//                    Log.e(TAG, "消费记录 tlv 3031 error ");
//                    return;
//                }
//                retvalue = ICSendAPDU(fuhe_tlv);
//                if (retvalue==null) {
//                    isExpense = 1;
//                    Log.e(TAG, "消费记录 tlv 3031 error ");
//                    return;
//                }
//

                break;
            case R.id.btn_m1:
//                retvalue = mifareManager.SearchCard();
//                if (retvalue == null) {
//                    return;
//                }
//                byte[] keyData = {(byte) 0x82, (byte) 0xF0, (byte) 0xAC, (byte) 0xB4, 0x20, (byte) 0x8C};
//                int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, retvalue, 8, keyData);
//                if (a != 0) {
//                    return;
//                }
//                retvalue = mifareManager.ReadBlock(9);
//                Log.d(TAG, "onClick: qian " + DataConversionUtils.byteArrayToString(retvalue));
//                a = mifareManager.DecrementBlockValue(9, 2);
//                retvalue = mifareManager.ReadBlock(9);
//
//                Log.d(TAG, "onClick:  hou " + DataConversionUtils.byteArrayToString(retvalue));

                M1ICCard();
                break;
        }
    }

    private Timer timer = new Timer();

    private void startTimer() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (snUid == null) {
                    Log.d("stw", "suid==0");
//                    isExpense = 0;
                    M1ICCard();
                } else {
                    int a = mifareManager.HaltCard();
                    if (a != 0) {
                        return;
                    }
                    a = mifareManager.ActiveCard(snUid);
                    if (a == 0) {
//                        isExpense = 0;
                        Log.d("stw", "卡片在");
                        return;
                    } else {
                        snUid = null;
                        Log.d("stw", "卡片不在");
                        return;
                    }
                }
            }
        }, 10, 100);

    }


    /**
     * psam 初始化流程
     */
    private void psamInit() {
        try {
            retvalue = iPsam.PsamPower(IPsam.PowerType.Psam1);
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "psamInit: psam1 上电失败 ");
                return;
            }
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, psam1_get_id);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:  psam读16文件错误 ");
                isExpense = 1;
                return;
            }
            Log.d(TAG, "交通部16文件：" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(cutBytes(retvalue, retvalue.length - 2, 2), APDU_RESULT_SUCCESS)) {
                Log.e(TAG, "交通部获取终端编号错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            deviceCode = cutBytes(retvalue, 0, retvalue.length - 2);//终端机编号
            tvShowMsg.append("\n交通部PSAM卡终端机编号: " + DataConversionUtils.byteArrayToString(deviceCode));
            Log.d(TAG, "====交通部PSAM卡终端机编号==== " + DataConversionUtils.byteArrayToString(deviceCode));
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, psam2_select_dir);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:psam 8011 错误");
                return;
            }
            Log.d(TAG, "===交通部80 11 ====" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(new byte[]{0x61, 0x1D}, cutBytes(retvalue, retvalue.length - 2, 2))) {
                tvShowMsg.append("\n交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                Log.d(TAG, "交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(retvalue, 1, GET_RESPONSE, 4, 1);
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, GET_RESPONSE);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:psam 8011 错误");
                return;
            }
            Log.d(TAG, "===交通部80 11 ====" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                tvShowMsg.append("\n交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                Log.d(TAG, "交通部切换8011错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            tvShowMsg.append("\n交通部8011return: " + DataConversionUtils.byteArrayToString(retvalue));

            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, psam3_get_index);
            if (retvalue == null) {
                tvShowMsg.append("\n交通部切换00B0错误:" + cutBytes(retvalue, retvalue.length - 2, 2));
                Log.d(TAG, "交通部切换00B0错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
            }
            Log.d(TAG, "===交通部读17文件获取秘钥索引===" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                Log.d(TAG, "交通部获取秘钥索引错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));

            }
            psamKey = cutBytes(retvalue, 0, 1);
            Log.d(TAG, "交通部秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            tvShowMsg.append("\n交通部秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            //切换等待读消费卡
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * psam m1psam初始化
     */
    private void psamM1Init() {
        try {
            retvalue = iPsam.PsamPower(IPsam.PowerType.Psam2);
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "psamInit: psam2 上电失败 ");
                return;
            }
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, psam1_get_id);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:  住建部M1 psam读16文件错误 ");
                isExpense = 1;
                return;
            }
            Log.d(TAG, "住建部M1 16文件：" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(cutBytes(retvalue, retvalue.length - 2, 2), APDU_RESULT_SUCCESS)) {
                Log.e(TAG, "住建部M1获取终端编号错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            deviceCode = cutBytes(retvalue, 0, retvalue.length - 2);//终端机编号
            tvShowMsg.append("\n住建部M1PSAM卡终端机编号: " + DataConversionUtils.byteArrayToString(deviceCode));
            Log.d(TAG, "====住建部M1PSAM卡终端机编号==== " + DataConversionUtils.byteArrayToString(deviceCode));
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, psamzhujian_select_dir);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:psam 1001 错误");
                return;
            }
            Log.d(TAG, "===住建部M110 01 ====" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(new byte[]{0x61, 0x28}, cutBytes(retvalue, retvalue.length - 2, 2))) {
                tvShowMsg.append("\n住建部M1切换10 01错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                Log.d(TAG, "住建部M1切换10 01错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(retvalue, 1, GET_RESPONSE, 4, 1);
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, GET_RESPONSE);
            if (retvalue == null) {
                Log.e(TAG, "psamInit:psam 8011 错误");
                return;
            }
            Log.d(TAG, "===住建部M110 01 ====" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                tvShowMsg.append("\n住建部M1切换10 011错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                Log.d(TAG, "住建部M1切换10 01错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            tvShowMsg.append("\n住建部M110 01return: " + DataConversionUtils.byteArrayToString(retvalue));

            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, psam3_get_index);
            if (retvalue == null) {
                tvShowMsg.append("\n住建部M1切换00B0错误:" + cutBytes(retvalue, retvalue.length - 2, 2));
                Log.d(TAG, "住建部M1切换00B0错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
            }
            Log.d(TAG, "===住建部M1读17文件获取秘钥索引===" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                Log.d(TAG, "住建部M1获取秘钥索引错误:" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));

            }
            psamKey = cutBytes(retvalue, 0, 1);
            Log.d(TAG, "住建部M1秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            tvShowMsg.append("\n住建部M1秘钥索引: " + DataConversionUtils.byteArrayToString(psamKey) + "\n" + "PSAM初始化成功！！！请读消费卡\n");
            //切换等待读消费卡
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    private void ICExpance() {
        try {
            //读卡操作后，可转化为String型便于查看
            byte[] eCard = mIR6Manager.ReadCard();
            if (eCard == null) {
                Log.e(TAG, "writeAndRead读卡失败 , ");
                return;
            }
            Log.d(TAG, "fuhexiaofei: 消费记录 tlv 3031 send " + DataConversionUtils.byteArrayToString(fuhe_tlv));
            retvalue = ICSendAPDU(fuhe_tlv);
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "消费记录 tlv 3031 error ");
                return;
            }
            byte[] testTlv = cutBytes(retvalue, 0, retvalue.length - 2);
            Log.d(TAG, "消费记录 tlv 3031 return :" + DataConversionUtils.byteArrayToString(testTlv));
            tvShowMsg.append("解析 TLV0105 return ：" + DataConversionUtils.byteArrayToString(testTlv) + "\n");
            if (Arrays.equals(cutBytes(retvalue, retvalue.length - 2, 2), APDU_RESULT_FAILE)) {//黑名单
                tvShowMsg.append("黑名单（3031）\n");
            } else if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("切换电子钱包应用失败（0105）\n");
            }

            boolean isFlag = true;
            List<String> listTlv = new ArrayList<>();
            TLV.anaTagSpeedata(testTlv, listTlv);
            for (int i = 0; i < listTlv.size(); i++) {
                Log.d(TAG, "test: 解析TLV" + i + "&&&&&&&" + listTlv.get(i).toString());
                //判断解析出来的tlv 61目录里是否 是否存在A000000632010105
                if (listTlv.get(i).equals("A000000632010105")) {
                    // TODO: 2018/8/17  APDU
                    isFlag = false;
                    systemTime = getDateTime();//获取交易时间
                    String select_ic = "00A4040008" + listTlv.get(i);
                    Log.d(TAG, "test: 解析到TLV发送0105 send：" + select_ic);
                    tvShowMsg.append("解析到TLV发送0105 send： " + select_ic + "\n");
                    byte[] ELECT_DIANZIQIANBAO = DataConversionUtils.HexString2Bytes(select_ic);
                    retvalue = ICSendAPDU(ELECT_DIANZIQIANBAO);//选择电子钱包应用
                    break;
                }
            }

            if (isFlag) {
                systemTime = getDateTime();//获取交易时间
                tvShowMsg.append("默认发送 0105 send： " + DataConversionUtils.byteArrayToString(ic_file) + "\n");
                Log.d(TAG, "test: 默认发送 0105 send ：" + DataConversionUtils.byteArrayToString(ic_file));
                retvalue = ICSendAPDU(ic_file);//选择电子钱包应用
            }
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "test: 0105 return： " + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {

                isExpense = 1;
                tvShowMsg.append("切换电子钱包应用失败（0105）\n");
                return;
            }
            tvShowMsg.append("发送（0105）选电子钱包 返回：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");

            Log.d(TAG, "===读15文件 === sebd" + DataConversionUtils.byteArrayToString(ic_read_file));
            //读应用下公共应用基本信息文件指令
            retvalue = ICSendAPDU(ic_read_file);
            if (retvalue[0] == 1 || retvalue[0] == 3) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===IC读15文件 === retur:" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {

                isExpense = 1;
                tvShowMsg.append("IC读15文件 错误 ：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
                return;
            }
            tvShowMsg.append("IC读15文件 return ：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            //02313750FFFFFFFF0201 031049906000000000062017010820991231000090000000000000000000
            Log.e(TAG, "ICExpance:arraycopy开始 ");
            System.arraycopy(retvalue, 12, cardId, 0, 8);
            Log.e(TAG, "ICExpance:arraycopy 停止");
            System.arraycopy(retvalue, 0, file15_8, 0, 8);
            System.arraycopy(retvalue, 2, city, 0, 2);
            Log.d(TAG, "===卡应用序列号 ===" + DataConversionUtils.byteArrayToString(cardId));

            //读17文件
            byte[] IC_READ17_FILE = {0x00, (byte) 0xB0, (byte) 0x97, 0x00, 0x00};
            Log.d(TAG, "test: 读17文件 00b0 send:" + DataConversionUtils.byteArrayToString(IC_READ17_FILE));
            retvalue = ICSendAPDU(IC_READ17_FILE);
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===IC读17文件 === return:" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC读17文件错误：\n");
                return;
            }
            tvShowMsg.append("IC读17文件返回：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            Log.d(TAG, "test: IC读1E文件 00b2 send  00B201F400");
            retvalue = ICSendAPDU(DataConversionUtils.HexString2Bytes("00B201F400"));
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "test: IC读1E文件 00b2 return：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC读1E文件错误：\n");
                return;
            }
            tvShowMsg.append("IC读1E文件返回：" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)) + "\n");
            Log.d(TAG, "===805c IC余额===  send   :805C030204");
            retvalue = ICSendAPDU(DataConversionUtils.HexString2Bytes("805C030204"));
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===IC余额 (805c)===  return  :" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC卡余额失败：\n");
                return;
            }
            tvShowMsg.append("IC卡余额返回：" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)) + "\n");

            byte[] INIT_IC_FILE = initICcard();
            Log.d(TAG, "===IC卡初始化=== 8050 send   :" + DataConversionUtils.byteArrayToString(INIT_IC_FILE));
            retvalue = ICSendAPDU(INIT_IC_FILE);
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===IC卡初始化=== 8050  return:" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC卡初始化失败:\n");
                return;
            }

            tvShowMsg.append("IC卡初始化返回: " + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)) + "\n");
            System.arraycopy(retvalue, 0, blance, 0, 4);
            System.arraycopy(retvalue, 4, ATC, 0, 2);
            System.arraycopy(retvalue, 6, keyVersion, 0, 4);
            flag = retvalue[10];
            System.arraycopy(retvalue, 11, rondomCpu, 0, 4);
            Log.d(TAG, "===余额:  " + DataConversionUtils.byteArrayToString(blance));
            Log.d(TAG, "===CPU卡脱机交易序号:  " + DataConversionUtils.byteArrayToString(ATC));
            Log.d(TAG, "===密钥版本 : " + (int) flag);
            Log.d(TAG, "===随机数 : " + DataConversionUtils.byteArrayToString(rondomCpu));

            byte[] psam_mac1 = initSamForPurchase();
            Log.d(TAG, "===获取MAC1 8070  send===" + DataConversionUtils.byteArrayToString(psam_mac1));
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, psam_mac1);
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(retvalue, 1, GET_RESPONSE, 4, 1);
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam1, GET_RESPONSE);

            Log.d(TAG, "===获取MAC1 8070 return:" + DataConversionUtils.byteArrayToString(retvalue) + "   " + retvalue);
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC获取MAC1失败:\n");
                return;
            }
            praseMAC1(retvalue);
            tvShowMsg.append("IC获取MAC1返回：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");

            //80dc
            String ss = "80DC00F030060000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            Log.d(TAG, ss.length() + "===更新1E文件 80dc  send===" + ss);
            retvalue = ICSendAPDU(DataConversionUtils.HexString2Bytes(ss));
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===更新1E文件 return===" + DataConversionUtils.byteArrayToString(retvalue));
            tvShowMsg.append("更新1E文件 80dc  return: " + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("更新1E文件 80dc错误：\n");
                return;
            }

            byte[] cmd = getIcPurchase();
            Log.d(TAG, "===IC卡 8054消费发送===" + DataConversionUtils.byteArrayToString(cmd));
            retvalue = ICSendAPDU(cmd);
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===IC 卡 8054消费返回===" + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("IC 卡 8054消费返回失败:\n");
                return;
            }
            tvShowMsg.append("IC 卡 8054消费返回:" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            byte[] mac2 = cutBytes(retvalue, 0, 8);
            byte[] PSAM_CHECK_MAC2 = checkPsamMac2(mac2);


            Log.d(TAG, "===psam卡 8072校验 send===: " + DataConversionUtils.byteArrayToString(PSAM_CHECK_MAC2) + "微智结果：" + retvalue);
//             retvalue =ICSendAPDU(PSAM_CHECK_MAC2);
            if (retvalue == null) {
                isExpense = 1;
                return;
            }
            Log.d(TAG, "===psam卡 8072校验返回===: " + DataConversionUtils.byteArrayToString(retvalue));
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                isExpense = 1;
                tvShowMsg.append("=psam卡 8072校验返回失败：\n");
                return;
            }
            tvShowMsg.append("=psam卡 8072校验返回 ：" + DataConversionUtils.byteArrayToString(retvalue) + "\n");
            isExpense = 1;
            Log.d("times", "ICExpance:=====  消费完成=======");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
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
     * @return 返回结果为：XXXXXXXX（终端脱机交易序号）XXXXXXXX（MAC1）
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
        System.arraycopy(PSAM_ATC, 0, cmd, 5, 4);
        //PSAM_ATC 4
        System.arraycopy(systemTime, 0, cmd, 9, 7);//系统时间
        System.arraycopy(MAC1, 0, cmd, 16, 4);
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
     * 获取psam MAC1
     *
     * @param data
     */
    private void praseMAC1(byte[] data) {
        if (data.length <= 2) {
            Log.e(TAG, "===获取MAC1失败===" + HEX.bytesToHex(data));
            return;
        }
        System.arraycopy(data, 0, PSAM_ATC, 0, 4);
        System.arraycopy(data, 4, MAC1, 0, 4);
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

    private void M1ICCard() {
        CInfoF = new TCommInfo();
        CInfoZ = new TCommInfo();
        CInfo = new TCommInfo();
        try {
            Log.d(TAG, "M1ICCard:m1卡消费开始 ");
            //读取非接卡 SN(UID)信息
            retvalue = mifareManager.SearchCard();
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "M1ICCard: 获取UID失败");
                return;
            }
            snUid = retvalue;
            icCardBeen.setSnr(snUid);
            Log.d(TAG, "M1ICCard: getUID==" + HEX.bytesToHex(snUid));
            byte[] key = new byte[6];
            System.arraycopy(snUid, 0, key, 0, 4);
            System.arraycopy(snUid, 0, key, 4, 2);

            //认证1扇区第4块
            int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 4, key);
            if (a != 0) {
                Log.e(TAG, "M1ICCard: 认证认证1扇区第4块失败");
                isExpense = 1;
                return;
            }
            retvalue = mifareManager.ReadBlock(4);
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "M1ICCard: 读取1扇区第4块失败");
                return;
            }
            byte[] bytes04 = retvalue;
            Log.d(TAG, "M1ICCard: 读取1扇区第4块返回：" + HEX.bytesToHex(bytes04));

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
                    Log.e(TAG, "M1ICCard: 启用标志未启用");
                    isExpense = 1;
                    return;
                case (byte) 0x02://正常
                    // TODO: 2018/8/29
                    break;
                case (byte) 0x03://停用
                    Log.e(TAG, "M1ICCard: 启用标志停用");
                    isExpense = 1;
                    return;
                case (byte) 0x04://黑名单
                    Log.e(TAG, "M1ICCard: 启用标志黑名单");
                    isExpense = 1;
                    icCardBeen.setfBlackCard(1);
                    return;
            }

            //读1扇区05块数据
            retvalue = mifareManager.ReadBlock(5);
            if (retvalue == null) {
                isExpense = 1;
                Log.e(TAG, "M1ICCard: 读1扇区05块数据失败");
                return;
            }
            byte[] bytes05 = retvalue;
            Log.d(TAG, "M1ICCard: 读1扇区05块数据：" + HEX.bytesToHex(bytes05));
            icCardBeen.setIssueDate(cutBytes(bytes05, 0, 4));
            icCardBeen.setEndUserDate(cutBytes(bytes05, 4, 4));
            icCardBeen.setStartUserDate(cutBytes(bytes05, 8, 4));

            //读1扇区06块数据
            retvalue = mifareManager.ReadBlock(6);
            if (retvalue == null) {
                Log.e(TAG, "M1ICCard: 读1扇区06块数据失败");
                isExpense = 1;
                return;
            }
            byte[] bytes06 = retvalue;
            Log.d(TAG, "M1ICCard: 读1扇区06块数据返回：" + HEX.bytesToHex(bytes06));
            icCardBeen.setPurIncUtc(cutBytes(bytes06, 0, 6));//转UTC时间
            icCardBeen.setPurIncMoney(cutBytes(bytes06, 9, 2));

            //第0扇区 01块认证
            a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 1, new byte[]{(byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5});
            if (a != 0) {
                Log.e(TAG, "M1ICCard: 第0扇区01块认证失败");
                isExpense = 1;
                return;
            }
            retvalue = mifareManager.ReadBlock(1);//读第0扇区第一块秘钥
            if (retvalue == null) {
                Log.e(TAG, "M1ICCard: 读第0扇区01块失败");
                isExpense = 1;
                return;
            }

            byte[] bytes01 = retvalue;
            Log.d(TAG, "M1ICCard: 读第0扇区01块：" + HEX.bytesToHex(bytes01));
            //扇区标识符
            secF = bytes01;
            //算秘钥指令
            String sendCmd = "80FC010110" + HEX.bytesToHex(icCardBeen.getCityNr()) + DataConversionUtils.byteArrayToString(icCardBeen.getSnr()) + HEX.bytesToHex(cutBytes(icCardBeen.getIssueSnr(), 6, 2)) + HEX.bytesToHex(icCardBeen.getMackNr())
                    + HEX.bytesToHex(cutBytes(secF, 2, 2)) + HEX.bytesToHex(cutBytes(secF, 6, 2));
            Log.d(TAG, "M1ICCard:psam计算秘钥指令 ：" + sendCmd);
            //psam卡计算秘钥
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, DataConversionUtils.HexString2Bytes(sendCmd));
            if (retvalue == null) {
                Log.e(TAG, "M1ICCard: psam计算秘钥指令错误");
                isExpense = 1;
                return;
            }
            if (!Arrays.equals(new byte[]{0x61, 0x18}, cutBytes(retvalue, retvalue.length - 2, 2))) {
                Log.e(TAG, "M1ICCard: psam计算秘钥指令错误");
                isExpense = 1;
                return;
            }

            byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte) 0xFF};
            System.arraycopy(retvalue, 1, GET_RESPONSE, 4, 1);
            retvalue = PsamSenAPDU(IPsam.PowerType.Psam2, GET_RESPONSE);
            if (retvalue == null) {
                Log.e(TAG, "错误");
                return;
            }
            if (!Arrays.equals(APDU_RESULT_SUCCESS, cutBytes(retvalue, retvalue.length - 2, 2))) {
                Log.d(TAG, " psam计算秘钥指令错误非9000" + DataConversionUtils.byteArrayToString(cutBytes(retvalue, retvalue.length - 2, 2)));
                return;
            }
            byte[] result = cutBytes(retvalue, 0, retvalue.length - 2);
            Log.d(TAG, "M1ICCard: psam计算秘钥返回：" + HEX.bytesToHex(result));
            //3/4/5扇区秘钥相同
            lodkey[2] = cutBytes(result, 0, 6);//第2扇区秘钥
            lodkey[3] = cutBytes(result, 6, 6);//第3扇区秘钥
            lodkey[4] = cutBytes(result, 6, 6);//第4扇区秘钥
            lodkey[5] = cutBytes(result, 6, 6);//第5扇区秘钥
            lodkey[6] = cutBytes(result, 12, 6);//第6扇区秘钥
            lodkey[7] = cutBytes(result, 18, 6);//第7扇区秘钥

            //第6扇区24 块认证
            byte[] lodKey6 = lodkey[6];
            a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 24, lodKey6);
            if (a != 0) {
                Log.e(TAG, "M1ICCard: 第6扇区24 块认证错误");
                isExpense = 1;
                return;
            }
            //读6扇区第24块
            retvalue = mifareManager.ReadBlock(24);
            if (retvalue == null) {
                Log.e(TAG, "M1ICCard: 读6扇区第24块失败");
                isExpense = 1;
                return;
            }
            byte[] bytes24 = retvalue;

//            System.arraycopy(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00}, 0, bytes24, 0, 8);
//            System.arraycopy(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 0, bytes24, 8, 7);
//            a = mifareManager.WriteBlock(24, bytes24);
//            a =mifareManager.WriteBlock(25, bytes24);
//            retvalue = mifareManager.ReadBlock(24);
//            if (retvalue == null) {
//                Log.e(TAG, "M1ICCard: 读6扇区第24块失败");
//                isExpense = 1;
//                return;
//            }
//            bytes24 = retvalue;

            Log.d(TAG, "M1ICCard: 读6扇区第24块返回：" + HEX.bytesToHex(bytes24));
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
            retvalue = mifareManager.ReadBlock(25);
            if (retvalue == null) {
                Log.e(TAG, "M1ICCard:读6扇区第25块失败 ");
                isExpense = 1;
                return;
            }
            byte[] bytes25 = retvalue;
            byte[] dtF = bytes25;
            Log.d(TAG, "M1ICCard:读6扇区第25块: " + DataConversionUtils.byteArrayToString(bytes25));
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
                Log.e(TAG, "M1ICCard: 24 25块有效标志错误 返回0");
                isExpense = 1;
                CInfo = CInfoF;
//                return;
            }

            if ((CInfoZ.fValid == 1 && (CInfoZ.fBlack == 4)) || (CInfoF.fValid == 1 && (CInfoF.fBlack == 4))) {
                icCardBeen.setfBlackCard(1);//黑名单 报语音
                Log.e(TAG, "M1ICCard: 黑名单");
                isExpense = 1;
                return;
            }
            BackupManage(8);
            if (!writeCardRcd()) {
                isExpense = 1;
                return;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private boolean BackupManage(int blk) {
        //第二扇区08 块认证

        byte[] lodKey2 = lodkey[blk / 4];
        int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKey2);
        if (a != 0) {
            Log.e(TAG, "M1ICCard:认证2扇区第8块失败：");
            isExpense = 1;
            return false;
        }
        //读2扇区第9块
        retvalue = mifareManager.ReadBlock(9);
        if (retvalue == null) {
            Log.e(TAG, "M1ICCard: 读2扇区第9块失败");
            isExpense = 1;
            return false;
        }
        byte[] bytes09 = retvalue;
        Log.d(TAG, "M1ICCard:读2扇区第9块返回： " + HEX.bytesToHex(bytes09));

        //读2扇区第10块
        //读2扇区第10块
        retvalue = mifareManager.ReadBlock(10);
        if (retvalue == null) {
            Log.e(TAG, "M1ICCard:读2扇区第10块失败：");
            isExpense = 1;
            return false;
        }
        byte[] bytes10 = retvalue;
        Log.d(TAG, "M1ICCard:读2扇区第10块返回： " + HEX.bytesToHex(bytes10));

        if (ValueBlockValid(bytes09)) {
            Log.d(TAG, "M1ICCard: 2区10块过");
            //判断2区9块10块数据是否一致
            if (!Arrays.equals(bytes09, bytes10)) {
                a = mifareManager.restoreBlock(9);
                if (a != 0) {
                    Log.e(TAG, "M1ICCard:restoreBlock失败");
                }
                a = mifareManager.transferBlock(10);
                if (a != 0) {
                    Log.e(TAG, "M1ICCard:transferBlock失败");
                }
            }
        } else {
            if (ValueBlockValid(bytes10)) {
                Log.d(TAG, "M1ICCard: 2区10块过 ");
                if (!Arrays.equals(bytes10, bytes09)) {
                    bytes09 = bytes10;
                    a = mifareManager.restoreBlock(10);
                    if (a != 0) {
                        Log.e(TAG, "M1ICCard:restoreBlock失败");
                        return false;
                    }
                    a = mifareManager.transferBlock(9);
                    if (a != 0) {
                        Log.e(TAG, "M1ICCard:transferBlock失败");
                        return false;
                    }
                }
            } else {
                isExpense = 1;
                Log.d(TAG, "M1ICCard: 2区10块错返回 ");
                return false;
            }
        }
        byte[] yue09 = cutBytes(bytes09, 0, 4);
        //原额 倒叙
//            actRemaining = ReverseSelf(yue09);
        actRemaining = yue09;
        icCardBeen.setPurOriMoney(actRemaining);
        icCardBeen.setPurSub(new byte[]{0x00, 0x00, 0x00, 0x01});//定义消费金额
        Log.d(TAG, "M1ICCard: " + HEX.bytesToHex(actRemaining));
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
        Log.d(TAG, "writeCardRcd: 当前交易记录块：" + blk);

        CInfo.cPtr = (byte) (CInfo.cPtr == 8 ? 0 : CInfo.cPtr + 1);//
        byte[] ulDevUTC = DataConversionUtils.HexString2Bytes(DataUtils.getUTCtimes());//获取UTC时间
//        if (tCardOpDu.ucSec != 2) {
//            VarToArr( & RcdToCard[4], tCardOpDu.YueOriMoney, 4);
//            VarToArr( & RcdToCard[8], tCardOpDu.YueSub, 3);
//            RcdToCard[11] = 2;
//            CInfo.fProc = 3;
//            CInfo.iYueCount = CInfo.iYueCount + 1;
//        } else {
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
        Log.d(TAG, "writeCardRcd: 本次交易记录指令：" + HEX.bytesToHex(RcdToCard));
        int count = DataConversionUtils.byteArrayToInt(CInfo.iPurCount) + 1;
        byte[] result = new byte[2];
        result[0] = (byte) ((count >> 8) & 0xFF);
        result[1] = (byte) (count & 0xFF);
        CInfo.iPurCount = result;

        for (; ; ) {

            //step 1 改写24 25块数据
            if (!Modify_InfoArea(24)) {
                Log.e(TAG, "writeCardRcd: 改写24块错误");
                return false;
            }
            //step 2
            byte[] lodKeys = lodkey[blk / 4];
            int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
            if (a != 0) {
                return false;
            }
            //写卡  将消费记录写入消费记录区
            a = mifareManager.WriteBlock(blk, RcdToCard);

            if (a != 0) {
                Log.e(TAG, "writeCardRcd: 将消费记录写入消费记录区错误 块为" + blk);
                return false;
            }
            //消费记录区读取
            retvalue = mifareManager.ReadBlock(blk);
            if (retvalue == null) {
                Log.e(TAG, "writeCardRcd: 读取消费记录区错误");
                return false;
            }
            byte[] RcdInCard = retvalue;
            Log.d(TAG, "writeCardRcd: 读当前消费记录区数据：" + HEX.bytesToHex(RcdInCard));
            if (!Arrays.equals(RcdInCard, RcdToCard)) {
                Log.e(TAG, "writeCardRcd: 读数据不等于消费返回错误");
                return false;
            }
            byte[] bytes = new byte[16];
            if (Arrays.equals(RcdInCard, bytes)) {//判断是否 读回==00
                Log.e(TAG, "writeCardRcd: 读数据不等于消费返回0错误");
                return false;
            }

            //step 3
//            PrepareRecord(tCardOpDu.ucSec == 2 ? 1 : 3);   1代表 钱包灰记录 3 月票灰记录
            fErr = 1;
            if (!Modify_InfoArea(25)) {
                Log.e(TAG, "writeCardRcd: 改写25块错误");
                return false;                  // 改写25块，不成功退出
            }
            //step 4
            //认证2扇区8块
            lodKeys = lodkey[2];
            a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, 8, lodKeys);
            if (a != 0) {
                return false;
            }

            //step 5
//            if (tCardOpDu.ucSec != 2) {
//                for (i = 0; i < 4; i++)
//                    dtZ[i] = (tCardOpDu.ActYueSub >> (8 * i));
//            } else {
            byte[] dtZ = icCardBeen.getPurSub();//获取消费金额 倒叙
//            }

            //执行消费 将消费金额带入
            int mon = DataConversionUtils.byteArrayToInt(dtZ);
            a = mifareManager.DecrementBlockValue(9, 1);
            if (a != 0) {
                Log.e(TAG, "writeCardRcd: 执行消费错误");
                return false;
            }
            //执行 读出 现在原额
            retvalue = mifareManager.ReadBlock(9);
            if (retvalue == null) {
                Log.e(TAG, "writeCardRcd: 读原额错误");
                return false;
            }
            dtZ = retvalue;//本次消费后的原额
            Log.d(TAG, "writeCardRcd:正本读09块返回：" + HEX.bytesToHex(dtZ));
            //判断消费前金额-消费金额=消费后金额
//                if (DataConversionUtils.byteArrayToInt(icCardBeen.getPurIncMoney()) - DataConversionUtils.byteArrayToInt(dtZ)
//                        != DataConversionUtils.byteArrayToInt(cutBytes(dtZ, 0, 4))) {
//                    return false;
//                }
            //step 6 将9块消费后  需要确认
            a = mifareManager.restoreBlock(9);
            if (a != 0) {
                Log.e(TAG, "M1ICCard:restoreBlock失败");
            }
            a = mifareManager.transferBlock(10);
            if (a != 0) {
                Log.e(TAG, "M1ICCard:transferBlock失败");
            }
            a = mifareManager.WriteBlock(10, dtZ);
            if (a != 0) {
                Log.e(TAG, "writeCardRcd: 写10块错误");
                return false;
            }
            retvalue = mifareManager.ReadBlock(10);
            if (retvalue == null) {
                Log.e(TAG, "writeCardRcd: 读10块错误");
                return false;
            }
            byte[] dtF = result;//本次消费后的原额
            Log.d(TAG, "writeCardRcd: 副本读10块返回：" + HEX.bytesToHex(dtF));
            if (!Arrays.equals(dtF, dtZ)) {
                Log.d(TAG, "writeCardRcd: 正副本判断返回");
                return false;
            }
            //step 7
            CInfo.fProc += 1;
            if (!Modify_InfoArea(24)) {
                Log.e(TAG, "writeCardRcd: 改写24错误");
                return false;
            }
            //step 8
            fErr = 0;
            if (!Modify_InfoArea(25)) {
                Log.e(TAG, "writeCardRcd: 改写25错误");
                return false;
            }

        }
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
                0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 0, info, 4, 7);
        for (chk = 0, i = 0; i < 15; i++) {
            chk ^= info[i];
        }
        info[15] = chk;

        //认证6扇区24块
        byte[] lodKeys = lodkey[6];
        int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
        if (a != 0) {
            Log.e(TAG, "Modify_InfoArea: 认证6扇区24块失败");
            return false;
        }
        a = mifareManager.WriteBlock(blk, info);
        if (a != 0) {
            Log.e(TAG, "Modify_InfoArea: 写6扇区24块错误");
            return false;
        }
        retvalue = mifareManager.ReadBlock(blk);
        if (retvalue == null) {
            Log.e(TAG, "Modify_InfoArea: 读6扇区24块错误");
            return false;
        }
        tpdt = retvalue;
        if (!Arrays.equals(info, tpdt)) {
            Log.e(TAG, "Modify_InfoArea: ");
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
            int a = mifareManager.AuthenticationCardByKey(AUTH_TYPEA, snUid, blk, lodKeys);
            if (a != 0) {
                return;
            }
            retvalue = mifareManager.ReadBlock(blk);
            if (retvalue == null) {
                return;
            }
            byte[] RcdInCard = retvalue;
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
}
