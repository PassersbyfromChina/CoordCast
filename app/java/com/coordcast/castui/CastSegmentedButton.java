package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
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
 * <p><b>Why this is not a sliding "pill".</b> A single thumb that travels between segments is an
 * iOS idiom, not an M3 one. In the M3 implementation (see
 * {@code androidx.compose.material3.SegmentedButton}) every segment is its own surface with its
 * own container colour: the selected segment simply fills in place, and the only motion is the
 * check fading and scaling in from its own bottom-left corner, plus the label displacing to make
 * room for it. Nothing travels between segments.</p>
 *
 * <p>The sliding version had exactly the problems you would expect from that mismatch: geometry
 * that had to be kept in sync with layout (and was not, so a cold start showed no selection at
 * all), a drag that could silently change the selection, and touch handling that could swallow
 * gestures belonging to a scrolling parent.</p>
 *
 * <p>Tap only. A drag never changes the selection, and vertical drags are left for an ancestor
 * to intercept, so this can live inside a ScrollView.</p>
 */
public class CastSegmentedButton extends FrameLayout {

    /** Notified after the user picks a different segment. */
    public interface OnSegmentSelected {
        void onSegmentSelected(int index);
    }

    /** M3 segmented button height. */
    private static final float HEIGHT_DP = 40f;
    /** M3 check icon size in a segmented button. */
    private static final float ICON_DP = 18f;
    /** Gap between the check and the label. */
    private static final float ICON_GAP_DP = 8f;

    private final LinearLayout row = new LinearLayout(getContext());
    private final List<TextView> labels = new ArrayList<>();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path check = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    private int selected;
    private boolean dragging;

    /** 0 = no check, 1 = check fully in. Drives the check and the label displacement. */
    private float checkProgress = 1f;
    /** Segment the finger is on, or -1. Drives the M3 press state layer. */
    private int pressedIndex = -1;
    private float downX;
    private float downY;

    private OnSegmentSelected listener;
    private ValueAnimator checkAnimator;

    public CastSegmentedButton(Context context) {
        this(context, null);
    }

    public CastSegmentedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        int height = CastShape.dp(context, HEIGHT_DP);
        setMinimumHeight(height);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        setClickable(true);
        setFocusable(true);
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
        checkProgress = 1f;
        refreshLabels();
        updateContentDescription();
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
        updateContentDescription();
        animateCheck();
        if (notify && changed) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) {
                listener.onSegmentSelected(index);
            }
        }
    }

    // ------------------------------------------------------------ geometry

    private int count() {
        return labels.size();
    }

    private float segmentWidth() {
        int n = count();
        return n == 0 ? 0f : (float) getWidth() / n;
    }

    private int indexAt(float x) {
        float w = segmentWidth();
        if (w <= 0f) {
            return selected;
        }
        return Math.max(0, Math.min((int) (x / w), count() - 1));
    }

    /**
     * The rounded rect for one segment. Only the outer ends of the group are rounded; interior
     * edges are square and pushed out by half a stroke so two neighbouring segments draw exactly
     * the same dividing line, instead of two lines a pixel apart.
     */
    private void shapeFor(int i, float stroke, Path out) {
        float segW = segmentWidth();
        float h = getHeight();
        float r = h / 2f;
        float half = stroke / 2f;
        float left = i * segW - (i == 0 ? 0f : half);
        float right = (i + 1) * segW + (i == count() - 1 ? 0f : half);
        rect.set(left + half, half, right - half, h - half);

        float tl = i == 0 ? r : 0f;
        float tr = i == count() - 1 ? r : 0f;
        radii[0] = tl; radii[1] = tl;
        radii[2] = tr; radii[3] = tr;
        radii[4] = tr; radii[5] = tr;
        radii[6] = tl; radii[7] = tl;

        out.reset();
        out.addRoundRect(rect, radii, Path.Direction.CW);
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void onDraw(Canvas canvas) {
        int n = count();
        if (n == 0) {
            return;
        }
        CastColor scheme = CastColor.get();
        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(density, CastShape.dp(getContext(), 1));

        // 1. Fills first, so a neighbour's outline cannot be covered by the selected fill.
        fillPaint.setStyle(Paint.Style.FILL);
        if (selected >= 0 && selected < n) {
            shapeFor(selected, stroke, shape);
            fillPaint.setColor(scheme.secondaryContainer);
            canvas.drawPath(shape, fillPaint);
        }

        // 2. Press state layer: a 10% veil of the content colour, not a different fill.
        if (pressedIndex >= 0 && pressedIndex < n) {
            shapeFor(pressedIndex, stroke, shape);
            fillPaint.setColor(CastColor.pressed(scheme.onSurface));
            canvas.drawPath(shape, fillPaint);
        }

        // 3. Outlines, one per segment. Shared edges land exactly on top of each other.
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setColor(scheme.outline);
        for (int i = 0; i < n; i++) {
            shapeFor(i, stroke, shape);
            canvas.drawPath(shape, strokePaint);
        }

        // 4. The check, scaling in from its bottom-left corner: M3 enters it with
        //    scaleIn(initialScale = 0, transformOrigin = (0, 1)) plus a fade.
        if (checkProgress > 0.001f && selected >= 0 && selected < n) {
            drawCheck(canvas, selected, scheme.onSecondaryContainer, stroke);
        }
    }

    private void drawCheck(Canvas canvas, int index, int color, float stroke) {
        float segW = segmentWidth();
        float d = CastShape.dp(getContext(), ICON_DP);
        float gap = CastShape.dp(getContext(), ICON_GAP_DP);
        TextView label = labels.get(index);

        // Check and label are centred in the segment as one group, which is what the M3
        // measure policy does: group width = icon + gap + label.
        float groupW = d + gap + label.getMeasuredWidth();
        float groupLeft = index * segW + (segW - groupW) / 2f;
        float originX = groupLeft;
        float originY = getHeight() / 2f + d / 2f;

        float p = Math.min(1f, checkProgress);
        int save = canvas.save();
        canvas.scale(p, p, originX, originY);

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(Math.max(CastShape.dp(getContext(), 2), stroke * 2f));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setColor(color);
        checkPaint.setAlpha(Math.round(255 * p));

        float cx = originX + d / 2f;
        float cy = getHeight() / 2f;
        float s = d / 2f;
        check.reset();
        check.moveTo(cx - s * 0.72f, cy + s * 0.04f);
        check.lineTo(cx - s * 0.20f, cy + s * 0.56f);
        check.lineTo(cx + s * 0.72f, cy - s * 0.52f);
        canvas.drawPath(check, checkPaint);
        canvas.restoreToCount(save);
    }

    // --------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (count() == 0) {
            return false;
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                pressedIndex = indexAt(downX);
                invalidate();
                // Deliberately NOT calling requestDisallowInterceptTouchEvent: an ancestor
                // ScrollView stays free to claim a vertical drag and scroll.
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > slop
                        || Math.abs(event.getY() - downY) > slop) {
                    // Too far to be a tap. A drag is not a selection gesture in M3, so this
                    // only cancels the press — it never changes the selection.
                    dragging = true;
                    pressedIndex = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                pressedIndex = -1;
                invalidate();
                if (!dragging) {
                    int tapped = indexAt(event.getX());
                    if (tapped != selected) {
                        select(tapped, true);
                    } else {
                        performClick();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = true;
                pressedIndex = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    // ------------------------------------------------------------ animation

    /** Plays the M3 enter transition for the check when the selection moves. */
    private void animateCheck() {
        if (!CastMotion.animationsEnabled(getContext())) {
            checkProgress = 1f;
            positionLabels();
            invalidate();
            return;
        }
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
        // Start from nothing so the check does not flash at full size for one frame.
        checkProgress = 0f;
        positionLabels();
        invalidate();
        checkAnimator = ValueAnimator.ofFloat(0f, 1f);
        // The icon shares the label's fast spatial spring in M3; the fade rides along.
        checkAnimator.setDuration(CastMotion.MEDIUM1);
        checkAnimator.setInterpolator(CastMotion.SPATIAL_FAST);
        checkAnimator.addUpdateListener(a -> {
            checkProgress = (float) a.getAnimatedValue();
            positionLabels();
            invalidate();
        });
        checkAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        positionLabels();
    }

    /**
     * Displaces the selected label to make room for the check. M3 moves the content by half the
     * icon-plus-gap, because the group is centred rather than left-aligned.
     */
    private void positionLabels() {
        if (labels.isEmpty()) {
            return;
        }
        for (TextView label : labels) {
            label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        }
        float shift = (CastShape.dp(getContext(), ICON_DP)
                + CastShape.dp(getContext(), ICON_GAP_DP)) / 2f;
        for (int i = 0; i < labels.size(); i++) {
            boolean on = i == selected;
            labels.get(i).setTranslationX(on ? shift * Math.min(1f, checkProgress) : 0f);
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
        }
        positionLabels();
    }

    /**
     * A custom view with plain child views exposes one accessibility node, so there is no
     * per-segment semantics here; announcing the current value is the honest minimum. Real
     * per-segment support needs {@code ExploreByTouchHelper}.
     */
    private void updateContentDescription() {
        if (selected >= 0 && selected < labels.size()) {
            setContentDescription(labels.get(selected).getText());
        }
    }
}
