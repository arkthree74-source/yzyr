package com.dsh.toolbox.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.dsh.toolbox.data.Timetable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * WakeUp-style timetable grid: 节次 down the left, 周日..周六 across the top (the
 * 教务系统 also counts Sunday as the first day of a week), one rounded block per course.
 * Only courses that run in {@link #week} are drawn.
 */
public class TimetableGrid extends View {

    /** Column order matches 强智's table: 周日 first, 周六 last. */
    private static final String[] DAY_NAMES = {"日", "一", "二", "三", "四", "五", "六"};
    private static final int[] COLORS = {
            0xFF2F6FED, 0xFF10B981, 0xFFF97316, 0xFF8B5CF6,
            0xFFEC4899, 0xFF0EA5E9, 0xFFF59E0B, 0xFF14B8A6
    };

    private final List<Timetable> items = new ArrayList<>();
    private final java.util.Map<Integer, String> times = new java.util.LinkedHashMap<>();
    private int sections = 10;
    private int week = 0; // 0 = show everything

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headTodayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float headH;
    private float labelW;
    private float rowH;
    private float colW;
    private float density;

    public TimetableGrid(Context context) {
        this(context, null);
    }

    public TimetableGrid(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        headH = dp(30);
        labelW = dp(36);
        rowH = dp(46);
        gridPaint.setColor(0xFFEDEFF4);
        gridPaint.setStrokeWidth(dp(1));
        todayPaint.setColor(0xFFF1F6FF);
        todayEdge.setColor(0xFFDDE8FF);
        todayEdge.setStrokeWidth(dp(1));
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(dp(9.5f));
        namePaint.setFakeBoldText(true);
        metaPaint.setColor(0xD9FFFFFF);
        metaPaint.setTextSize(dp(8));
        headPaint.setColor(0xFF6B7280);
        headPaint.setTextSize(dp(11));
        headPaint.setTextAlign(Paint.Align.CENTER);
        headTodayPaint.setColor(0xFF2F6FED);
        headTodayPaint.setTextSize(dp(11));
        headTodayPaint.setFakeBoldText(true);
        headTodayPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setColor(0xFF9CA3AF);
        numPaint.setTextSize(dp(9.5f));
        numPaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setColor(0xFFB6BCC8);
        timePaint.setTextSize(dp(6.5f));
        timePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<Timetable> list, int currentWeek) {
        items.clear();
        if (list != null) items.addAll(list);
        week = currentWeek;
        int max = 10;
        for (Timetable t : items) {
            if (t.end > 0) max = Math.max(max, t.end);
        }
        sections = Math.min(14, max);
        requestLayout();
        invalidate();
    }

    public int week() {
        return week;
    }

    /** startSection -> "08:00-09:35", drawn under the section number. */
    public void setTimes(java.util.Map<Integer, String> map) {
        times.clear();
        if (map != null) times.putAll(map);
        invalidate();
    }

    private float dp(float v) {
        return v * density;
    }

    /** 强智 lists 周日 first, so 周日 maps to column 0. */
    private static int colOf(int day) {
        if (day < 1 || day > 7) return -1;
        return day % 7;
    }

    private float colLeft(int col) {
        return labelW + colW * col;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) Math.ceil(headH + rowH * sections + dp(6));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        colW = (width - labelW - dp(2)) / 7f;

        int todayCol = colOf(todayIndex());

        // today's column first, so the grid lines sit on top of the tint
        if (todayCol >= 0) {
            rect.set(colLeft(todayCol), headH, colLeft(todayCol) + colW, headH + rowH * sections);
            canvas.drawRect(rect, todayPaint);
        }

        // grid lines
        for (int r = 0; r <= sections; r++) {
            float y = headH + rowH * r;
            canvas.drawLine(labelW, y, labelW + colW * 7, y, gridPaint);
        }
        for (int c = 0; c <= 7; c++) {
            float x = colLeft(c);
            canvas.drawLine(x, headH, x, headH + rowH * sections, gridPaint);
        }

        // header: 日 一 二 三 四 五 六
        for (int c = 0; c < 7; c++) {
            String label = "周" + DAY_NAMES[c];
            float cx = colLeft(c) + colW / 2f;
            TextPaint p = (c == todayCol) ? headTodayPaint : headPaint;
            canvas.drawText(label, cx, headH / 2f + dp(4), p);
        }

        // section numbers (with 上课时间 under the first row of each 大节)
        for (int r = 0; r < sections; r++) {
            String time = times.get(r + 1);
            float cy = headH + rowH * r + rowH / 2f;
            if (time != null && !time.isEmpty()) {
                canvas.drawText(String.valueOf(r + 1), labelW / 2f, cy - dp(1), numPaint);
                canvas.drawText(time, labelW / 2f, cy + dp(9), timePaint);
            } else {
                canvas.drawText(String.valueOf(r + 1), labelW / 2f, cy + dp(3), numPaint);
            }
        }

        // course blocks
        for (Timetable t : items) {
            int col = colOf(t.day);
            if (col < 0) continue;
            if (week > 0 && !t.inWeek(week)) continue;
            float left = colLeft(col) + dp(1.5f);
            float right = colLeft(col) + colW - dp(1.5f);
            float top = headH + rowH * (t.start - 1) + dp(1.5f);
            float bottom = headH + rowH * t.end - dp(1.5f);
            rect.set(left, top, right, bottom);
            blockPaint.setColor(colorOf(t.name));
            canvas.drawRoundRect(rect, dp(6), dp(6), blockPaint);

            float pad = dp(3.5f);
            float innerW = right - left - pad * 2;
            boolean showRoom = (bottom - top) > dp(38) && !t.room.isEmpty();
            float textBottom = bottom - (showRoom ? dp(15) : dp(4));
            drawWrapped(canvas, t.name, left + pad, top + pad + dp(8), innerW,
                    textBottom);

            if (showRoom) {
                String room = t.room;
                while (!room.isEmpty() && metaPaint.measureText(room) > innerW) {
                    room = room.substring(0, room.length() - 1);
                }
                if (!room.isEmpty()) canvas.drawText(room, left + pad, bottom - dp(4.5f), metaPaint);
            }
        }
    }

    /** Draws the course name wrapped by character, one char budget per line, clipped to maxY. */
    private void drawWrapped(Canvas c, String text, float x, float y, float maxW, float maxY) {
        float lineH = dp(10.5f);
        String rest = text == null ? "" : text.replace('\n', ' ').trim();
        int line = 0;
        while (!rest.isEmpty() && (y + dp(1)) <= maxY) {
            int count = namePaint.breakText(rest, true, maxW, null);
            if (count <= 0) break;
            String part = rest.substring(0, count);
            boolean more = count < rest.length();
            if (more && (y + lineH + dp(1)) > maxY) {
                // last visible line: leave room for the ellipsis
                int fit = namePaint.breakText(rest, true, maxW - namePaint.measureText("…"), null);
                if (fit <= 0) fit = Math.max(1, count - 1);
                part = rest.substring(0, fit) + "…";
                c.drawText(part, x, y, namePaint);
                return;
            }
            c.drawText(part, x, y, namePaint);
            rest = rest.substring(count);
            y += lineH;
            line++;
            if (line > 8) break;
        }
    }

    private int colorOf(String name) {
        if (name == null) return COLORS[0];
        int h = Math.abs(name.hashCode());
        return COLORS[h % COLORS.length];
    }

    /** 1 = Monday … 7 = Sunday. */
    private int todayIndex() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // Sunday = 1
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float x = e.getX();
            float y = e.getY();
            if (y > headH && x > labelW && colW > 0) {
                int col = (int) ((x - labelW) / colW);
                int day = col == 0 ? 7 : col;
                int section = (int) ((y - headH) / rowH) + 1;
                for (Timetable t : items) {
                    if (t.day == day && section >= t.start && section <= t.end) {
                        if (week > 0 && !t.inWeek(week)) continue;
                        String msg = t.name
                                + (t.room.isEmpty() ? "" : " · " + t.room)
                                + (t.teacher.isEmpty() ? "" : " · " + t.teacher)
                                + (t.weekLabel().isEmpty() ? "" : " · " + t.weekLabel());
                        android.widget.Toast.makeText(getContext(), msg,
                                android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            }
        }
        return true;
    }
}