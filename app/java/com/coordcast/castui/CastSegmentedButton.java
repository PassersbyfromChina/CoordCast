package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();
    private final RectF rect = new RectF();

    private int selected;
    private float pillLeft;
    private float pillWidth;
    private float stretch;
    private boolean dragging;
    private float downX;
    private float grabOffset;

    /** 0 = no check, 1 = full check. Starts at 1 so the first frame is already correct. */
    private float checkProgress = 1f;
    /** Segment the finger is currently on, or -1. Drives the M3 press state layer. */
    private int pressedIndex = -1;

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
        checkProgress = 1f;
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
        float width = getWidth();
        float radius = height * 0.5f;
        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(density, CastShape.dp(getContext(), 1));
        float inset = stroke / 2f;

        // Never let the pill be invisible: if setItems() ran before the first layout the
        // stored width is still 0, and a segmented button with no visible selection is
        // indistinguishable from an empty track. Work it out on the fly instead.
        float segW = segmentWidth();
        float pillX = pillWidth > 0f ? pillLeft : targetLeft();
        float pillW = pillWidth > 0f ? pillWidth : segW;

        // 1. The selected pill, under everything else.
        if (pillW > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(scheme.secondaryContainer);
            float left = Math.max(0, pillX - stretch);
            float right = Math.min(width, pillX + pillW + stretch);
            rect.set(left, 0, right, height);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        // 2. Dividers between segments. M3 draws a 1dp outline-coloured rule on each
        //    internal boundary, and the selected pill covers the ones it touches — that
        //    is what makes it read as one control rather than a track with a dot on it.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scheme.outline);
        for (int i = 1; i < labels.size(); i++) {
            float x = i * segW;
            boolean coveredByPill = pillW > 0f && x >= (pillX - stretch) && x <= (pillX + pillW + stretch);
            if (coveredByPill) {
                continue;
            }
            canvas.drawRect(x - stroke / 2f, inset + radius * 0.42f,
                    x + stroke / 2f, height - inset - radius * 0.42f, paint);
        }

        // 3. The container outline, on top of both.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(scheme.outline);
        rect.set(inset, inset, width - inset, height - inset);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 4. The press state layer. M3 does not change the fill on press; it lays a 10%
        //    veil of the content colour over whatever is already there.
        if (pressedIndex >= 0 && segW > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CastColor.pressed(scheme.onSurface));
            float l = pressedIndex * segW;
            float r = l + segW;
            boolean first = pressedIndex == 0;
            boolean last = pressedIndex == labels.size() - 1;
            if (first || last) {
                rect.set(first ? 0 : l, 0, last ? width : r, height);
                canvas.drawRoundRect(rect, radius, radius, paint);
                if (first && last) {
                    // one segment only: the whole pill is already rounded
                } else if (first) {
                    canvas.drawRect(l + radius, 0, r, height, paint);
                } else {
                    canvas.drawRect(l, 0, r - radius, height, paint);
                }
            } else {
                canvas.drawRect(l, 0, r, height, paint);
            }
        }

        // 5. The check on the selected segment.
        if (pillW > 0f && checkProgress > 0f) {
            drawCheck(canvas, pillX, pillW, height, scheme.onSecondaryContainer);
        }
    }

    /**
     * The M3 selected cue is a leading check mark. Drawn as a path rather than pulled from
     * {@code android.R.drawable}, which is a platform checkbox glyph and looks nothing
     * like an M3 check.
     */
    private void drawCheck(Canvas canvas, float pillX, float pillW, float height, int color) {
        float d = CastShape.dp(getContext(), 18);
        // Leading edge of the *label*, not of the pill: the label is offset by the same
        // amount when the check is showing, so the two stay concentric.
        float cx = pillX + CastShape.dp(getContext(), 12) + d / 2f;
        float cy = height / 2f;
        float s = d / 2f * checkProgress;
        checkPaint.setColor(color);
        checkPaint.setAlpha(Math.round(255 * Math.min(1f, checkProgress)));
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(CastShape.dp(getContext(), 2));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPath.reset();
        checkPath.moveTo(cx - s * 0.85f, cy + s * 0.05f);
        checkPath.lineTo(cx - s * 0.22f, cy + s * 0.68f);
        checkPath.lineTo(cx + s * 0.88f, cy - s * 0.62f);
        canvas.drawPath(checkPath, checkPaint);
    }

    /** How much room the check takes from the front of a segment's label. */
    private float checkShift() {
        return CastShape.dp(getContext(), 22);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!dragging && pillAnimator == null) {
            float target = targetLeft();
            float w = segmentWidth();
            // Comparing the width as well matters: setItems() runs before the first
            // layout, when getWidth() is still 0, so the pill width it stored is 0 and
            // the selection is never drawn until something else moves it.
            if (Math.abs(pillLeft - target) > 0.5f || Math.abs(pillWidth - w) > 0.5f) {
                pillLeft = target;
                pillWidth = w;
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
                pressedIndex = indexAt(downX);
                cancel(pillAnimator);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(event.getX() - downX) > slop) {
                    dragging = true;
                    stretch(true);
                    pressedIndex = -1;
                }
                if (dragging) {
                    dragTo(event.getX() - grabOffset);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressedIndex = -1;
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
        checkProgress = 1f;
        pressedIndex = -1;
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
            checkProgress = 1f;
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
            // The check fades and grows in behind the pill; the effects spring keeps it
            // from overshooting into a wobble.
            checkProgress = Math.min(1f, t * 1.6f);
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
        float shift = checkShift();
        for (int i = 0; i < labels.size(); i++) {
            TextView label = labels.get(i);
            boolean on = i == selected;
            label.setTextColor(on ? scheme.onSecondaryContainer : scheme.onSurfaceVariant);
            label.setTypeface(android.graphics.Typeface.create("sans-serif",
                    on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            // The check sits in front of the label, so the label slides over by the same
            // amount to keep the pair centred inside the segment.
            label.setTranslationX(on ? shift : 0f);
        }
    }
}
