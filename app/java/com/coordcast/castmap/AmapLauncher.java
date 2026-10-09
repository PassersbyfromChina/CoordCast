package com.coordcast.castmap;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * The only place that touches Android's Intent machinery for Amap.
 *
 * <p>All URI strings come from {@link AmapUris}, which is platform independent and
 * unit tested.</p>
 */
public final class AmapLauncher {

    private AmapLauncher() {
    }

    /** True when either the phone app or the car/HD build of Amap is installed. */
    public static boolean isAmapInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : new String[]{AmapUris.PKG_PHONE, AmapUris.PKG_AUTO}) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
                // try the next candidate
            }
        }
        return false;
    }

    /**
     * {@code ACTION_VIEW} with the given URI. A category is deliberately not added:
     * an intent with no categories matches Amap's declared filters, whereas adding
     * {@code CATEGORY_BROWSABLE} would require Amap to declare it too.
     */
    public static Intent toIntent(String uri, String pkg) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (pkg != null) {
            intent.setPackage(pkg);
        }
        return intent;
    }

    public static boolean canHandle(Context context, Intent intent) {
        return !context.getPackageManager().queryIntentActivities(intent, 0).isEmpty();
    }

    /** Starts the intent, returning false when nothing can handle it. */
    public static boolean launch(Context context, String uri, String pkg) {
        Intent intent = toIntent(uri, pkg);
        if (!canHandle(context, intent)) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Tries several packages in order, e.g. phone Amap then Amap Auto. */
    public static boolean launchAny(Context context, String uri, String... packages) {
        for (String pkg : packages) {
            if (launch(context, uri, pkg)) {
                return true;
            }
        }
        return false;
    }
}
