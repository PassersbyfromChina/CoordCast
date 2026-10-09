package com.coordcast.castmap;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out which map apps are installed and in what order to offer them.
 *
 * <p>Two sources. First the apps {@link MapLinks} knows how to build a proper deep link
 * for. Then anything else that answers the standard {@code geo:} URI — that second group
 * is what keeps the list open-ended, since a map app nobody wrote code for still shows
 * up, labelled with whatever it calls itself.</p>
 *
 * <p>Needs the matching {@code <queries>} entries in the manifest: without them Android 11
 * and later hide every other package from {@code PackageManager}.</p>
 */
public final class MapAppFinder {

    /** One launchable target. */
    public static final class Entry {
        public final MapApp app;
        /** The package the intent is pointed at. Never null. */
        public final String packageName;
        public final CharSequence label;
        public final Drawable icon;

        Entry(MapApp app, String packageName, CharSequence label, Drawable icon) {
            this.app = app;
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private MapAppFinder() {
    }

    /** Everything installed that can show a coordinate, best-known targets first. */
    public static List<Entry> find(Context context) {
        PackageManager pm = context.getPackageManager();
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (MapApp app : MapApp.values()) {
            if (app.isGeneric()) {
                continue;
            }
            for (String pkg : app.packages()) {
                if (!seen.add(pkg) || !installed(pm, pkg)) {
                    continue;
                }
                out.add(new Entry(app, pkg, applicationLabel(pm, pkg, app),
                        applicationIcon(pm, pkg)));
                break; // one package per app is enough
            }
        }

        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"));
        for (ResolveInfo info : pm.queryIntentActivities(probe, 0)) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(context.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            out.add(new Entry(MapApp.GENERIC, pkg, info.loadLabel(pm), info.loadIcon(pm)));
        }
        return out;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static CharSequence applicationLabel(PackageManager pm, String pkg, MapApp app) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            return app.label();
        }
    }

    private static Drawable applicationIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }
}
