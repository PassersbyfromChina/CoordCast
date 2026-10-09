package com.coordcast.castmap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.coordcast.castcore.Formats;

/**
 * Builds the two deep links this app hands to Amap (高德地图), plus the web
 * fallbacks used when the app is not installed.
 *
 * <p>Coordinates must already be <b>GCJ-02</b>, which is exactly what {@code dev=0}
 * declares to Amap ("lat 和 lon 是已经加密后的,不需要国测加密" — already encrypted,
 * no conversion needed). {@code dev=1} would ask Amap to convert, so this class never
 * emits it.</p>
 *
 * <p>Scheme choice follows Amap's own reference, one page per function:
 * {@code androidamap://viewMap} for showing a point and
 * {@code amapuri://route/plan/} for route planning. They are not documented as
 * interchangeable, so each keeps the scheme Amap publishes for it.</p>
 *
 * <p>Pure Java (the Android layer turns these strings into {@code Uri}s), so every
 * generated link is unit tested on the desktop JVM.</p>
 */
public final class AmapUris {

    /** Identifies this app to Amap; required by every Amap page ("请务必填写"). */
    public static final String SOURCE = "coordcast";

    /** Amap's documented package. The car build is a best-effort secondary target. */
    public static final String PKG_PHONE = "com.autonavi.minimap";
    public static final String PKG_AUTO = "com.autonavi.amapauto";

    /** Marker label used when the user only supplied bare coordinates. */
    public static final String DEFAULT_NAME = "坐标点";

    private AmapUris() {
    }

    /** Drops a marker on Amap's map. {@code poiname} is required here. */
    public static String viewMap(String name, double lat, double lng) {
        return "androidamap://viewMap?sourceApplication=" + SOURCE
                + "&poiname=" + enc(blankToDefault(name))
                + "&lat=" + Formats.coord(lat)
                + "&lon=" + Formats.coord(lng)
                + "&dev=0";
    }

    /**
     * Opens Amap's route planner with this point as the destination.
     *
     * <p>The origin is left unset so Amap starts from the user's current location.</p>
     *
     * @param t 0 driving, 1 transit, 2 walking, 3 cycling, 4 train, 5 coach
     */
    public static String routePlan(String name, double lat, double lng, int t) {
        return "amapuri://route/plan/?sourceApplication=" + SOURCE
                + "&dlat=" + Formats.coord(lat)
                + "&dlon=" + Formats.coord(lng)
                + "&dname=" + enc(blankToDefault(name))
                + "&dev=0"
                + "&t=" + t
                + "&m=0";
    }

    /**
     * Amap web map with a marker — works without the app, opens in any browser.
     * {@code position} is <b>longitude first</b>, the opposite of the app links.
     */
    public static String webMarker(String name, double lat, double lng) {
        return "https://uri.amap.com/marker?position=" + Formats.coord(lng) + "," + Formats.coord(lat)
                + "&name=" + enc(blankToDefault(name))
                + "&src=" + SOURCE
                + "&coordinate=gaode"
                + "&callnative=1";
    }

    /**
     * Amap web route planner. The origin is left empty, which the mobile web page
     * turns into "use my current location"; {@code callnative=1} tries the app first.
     */
    public static String webRoute(String name, double lat, double lng) {
        return "https://uri.amap.com/navigation?from="
                + "&to=" + Formats.coord(lng) + "," + Formats.coord(lat) + "," + enc(blankToDefault(name))
                + "&mode=car&policy=1&src=" + SOURCE + "&coordinate=gaode&callnative=1";
    }

    private static String blankToDefault(String name) {
        return name == null || name.trim().isEmpty() ? DEFAULT_NAME : name.trim();
    }

    /** Percent-encodes one URI component as UTF-8 ({@code +} is not valid in a query). */
    public static String enc(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is mandated by the JVM spec; unreachable in practice.
            return value.replace(" ", "%20");
        }
    }
}
