package com.speedata.sc100_r6.spdata.been;
public class TCommInfo {
    public byte fValid;                                   // 有效标志
    public byte cPtr;                                     // 记录指针,1
    public byte[] iPurCount;                              // 钱包计数,2,3
    public byte fProc;                                    // 进程标志,4
    public byte[] iYueCount;                              // 月票计数,5,6
    public byte fBlack;                                   // 黑名单标志,7
    public byte fFileNr;                                  // 文件标识
    public byte[] UTC;                                    //本次交易时间

}
