package com.coordcast.castui;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Material 3 Expressive motion.
 *
 * <p>Two families, and using the wrong one is what makes an app feel "off":</p>
 *
 * <ul>
 *   <li><b>Spatial</b> — anything that moves, resizes or rotates. Springs with a little
 *       bounce, because the movement itself is the feedback.</li>
 *   <li><b>Effects</b> — colour, opacity, anything that must not overshoot. Critically
 *       damped, so a fade never flicks past its target.</li>
 * </ul>
 *
 * <p>The springs are modelled with the closed form of a damped oscillator rather than
 * sampled physics — same curve, no per-frame integration, and it can be handed straight to
 * a {@link ValueAnimator} as an interpolator.</p>
 */
public final class CastMotion {

    private CastMotion() {
    }

    // ------------------------------------------------------------- springs

    /**
     * {@code x(t) = 1 − e^(−ζωt)·(cos ωd t + (ζω/ωd)·sin ωd t)}.
     *
     * <p>Zeta below 1 overshoots slightly and settles; zeta of 1 is critical damping and
     * needs its own closed form.</p>
     */
    private static float spring(float t, float zeta, float omega) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        if (zeta >= 1f) {
            double envelope = Math.exp(-omega * t);
            return (float) (1 - envelope * (1 + omega * t));
        }
        double wd = omega * Math.sqrt(1 - zeta * zeta);
        double envelope = Math.exp(-zeta * omega * t);
        return (float) (1 - envelope
                * (Math.cos(wd * t) + (zeta * omega / wd) * Math.sin(wd * t)));
    }

    /*
     * The damping ratios below are the M3 Expressive token values, from androidx's
     * ExpressiveMotionTokens (material3):
     *
     *   SpringDefaultSpatialDamping = 0.8    SpringDefaultSpatialStiffness = 380
     *   SpringFastSpatialDamping    = 0.6    SpringFastSpatialStiffness    = 800
     *   SpringSlowSpatialDamping    = 0.8    SpringSlowSpatialStiffness    = 200
     *   SpringDefaultEffectsDamping = 1.0    SpringDefaultEffectsStiffness = 1600
     *
     * Note what the tokens say about "fast": it is *less* damped than the default
     * (0.6 vs 0.8), so a fast spatial spring bounces MORE, not less. It gets its
     * quickness from stiffness, not from damping.
     *
     * Omega (rad/s) is not a token. These numbers place each spring's settle inside the
     * normalised 0..1 range an Interpolator is evaluated over, since zeta*omega*t ~= 4
     * is the settle point: with zeta 0.8 and omega 13, the motion is done by t ~= 0.39.
     */

    /** Spatial spring for moving and resizing: the M3 default. A little bounce. */
    public static final TimeInterpolator SPATIAL = t -> spring(t, 0.8f, 13f);

    /**
     * Spatial spring for short distances. Bouncier than {@link #SPATIAL} because the M3
     * fast-spatial token is less damped, but stiffer so it still reads as quick.
     */
    public static final TimeInterpolator SPATIAL_FAST = t -> spring(t, 0.6f, 18f);

    /** Spatial spring for large surfaces, where a fast settle would look mechanical. */
    public static final TimeInterpolator SPATIAL_SLOW = t -> spring(t, 0.8f, 9f);

    /** Effects spring: critically damped, never overshoots a colour or an opacity. */
    public static final TimeInterpolator EFFECTS = t -> spring(t, 1f, 16f);

    /** Effects spring for small, quick colour changes. */
    public static final TimeInterpolator EFFECTS_FAST = t -> spring(t, 1f, 26f);

    /** A press that gives under the finger and comes back — spatial, damped. */
    public static final TimeInterpolator PRESS = t -> spring(t, 1f, 20f);

    // ------------------------------------------------------------ easings

    /** Emphasised decelerate: enters fast, leaves slowly. The M3 default for entrances. */
    public static final TimeInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    /** Emphasised accelerate: leaves fast. For exits. */
    public static final TimeInterpolator EMPHASIZED_ACCELERATE =
            new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    /** Standard easing, for small utility transitions. */
    public static final TimeInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    // ----------------------------------------------------------- durations

    public static final int SHORT1 = 50;
    public static final int SHORT2 = 100;
    public static final int SHORT3 = 150;
    public static final int SHORT4 = 200;
    public static final int MEDIUM1 = 250;
    public static final int MEDIUM2 = 300;
    public static final int MEDIUM3 = 350;
    public static final int MEDIUM4 = 400;
    public static final int LONG1 = 450;
    public static final int LONG2 = 500;
    public static final int LONG3 = 550;
    public static final int LONG4 = 600;

    // ------------------------------------------------------------ reduce motion

    /**
     * Android's Reduce Motion equivalent: when the system animation scale is zero, motion
     * is skipped rather than forced on someone who asked for stillness.
     */
    public static boolean animationsEnabled(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // -------------------------------------------------------------- helpers

    /** Animates a view to a new scale on the spatial spring. */
    public static void scale(View view, float target, int duration) {
        if (!animationsEnabled(view.getContext())) {
            view.setScaleX(target);
            view.setScaleY(target);
            return;
        }
        view.animate().cancel();
        view.animate()
                .scaleX(target)
                .scaleY(target)
                .setDuration(duration)
                .setInterpolator(target < 1f ? PRESS : SPATIAL)
                .start();
    }

    /**
     * The M3 Expressive press: the surface gives slightly under the finger and springs
     * back on release. Returning {@code false} keeps click listeners and ripples working.
     */
    public static void addPress(final View view) {
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scale(v, 0.96f, SHORT4);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(v, 1f, MEDIUM3);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Animates one float with the given spring. */
    public static void animateFloat(ValueAnimator.AnimatorUpdateListener listener,
                                    float from, float to, int duration,
                                    TimeInterpolator interpolator) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(listener);
        animator.start();
    }
}
