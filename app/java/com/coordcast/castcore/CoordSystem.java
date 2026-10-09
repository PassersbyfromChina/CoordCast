package com.coordcast.castcore;

/**
 * Supported geographic coordinate reference systems.
 *
 * <p>Amap (高德地图) renders and navigates in {@link #GCJ02}. Every point is
 * normalised to GCJ-02 immediately before it is handed to Amap.</p>
 */
public enum CoordSystem {

    /** Raw GPS / Google Maps / OpenStreetMap coordinates. */
    WGS84("WGS-84", "GPS"),

    /** Chinese national standard ("Mars coordinates"), used by Amap / Tencent. */
    GCJ02("GCJ-02", "高德"),

    /** Baidu coordinates, used by Baidu Maps. */
    BD09("BD-09", "百度");

    private final String display;
    private final String hint;

    CoordSystem(String display, String hint) {
        this.display = display;
        this.hint = hint;
    }

    /** Short label shown in the segmented control, e.g. {@code WGS-84}. */
    public String display() {
        return display;
    }

    /** One-word hint, e.g. {@code GPS}. */
    public String hint() {
        return hint;
    }

    public static CoordSystem fromIndex(int index) {
        CoordSystem[] all = values();
        if (index < 0 || index >= all.length) {
            return WGS84;
        }
        return all[index];
    }
}
