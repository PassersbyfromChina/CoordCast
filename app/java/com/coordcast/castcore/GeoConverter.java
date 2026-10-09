package com.coordcast.castcore;

/**
 * Datum conversions between WGS-84, GCJ-02 and BD-09.
 *
 * <p>Pure Java on purpose: no Android dependency, so the maths can be unit
 * tested on a desktop JVM.</p>
 */
public final class GeoConverter {

    /** Krasovsky 1940 semi-major axis used by the GCJ-02 algorithm. */
    private static final double A = 6378245.0;
    /** Krasovsky 1940 squared eccentricity. */
    private static final double EE = 0.00669342162296594323;
    private static final double X_PI = Math.PI * 3000.0 / 180.0;

    private GeoConverter() {
    }

    /** Rough bounding box of mainland China; outside it GCJ-02 equals WGS-84. */
    public static boolean outOfChina(double lat, double lng) {
        return !(lng >= 73.66 && lng <= 135.05 && lat >= 3.86 && lat <= 53.55);
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    /** @return {@code {lat, lng}} in GCJ-02 */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (outOfChina(lat, lng)) {
            return new double[]{lat, lng};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    /** @return {@code {lat, lng}} in WGS-84, refined by fixed-point iteration */
    public static double[] gcj02ToWgs84(double lat, double lng) {
        if (outOfChina(lat, lng)) {
            return new double[]{lat, lng};
        }
        double wLat = lat;
        double wLng = lng;
        for (int i = 0; i < 10; i++) {
            double[] guess = wgs84ToGcj02(wLat, wLng);
            double dLat = guess[0] - lat;
            double dLng = guess[1] - lng;
            if (Math.abs(dLat) < 1e-10 && Math.abs(dLng) < 1e-10) {
                break;
            }
            wLat -= dLat;
            wLng -= dLng;
        }
        return new double[]{wLat, wLng};
    }

    /** @return {@code {lat, lng}} in BD-09 */
    public static double[] gcj02ToBd09(double lat, double lng) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }

    /** @return {@code {lat, lng}} in GCJ-02 */
    public static double[] bd09ToGcj02(double lat, double lng) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    /** Converts {@code {lat, lng}} from one datum to another. */
    public static double[] convert(double lat, double lng, CoordSystem from, CoordSystem to) {
        if (from == to) {
            return new double[]{lat, lng};
        }
        double[] gcj;
        if (from == CoordSystem.WGS84) {
            gcj = wgs84ToGcj02(lat, lng);
        } else if (from == CoordSystem.BD09) {
            gcj = bd09ToGcj02(lat, lng);
        } else {
            gcj = new double[]{lat, lng};
        }
        if (to == CoordSystem.GCJ02) {
            return gcj;
        }
        if (to == CoordSystem.WGS84) {
            return gcj02ToWgs84(gcj[0], gcj[1]);
        }
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /** Great-circle distance in metres (for UI hints such as "偏移约 480 m"). */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371008.8;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
