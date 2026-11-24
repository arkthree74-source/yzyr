package com.dsh.toolbox.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.dsh.toolbox.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Game-style coach marks: dims the screen, cuts a rounded hole around the target view and
 * shows a small card explaining it. Steps can either be read-only (tap anywhere advances)
 * or "tap-through": touching the highlighted view performs the real click and the run ends,
 * which is how the first-run walkthrough lets people actually do the steps themselves.
 */
public class GuideOverlay extends FrameLayout {

    public static class Item {
        public final View target;
        public final String title;
        public final String body;
        public final boolean tapThrough;

        public Item(View target, String title, String body) {
            this(target, title, body, false);
        }

        public Item(View target, String title, String body, boolean tapThrough) {
            this.target = target;
            this.title = title;
            this.body = body;
            this.tapThrough = tapThrough;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF hole = new RectF();
    private final float density;
    private final int pad;

    private int index;
    private String finishLabel;
    private Runnable onComplete;
    private Runnable onSkip;
    private LinearLayout card;
    private TextView counter;
    private TextView title;
    private TextView body;
    private MaterialButton next;
    private MaterialButton skipBtn;

    private GuideOverlay(Activity activity) {
        super(activity);
        density = activity.getResources().getDisplayMetrics().density;
        pad = dp(7);

        dim.setColor(0xB8000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));
        ring.setColor(ContextCompat.getColor(activity, R.color.brand));

        setWillNotDraw(false);
        setClickable(true);
        setOnClickListener(v -> applyStep(index + 1));
        buildCard(activity);
    }

    public static GuideOverlay show(Activity activity, List<Item> steps, String finishLabel,
                                    Runnable onComplete, Runnable onSkip) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return null;
        GuideOverlay overlay = new GuideOverlay(activity);
        overlay.items.addAll(steps);
        overlay.finishLabel = finishLabel;
        overlay.onComplete = onComplete;
        overlay.onSkip = onSkip;
        root.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.post(() -> overlay.applyStep(0));
        return overlay;
    }

    public static GuideOverlay show(Activity activity, List<Item> steps,
                                    Runnable onComplete, Runnable onSkip) {
        return show(activity, steps, null, onComplete, onSkip);
    }

    private void buildCard(Activity activity) {
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(Color.WHITE, dp(16)));
        card.setElevation(dp(12));
        card.setClickable(true);
        card.setOnClickListener(v -> { /* swallow taps so the step does not skip */ });
        card.setPadding(dp(18), dp(16), dp(18), dp(10));

        counter = new TextView(activity);
        counter.setTextColor(ContextCompat.getColor(activity, R.color.brand));
        counter.setTextSize(12);
        counter.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(counter);

        title = new TextView(activity);
        title.setTextColor(ContextCompat.getColor(activity, R.color.text));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        card.addView(title, tp);

        body = new TextView(activity);
        body.setTextColor(ContextCompat.getColor(activity, R.color.text2));
        body.setTextSize(14);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(6);
        card.addView(body, bp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8);
        card.addView(row, rp);

        skipBtn = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonStyle);
        skipBtn.setText("跳过");
        skipBtn.setTextSize(14);
        skipBtn.setBackgroundColor(Color.TRANSPARENT);
        skipBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        skipBtn.setTextColor(ContextCompat.getColor(activity, R.color.text3));
        skipBtn.setStrokeWidth(0);
        skipBtn.setInsetTop(0);
        skipBtn.setInsetBottom(0);
        skipBtn.setMinWidth(0);
        skipBtn.setMinimumWidth(0);
        skipBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        skipBtn.setOnClickListener(v -> skip());
        row.addView(skipBtn);

        View spacer = new View(activity);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        next = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonStyle);
        next.setText("下一步");
        next.setTextSize(14);
        next.setCornerRadius(dp(12));
        next.setInsetTop(0);
        next.setInsetBottom(0);
        next.setOnClickListener(v -> applyStep(index + 1));
        row.addView(next);

        addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
    }

    private void applyStep(int i) {
        if (i < 0) i = 0;
        if (i >= items.size()) {
            complete();
            return;
        }
        index = i;
        Item item = items.get(i);

        Rect r = new Rect();
        if (item.target != null && item.target.isShown()
                && item.target.getWidth() > 0 && item.target.getHeight() > 0) {
            int[] loc = new int[2];
            item.target.getLocationOnScreen(loc);
            r.set(loc[0], loc[1], loc[0] + item.target.getWidth(), loc[1] + item.target.getHeight());
            int[] self = new int[2];
            getLocationOnScreen(self);
            r.offset(-self[0], -self[1]);
            hole.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad);
        } else {
            hole.set(-1, -1, -1, -1);
        }

        // A tap-through step lets the highlighted view be the button; the card only explains it
        // and offers an escape hatch, so the instruction text is never hidden.
        card.setVisibility(VISIBLE);
        if (item.tapThrough) {
            counter.setText("");
            title.setText(item.title);
            body.setText(item.body);
            skipBtn.setVisibility(GONE);
            next.setText("跳过这步");
            next.setOnClickListener(v -> skip());
        } else {
            skipBtn.setVisibility(VISIBLE);
            next.setOnClickListener(v -> applyStep(index + 1));
            counter.setText(finishLabel != null ? "" : ((i + 1) + " / " + items.size()));
            title.setText(item.title);
            body.setText(item.body);
            if (i == items.size() - 1) {
                next.setText(finishLabel != null ? finishLabel : "开始用吧");
            } else {
                next.setText("下一步");
            }
        }
        if (getWidth() > 0) {
            positionCard();
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN && index < items.size()) {
            Item item = items.get(index);
            if (item.tapThrough && item.target != null
                    && !hole.isEmpty() && hole.contains(e.getX(), e.getY())) {
                final View target = item.target;
                Runnable cb = onComplete;
                onComplete = null;
                onSkip = null;
                dismiss();
                if (cb != null) cb.run();
                target.postDelayed(target::performClick, 220);
                return true;
            }
        }
        // Swallow everything else: while coach marks are up the page underneath must not
        // react to stray taps, otherwise people end up on the wrong screen.
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (items.isEmpty() || w == 0 || h == 0) return;
        if (hole.isEmpty() || hole.left < 0) applyStep(index);
        else positionCard();
    }

    private void positionCard() {
        int margin = dp(18);
        int cardWidth = Math.min(getWidth() - margin * 2, dp(320));
        if (cardWidth <= 0) return;
        card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int cardHeight = card.getMeasuredHeight();
        int gap = dp(14);

        int top;
        if (hole.isEmpty() || hole.left < 0) {
            top = (getHeight() - cardHeight) / 2;
        } else {
            int below = (int) hole.bottom + gap;
            if (below + cardHeight + margin <= getHeight()) {
                top = below;
            } else {
                top = (int) hole.top - gap - cardHeight;
            }
        }
        top = Math.max(margin, Math.min(top, getHeight() - cardHeight - margin));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.width = cardWidth;
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        lp.leftMargin = (getWidth() - cardWidth) / 2;
        lp.topMargin = top;
        card.setLayoutParams(lp);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        shape.reset();
        shape.addRect(0, 0, w, h, Path.Direction.CW);
        boolean hasHole = !hole.isEmpty() && hole.left >= 0;
        if (hasHole) {
            shape.addRoundRect(hole, dp(18), dp(18), Path.Direction.CW);
            shape.setFillType(Path.FillType.EVEN_ODD);
        }
        canvas.drawPath(shape, dim);

        if (hasHole) {
            canvas.drawRoundRect(hole, dp(18), dp(18), ring);
        }
    }

    private void complete() {
        Runnable cb = onComplete;
        onComplete = null;
        onSkip = null;
        dismiss();
        if (cb != null) cb.run();
    }

    private void skip() {
        Runnable cb = onSkip;
        onComplete = null;
        onSkip = null;
        dismiss();
        if (cb != null) cb.run();
    }

    private void dismiss() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}