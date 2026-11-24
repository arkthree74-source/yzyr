package com.dsh.toolbox.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks how far the first-run walkthrough has got, so each screen knows whether
 * it should show its own coach marks. Steps: main intro -> pick platform -> pick
 * courses -> back home -> sync -> done.
 */
public class GuideFlow {

    public static final int OFF = 0;
    public static final int MAIN = 1;
    public static final int PLATFORMS = 2;
    public static final int COURSES = 3;
    public static final int BACK = 4;
    public static final int SYNC = 5;
    /** 已经跳到设置页，等用户把「后台保活」打开。 */
    public static final int SETTINGS = 6;
    public static final int DONE = 9;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("hw", Context.MODE_PRIVATE);
    }

    public static int step(Context c) {
        return prefs(c).getInt("guide_step", OFF);
    }

    public static void set(Context c, int step) {
        prefs(c).edit().putInt("guide_step", step).putBoolean("guide_v1", true).apply();
    }

    public static boolean active(Context c) {
        int s = step(c);
        return s != OFF && s != DONE;
    }
}