package com.dsh.toolbox.ui;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dsh.toolbox.R;
import com.dsh.toolbox.data.Homework;
import com.dsh.toolbox.data.Sources;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeworkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onToggle(Homework h);

        void onOpen(Homework h);

        void onDelete(Homework h);
    }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> rows = new ArrayList<>();
    private final Listener listener;

    public HomeworkAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Object> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_SECTION : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION) {
            return new SectionHolder(inflater.inflate(R.layout.item_section, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_homework, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText((String) row);
            return;
        }
        ItemHolder h = (ItemHolder) holder;
        Homework hw = (Homework) row;
        h.bind(hw, listener);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionHolder(View v) {
            super(v);
            title = v.findViewById(R.id.sectionTitle);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        final View stripe;
        final TextView course;
        final TextView source;
        final TextView title;
        final TextView due;
        final TextView remaining;
        final ImageView check;
        final View checkArea;

        ItemHolder(View v) {
            super(v);
            stripe = v.findViewById(R.id.stripe);
            course = v.findViewById(R.id.course);
            source = v.findViewById(R.id.source);
            title = v.findViewById(R.id.title);
            due = v.findViewById(R.id.due);
            remaining = v.findViewById(R.id.remaining);
            check = v.findViewById(R.id.check);
            checkArea = v.findViewById(R.id.checkArea);
        }

        void bind(final Homework hw, final Listener listener) {
            int color = ContextCompat.getColor(itemView.getContext(), Sources.colorOf(hw.source));
            stripe.setBackground(stripe(color));
            source.setText(TextUtils.isEmpty(hw.kind)
                    ? Sources.nameOf(hw.source)
                    : Sources.nameOf(hw.source) + " · " + hw.kind);
            source.setTextColor(color);
            source.setBackground(pill(color));
            course.setText(TextUtils.isEmpty(hw.course) ? Sources.nameOf(hw.source) : hw.course);
            title.setText(hw.title);

            long now = System.currentTimeMillis();
            due.setText(dueLabel(hw.dueAt, now));

            String remain = remainLabel(hw.dueAt, now);
            if (remain == null) {
                remaining.setVisibility(View.GONE);
            } else {
                remaining.setVisibility(View.VISIBLE);
                remaining.setText(remain);
                int rc = remainColor(hw.dueAt, now);
                remaining.setTextColor(rc);
                remaining.setBackground(pill(rc));
            }

            if (hw.done) {
                title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                title.setAlpha(0.45f);
                due.setAlpha(0.45f);
                course.setAlpha(0.45f);
                source.setAlpha(0.45f);
                check.setImageResource(R.drawable.ic_check_circle);
                check.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.brand));
            } else {
                title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                title.setAlpha(1f);
                due.setAlpha(1f);
                course.setAlpha(1f);
                source.setAlpha(1f);
                check.setImageResource(R.drawable.ic_check_circle);
                check.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.text3));
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onOpen(hw);
                }
            });
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    listener.onDelete(hw);
                    return true;
                }
            });
            checkArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onToggle(hw);
                }
            });
        }

        private GradientDrawable pill(int color) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(itemView.getResources().getDisplayMetrics().density * 20f);
            g.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
            return g;
        }

        private GradientDrawable stripe(int color) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(itemView.getResources().getDisplayMetrics().density * 2f);
            g.setColor(color);
            return g;
        }
    }

    public static String dueLabel(long dueAt, long now) {
        if (dueAt <= 0) return "无截止时间";
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(dueAt);
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(now);
        String hm = String.format("%02d:%02d", d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE));
        if (sameDay(d, t)) return "今天 " + hm;
        Calendar tomorrow = (Calendar) t.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(d, tomorrow)) return "明天 " + hm;
        Calendar yesterday = (Calendar) t.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(d, yesterday)) return "昨天 " + hm;
        if (d.get(Calendar.YEAR) != t.get(Calendar.YEAR)) {
            return String.format("%d年%d月%d日 %s", d.get(Calendar.YEAR),
                    d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH), hm);
        }
        return String.format("%d月%d日 %s", d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH), hm);
    }

    public static String remainLabel(long dueAt, long now) {
        if (dueAt <= 0) return null;
        long delta = dueAt - now;
        if (delta < 0) {
            long days = (-delta) / (24L * 3600 * 1000);
            if (days >= 1) return "已逾期 " + days + " 天";
            long hours = (-delta) / (3600L * 1000);
            return hours >= 1 ? "已逾期 " + hours + " 小时" : "已逾期";
        }
        long hours = delta / (3600L * 1000);
        if (hours < 1) return "剩 " + Math.max(1, delta / (60L * 1000)) + " 分钟";
        if (hours < 24) return "剩 " + hours + " 小时";
        return "剩 " + (hours / 24) + " 天";
    }

    public static int remainColor(long dueAt, long now) {
        if (dueAt <= 0) return 0xFF9CA3AF;
        long delta = dueAt - now;
        if (delta < 0) return 0xFFE5484D;
        if (delta < 24L * 3600 * 1000) return 0xFFF59E0B;
        if (delta < 72L * 3600 * 1000) return 0xFFF97316;
        return 0xFF6B7280;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}