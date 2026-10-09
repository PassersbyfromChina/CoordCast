import com.coordcast.castcore.CoordPoint;
import com.coordcast.castcore.CoordSystem;
import com.coordcast.castcore.CoordText;
import com.coordcast.castcore.FieldBinder;
import com.coordcast.castcore.Formats;
import com.coordcast.castmap.MapApp;
import com.coordcast.castmap.MapLinks;

/**
 * What a developer does with these libraries, in about thirty lines.
 *
 * <p>Compile against the jars in {@code dist/}:</p>
 *
 * <pre>
 *   javac -cp "dist/castcore-1.7.2.jar;dist/castmap-1.7.2.jar" samples/Sample.java
 *   java  -cp "dist/castcore-1.7.2.jar;dist/castmap-1.7.2.jar;." Sample
 * </pre>
 *
 * <p>Nothing here needs Android: {@code castcore} is pure Java, and the URI builders in
 * {@code castmap} are too. Only {@code MapAppFinder} and {@code AmapLauncher} touch
 * {@code android.*}.</p>
 */
public final class Sample {

    public static void main(String[] args) {
        // 1. Recognition. Hand it whatever the user typed or pasted; it never throws.
        String input = "上海市黄浦区人民大道200号 31.13.49.4 121.28.25.3";
        CoordText.Scan scan = CoordText.scan(input);

        if (!scan.complete()) {
            System.out.println("not a coordinate pair: " + scan.issue);
            return;
        }
        System.out.println("latitude  = " + Formats.shortCoord(scan.latitude)
                + "   (from \"" + scan.latRaw + "\")");
        System.out.println("longitude = " + Formats.shortCoord(scan.longitude)
                + "   (from \"" + scan.lngRaw + "\")");
        System.out.println("DMS       = " + CoordText.toDms(scan.latitude, true));

        // 2. Datum. Say what the numbers are; ask for what the map wants.
        CoordPoint point = new CoordPoint(scan.latitude, scan.longitude, CoordSystem.WGS84);
        double[] gcj = point.gcj02();
        double[] wgs = point.wgs84();
        System.out.println("GCJ-02    = " + point.gcj02Text()
                + "   (shifted " + Formats.meters(point.shiftMeters()) + ")");
        System.out.println("BD-09     = " + Formats.coord(point.bd09()[0]) + ", "
                + Formats.coord(point.bd09()[1]));

        // 3. Deep links. One per map app, each in the dialect that app documents.
        System.out.println();
        for (MapApp app : new MapApp[]{MapApp.AMAP, MapApp.BAIDU, MapApp.TENCENT, MapApp.GOOGLE}) {
            double[] c = app.wantsGcj02() ? gcj : wgs;
            System.out.println(app.label());
            System.out.println("  view  " + MapLinks.view(app, "目的地", c[0], c[1]));
            System.out.println("  route " + MapLinks.route(app, "目的地", c[0], c[1]));
        }

        // 4. Two input fields. This is the whole policy the app runs on every paste.
        System.out.println();
        FieldBinder.Fields fields = FieldBinder.distribute("31.2304,121.4737", "");
        System.out.println("one-line paste -> latitude=\"" + fields.latitude
                + "\" longitude=\"" + fields.longitude + "\"");
    }

    /** Kept out of the library so the sample shows the conversion explicitly. */
    private static final class GeoConverterShim {
        static double[] toWgs84(double lat, double lng) {
            return com.coordcast.castcore.GeoConverter.convert(
                    lat, lng, CoordSystem.GCJ02, CoordSystem.WGS84);
        }
    }

    private Sample() {
    }
}
