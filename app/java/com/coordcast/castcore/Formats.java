package com.coordcast.castcore;

import java.util.Locale;

/** Number formatting shared by the parser, the UI and the URI builders. */
public final class Formats {

    private Formats() {
    }

    /** 6 decimal places ≈ 11 cm; plenty for navigation, and stable for URIs. */
    public static String coord(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    /** Trims trailing zeros for on-screen display: {@code 31.2304, 121.4737}. */
    public static String shortCoord(double v) {
        double value = v == 0.0 ? 0.0 : v; // collapse -0.0 so it never shows as "-0"
        String s = String.format(Locale.US, "%.6f", value);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && s.charAt(end - 1) == '.') {
                end--;
            }
            s = s.substring(0, end);
        }
        return s;
    }

    public static String meters(double m) {
        if (m < 1) {
            return "<1\u00A0m";
        }
        if (m < 1000) {
            return String.format(Locale.US, "%.0f\u00A0m", m);
        }
        return String.format(Locale.US, "%.1f\u00A0km", m / 1000.0);
    }
}
