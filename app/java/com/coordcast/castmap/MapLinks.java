package com.coordcast.castmap;

import com.coordcast.castcore.Formats;

/**
 * Builds the deep link each map app is handed.
 *
 * <p>Every app gets the link its own documentation publishes, one scheme per function —
 * they are not interchangeable, so no attempt is made to find a lowest common
 * denominator. Coordinates must already be in the datum the target wants; see
 * {@link MapApp#wantsGcj02()}.</p>
 *
 * <p>Latitude comes before longitude in every link below. That is the opposite of the
 * Amap <em>web</em> URLs in {@link AmapUris}, which are handled there.</p>
 *
 * <p>Pure Java, so each generated link is unit tested on the desktop JVM.</p>
 */
public final class MapLinks {

    /**
     * Baidu's {@code src} is "companyName|appName" style app identification, not a
     * developer key — any stable string is accepted.
     */
    private static final String BAIDU_SRC = "andr.coordcast";

    /** Amap's {@code t} parameter for driving; the only mode this app offers. */
    private static final int AMAP_DRIVING = 0;

    private MapLinks() {
    }

    /** A link that shows the point as a marker. */
    public static String view(MapApp app, String name, double lat, double lng) {
        String label = label(name);
        switch (app) {
            case BAIDU:
                return "baidumap://map/marker?location=" + point(lat, lng)
                        + "&title=" + AmapUris.enc(label)
                        + "&content=" + AmapUris.enc(label)
                        + "&coord_type=gcj02"
                        + "&src=" + BAIDU_SRC;
            case TENCENT:
                // No referer: Tencent's own examples call this without the developer key,
                // and a wrong key is rejected outright where a missing one is not.
                return "qqmap://map/marker?marker=coord:" + point(lat, lng)
                        + ";title:" + AmapUris.enc(label);
            case GOOGLE:
                return "geo:0,0?q=" + point(lat, lng) + "(" + AmapUris.enc(label) + ")";
            case GENERIC:
                return "geo:" + point(lat, lng)
                        + "?q=" + point(lat, lng) + "(" + AmapUris.enc(label) + ")";
            case AMAP:
            default:
                return AmapUris.viewMap(label, lat, lng);
        }
    }

    /** A link that opens route planning with this point as the destination. */
    public static String route(MapApp app, String name, double lat, double lng) {
        String label = label(name);
        switch (app) {
            case BAIDU:
                // The origin is left out so Baidu starts from the current location.
                return "baidumap://map/direction?destination=latlng:" + point(lat, lng)
                        + "|name:" + AmapUris.enc(label)
                        + "&mode=driving"
                        + "&coord_type=gcj02"
                        + "&src=" + BAIDU_SRC;
            case TENCENT:
                return "qqmap://map/routeplan?type=drive"
                        + "&fromcoord=CurrentLocation"
                        + "&tocoord=" + point(lat, lng)
                        + "&to=" + AmapUris.enc(label);
            case GOOGLE:
                return "google.navigation:q=" + point(lat, lng) + "&mode=d";
            case GENERIC:
                // geo: has no routing verb; the app opens at the point and the user
                // starts navigation from there.
                return "geo:" + point(lat, lng)
                        + "?q=" + point(lat, lng) + "(" + AmapUris.enc(label) + ")";
            case AMAP:
            default:
                return AmapUris.routePlan(label, lat, lng, AMAP_DRIVING);
        }
    }

    /** Which URL a browser should be handed when the app itself cannot be started. */
    public static String web(MapApp app, String name, double lat, double lng) {
        switch (app) {
            case BAIDU:
                return "https://api.map.baidu.com/marker?location=" + point(lat, lng)
                        + "&title=" + AmapUris.enc(label(name))
                        + "&content=" + AmapUris.enc(label(name))
                        + "&coord_type=gcj02&output=html&src=webapp.coordcast";
            case TENCENT:
                return "https://apis.map.qq.com/uri/v1/marker?marker=coord:"
                        + point(lat, lng) + ";title:" + AmapUris.enc(label(name));
            case GOOGLE:
                return "https://www.google.com/maps/search/?api=1&query=" + point(lat, lng);
            case AMAP:
            case GENERIC:
            default:
                return AmapUris.webMarker(label(name), lat, lng);
        }
    }

    /** Which URL a browser should be handed for route planning. */
    public static String webRoute(MapApp app, String name, double lat, double lng) {
        String label = label(name);
        switch (app) {
            case BAIDU:
                return "https://api.map.baidu.com/direction?destination=latlng:" + point(lat, lng)
                        + "|name:" + AmapUris.enc(label)
                        + "&mode=driving&coord_type=gcj02&output=html&src=webapp.coordcast";
            case GOOGLE:
                return "https://www.google.com/maps/dir/?api=1&destination=" + point(lat, lng);
            case AMAP:
            case TENCENT:
            case GENERIC:
            default:
                // Tencent's web URI API needs a developer key too, so its fallback is
                // Amap's web planner — the one that always works without one.
                return AmapUris.webRoute(label, lat, lng);
        }
    }

    /** {@code lat,lng} — the order every link above expects. */
    private static String point(double lat, double lng) {
        return Formats.coord(lat) + "," + Formats.coord(lng);
    }

    private static String label(String name) {
        return name == null || name.trim().isEmpty() ? AmapUris.DEFAULT_NAME : name.trim();
    }
}
