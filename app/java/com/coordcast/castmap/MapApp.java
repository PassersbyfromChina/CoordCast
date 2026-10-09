package com.coordcast.castmap;

/**
 * A map application that coordinates can be handed to.
 *
 * <p>Each entry carries the package names it may be installed under and which datum its
 * deep links expect, so the link builders and the app picker share one source of truth
 * instead of each knowing half of it.</p>
 */
public enum MapApp {

    /** 高德地图. The original target, and the one every link in this app was built for. */
    AMAP("高德地图", true, "com.autonavi.minimap", "com.autonavi.amapauto"),

    /**
     * 百度地图. Its links take GCJ-02 and convert to BD-09 themselves, which is why
     * {@code coord_type=gcj02} is used rather than pre-converting to BD-09 here.
     */
    BAIDU("百度地图", true, "com.baidu.BaiduMap", "com.baidu.BaiduMapCar"),

    /** 腾讯地图. */
    TENCENT("腾讯地图", true, "com.tencent.map"),

    /**
     * Google 地图. In mainland China its tiles are GCJ-02 aligned, so a GCJ-02 coordinate
     * is what puts the pin in the right place; outside China the two datums are identical.
     */
    GOOGLE("Google 地图", true, "com.google.android.apps.maps"),

    /**
     * Any other app that answers the standard {@code geo:} URI — Petal, OsmAnd, Here and
     * friends. The geo URI standard is WGS-84, so this is the one target that is not
     * handed GCJ-02.
     */
    GENERIC("地图应用", false);

    private final String label;
    private final boolean gcj02;
    private final String[] packages;

    MapApp(String label, boolean gcj02, String... packages) {
        this.label = label;
        this.gcj02 = gcj02;
        this.packages = packages;
    }

    /** Fallback name, used when the installed app cannot supply its own label. */
    public String label() {
        return label;
    }

    /** True when this app wants GCJ-02 coordinates rather than WGS-84. */
    public boolean wantsGcj02() {
        return gcj02;
    }

    /** Package names this app may be installed under, most likely first. */
    public String[] packages() {
        return packages.clone();
    }

    /** True for the catch-all target that has no fixed package. */
    public boolean isGeneric() {
        return this == GENERIC;
    }
}
