package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A Material 3 <b>outlined</b> text field.
 *
 * <p>The frame is a 1dp {@code outline} rounded rect that becomes a 2dp {@code primary}
 * outline on focus, and the label floats up into a notch cut out of the top edge. The
 * notch is a real gap in the outline path, not a patch painted over it, so the field works
 * on any background.</p>
 *
 * <p>Put the input inside it:</p>
 *
 * <pre>
 *   &lt;com.coordcast.castui.CastTextField android:layout_height="56dp"&gt;
 *       &lt;EditText android:id="@+id/input" /&gt;
 *   &lt;/com.coordcast.castui.CastTextField&gt;
 * </pre>
 *
 * <p>then call {@link #setLabel} and read the text with {@link #input()}.</p>
 */
public class CastTextField extends FrameLayout {

    private static final float RADIUS = CastShape.EXTRA_SMALL;

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outlinePath = new Path();
    private final RectF arc = new RectF();

    private EditText input;
    private TextView label;
    private View supporting;

    /** 0 = label resting inside, 1 = label floated onto the border. */
    private float floatProgress;
    private boolean focused;
    private boolean error;
    private CharSequence errorText;

    private ValueAnimator floatAnimator;
    private ValueAnimator colorAnimator;

    public CastTextField(Context context) {
        this(context, null);
    }

    public CastTextField(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        outlinePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof EditText) {
                input = (EditText) getChildAt(i);
                break;
            }
        }
        if (input == null) {
            input = new EditText(getContext());
            addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        // The frame is drawn by this view, so the input itself carries no background.
        input.setBackground(null);
        input.setIncludeFontPadding(false);
        CastType.bodyLarge(input);
        input.setTextColor(CastColor.get().onSurface);
        input.setHintTextColor(CastColor.get().onSurfaceVariant);
        input.setPadding(CastShape.dp(getContext(), 16), CastShape.dp(getContext(), 24),
                CastShape.dp(getContext(), 16), CastShape.dp(getContext(), 8));

        label = new TextView(getContext());
        CastType.bodyLarge(label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        input.setOnFocusChangeListener((v, hasFocus) -> {
            focused = hasFocus;
            updateFloat();
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFloat();
            }
        });
        updateFloat();
    }

    // ------------------------------------------------------------- public API

    /** The label that floats from inside the field up onto the border. */
    public void setLabel(CharSequence text) {
        label.setText(text);
        requestLayout();
        invalidate();
    }

    /** The wrapped input, for text, listeners and input types. */
    public EditText input() {
        return input;
    }

    /** Switches the frame to the M3 error role and shows {@code message} underneath. */
    public void setError(CharSequence message) {
        error = message != null && message.length() > 0;
        errorText = message;
        invalidate();
    }

    // -------------------------------------------------------------- internals

    private boolean shouldFloat() {
        return focused || (input != null && input.getText().length() > 0);
    }

    private void updateFloat() {
        final float target = shouldFloat() ? 1f : 0f;
        if (floatProgress == target) {
            return;
        }
        if (!CastMotion.animationsEnabled(getContext())) {
            floatProgress = target;
            invalidate();
            return;
        }
        if (floatAnimator != null) {
            floatAnimator.cancel();
        }
        floatAnimator = ValueAnimator.ofFloat(floatProgress, target);
        floatAnimator.setDuration(CastMotion.MEDIUM2);
        // Effects spring: a label must never overshoot the border it is landing on.
        floatAnimator.setInterpolator(CastMotion.EFFECTS);
        floatAnimator.addUpdateListener(a -> {
            floatProgress = (float) a.getAnimatedValue();
            positionLabel();
            invalidate();
        });
        floatAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        positionLabel();
    }

    private void positionLabel() {
        if (label == null || input == null) {
            return;
        }
        label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int w = label.getMeasuredWidth();
        int h = label.getMeasuredHeight();
        float restingX = CastShape.dp(getContext(), 16);
        float floatedX = CastShape.dp(getContext(), 12);
        float x = restingX + (floatedX - restingX) * floatProgress;
        float restingY = (getHeight() - h) / 2f;
        float floatedY = -h / 2f;
        float y = restingY + (floatedY - restingY) * floatProgress;
        label.layout(Math.round(x), Math.round(y), Math.round(x) + w, Math.round(y) + h);

        float sizeResting = 16f;
        float sizeFloated = 12f;
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                sizeResting + (sizeFloated - sizeResting) * floatProgress);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Children first (the input and the label), then the outline on top — the label
        // does not need to fight the frame because the frame is notched where it sits.
        super.dispatchDraw(canvas);
        drawOutline(canvas);
    }

    private void drawOutline(Canvas canvas) {
        CastColor scheme = CastColor.get();
        float density = getResources().getDisplayMetrics().density;
        float stroke = (1f + floatProgress) * density;
        int color = error ? scheme.error
                : (focused ? scheme.primary : scheme.outline);
        outlinePaint.setStrokeWidth(Math.max(density, stroke));
        outlinePaint.setColor(color);

        float inset = outlinePaint.getStrokeWidth() / 2f;
        float r = CastShape.dp(getContext(), RADIUS);
        float left = inset;
        float top = inset;
        float right = getWidth() - inset;
        float bottom = getHeight() - inset;

        // Where the top edge is interrupted for the floating label.
        float gapStart = r;
        float gapEnd = r;
        if (floatProgress > 0.01f && label != null && label.getText().length() > 0) {
            float pad = CastShape.dp(getContext(), 4);
            float gs = label.getLeft() - pad;
            float ge = label.getRight() + pad;
            gapStart = Math.max(r, Math.min(gs, (left + right) / 2f));
            gapEnd = Math.min(right - r, Math.max(ge, gapStart));
        }

        outlinePath.reset();
        outlinePath.moveTo(gapEnd, top);
        outlinePath.lineTo(right - r, top);
        arc.set(right - 2 * r, top, right, top + 2 * r);
        outlinePath.arcTo(arc, -90, 90);
        outlinePath.lineTo(right, bottom - r);
        arc.set(right - 2 * r, bottom - 2 * r, right, bottom);
        outlinePath.arcTo(arc, 0, 90);
        outlinePath.lineTo(left + r, bottom);
        arc.set(left, bottom - 2 * r, left + 2 * r, bottom);
        outlinePath.arcTo(arc, 90, 90);
        outlinePath.lineTo(left, top + r);
        arc.set(left, top, left + 2 * r, top + 2 * r);
        outlinePath.arcTo(arc, 180, 90);
        outlinePath.lineTo(gapStart, top);
        canvas.drawPath(outlinePath, outlinePaint);

        label.setTextColor(error ? scheme.error
                : (focused ? scheme.primary : scheme.onSurfaceVariant));
    }
}
