package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A Material 3 single-select segmented button.
 *
 * <p>Follows {@code androidx.compose.material3.SegmentedButton}: every segment is its own
 * surface with its own container colour, the selected one fills <em>in place</em>, the check
 * enters with {@code fadeIn + scaleIn(initialScale = 0, transformOrigin = (0, 1))} — growing
 * out of its own bottom-left corner — and the label shifts by half the icon-plus-gap because
 * the pair is centred as a group. Nothing travels between segments; a sliding thumb is an iOS
 * idiom, not an M3 one.</p>
 *
 * <p><b>The labels are drawn here rather than being child {@code TextView}s.</b> The earlier
 * version used children and they rendered their text above the segment's centre no matter what
 * gravity they were given, even though a view-hierarchy dump showed them correctly sized and
 * positioned at full segment height. Owning the baseline removes that whole category of
 * problem: the text is placed from {@link Paint.FontMetrics} and cannot disagree with the box
 * it is drawn in.</p>
 *
 * <p>Tap only. A drag never changes the selection, and vertical drags are left for an ancestor
 * to intercept, so this can live inside a ScrollView.</p>
 */
public class CastSegmentedButton extends View {

    /** Notified after the user picks a different segment. */
    public interface OnSegmentSelected {
        void onSegmentSelected(int index);
    }

    private static final float HEIGHT_DP = 40f;
    private static final float ICON_DP = 18f;
    private static final float ICON_GAP_DP = 8f;
    private static final float LABEL_SP = 14f;
    /** M3 label-large tracking, in em (0.1sp at 14sp). */
    private static final float LABEL_TRACKING_EM = 0.1f / 14f;

    private final List<String> items = new ArrayList<>();

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path check = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    private int selected;
    private boolean dragging;
    private float checkProgress = 1f;
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
        setClickable(true);
        setFocusable(true);

        labelPaint.setColor(0xFF9AA0A6);
        labelPaint.setTextSize(sp(context, LABEL_SP));
        labelPaint.setLetterSpacing(LABEL_TRACKING_EM);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        boldPaint.set(labelPaint);
        boldPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private static float sp(Context c, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics());
    }

    // ----------------------------------------------------------- public API

    public void setItems(String[] newItems, int initialIndex) {
        items.clear();
        for (String s : newItems) {
            items.add(s);
        }
        selected = items.isEmpty() ? 0
                : Math.max(0, Math.min(initialIndex, items.size() - 1));
        checkProgress = 1f;
        updateContentDescription();
        requestLayout();
        invalidate();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setOnSegmentSelected(OnSegmentSelected l) {
        this.listener = l;
    }

    /** Programmatic selection; {@code notify} decides whether the listener fires. */
    public void select(int index, boolean notify) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        boolean changed = index != selected;
        selected = index;
        updateContentDescription();
        animateCheck();
        if (notify && changed) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) {
                listener.onSegmentSelected(index);
            }
        }
    }

    // ----------------------------------------------------------- measurement

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int want = Math.max(Math.round(CastShape.dp(getContext(), HEIGHT_DP)),
                getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec),
                resolveSize(want, heightSpec));
    }

    private int count() {
        return items.size();
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
     * The rounded rect for one segment. Only the outer ends are rounded; interior edges are
     * pushed out by half a stroke so two neighbours draw exactly the same dividing line.
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

    /** The icon-plus-gap-plus-label group for a segment, laid out as one centred run. */
    private float groupWidth(int i) {
        Paint p = i == selected ? boldPaint : labelPaint;
        return CastShape.dp(getContext(), ICON_DP) + CastShape.dp(getContext(), ICON_GAP_DP)
                + p.measureText(items.get(i));
    }

    private float groupLeft(int i) {
        float segW = segmentWidth();
        return i * segW + (segW - groupWidth(i)) / 2f;
    }

    /** Baseline that centres the text's ascent/descent box on {@code centreY}. */
    private float baselineFor(Paint p, float centreY) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return centreY - (fm.ascent + fm.descent) / 2f;
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void onDraw(Canvas canvas) {
        int n = count();
        if (n == 0) {
            return;
        }
        CastColor scheme = CastColor.get();
        float stroke = Math.max(getResources().getDisplayMetrics().density,
                CastShape.dp(getContext(), 1));

        // 1. Fill, so a neighbour's outline cannot cover it.
        if (selected >= 0 && selected < n) {
            shapeFor(selected, stroke, shape);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(scheme.secondaryContainer);
            canvas.drawPath(shape, fillPaint);
        }

        // 2. Press state layer: a 10% veil, not a different fill.
        if (pressedIndex >= 0 && pressedIndex < n) {
            shapeFor(pressedIndex, stroke, shape);
            fillPaint.setColor(CastColor.pressed(scheme.onSurface));
            canvas.drawPath(shape, fillPaint);
        }

        // 3. Outlines.
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setColor(scheme.outline);
        for (int i = 0; i < n; i++) {
            shapeFor(i, stroke, shape);
            canvas.drawPath(shape, strokePaint);
        }

        // 4. Labels, then the check in front of the selected one.
        float centreY = getHeight() / 2f;
        for (int i = 0; i < n; i++) {
            boolean on = i == selected;
            Paint p = on ? boldPaint : labelPaint;
            p.setColor(on ? scheme.onSecondaryContainer : scheme.onSurfaceVariant);
            float left = groupLeft(i);
            if (on) {
                left += CastShape.dp(getContext(), ICON_DP) + CastShape.dp(getContext(), ICON_GAP_DP);
            }
            canvas.drawText(items.get(i), left, baselineFor(p, centreY), p);
        }
        if (checkProgress > 0.001f && selected >= 0 && selected < n) {
            drawCheck(canvas, selected, scheme.onSecondaryContainer, stroke, centreY);
        }
    }

    private void drawCheck(Canvas canvas, int index, int color, float stroke, float centreY) {
        float d = CastShape.dp(getContext(), ICON_DP);
        float originX = groupLeft(index);
        float originY = centreY + d / 2f;

        float p = Math.min(1f, checkProgress);
        int save = canvas.save();
        canvas.scale(p, p, originX, originY);

        checkPaint.setStrokeWidth(Math.max(CastShape.dp(getContext(), 2), stroke * 2f));
        checkPaint.setColor(color);
        checkPaint.setAlpha(Math.round(255 * p));

        float cx = originX + d / 2f;
        float s = d / 2f;
        check.reset();
        check.moveTo(cx - s * 0.72f, centreY + s * 0.04f);
        check.lineTo(cx - s * 0.20f, centreY + s * 0.56f);
        check.lineTo(cx + s * 0.72f, centreY - s * 0.52f);
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

    private void animateCheck() {
        if (!CastMotion.animationsEnabled(getContext())) {
            checkProgress = 1f;
            invalidate();
            return;
        }
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
        checkProgress = 0f;
        invalidate();
        checkAnimator = ValueAnimator.ofFloat(0f, 1f);
        checkAnimator.setDuration(CastMotion.MEDIUM1);
        checkAnimator.setInterpolator(CastMotion.SPATIAL_FAST);
        checkAnimator.addUpdateListener(a -> {
            checkProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        checkAnimator.start();
    }

    /**
     * A {@code View} has no per-segment semantics; announcing the current value is the honest
     * minimum. Real per-segment support needs {@code ExploreByTouchHelper}.
     */
    private void updateContentDescription() {
        if (selected >= 0 && selected < items.size()) {
            setContentDescription(items.get(selected));
        }
    }
}
