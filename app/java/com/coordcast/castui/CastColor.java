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
 * <p><b>The default is Google's own dark scheme, not the M3 baseline one.</b> The M3
 * baseline dark palette is purple-tinted ({@code #D0BCFF} on {@code #141218}), which is
 * what the spec's reference theme uses — but no Google app actually ships it. The values
 * below were sampled pixel by pixel from Google's own dark-themed apps (Play Store,
 * Translate, Maps, Earth, Gboard), which agree on two things: a <em>neutral</em> grey
 * surface around {@code #131313}, and a light periwinkle {@code #B2C5FF} primary.</p>
 *
 * <p>Set {@link #set} once at startup to re-theme everything.</p>
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
    // #B2C5FF sampled from Gboard's "Add keyboard" and Translate's mic FAB.
    public int primary = 0xFFB2C5FF;
    public int onPrimary = 0xFF002E69;
    // #004A77 sampled from Google Earth's filled buttons and banner.
    public int primaryContainer = 0xFF004A77;
    public int onPrimaryContainer = 0xFFC2E7FF;
    public int primaryFixedDim = 0xFF8FA9E8;
    public int inversePrimary = 0xFF3B5C9E;

    // ---- secondary ------------------------------------------------------
    public int secondary = 0xFFC2E7FF;
    public int onSecondary = 0xFF003355;
    public int secondaryContainer = 0xFF004A77;
    public int onSecondaryContainer = 0xFFC2E7FF;

    // ---- tertiary -------------------------------------------------------
    public int tertiary = 0xFFD3E3FD;
    public int onTertiary = 0xFF0B2A4A;
    public int tertiaryContainer = 0xFF1A4C78;
    public int onTertiaryContainer = 0xFFD3E3FD;

    // ---- error ----------------------------------------------------------
    public int error = 0xFFF2B8B5;
    public int onError = 0xFF601410;
    public int errorContainer = 0xFF8C1D18;
    public int onErrorContainer = 0xFFF9DEDC;

    // ---- surfaces -------------------------------------------------------
    // #131313 is the single most common pixel in Play Store, Earth and Maps alike.
    public int surface = 0xFF131313;
    public int onSurface = 0xFFE3E3E3;
    public int surfaceVariant = 0xFF444746;
    public int onSurfaceVariant = 0xFFC4C7C5;
    public int surfaceContainerLowest = 0xFF0E0E0E;
    public int surfaceContainerLow = 0xFF1B1B1B;
    public int surfaceContainer = 0xFF1F1F1F;
    public int surfaceContainerHigh = 0xFF2A2A2A;
    public int surfaceContainerHighest = 0xFF363636;
    public int inverseSurface = 0xFFE3E3E3;
    public int inverseOnSurface = 0xFF303030;

    // ---- lines ----------------------------------------------------------
    // Maps' chips sit on #393939, which is Google's own "outline variant on a surface".
    public int outline = 0xFF8E918F;
    public int outlineVariant = 0xFF444746;
    public int scrim = 0xFF000000;

    /** Google's dark scheme — the default. */
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
