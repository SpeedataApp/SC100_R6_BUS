package com.speedata.sc100_r6;

public class HEX {
    private static final char[] a = "0123456789ABCDEF".toCharArray();

    public HEX() {
    }

    public static byte[] hexToBytes(String s) {
        int len = (s = s.toUpperCase()).length() / 2;
        int ii = 0;
        byte[] bs = new byte[len];

        for(int i = 0; i < len; ++i) {
            char c;
            int h;
            if ((c = s.charAt(ii++)) <= '9') {
                h = c - 48;
            } else {
                h = c - 65 + 10;
            }

            h <<= 4;
            if ((c = s.charAt(ii++)) <= '9') {
                h |= c - 48;
            } else {
                h |= c - 65 + 10;
            }

            bs[i] = (byte)h;
        }

        return bs;
    }

    public static String bytesToHex(byte[] bs) {
        char[] cs = new char[bs.length * 2];
        int io = 0;
        byte[] var6 = bs;
        int var5 = bs.length;

        for(int var4 = 0; var4 < var5; ++var4) {
            byte n = var6[var4];
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
        }

        return new String(cs);
    }

    public static String bytesToHex(byte[] bs, int len) {
        char[] cs = new char[len * 2];
        int io = 0;

        for(int i = 0; i < len; ++i) {
            byte n = bs[i];
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
        }

        return new String(cs);
    }

    public static String bytesToHex(byte[] bs, int pos, int len) {
        char[] cs = new char[len * 2];
        int io = 0;

        for(int i = pos; i < pos + len; ++i) {
            byte n = bs[i];
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
        }

        return new String(cs);
    }

    public static String bytesToHex(byte[] bs, char gap) {
        char[] cs = new char[bs.length * 3];
        int io = 0;
        byte[] var7 = bs;
        int var6 = bs.length;

        for(int var5 = 0; var5 < var6; ++var5) {
            byte n = var7[var5];
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
            cs[io++] = gap;
        }

        return new String(cs);
    }

    public static String bytesToHex(byte[] bs, char gap, int len) {
        char[] cs = new char[len * 3];
        int io = 0;

        for(int i = 0; i < len; ++i) {
            byte n = bs[i];
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
            cs[io++] = gap;
        }

        return new String(cs);
    }

    public static String bytesToCppHex(byte[] bs, int bytePerLine) {
        if (bytePerLine <= 0 || bytePerLine >= 65536) {
            bytePerLine = 65536;
        }

        int lines = 0;
        if (bytePerLine < 65536) {
            lines = (bs.length + bytePerLine - 1) / bytePerLine;
        }

        char[] cs = new char[bs.length * 5 + lines * 3];
        int io = 0;
        int ic = 0;
        byte[] var9 = bs;
        int var8 = bs.length;

        for(int var7 = 0; var7 < var8; ++var7) {
            byte n = var9[var7];
            cs[io++] = '0';
            cs[io++] = 'x';
            cs[io++] = a[n >> 4 & 15];
            cs[io++] = a[n >> 0 & 15];
            cs[io++] = ',';
            if (bytePerLine < 65536) {
                ++ic;
                if (ic >= bytePerLine) {
                    ic = 0;
                    cs[io++] = '/';
                    cs[io++] = '/';
                    cs[io++] = '\n';
                }
            }
        }

        if (bytePerLine < 65536 && io < cs.length) {
            cs[io++] = '/';
            cs[io++] = '/';
            cs[io] = '\n';
        }

        return new String(cs);
    }

    public static String toLeHex(int n, int byteCount) {
        char[] rs = new char[byteCount * 2];
        int io = 0;

        for(int i = 0; i < byteCount; ++i) {
            rs[io++] = a[n >> 4 & 15];
            rs[io++] = a[n >> 0 & 15];
            n >>>= 8;
        }

        return new String(rs);
    }

    public static String toBeHex(int n, int byteCount) {
        char[] rs = new char[byteCount * 2];
        int io = 0;
        n <<= 32 - byteCount * 8;

        for(int i = 0; i < byteCount; ++i) {
            rs[io++] = a[n >> 28 & 15];
            rs[io++] = a[n >> 24 & 15];
            n <<= 8;
        }

        return new String(rs);
    }
}
