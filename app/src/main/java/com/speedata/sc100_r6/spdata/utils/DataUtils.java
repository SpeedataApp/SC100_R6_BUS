package com.speedata.sc100_r6.spdata.utils;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class DataUtils {

    public static String getUTCtimes() {
        long times = System.currentTimeMillis() / 1000L;
        String bytetime = Long.toHexString(times).toUpperCase();
        Log.i("sss", "main: " + bytetime);
        return bytetime;
    }

    private static String mYear;
    private static String mMonth;
    private static String mDay;
    private static String mWay;
    private static String mHh;
    private static String mMm;

    public static String getNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒 EEEE");
        final Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        mYear = String.valueOf(c.get(Calendar.YEAR)); // 获取当前年份
        mMonth = String.valueOf(c.get(Calendar.MONTH) + 1);// 获取当前月份
        mDay = String.valueOf(c.get(Calendar.DAY_OF_MONTH));// 获取当前月份的日期号码
        mWay = String.valueOf(c.get(Calendar.DAY_OF_WEEK));
        mHh = String.valueOf(c.get(Calendar.HOUR_OF_DAY));
        mMm = String.valueOf(c.get(Calendar.MINUTE));
        if ("1".equals(mWay)) {
            mWay = "天";
        } else if ("2".equals(mWay)) {
            mWay = "一";
        } else if ("3".equals(mWay)) {
            mWay = "二";
        } else if ("4".equals(mWay)) {
            mWay = "三";
        } else if ("5".equals(mWay)) {
            mWay = "四";
        } else if ("6".equals(mWay)) {
            mWay = "五";
        } else if ("7".equals(mWay)) {
            mWay = "六";
        }
        return mYear + "/" + mMonth + "/" + mDay + "    " + "星期" + mWay + "   " + mHh + ":" + mMm;
    }

}
