package com.speedata.sc100_r6.spdata.utils;

import android.util.Log;

/**
 * Created by 张明_ on 2018/8/30.
 * Email 741183142@qq.com
 */

public class DataUtils {

    public static String getUTCtimes() {
        long times = System.currentTimeMillis() / 1000L;
        String bytetime = Long.toHexString(times).toUpperCase();
        Log.i("sss", "main: " + bytetime);
        return bytetime;
    }
}
