package com.dsh.toolbox.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dsh.toolbox.R;
import com.dsh.toolbox.data.Course;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.Holder> {

    public interface Listener {
        void onToggle(Course course, boolean checked);

        void onOpen(Course course);
    }

    private final List<Course> rows = new ArrayList<>();
    private final Listener listener;

    public CourseAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Course> items) {
        rows.clear();
        if (items != null) rows.addAll(items);
        notifyDataSetChanged();
    }

    public List<Course> items() {
        return rows;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Course c = rows.get(position);
        h.name.setText(c.name);
        String sub = c.subtitle();
        h.subtitle.setText(sub);
        h.subtitle.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        h.term.setText(c.term.isEmpty() ? "—" : c.term);
        h.check.setOnCheckedChangeListener(null);
        h.check.setChecked(c.selected);
        h.check.setOnCheckedChangeListener((b, checked) -> {
            c.selected = checked;
            if (listener != null) listener.onToggle(c, checked);
        });
        h.itemView.setOnClickListener(v -> {
            c.selected = !c.selected;
            h.check.setChecked(c.selected);
        });
        h.open.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(c);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCheckBox check;
        final TextView name;
        final TextView subtitle;
        final TextView term;
        final TextView open;

        Holder(@NonNull View v) {
            super(v);
            check = v.findViewById(R.id.check);
            name = v.findViewById(R.id.name);
            subtitle = v.findViewById(R.id.subtitle);
            term = v.findViewById(R.id.term);
            open = v.findViewById(R.id.open);
        }
    }
}