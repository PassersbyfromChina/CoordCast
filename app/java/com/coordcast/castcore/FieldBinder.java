package com.coordcast.castcore;

/**
 * Decides what the two input fields should hold.
 *
 * <p>The whole paste / move / split policy lives here rather than in the activity, so it
 * can be unit tested on the desktop. The activity only reads the fields, asks for the next
 * state, and writes it back.</p>
 */
public final class FieldBinder {

    /** The text the two fields should show. */
    public static final class Fields {
        public final String latitude;
        public final String longitude;

        Fields(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private FieldBinder() {
    }

    /**
     * Normalises each field on its own — degrees/minutes/seconds become decimal — without
     * moving anything between them. Used when a field loses focus.
     *
     * <p>A field holding a <b>whole pair</b> is deliberately left alone: collapsing it to
     * just its latitude here would throw the longitude away before {@link #distribute}
     * ever got the chance to split it. That is how a same-line pair used to lose half of
     * itself when the user simply tapped the other field.</p>
     */
    public static Fields normalise(String latText, String lngText) {
        CoordText.Scan latScan = CoordText.scan(latText);
        CoordText.Scan lngScan = CoordText.scan(lngText);
        String lat = latScan.hasLat() && !latScan.complete()
                ? Formats.shortCoord(latScan.latitude) : latText;
        String lng = lngScan.hasLng() && !lngScan.complete()
                ? Formats.shortCoord(lngScan.longitude) : lngText;
        return new Fields(lat, lng);
    }

    /**
     * Moves values to the field they belong in.
     *
     * <p>Fields that are not involved keep their text untouched, so calling this is never
     * destructive to a value that is already in the right place.</p>
     */
    public static Fields distribute(String latText, String lngText) {
        CoordText.Scan latScan = CoordText.scan(latText);
        CoordText.Scan lngScan = CoordText.scan(lngText);

        // One field holds both values — the same-line paste case: split them across the
        // two fields instead of leaving the longitude stranded in the latitude box.
        if (latScan.complete()) {
            return new Fields(Formats.shortCoord(latScan.latitude),
                    Formats.shortCoord(latScan.longitude));
        }
        if (lngScan.complete()) {
            return new Fields(Formats.shortCoord(lngScan.latitude),
                    Formats.shortCoord(lngScan.longitude));
        }

        // A field holds only the other kind of value: move it across.
        boolean latHoldsLongitude = latScan.hasLng() && !latScan.hasLat();
        boolean lngHoldsLatitude = lngScan.hasLat() && !lngScan.hasLng();
        if (latHoldsLongitude && !lngScan.hasLng()) {
            return new Fields("", Formats.shortCoord(latScan.longitude));
        }
        if (lngHoldsLatitude && !latScan.hasLat()) {
            return new Fields(Formats.shortCoord(lngScan.latitude), "");
        }

        // A single value written as degrees/minutes/seconds becomes decimal.
        String lat = latScan.hasLat() && !latScan.hasLng()
                ? Formats.shortCoord(latScan.latitude) : latText;
        String lng = lngScan.hasLng() && !lngScan.hasLat()
                ? Formats.shortCoord(lngScan.longitude) : lngText;
        return new Fields(lat, lng);
    }

    /** What a pasted block should put into the two fields, whatever field it landed in. */
    public static Fields fromPasted(String text) {
        CoordText.Scan scan = CoordText.scan(text);
        if (scan.complete()) {
            return new Fields(Formats.shortCoord(scan.latitude),
                    Formats.shortCoord(scan.longitude));
        }
        if (scan.hasLat()) {
            return new Fields(Formats.shortCoord(scan.latitude), "");
        }
        if (scan.hasLng()) {
            return new Fields("", Formats.shortCoord(scan.longitude));
        }
        // Nothing recognised: keep the text where the user can see and fix it.
        return new Fields(text.trim(), "");
    }
}
