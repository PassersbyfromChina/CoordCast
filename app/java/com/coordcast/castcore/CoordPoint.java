package com.coordcast.castcore;

/**
 * A validated latitude/longitude pair in decimal degrees, plus the datum it was
 * written in.
 *
 * <p>This is the single value the whole app works with: the two input fields are
 * parsed into one of these, normalised to GCJ-02, and handed to Amap.</p>
 */
public final class CoordPoint {

    public final double lat;
    public final double lng;
    /** Datum the numbers were written in; chosen by the user, defaults to WGS-84. */
    public final CoordSystem source;

    public CoordPoint(double lat, double lng, CoordSystem source) {
        this.lat = lat;
        this.lng = lng;
        this.source = source == null ? CoordSystem.WGS84 : source;
    }

    public boolean isValid() {
        return Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0 && !(lat == 0.0 && lng == 0.0);
    }

    /** Latitude/longitude converted into the datum Amap expects. */
    public double[] gcj02() {
        return GeoConverter.convert(lat, lng, source, CoordSystem.GCJ02);
    }

    /** Latitude/longitude converted into the datum Baidu expects. */
    public double[] bd09() {
        return GeoConverter.convert(lat, lng, source, CoordSystem.BD09);
    }

    /**
     * Latitude/longitude converted into plain WGS-84 — what the {@code geo:} URI
     * standard (RFC 5870) and OpenStreetMap-based apps want.
     */
    public double[] wgs84() {
        return GeoConverter.convert(lat, lng, source, CoordSystem.WGS84);
    }

    /** {@code "lat, lng"} in GCJ-02 — what actually gets sent to Amap. */
    public String gcj02Text() {
        double[] g = gcj02();
        return Formats.coord(g[0]) + ", " + Formats.coord(g[1]);
    }

    /** How far the point moved during the datum shift, in metres. */
    public double shiftMeters() {
        double[] g = gcj02();
        return GeoConverter.distanceMeters(lat, lng, g[0], g[1]);
    }

    @Override
    public String toString() {
        return Formats.coord(lat) + "," + Formats.coord(lng);
    }
}
