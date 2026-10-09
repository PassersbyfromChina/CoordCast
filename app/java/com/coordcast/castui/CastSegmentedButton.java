package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A Material 3 single-select segmented button.
 *
 * <p>Equal segments in one rounded container. The selected segment carries a
 * {@code secondaryContainer} pill with a leading check; the rest stay transparent with
 * {@code onSurfaceVariant} labels. Interaction is a state layer, and the pill moves
 * between segments on the spatial spring.</p>
 *
 * <p>Tap a segment to pick it, or drag across the group — the pill follows and the
 * selection changes live, with a tick of haptics at each boundary.</p>
 */
public class CastSegmentedButton extends FrameLayout {

    /** Notified after the user picks a different segment. */
    public interface OnSegmentSelected {
        void onSegmentSelected(int index);
    }

    private final LinearLayout row = new LinearLayout(getContext());
    private final List<TextView> labels = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int selected;
    private float pillLeft;
    private float pillWidth;
    private float stretch;
    private boolean dragging;
    private float downX;
    private float grabOffset;

    private OnSegmentSelected listener;
    private ValueAnimator pillAnimator;
    private ValueAnimator stretchAnimator;

    public CastSegmentedButton(Context context) {
        this(context, null);
    }

    public CastSegmentedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        int height = CastShape.dp(context, 40);
        setMinimumHeight(height);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        setClickable(true);
    }

    // ----------------------------------------------------------- public API

    public void setItems(String[] items, int initialIndex) {
        row.removeAllViews();
        labels.clear();
        for (String item : items) {
            TextView label = new TextView(getContext());
            label.setText(item);
            CastType.labelLarge(label);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setClickable(false);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            labels.add(label);
        }
        selected = items.length == 0 ? 0
                : Math.max(0, Math.min(initialIndex, items.length - 1));
        refreshLabels();
        cancel(pillAnimator);
        pillLeft = targetLeft();
        pillWidth = segmentWidth();
        invalidate();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setOnSegmentSelected(OnSegmentSelected listener) {
        this.listener = listener;
    }

    /** Programmatic selection; {@code notify} decides whether the listener fires. */
    public void select(int index, boolean notify) {
        if (index < 0 || index >= labels.size()) {
            return;
        }
        boolean changed = index != selected;
        selected = index;
        refreshLabels();
        settle();
        if (notify && changed) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) {
                listener.onSegmentSelected(index);
            }
        }
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void onDraw(Canvas canvas) {
        CastColor scheme = CastColor.get();
        float height = getHeight();
        float radius = height * 0.5f;

        // The container outline.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(CastShape.dp(getContext(), 1));
        paint.setColor(scheme.outline);
        float inset = paint.getStrokeWidth() / 2f;
        rect.set(inset, inset, getWidth() - inset, height - inset);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // The selected pill.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scheme.secondaryContainer);
        float left = Math.max(0, pillLeft - stretch);
        float right = Math.min(getWidth(), pillLeft + pillWidth + stretch);
        rect.set(left, 0, right, height);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!dragging && pillAnimator == null) {
            float target = targetLeft();
            if (Math.abs(pillLeft - target) > 0.5f) {
                pillLeft = target;
                pillWidth = segmentWidth();
                invalidate();
            }
        }
    }

    private float segmentWidth() {
        return labels.isEmpty() ? 0 : (float) getWidth() / labels.size();
    }

    private float targetLeft() {
        return selected * segmentWidth();
    }

    // --------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (labels.isEmpty()) {
            return false;
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                dragging = false;
                grabOffset = downX - targetLeft();
                cancel(pillAnimator);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(event.getX() - downX) > slop) {
                    dragging = true;
                    stretch(true);
                }
                if (dragging) {
                    dragTo(event.getX() - grabOffset);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    stretch(false);
                    settle();
                } else {
                    int tapped = indexAt(event.getX());
                    if (tapped == selected) {
                        settle();
                    } else {
                        select(tapped, true);
                    }
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private int indexAt(float x) {
        int width = Math.round(segmentWidth());
        if (width <= 0) {
            return selected;
        }
        return Math.max(0, Math.min((int) (x / width), labels.size() - 1));
    }

    private void dragTo(float newLeft) {
        float width = segmentWidth();
        pillLeft = Math.max(0, Math.min(getWidth() - width, newLeft));
        pillWidth = width;
        int index = indexAt(pillLeft + width / 2f);
        if (index != selected) {
            selected = index;
            refreshLabels();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        invalidate();
    }

    // ------------------------------------------------------------ animation

    private void settle() {
        final float fromLeft = pillLeft;
        final float fromWidth = pillWidth;
        final float toLeft = targetLeft();
        final float toWidth = segmentWidth();
        if (!CastMotion.animationsEnabled(getContext())) {
            pillLeft = toLeft;
            pillWidth = toWidth;
            invalidate();
            return;
        }
        cancel(pillAnimator);
        pillAnimator = ValueAnimator.ofFloat(0f, 1f);
        pillAnimator.setDuration(CastMotion.MEDIUM3);
        // Spatial spring: the pill lands with a touch of weight.
        pillAnimator.setInterpolator(CastMotion.SPATIAL);
        pillAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            pillLeft = fromLeft + (toLeft - fromLeft) * t;
            pillWidth = fromWidth + (toWidth - fromWidth) * t;
            invalidate();
        });
        pillAnimator.start();
    }

    private void stretch(boolean on) {
        if (!CastMotion.animationsEnabled(getContext())) {
            stretch = on ? CastShape.dp(getContext(), 6) : 0f;
            invalidate();
            return;
        }
        cancel(stretchAnimator);
        stretchAnimator = ValueAnimator.ofFloat(stretch, on ? CastShape.dp(getContext(), 6) : 0f);
        stretchAnimator.setDuration(on ? CastMotion.SHORT4 : CastMotion.MEDIUM3);
        stretchAnimator.setInterpolator(on ? CastMotion.PRESS : CastMotion.SPATIAL);
        stretchAnimator.addUpdateListener(a -> {
            stretch = (float) a.getAnimatedValue();
            invalidate();
        });
        stretchAnimator.start();
    }

    private void cancel(ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    private void refreshLabels() {
        CastColor scheme = CastColor.get();
        for (int i = 0; i < labels.size(); i++) {
            TextView label = labels.get(i);
            boolean on = i == selected;
            label.setTextColor(on ? scheme.onSecondaryContainer : scheme.onSurfaceVariant);
            label.setTypeface(android.graphics.Typeface.create("sans-serif",
                    on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            // The selected cue is the pill plus the label weight — M3 does not put a
            // glyph in a segmented button, and a platform checkbox drawable is not an
            // M3 check anyway.
        }
    }
}
