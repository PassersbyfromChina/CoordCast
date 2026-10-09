package com.coordcast.castui;

/**
 * Material 3 colour roles.
 *
 * <p>M3 is a <b>role-based</b> system: nothing ever references a raw palette swatch, only a
 * semantic role — {@code primary}, {@code onPrimary}, {@code surfaceContainerHigh},
 * {@code outline}. Swapping the scheme (or seeding it from a wallpaper colour) re-themes
 * every component for free, which is why components here ask for roles and never for
 * colours.</p>
 *
 * <p>Defaults are the standard M3 dark scheme, which is what this app ships. Set
 * {@link #set} once at startup to re-theme everything.</p>
 */
public final class CastColor {

    private static CastColor current = dark();

    public static CastColor get() {
        return current;
    }

    public static void set(CastColor scheme) {
        if (scheme != null) {
            current = scheme;
        }
    }

    // ---- primary --------------------------------------------------------
    public int primary = 0xFFD0BCFF;
    public int onPrimary = 0xFF381E72;
    public int primaryContainer = 0xFF4F378B;
    public int onPrimaryContainer = 0xFFEADDFF;
    public int primaryFixedDim = 0xFFB69DF8;
    public int inversePrimary = 0xFF6750A4;

    // ---- secondary ------------------------------------------------------
    public int secondary = 0xFFCCC2DC;
    public int onSecondary = 0xFF332D41;
    public int secondaryContainer = 0xFF4A4458;
    public int onSecondaryContainer = 0xFFE8DEF8;

    // ---- tertiary -------------------------------------------------------
    public int tertiary = 0xFFEFB8C8;
    public int onTertiary = 0xFF492532;
    public int tertiaryContainer = 0xFF633B48;
    public int onTertiaryContainer = 0xFFFFD8E4;

    // ---- error ----------------------------------------------------------
    public int error = 0xFFF2B8B5;
    public int onError = 0xFF601410;
    public int errorContainer = 0xFF8C1D18;
    public int onErrorContainer = 0xFFF9DEDC;

    // ---- surfaces -------------------------------------------------------
    public int surface = 0xFF141218;
    public int onSurface = 0xFFE6E0E9;
    public int surfaceVariant = 0xFF49454F;
    public int onSurfaceVariant = 0xFFCAC4D0;
    public int surfaceContainerLowest = 0xFF0F0D13;
    public int surfaceContainerLow = 0xFF1D1B20;
    public int surfaceContainer = 0xFF211F26;
    public int surfaceContainerHigh = 0xFF2B2930;
    public int surfaceContainerHighest = 0xFF36343B;
    public int inverseSurface = 0xFFE6E0E9;
    public int inverseOnSurface = 0xFF313033;

    // ---- lines ----------------------------------------------------------
    public int outline = 0xFF938F99;
    public int outlineVariant = 0xFF49454F;
    public int scrim = 0xFF000000;

    /** The standard M3 dark scheme. */
    public static CastColor dark() {
        return new CastColor();
    }

    /** The standard M3 light scheme, for completeness. */
    public static CastColor light() {
        CastColor s = new CastColor();
        s.primary = 0xFF6750A4;
        s.onPrimary = 0xFFFFFFFF;
        s.primaryContainer = 0xFFEADDFF;
        s.onPrimaryContainer = 0xFF4F378A;
        s.inversePrimary = 0xFFD0BCFF;
        s.secondary = 0xFF625B71;
        s.onSecondary = 0xFFFFFFFF;
        s.secondaryContainer = 0xFFE8DEF8;
        s.onSecondaryContainer = 0xFF4A4459;
        s.tertiary = 0xFF7D5260;
        s.onTertiary = 0xFFFFFFFF;
        s.tertiaryContainer = 0xFFFFD8E4;
        s.onTertiaryContainer = 0xFF633B48;
        s.error = 0xFFB3261E;
        s.onError = 0xFFFFFFFF;
        s.errorContainer = 0xFFF9DEDC;
        s.onErrorContainer = 0xFF8C1D18;
        s.surface = 0xFFFEF7FF;
        s.onSurface = 0xFF1D1B20;
        s.surfaceVariant = 0xFFE7E0EC;
        s.onSurfaceVariant = 0xFF49454F;
        s.surfaceContainerLowest = 0xFFFFFFFF;
        s.surfaceContainerLow = 0xFFF7F2FA;
        s.surfaceContainer = 0xFFF3EDF7;
        s.surfaceContainerHigh = 0xFFECE6F0;
        s.surfaceContainerHighest = 0xFFE6E0E9;
        s.inverseSurface = 0xFF322F35;
        s.inverseOnSurface = 0xFFF5EFF7;
        s.outline = 0xFF79747E;
        s.outlineVariant = 0xFFCAC4D0;
        return s;
    }

    // ------------------------------------------------------------- helpers

    /** The role to use for content sitting on top of {@code background}. */
    public int on(int background) {
        if (background == primary) {
            return onPrimary;
        }
        if (background == primaryContainer) {
            return onPrimaryContainer;
        }
        if (background == secondaryContainer) {
            return onSecondaryContainer;
        }
        if (background == tertiaryContainer) {
            return onTertiaryContainer;
        }
        if (background == error) {
            return onError;
        }
        if (background == errorContainer) {
            return onErrorContainer;
        }
        if (background == inverseSurface) {
            return inverseOnSurface;
        }
        return onSurface;
    }

    /**
     * A state layer is a translucent veil of the <em>content</em> colour painted over the
     * resting fill — M3 never swaps the fill on hover or press. Hover is 8 %, focus and
     * press are 10 %, drag is 16 %.
     */
    public static int withAlpha(int argb, float fraction) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, fraction)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int hover(int contentColor) {
        return withAlpha(contentColor, 0.08f);
    }

    public static int pressed(int contentColor) {
        return withAlpha(contentColor, 0.10f);
    }

    public static int dragged(int contentColor) {
        return withAlpha(contentColor, 0.16f);
    }

    /** Blends {@code veil} over {@code base}; used where a real layer is not possible. */
    public static int composite(int base, int veil) {
        float a = ((veil >>> 24) & 0xFF) / 255f;
        int r = Math.round(((veil >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((veil >> 8) & 0xFF) * a + ((base >> 8) & 0xFF) * (1 - a));
        int b = Math.round((veil & 0xFF) * a + (base & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
