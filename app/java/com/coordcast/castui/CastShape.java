package com.coordcast.castui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

/**
 * The Material 3 shape scale, and the Expressive habit of morphing between its steps.
 *
 * <p>Radii are a scale, not arbitrary numbers: a component picks a step, and nested
 * elements step <em>down</em> so the corners stay concentric.</p>
 */
public final class CastShape {

    public static final float NONE = 0f;
    public static final float EXTRA_SMALL = 4f;
    public static final float SMALL = 8f;
    public static final float MEDIUM = 12f;
    public static final float LARGE = 16f;
    public static final float EXTRA_LARGE = 28f;
    /** Stadium: the radius is half the shorter side, so pass the height instead. */
    public static final float FULL = -1f;

    private CastShape() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    /** Resolves a step against a view's height, so {@link #FULL} becomes a stadium. */
    public static float radiusPx(View view, float stepDp) {
        if (stepDp == FULL) {
            return Math.min(view.getWidth(), view.getHeight()) * 0.5f;
        }
        return dp(view.getContext(), stepDp);
    }

    // ------------------------------------------------------------ shapes

    /** A filled shape at one step of the scale. */
    public static GradientDrawable filled(Context context, int color, float stepDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(stepDp == FULL ? 0f : dp(context, stepDp));
        return d;
    }

    /** An outlined shape: {@code strokeDp} at the given colour, transparent inside. */
    public static GradientDrawable outlined(Context context, int strokeColor, float strokeDp,
                                            float stepDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(0x00000000);
        d.setStroke(dp(context, strokeDp), strokeColor);
        d.setCornerRadius(stepDp == FULL ? 0f : dp(context, stepDp));
        return d;
    }

    /** A filled shape with a stroke, which is what most M3 containers actually are. */
    public static GradientDrawable filledOutlined(Context context, int fill, int strokeColor,
                                                  float strokeDp, float stepDp) {
        GradientDrawable d = filled(context, fill, stepDp);
        d.setStroke(dp(context, strokeDp), strokeColor);
        return d;
    }

    /**
     * M3 Expressive morphs a control's shape as it is pressed — the corners open up. This
     * animates a real {@link GradientDrawable}, so the morph is on the actual surface and
     * not a separate overlay.
     */
    public static void morphCorners(final GradientDrawable shape, final Context context,
                                    final float fromDp, final float toDp) {
        if (!CastMotion.animationsEnabled(context)) {
            shape.setCornerRadius(fromDp == FULL ? 0f : dp(context, toDp));
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(fromDp, toDp);
        animator.setDuration(CastMotion.MEDIUM2);
        animator.setInterpolator(CastMotion.SPATIAL_FAST);
        animator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            shape.setCornerRadius(v == FULL ? 0f : dp(context, v));
        });
        animator.start();
    }

    /** Keeps a stadium shape actually stadium-shaped as the view resizes. */
    public static void applyStadium(GradientDrawable shape, View view) {
        shape.setCornerRadius(Math.min(view.getWidth(), view.getHeight()) * 0.5f);
    }
}
