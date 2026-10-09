package com.coordcast.castui;

import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * The Material 3 type scale.
 *
 * <p>Components never invent a size — they pick a role from the scale. Roboto is the M3
 * face by spec and is what Android ships, so nothing is bundled or overridden here; only
 * the size, weight, tracking and leading that the scale defines.</p>
 *
 * <table>
 *   <tr><td>Display</td><td>57 / 45 / 36</td><td>regular, tight tracking</td></tr>
 *   <tr><td>Headline</td><td>32 / 28 / 24</td><td>regular</td></tr>
 *   <tr><td>Title</td><td>22 / 16 / 14</td><td>medium</td></tr>
 *   <tr><td>Body</td><td>16 / 14 / 12</td><td>regular</td></tr>
 *   <tr><td>Label</td><td>14 / 12 / 11</td><td>medium</td></tr>
 * </table>
 */
public final class CastType {

    private CastType() {
    }

    // ------------------------------------------------------------ display

    public static void displayLarge(TextView v) {
        apply(v, 57, 400, -0.25f, 64);
    }

    public static void displayMedium(TextView v) {
        apply(v, 45, 400, 0f, 52);
    }

    public static void displaySmall(TextView v) {
        apply(v, 36, 400, 0f, 44);
    }

    // ----------------------------------------------------------- headline

    public static void headlineLarge(TextView v) {
        apply(v, 32, 400, 0f, 40);
    }

    public static void headlineMedium(TextView v) {
        apply(v, 28, 400, 0f, 36);
    }

    public static void headlineSmall(TextView v) {
        apply(v, 24, 400, 0f, 32);
    }

    // -------------------------------------------------------------- title

    public static void titleLarge(TextView v) {
        apply(v, 22, 400, 0f, 28);
    }

    public static void titleMedium(TextView v) {
        apply(v, 16, 500, 0.15f, 24);
    }

    public static void titleSmall(TextView v) {
        apply(v, 14, 500, 0.1f, 20);
    }

    // --------------------------------------------------------------- body

    public static void bodyLarge(TextView v) {
        apply(v, 16, 400, 0.5f, 24);
    }

    public static void bodyMedium(TextView v) {
        apply(v, 14, 400, 0.25f, 20);
    }

    public static void bodySmall(TextView v) {
        apply(v, 12, 400, 0.4f, 16);
    }

    // -------------------------------------------------------------- label

    public static void labelLarge(TextView v) {
        apply(v, 14, 500, 0.1f, 20);
    }

    public static void labelMedium(TextView v) {
        apply(v, 12, 500, 0.5f, 16);
    }

    public static void labelSmall(TextView v) {
        apply(v, 11, 500, 0.5f, 16);
    }

    /** The one place the scale turns into view attributes. */
    private static void apply(TextView v, float sizeSp, int weight, float tracking, int lineHeightSp) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        v.setLetterSpacing(tracking / sizeSp);
        v.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL),
                weight >= 500 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        v.setIncludeFontPadding(false);
        v.setLineSpacing(0f, 1f);
        float density = v.getResources().getDisplayMetrics().scaledDensity;
        v.setMinHeight(Math.round(lineHeightSp * density));
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
