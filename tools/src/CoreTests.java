import com.coordcast.castmap.AmapUris;
import com.coordcast.castcore.CoordSystem;
import com.coordcast.castcore.CoordText;
import com.coordcast.castcore.CoordPoint;
import com.coordcast.castcore.FieldBinder;
import com.coordcast.castcore.Formats;
import com.coordcast.castcore.GeoConverter;
import com.coordcast.castmap.MapApp;
import com.coordcast.castmap.MapLinks;

/**
 * Desktop unit tests for the pure-Java core (no Android required).
 * Run: java -cp out CoreTests
 */
public final class CoreTests {

    private static int passed = 0;
    private static int failed = 0;

    private static final double SH_LAT = 31.2304;
    private static final double SH_LNG = 121.4737;

    public static void main(String[] args) {
        decimalForms();
        labelsAndPrefixes();
        hemisphereForms();
        dmsForms();
        dottedDms();
        chineseDms();
        bareDms();
        compactDms();
        runInterpretation();
        lineBoundaries();
        globalAssignment();
        wrongFieldValues();
        binderDistribute();
        binderPaste();
        incompleteAndInvalid();
        normalisation();
        dmsFormatting();
        geoRanges();
        geoRoundTrip();
        geoTokyo();
        geoShiftMagnitude();
        uris();
        mapLinks();

        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // --------------------------------------------------------- decimal input

    private static void decimalForms() {
        section("decimal: every separator people paste");
        checkPair("comma+space", "31.2304, 121.4737", SH_LAT, SH_LNG);
        checkPair("comma only", "31.2304,121.4737", SH_LAT, SH_LNG);
        checkPair("space", "31.2304 121.4737", SH_LAT, SH_LNG);
        checkPair("newline", "31.2304\n121.4737", SH_LAT, SH_LNG);
        checkPair("crlf newline", "31.2304\r\n121.4737", SH_LAT, SH_LNG);
        checkPair("semicolon", "31.2304;121.4737", SH_LAT, SH_LNG);
        checkPair("pipe", "31.2304|121.4737", SH_LAT, SH_LNG);
        checkPair("slashes", "31.2304/121.4737", SH_LAT, SH_LNG);
        checkPair("extra blank lines", "\n\n  31.2304 ,\n\n  121.4737  \n", SH_LAT, SH_LNG);
        checkPair("full-width comma", "31.2304，121.4737", SH_LAT, SH_LNG);
        checkPair("tabs", "31.2304\t121.4737", SH_LAT, SH_LNG);
        checkPair("integers", "31,121", 31.0, 121.0);
        checkPair("negative west", "-31.2304 -121.4737", -31.2304, -121.4737);
    }

    private static void labelsAndPrefixes() {
        section("labels and arbitrary prefixes");
        checkPair("chinese labels", "纬度: 31.2304 经度: 121.4737", SH_LAT, SH_LNG);
        checkPair("chinese labels no space", "纬度31.2304经度121.4737", SH_LAT, SH_LNG);
        checkPair("english labels", "lat: 31.2304 lng: 121.4737", SH_LAT, SH_LNG);
        checkPair("latitude/longitude", "latitude 31.2304 longitude 121.4737", SH_LAT, SH_LNG);
        checkPair("lon abbreviation", "lat 31.2304 lon 121.4737", SH_LAT, SH_LNG);
        checkPair("position prefix", "我的位置：31.2304, 121.4737", SH_LAT, SH_LNG);
        checkPair("gps prefix", "GPS 31.2304, 121.4737", SH_LAT, SH_LNG);
        checkPair("long sentence", "地址是上海市黄浦区人民大道200号 坐标 31.2304, 121.4737 谢谢",
                SH_LAT, SH_LNG);
        checkPair("label after value", "31.2304 纬度 121.4737 经度", SH_LAT, SH_LNG);
        checkPair("shared link text", "我在高德地图等你 https://x.cn/a?lat=31.2304&lng=121.4737",
                SH_LAT, SH_LNG);

        // Reversed order with no labels: the value above 90 gives it away.
        checkPair("reversed order", "121.4737,31.2304", SH_LAT, SH_LNG);
        checkPair("reversed with newline", "121.4737\n31.2304", SH_LAT, SH_LNG);

        CoordText.Scan s = CoordText.scan("纬度 31.2304 经度 121.4737");
        isTrue("lat marked as clue-based", s.latFromClue);
        isTrue("lng marked as clue-based", s.lngFromClue);
    }

    private static void hemisphereForms() {
        section("hemisphere letters");
        checkPair("leading letters", "N31.2304 E121.4737", SH_LAT, SH_LNG);
        checkPair("leading letters spaced", "N 31.2304 E 121.4737", SH_LAT, SH_LNG);
        checkPair("trailing letters", "31.2304N 121.4737E", SH_LAT, SH_LNG);
        checkPair("trailing lowercase", "31.2304n 121.4737e", SH_LAT, SH_LNG);
        checkPair("trailing letters spaced", "31.2304 N 121.4737 E", SH_LAT, SH_LNG);
        checkPair("mixed", "31.2304N E121.4737", SH_LAT, SH_LNG);
        checkPair("south and west", "S31.2304 W121.4737", -31.2304, -121.4737);
        checkPair("south and west trailing", "31.2304S 121.4737W", -31.2304, -121.4737);

        CoordText.Scan s = CoordText.scan("N31.2304 E121.4737");
        isTrue("hemisphere drives latitude", s.latFromClue);
        isTrue("hemisphere drives longitude", s.lngFromClue);
    }

    // ------------------------------------------------------------- dms input

    private static void dmsForms() {
        section("degrees / minutes / seconds");
        // 31 + 13/60 + 49.4/3600 = 31.230389 ; 121 + 28/60 + 25.3/3600 = 121.473694
        checkPair("symbols", "31°13'49.4\"N 121°28'25.3\"E", 31.230389, 121.473694);
        checkPair("symbols no space", "31°13'49.4\"N,121°28'25.3\"E", 31.230389, 121.473694);
        checkPair("unicode primes", "31°13′49.4″N 121°28′25.3″E", 31.230389, 121.473694);
        checkPair("curly quotes", "31°13’49.4”N 121°28’25.3”E", 31.230389, 121.473694);
        checkPair("masculine ordinal", "31º13'49.4\"N 121º28'25.3\"E", 31.230389, 121.473694);
        checkPair("no seconds", "31°13'N 121°28'E", 31.216667, 121.466667);
        checkPair("decimal minutes", "31°13.823'N 121°28.42'E", 31.230383, 121.473667);
        checkPair("spaces instead of symbols", "31° 13' 49.4\" N 121° 28' 25.3\" E",
                31.230389, 121.473694);
        checkPair("degrees only", "31°N 121°E", 31.0, 121.0);
        checkPair("seconds without minutes symbol", "31°49.4\"N 121°25.3\"E", 31.013722, 121.007028);
    }

    private static void chineseDms() {
        section("chinese units");
        checkPair("度分秒北纬东经", "北纬31度13分49.4秒 东经121度28分25.3秒",
                31.230389, 121.473694);
        checkPair("度分秒南纬西经", "南纬31度13分49.4秒 西经121度28分25.3秒",
                -31.230389, -121.473694);
        checkPair("labels plus units", "纬度31度13分49.4秒 经度121度28分25.3秒",
                31.230389, 121.473694);
        checkPair("chinese units after number", "31度13分49.4秒N 121度28分25.3秒E",
                31.230389, 121.473694);
    }

    private static void bareDms() {
        section("bare numbers (no symbols at all)");
        checkPair("three numbers spaced", "31 13 49.4 N 121 28 25.3 E", 31.230389, 121.473694);
        checkPair("newline separated", "31 13 49.4 N\n121 28 25.3 E", 31.230389, 121.473694);
        checkPair("decimal minutes with hemisphere", "31 13.823 N 121 28.42 E", 31.230383, 121.473667);
        // No hemisphere: three bare integers are still read as DMS.
        checkPair("three numbers no hemisphere", "31 13 49 121 28 25",
                31.230278, 121.473611);
        // Two plain integers stay two coordinates (31, 121).
        checkPair("two integers stay coordinates", "31 121", 31.0, 121.0);
    }

    private static void compactDms() {        section("compact DDMMSS");
        checkPair("compact lat/lng", "311349.4N 1212825.3E", 31.230389, 121.473694);
        checkPair("compact with prefix text", "位置 311349.4N 1212825.3E 结束", 31.230389, 121.473694);
        // 7-digit longitude form DDDMMSS
        checkPair("seven digit longitude", "311349.4N 1212825.3E", 31.230389, 121.473694);

        // A plain decimal with a hemisphere must NOT be expanded.
        CoordText.Scan s = CoordText.scan("121.4737E");
        isTrue("decimal with E is not compact dms", s.hasLng() && Math.abs(s.longitude - 121.4737) < 1e-9);
    }

    // ------------------------------------------------- dots used as DMS marks

    /**
     * The reported bug: people write 31°13'49.4" as {@code 31.13.49.4}. The dots must
     * belong to ONE angle, never split it into "31.13" and "49.4" — that split is what
     * pushed half of the input into the other field.
     */
    private static void dottedDms() {
        section("dms written with dots / dashes / slashes");
        checkPair("dots with decimal seconds", "31.13.49.4 121.28.25.3", 31.230389, 121.473694);
        checkPair("dots, whole seconds", "31.13.49 121.28.25", 31.230278, 121.473611);
        checkPair("dots, chinese full stop", "31。13。49.4 121。28。25.3", 31.230389, 121.473694);
        checkPair("dashes", "31-13-49.4 121-28-25.3", 31.230389, 121.473694);
        checkPair("slashes", "31/13/49.4 121/28/25.3", 31.230389, 121.473694);
        checkPair("dots with hemispheres", "31.13.49.4N 121.28.25.3E", 31.230389, 121.473694);
        checkPair("colons", "31:13:49.4 121:28:25.3", 31.230389, 121.473694);
        checkPair("colons with labels", "纬度:31:13:49.4 经度:121:28:25.3", 31.230389, 121.473694);

        // The single most important assertion: one dotted angle stays ONE value and does
        // not leak into the longitude field.
        CoordText.Scan s = CoordText.scan("31.13.49.4");
        isTrue("dotted dms is a latitude", s.hasLat());
        near("dotted dms value", 31.230389, s.latitude, 1e-6);
        isFalse("dotted dms does NOT also become a longitude", s.hasLng());
        eq("dotted dms is partial", CoordText.Issue.PARTIAL, s.issue);

        // A longitude-shaped dotted angle lands in the longitude field by itself.
        s = CoordText.scan("121.28.25.3");
        isFalse("dotted longitude is not a latitude", s.hasLat());
        isTrue("dotted longitude recognised", s.hasLng());
        near("dotted longitude value", 121.473694, s.longitude, 1e-6);

        // One dot is still a decimal, not degrees-and-minutes.
        s = CoordText.scan("31.2304");
        near("one dot stays decimal", 31.2304, s.latitude, 1e-9);

        // Two values joined by a dot separator are still two decimals.
        checkPair("decimal pair with dot separators", "31.2304.121.4737",
                31.2304, 121.4737);

        // A date must not be read as degrees/minutes/seconds.
        s = CoordText.scan("2024-10-07");
        eq("a date is not a coordinate", CoordText.Issue.EMPTY, s.issue);
        s = CoordText.scan("2024.10.07");
        eq("a dotted date is not a coordinate", CoordText.Issue.EMPTY, s.issue);
    }

    private static void runInterpretation() {
        section("number-run interpretation");
        near("integer", 31.0, CoordText.interpretRun("31"), 1e-9);
        near("decimal", 31.2304, CoordText.interpretRun("31.2304"), 1e-9);
        near("dms dots", 31.230278, CoordText.interpretRun("31.13.49"), 1e-6);
        near("dms dots + fraction", 31.230389, CoordText.interpretRun("31.13.49.4"), 1e-6);
        near("dms dashes", 31.230389, CoordText.interpretRun("31-13-49.4"), 1e-6);
        near("dms slashes", 31.230278, CoordText.interpretRun("31/13/49"), 1e-6);
        near("longitude dms", 121.473694, CoordText.interpretRun("121.28.25.3"), 1e-6);
        near("colon dms", 31.230389, CoordText.interpretRun("31:13:49.4"), 1e-6);
        // Two numbers joined by a dash are degrees and minutes, not a fraction.
        near("dash pair is degrees and minutes", 31.216667, CoordText.interpretRun("31-13"), 1e-6);
        near("dot pair is still a decimal", 31.2304, CoordText.interpretRun("31.2304"), 1e-9);

        isTrue("date rejected", CoordText.interpretRun("2024-10-07") == null);
        isTrue("minutes over 60 rejected", CoordText.interpretRun("31.70.00") == null);
        isTrue("degrees over 180 rejected", CoordText.interpretRun("190.13.49") == null);
        isTrue("phone number rejected", CoordText.interpretRun("138-1234-5678") == null);
    }

    /**
     * A newline must be a real boundary. Half an angle on one line and half on the next
     * is a broken paste, not a coordinate, and must not be silently glued together.
     */
    private static void lineBoundaries() {
        section("line boundaries");

        // One complete angle per line is the normal multi-line paste.
        checkPair("one angle per line", "31 13 49.4 N\n121 28 25.3 E", 31.230389, 121.473694);
        checkPair("decimals on separate lines", "31.2304\n121.4737", 31.2304, 121.4737);
        checkPair("dotted dms on separate lines", "31.13.49.4\n121.28.25.3", 31.230389, 121.473694);

        // A bare DMS spread over two lines must NOT be assembled.
        CoordText.Scan s = CoordText.scan("31 13\n49.4 N");
        isFalse("dms split across a newline stays incomplete", s.complete());
        if (s.hasLat()) {
            isTrue("split dms did not produce 31.230389",
                    Math.abs(s.latitude - 31.230389) > 1e-3);
        }

        // Symbol-separated halves on different lines are never glued into one angle.
        s = CoordText.scan("31°13'\n49.4\"N");
        isTrue("neither half became the joined value",
                (s.latitude == null || Math.abs(s.latitude - 31.230389) > 1e-3)
                        && (s.longitude == null || Math.abs(s.longitude - 31.230389) > 1e-3));

        // A hemisphere on the next line does not reach back up.
        s = CoordText.scan("31.13.49.4\nN");
        isTrue("value still read", s.hasLat());
        isTrue("hemisphere on the next line is ignored", s.latitude > 0);

        // CRLF is the same boundary.
        s = CoordText.scan("31 13\r\n49.4 N");
        isFalse("crlf also separates", s.complete());
    }

    /**
     * Latitude and longitude are decided from the whole text, so a stray number earlier
     * in the line cannot steal the latitude slot.
     */
    private static void globalAssignment() {
        section("whole-text assignment");

        checkPair("stray house number", "3号楼 31.2304, 121.4737", 31.2304, 121.4737);
        checkPair("stray number after", "31.2304, 121.4737 共3层", 31.2304, 121.4737);
        checkPair("address with coordinates",
                "上海市黄浦区人民大道200号 31.2304,121.4737", 31.2304, 121.4737);
        checkPair("reversed pair", "121.4737,31.2304", 31.2304, 121.4737);
        checkPair("reversed pair no separator", "121.4737 31.2304", 31.2304, 121.4737);
        checkPair("year in the text", "2024年 31.2304,121.4737", 31.2304, 121.4737);

        // Two plain values with nothing to go on still follow the natural reading order.
        CoordText.Scan s = CoordText.scan("45.5, 31.2");
        near("ambiguous first is the latitude", 45.5, s.latitude, 1e-9);
        near("ambiguous second is the longitude", 31.2, s.longitude, 1e-9);

        // A latitude is never claimed by a value that is impossible as one.
        s = CoordText.scan("121.4737 39.9042");
        near("large value becomes the longitude", 121.4737, s.longitude, 1e-9);
        near("small value becomes the latitude", 39.9042, s.latitude, 1e-9);
    }

    // ------------------------------------------------------------ field logic

    private static void wrongFieldValues() {
        section("values typed into the wrong field");
        // A lone value above 90 is recognised as a longitude, so the UI can move it.
        CoordText.Scan s = CoordText.scan("121.4737");
        isFalse("no latitude", s.hasLat());
        isTrue("recognised as longitude", s.hasLng());
        near("longitude value", 121.4737, s.longitude, 1e-9);

        s = CoordText.scan("31.2304");
        isTrue("ambiguous lone value is a latitude", s.hasLat());
        isFalse("and not a longitude", s.hasLng());

        // A value that fits nowhere keeps both fields empty and reports a range problem.
        s = CoordText.scan("200.0");
        isTrue("out of range flagged", s.issue == CoordText.Issue.RANGE || s.issue == CoordText.Issue.PARTIAL);

        s = CoordText.scan("经度 121.4737");
        isTrue("label alone fills longitude", s.hasLng());
        isFalse("no latitude from a longitude label", s.hasLat());
    }

    private static void incompleteAndInvalid() {
        section("incomplete and invalid input");
        CoordText.Scan s = CoordText.scan("");
        eq("empty issue", CoordText.Issue.EMPTY, s.issue);
        s = CoordText.scan(null);
        eq("null issue", CoordText.Issue.EMPTY, s.issue);
        s = CoordText.scan("这里没有坐标");
        eq("text without numbers", CoordText.Issue.EMPTY, s.issue);
        s = CoordText.scan("31.2304");
        eq("single value", CoordText.Issue.PARTIAL, s.issue);
        s = CoordText.scan("31.2304,121.4737");
        isTrue("complete has no issue", s.issue == null);

        s = CoordText.scan("31°70'00\"N 121°28'25.3\"E");
        eq("minutes above 60", CoordText.Issue.MINUTES, s.issue);

        // A lone value above 90 can only be a longitude.
        s = CoordText.scan("95.0");
        isTrue("lone 95 becomes longitude", s.hasLng() && Math.abs(s.longitude - 95.0) < 1e-9);

        // Two values that are both impossible as a latitude are reported, not guessed at.
        s = CoordText.scan("95.0, 121.4737");
        isFalse("two impossible latitudes are not paired into a point", s.complete());
        eq("and it is reported as out of range", CoordText.Issue.RANGE, s.issue);
    }

    private static void normalisation() {
        section("normalisation");
        eq("chinese north", "N31", CoordText.normalize("北纬31"));
        eq("chinese east", "E121", CoordText.normalize("东经121"));
        eq("chinese units", "31°13'49.4\"", CoordText.normalize("31度13分49.4秒"));
        eq("labels", "LAT31LNG121", CoordText.normalize("纬度31经度121"));
        eq("latin labels", "LAT31LNG121", CoordText.normalize("lat31lng121"));
        eq("full width", "31.2304,121.4737", CoordText.normalize("31.2304，121.4737"));
        eq("unicode primes", "31°13'49.4\"", CoordText.normalize("31°13′49.4″"));
        eq("compact expansion", "31°13'49.4\"N",
                CoordText.expandCompactDms("311349.4N"));
        eq("compact untouched when decimal", "121.4737E",
                CoordText.expandCompactDms("121.4737E"));
        eq("compact rejects bad seconds", "311394N",
                CoordText.expandCompactDms("311394N"));
    }

    private static void dmsFormatting() {
        section("dms formatting");
        eq("latitude", "31°13'49.4\"N", CoordText.toDms(31.230389, true));
        eq("longitude", "121°28'25.3\"E", CoordText.toDms(121.473694, false));
        eq("south", "31°13'49.4\"S", CoordText.toDms(-31.230389, true));
        eq("west", "121°28'25.3\"W", CoordText.toDms(-121.473694, false));
    }

    // ------------------------------------------------------------ converter

    private static void geoRanges() {
        section("geo: china envelope");
        isFalse("shanghai inside", GeoConverter.outOfChina(31.23, 121.47));
        isFalse("beijing inside", GeoConverter.outOfChina(39.90, 116.40));
        isTrue("tokyo outside", GeoConverter.outOfChina(35.68, 139.65));
        isTrue("london outside", GeoConverter.outOfChina(51.50, -0.12));
    }

    private static void geoRoundTrip() {
        section("geo: round trips");
        double[][] samples = {
                {31.2304, 121.4737},
                {39.9042, 116.4074},
                {22.5431, 114.0579},
                {29.5630, 106.5516},
                {43.8256, 87.6168},
        };
        for (double[] s : samples) {
            double[] gcj = GeoConverter.wgs84ToGcj02(s[0], s[1]);
            double[] back = GeoConverter.gcj02ToWgs84(gcj[0], gcj[1]);
            near("wgs->gcj->wgs lat " + s[0], s[0], back[0], 1e-8);
            near("wgs->gcj->wgs lng " + s[1], s[1], back[1], 1e-8);

            double[] bd = GeoConverter.gcj02ToBd09(gcj[0], gcj[1]);
            double[] backGcj = GeoConverter.bd09ToGcj02(bd[0], bd[1]);
            near("gcj->bd->gcj lat " + s[0], gcj[0], backGcj[0], 1e-5);
            near("gcj->bd->gcj lng " + s[1], gcj[1], backGcj[1], 1e-5);

            double[] viaBd = GeoConverter.convert(s[0], s[1], CoordSystem.WGS84, CoordSystem.BD09);
            double[] backWgs = GeoConverter.convert(viaBd[0], viaBd[1], CoordSystem.BD09, CoordSystem.WGS84);
            near("wgs->bd->wgs lat " + s[0], s[0], backWgs[0], 1e-5);
            near("wgs->bd->wgs lng " + s[1], s[1], backWgs[1], 1e-5);
            isTrue("bd round trip under 1m " + s[0],
                    GeoConverter.distanceMeters(s[0], s[1], backWgs[0], backWgs[1]) < 1.0);
        }
    }

    private static void geoTokyo() {
        section("geo: outside china is unchanged");
        double[] gcj = GeoConverter.wgs84ToGcj02(35.6762, 139.6503);
        near("tokyo lat unchanged", 35.6762, gcj[0], 1e-12);
        near("tokyo lng unchanged", 139.6503, gcj[1], 1e-12);
    }

    private static void geoShiftMagnitude() {
        section("geo: offset magnitude is plausible");
        double[] gcj = GeoConverter.wgs84ToGcj02(39.90869, 116.39746); // Tiananmen, WGS-84
        System.out.println("    Tiananmen WGS-84 -> GCJ-02: "
                + Formats.coord(gcj[0]) + ", " + Formats.coord(gcj[1]));
        double dLat = gcj[0] - 39.90869;
        double dLng = gcj[1] - 116.39746;
        isTrue("dLat in (0.0003, 0.006): " + dLat, dLat > 0.0003 && dLat < 0.006);
        isTrue("dLng in (0.002, 0.010): " + dLng, dLng > 0.002 && dLng < 0.010);
        double shift = GeoConverter.distanceMeters(39.90869, 116.39746, gcj[0], gcj[1]);
        System.out.println("    shift = " + Formats.meters(shift));
        isTrue("shift 100m..900m: " + shift, shift > 100 && shift < 900);
    }

    // ------------------------------------------------------------------ uris

    private static void uris() {
        section("uri: view");
        String view = AmapUris.viewMap("坐标点", 31.2397, 121.4998);
        System.out.println("    " + view);
        isTrue("viewMap scheme", view.startsWith("androidamap://viewMap?"));
        isTrue("source", view.contains("sourceApplication=" + AmapUris.SOURCE));
        isTrue("poiname present",
                view.contains("poiname=%E5%9D%90%E6%A0%87%E7%82%B9"));
        isTrue("lat 6dp", view.contains("lat=31.239700"));
        isTrue("lon 6dp", view.contains("lon=121.499800"));
        isTrue("dev=0 means GCJ-02", view.contains("dev=0"));
        isFalse("no spaces", view.contains(" "));

        section("uri: route");
        String route = AmapUris.routePlan("坐标点", 31.2397, 121.4998, 0);
        System.out.println("    " + route);
        isTrue("amapuri scheme with trailing slash", route.startsWith("amapuri://route/plan/?"));
        isTrue("dlat", route.contains("dlat=31.239700"));
        isTrue("dlon", route.contains("dlon=121.499800"));
        isTrue("t param", route.contains("&t=0"));
        isFalse("no origin means current location", route.contains("slat="));
        isFalse("no waypoints", route.contains("vian="));

        section("uri: web fallbacks");
        String web = AmapUris.webMarker("坐标点", 31.2397, 121.4998);
        System.out.println("    " + web);
        isTrue("https marker", web.startsWith("https://uri.amap.com/marker?"));
        isTrue("position is lon,lat", web.contains("position=121.499800,31.239700"));
        isTrue("coordinate gaode", web.contains("coordinate=gaode"));
        isTrue("callnative", web.contains("callnative=1"));

        String webRoute = AmapUris.webRoute("坐标点", 31.2397, 121.4998);
        System.out.println("    " + webRoute);
        isTrue("navigation url not /route", webRoute.startsWith("https://uri.amap.com/navigation?"));
        isTrue("empty origin = current location", webRoute.contains("from=&to="));

        section("uri: blank name falls back");
        isTrue("blank name defaulted",
                AmapUris.viewMap("", 1.0, 2.0).contains("poiname=%E5%9D%90%E6%A0%87%E7%82%B9"));
        isTrue("null name defaulted",
                AmapUris.viewMap(null, 1.0, 2.0).contains("poiname=%E5%9D%90%E6%A0%87%E7%82%B9"));

        section("uri: coordinate point plumbing");
        CoordPoint p = new CoordPoint(SH_LAT, SH_LNG, CoordSystem.WGS84);
        isTrue("valid", p.isValid());
        double[] gcj = p.gcj02();
        near("gcj lat", 31.228458, gcj[0], 1e-6);
        near("gcj lng", 121.478223, gcj[1], 1e-6);
        isTrue("shift shown", p.shiftMeters() > 400 && p.shiftMeters() < 600);
        isTrue("gcj02 text", p.gcj02Text().startsWith("31.2284"));

        CoordPoint gcjIn = new CoordPoint(31.228458, 121.478223, CoordSystem.GCJ02);
        near("gcj passthrough lat", 31.228458, gcjIn.gcj02()[0], 1e-9);
        isTrue("gcj passthrough has no shift", gcjIn.shiftMeters() < 0.001);

        CoordPoint bd = new CoordPoint(31.234492, 121.484492, CoordSystem.BD09);
        isTrue("bd09 converts", Math.abs(bd.gcj02()[0] - 31.228458) < 0.002);
    }

    // ------------------------------------------------- field binding policy

    /**
     * The reported bug: latitude and longitude arrive on ONE line, so a single field ends
     * up holding both values. Distributing has to split them across the two fields.
     */
    private static void binderDistribute() {
        section("field binding: a same-line pair sitting in one field");

        FieldBinder.Fields f = FieldBinder.distribute("31.2304,121.4737", "");
        eq("lat box keeps the latitude", "31.2304", f.latitude);
        eq("lng box receives the longitude", "121.4737", f.longitude);

        f = FieldBinder.distribute("", "31.2304,121.4737");
        eq("pasted into the lng box: latitude", "31.2304", f.latitude);
        eq("pasted into the lng box: longitude", "121.4737", f.longitude);

        f = FieldBinder.distribute("31.2304 121.4737", "");
        eq("space separated: latitude", "31.2304", f.latitude);
        eq("space separated: longitude", "121.4737", f.longitude);

        f = FieldBinder.distribute("121.4737,31.2304", "");
        eq("reversed pair: latitude", "31.2304", f.latitude);
        eq("reversed pair: longitude", "121.4737", f.longitude);

        f = FieldBinder.distribute("31.13.49.4 121.28.25.3", "");
        eq("dotted dms pair: latitude", "31.230389", f.latitude);
        eq("dotted dms pair: longitude", "121.473694", f.longitude);

        section("field binding: nothing is moved unless it is warranted");

        f = FieldBinder.distribute("31.2304", "121.4737");
        eq("latitude untouched", "31.2304", f.latitude);
        eq("longitude untouched", "121.4737", f.longitude);

        // One dotted angle stays in its own field and never leaks into the other.
        f = FieldBinder.distribute("31.13.49.4", "");
        eq("dotted dms stays put", "31.230389", f.latitude);
        eq("dotted dms does not leak", "", f.longitude);

        // A longitude typed into the latitude box moves down.
        f = FieldBinder.distribute("121.4737", "");
        eq("moved across: latitude box empties", "", f.latitude);
        eq("moved across: longitude arrives", "121.4737", f.longitude);

        // Degrees/minutes/seconds in a single field become decimal in place.
        f = FieldBinder.distribute("31 13 49.4 N", "121 28 25.3 E");
        eq("dms normalised in the lat box", "31.230389", f.latitude);
        eq("dms normalised in the lng box", "121.473694", f.longitude);

        // Text that is not a coordinate is left exactly as it is.
        f = FieldBinder.distribute("hello", "");
        eq("unreadable latitude left alone", "hello", f.latitude);
        eq("longitude still empty", "", f.longitude);

        section("field binding: the order the UI runs when focus leaves a field");

        // normalise() must never destroy a pair that distribute() has not split yet —
        // getting this order wrong silently dropped the longitude on a real device.
        FieldBinder.Fields n = FieldBinder.normalise("31.2304,121.4737", "");
        eq("a whole pair is not collapsed", "31.2304,121.4737", n.latitude);
        eq("and nothing is invented", "", n.longitude);

        FieldBinder.Fields d = FieldBinder.distribute(n.latitude, n.longitude);
        eq("focus loss: latitude", "31.2304", d.latitude);
        eq("focus loss: longitude", "121.4737", d.longitude);

        n = FieldBinder.normalise("", "31.13.49.4 121.28.25.3");
        d = FieldBinder.distribute(n.latitude, n.longitude);
        eq("focus loss from the lng box: latitude", "31.230389", d.latitude);
        eq("focus loss from the lng box: longitude", "121.473694", d.longitude);

        // A single value still gets normalised on focus loss.
        n = FieldBinder.normalise("31 13 49.4 N", "");
        eq("a lone angle is still normalised", "31.230389", n.latitude);
    }

    /** What a paste should put into the fields, whichever field received it. */
    private static void binderPaste() {
        section("field binding: pasted blocks");

        FieldBinder.Fields f = FieldBinder.fromPasted("31.2304,121.4737");
        eq("pair: latitude", "31.2304", f.latitude);
        eq("pair: longitude", "121.4737", f.longitude);

        f = FieldBinder.fromPasted("31.2304\n121.4737");
        eq("one per line: latitude", "31.2304", f.latitude);
        eq("one per line: longitude", "121.4737", f.longitude);

        f = FieldBinder.fromPasted("3号楼 31.2304, 121.4737");
        eq("address prefix: latitude", "31.2304", f.latitude);
        eq("address prefix: longitude", "121.4737", f.longitude);

        f = FieldBinder.fromPasted("31.13.49.4");
        eq("single dotted dms: latitude", "31.230389", f.latitude);
        eq("single dotted dms: longitude empty", "", f.longitude);

        f = FieldBinder.fromPasted("121.28.25.3");
        eq("single longitude: latitude empty", "", f.latitude);
        eq("single longitude: longitude", "121.473694", f.longitude);

        f = FieldBinder.fromPasted("hello world");
        eq("unreadable text is kept", "hello world", f.latitude);
        eq("unreadable text clears the longitude", "", f.longitude);
    }

    // -------------------------------------------------------------- map links

    /** Every app gets the link its own documentation publishes. */
    private static void mapLinks() {
        section("amap links are unchanged");

        String s = MapLinks.view(MapApp.AMAP, "坐标点", 31.230389, 121.473694);
        isTrue("amap view scheme", s.startsWith("androidamap://viewMap?"));
        isTrue("amap view lat/lon", s.contains("&lat=31.230389&lon=121.473694"));
        isTrue("amap view dev=0", s.contains("&dev=0"));

        s = MapLinks.route(MapApp.AMAP, "坐标点", 31.230389, 121.473694);
        isTrue("amap route scheme", s.startsWith("amapuri://route/plan/?"));
        isTrue("amap route dlat/dlon", s.contains("&dlat=31.230389") && s.contains("&dlon=121.473694"));
        isTrue("amap route dev=0", s.contains("&dev=0"));

        section("baidu links");

        s = MapLinks.view(MapApp.BAIDU, "坐标点", 31.230389, 121.473694);
        isTrue("baidu view scheme", s.startsWith("baidumap://map/marker?"));
        isTrue("baidu view is lat,lng", s.contains("location=31.230389,121.473694"));
        isTrue("baidu view declares gcj02", s.contains("coord_type=gcj02"));
        isTrue("baidu view identifies the app", s.contains("src=andr.coordcast"));

        s = MapLinks.route(MapApp.BAIDU, "坐标点", 31.230389, 121.473694);
        isTrue("baidu route scheme", s.startsWith("baidumap://map/direction?"));
        isTrue("baidu route destination", s.contains("destination=latlng:31.230389,121.473694"));
        isTrue("baidu route drives", s.contains("mode=driving"));
        isTrue("baidu route declares gcj02", s.contains("coord_type=gcj02"));

        section("tencent links");

        s = MapLinks.view(MapApp.TENCENT, "坐标点", 31.230389, 121.473694);
        isTrue("tencent view scheme", s.startsWith("qqmap://map/marker?"));
        isTrue("tencent marker is coord:lat,lng;title:", s.contains("marker=coord:31.230389,121.473694;title:"));
        isFalse("tencent view needs no developer key", s.contains("referer"));

        s = MapLinks.route(MapApp.TENCENT, "坐标点", 31.230389, 121.473694);
        isTrue("tencent route scheme", s.startsWith("qqmap://map/routeplan?"));
        isTrue("tencent route drives", s.contains("type=drive"));
        isTrue("tencent starts from current location", s.contains("fromcoord=CurrentLocation"));
        isTrue("tencent destination", s.contains("tocoord=31.230389,121.473694"));
        isFalse("tencent route needs no developer key", s.contains("referer"));

        section("google links");

        s = MapLinks.view(MapApp.GOOGLE, "坐标点", 31.230389, 121.473694);
        isTrue("google view is a geo uri", s.startsWith("geo:0,0?q=31.230389,121.473694"));
        s = MapLinks.route(MapApp.GOOGLE, "坐标点", 31.230389, 121.473694);
        isTrue("google route scheme", s.startsWith("google.navigation:q=31.230389,121.473694"));
        isTrue("google route drives", s.contains("mode=d"));

        section("generic geo uri, used for every other map app");

        s = MapLinks.view(MapApp.GENERIC, "坐标点", 31.230389, 121.473694);
        isTrue("generic view carries the point", s.startsWith("geo:31.230389,121.473694?q="));
        s = MapLinks.route(MapApp.GENERIC, "坐标点", 31.230389, 121.473694);
        isTrue("generic route carries the point", s.startsWith("geo:31.230389,121.473694?"));

        section("every app is handed the datum it expects");

        for (MapApp app : MapApp.values()) {
            eq(app.name() + " datum", !app.isGeneric(), app.wantsGcj02());
            String view = MapLinks.web(app, null, 31.230389, 121.473694);
            String route = MapLinks.webRoute(app, null, 31.230389, 121.473694);
            isTrue(app.name() + " web view is https", view.startsWith("https://"));
            isTrue(app.name() + " web route is https", route.startsWith("https://"));
            isTrue(app.name() + " web view has no space",
                    MapLinks.view(app, "我的 位置", 31.0, 121.0).indexOf(' ') < 0);
        }

        section("names survive the trip");

        s = MapLinks.view(MapApp.BAIDU, "我的 位置", 31.0, 121.0);
        isTrue("spaces are percent-encoded", s.contains("%20"));
        isFalse("no raw space left in the uri", s.contains(" "));
        isTrue("chinese is utf-8 encoded", s.contains("%E6%88%91"));
    }

    // --------------------------------------------------------------- harness

    private static void checkPair(String what, String input, double lat, double lng) {
        CoordText.Scan s = CoordText.scan(input);
        if (!s.complete()) {
            fail(what + " -> not complete (" + s.issue + ") for <" + input + ">");
            return;
        }
        double dLat = Math.abs(s.latitude - lat);
        double dLng = Math.abs(s.longitude - lng);
        if (dLat <= 1e-5 && dLng <= 1e-5) {
            ok(what);
        } else {
            fail(what + " -> got " + Formats.coord(s.latitude) + "," + Formats.coord(s.longitude)
                    + " expected " + Formats.coord(lat) + "," + Formats.coord(lng)
                    + " for <" + input + ">");
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
    }

    private static void eq(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            ok(what);
        } else {
            fail(what + " -> expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void near(String what, double expected, double actual, double tol) {
        if (Math.abs(expected - actual) <= tol) {
            ok(what);
        } else {
            fail(what + " -> expected " + expected + " ±" + tol + " but was " + actual
                    + " (delta " + Math.abs(expected - actual) + ")");
        }
    }

    private static void isTrue(String what, boolean v) {
        if (v) {
            ok(what);
        } else {
            fail(what + " -> expected true");
        }
    }

    private static void isFalse(String what, boolean v) {
        if (!v) {
            ok(what);
        } else {
            fail(what + " -> expected false");
        }
    }

    private static void ok(String what) {
        passed++;
    }

    private static void fail(String what) {
        failed++;
        System.out.println("  FAIL " + what);
    }
}
