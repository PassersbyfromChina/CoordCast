package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * A Material 3 button.
 *
 * <p>Five variants, one shape (stadium), one height (40dp), one label role (Label Large).
 * The variant decides the resting fill and its on-colour; interaction is always a
 * <b>state layer</b> — a translucent veil of the content colour — never a fill swap.</p>
 *
 * <p>Expressive touch: the corners open up while the button is held.</p>
 */
public class CastButton extends TextView {

    public static final int FILLED = 0;
    public static final int TONAL = 1;
    public static final int ELEVATED = 2;
    public static final int OUTLINED = 3;
    public static final int TEXT = 4;

    private static final float REST_RADIUS = CastShape.FULL;
    private static final float PRESSED_RADIUS = CastShape.MEDIUM;

    private int variant = FILLED;
    private int contentColor;
    private GradientDrawable fillShape;
    private GradientDrawable maskShape;
    private RippleDrawable background;

    public CastButton(Context context) {
        this(context, null);
    }

    public CastButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setClickable(true);
        setFocusable(true);
        setAllCaps(false);
        setIncludeFontPadding(false);
        CastType.labelLarge(this);
        int height = CastShape.dp(context, 40);
        setMinHeight(height);
        setMinimumHeight(height);
        setPadding(CastShape.dp(context, 24), 0, CastShape.dp(context, 24), 0);
        apply();
    }

    /** One of {@link #FILLED}, {@link #TONAL}, {@link #ELEVATED}, {@link #OUTLINED}, {@link #TEXT}. */
    public void setVariant(int variant) {
        this.variant = variant;
        apply();
    }

    public int getVariant() {
        return variant;
    }

    private void apply() {
        CastColor scheme = CastColor.get();
        int fill;
        int strokeWidth = 0;
        int strokeColor = 0;
        switch (variant) {
            case TONAL:
                fill = scheme.secondaryContainer;
                contentColor = scheme.onSecondaryContainer;
                break;
            case ELEVATED:
                fill = scheme.surfaceContainerLow;
                contentColor = scheme.primary;
                break;
            case OUTLINED:
                fill = 0x00000000;
                contentColor = scheme.primary;
                strokeWidth = 1;
                strokeColor = scheme.outline;
                break;
            case TEXT:
                fill = 0x00000000;
                contentColor = scheme.primary;
                break;
            case FILLED:
            default:
                fill = scheme.primary;
                contentColor = scheme.onPrimary;
                break;
        }
        setTextColor(contentColor);
        if (getCompoundDrawablesRelative()[0] != null) {
            setCompoundDrawableTintList(ColorStateList.valueOf(contentColor));
        }

        fillShape = CastShape.filledOutlined(getContext(), fill, strokeColor, strokeWidth,
                CastShape.FULL);
        maskShape = CastShape.outlined(getContext(), 0xFFFFFFFF, 0, CastShape.FULL);
        applyStadium();
        // The state layer is what M3 uses instead of changing the fill.
        background = new RippleDrawable(ColorStateList.valueOf(CastColor.pressed(contentColor)),
                fillShape, maskShape);
        setBackground(background);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyStadium();
    }

    private void applyStadium() {
        float radius = Math.min(getWidth(), getHeight()) * 0.5f;
        if (fillShape != null) {
            fillShape.setCornerRadius(radius);
        }
        if (maskShape != null) {
            maskShape.setCornerRadius(radius);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                morph(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                morph(false);
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /** Expressive shape morph: the corners relax while the button is held. */
    private void morph(boolean pressed) {
        if (fillShape == null || !isClickable()) {
            return;
        }
        float height = getHeight();
        float restRadius = height * 0.5f;
        float pressedRadius = CastShape.dp(getContext(), PRESSED_RADIUS);
        final float to = pressed ? pressedRadius : restRadius;
        final float from = pressed ? restRadius : pressedRadius;
        if (!CastMotion.animationsEnabled(getContext())) {
            fillShape.setCornerRadius(to);
            maskShape.setCornerRadius(to);
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(pressed ? CastMotion.SHORT4 : CastMotion.MEDIUM3);
        animator.setInterpolator(pressed ? CastMotion.PRESS : CastMotion.SPATIAL_FAST);
        animator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            fillShape.setCornerRadius(v);
            maskShape.setCornerRadius(v);
            invalidate();
        });
        animator.start();
    }

    /** Puts an icon before the label with the M3 8dp gap. */
    public void setLeadingIcon(int drawableRes) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(drawableRes, 0, 0, 0);
        setCompoundDrawablePadding(CastShape.dp(getContext(), 8));
        setCompoundDrawableTintList(ColorStateList.valueOf(contentColor));
        setPadding(CastShape.dp(getContext(), 16), 0, CastShape.dp(getContext(), 24), 0);
    }
}
