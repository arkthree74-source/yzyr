package com.dsh.toolbox.data;

import com.dsh.toolbox.R;

public final class Sources {

    public static final String YKT = "ykt";
    public static final String ZHS = "zhs";
    public static final String CX = "cx";
    public static final String JW = "jw";

    public static final class Info {
        public final String key;
        public final String name;
        public final String letter;
        public final String startUrl;
        public final int colorRes;

        Info(String key, String name, String letter, String startUrl, int colorRes) {
            this.key = key;
            this.name = name;
            this.letter = letter;
            this.startUrl = startUrl;
            this.colorRes = colorRes;
        }
    }

    public static final Info[] ALL = {
            new Info(YKT, "雨课堂", "雨", "https://www.yuketang.cn/web", R.color.src_ykt),
            new Info(ZHS, "智慧树", "智", "https://onlineweb.zhihuishu.com/onlinestuh5", R.color.src_zhs),
            new Info(CX, "超星学习通", "学", "https://i.mooc.chaoxing.com/space/index", R.color.src_cx),
            new Info(JW, "教务系统", "课", "https://jwxt.upc.edu.cn/jsxsd/framework/xsMainV.htmlx", R.color.brand)
    };

    public static final String[] ALL_KEYS = {YKT, ZHS, CX, JW};

    private Sources() {
    }

    public static Info byKey(String key) {
        for (Info i : ALL) if (i.key.equals(key)) return i;
        return ALL[0];
    }

    public static String nameOf(String key) {
        return byKey(key).name;
    }

    public static int colorOf(String key) {
        return byKey(key).colorRes;
    }
}