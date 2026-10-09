package com.coordcast.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import com.coordcast.R;
import com.coordcast.castcore.CoordPoint;
import com.coordcast.castcore.CoordSystem;
import com.coordcast.castcore.CoordText;
import com.coordcast.castcore.FieldBinder;
import com.coordcast.castcore.Formats;
import com.coordcast.castcore.GeoConverter;
import com.coordcast.castui.CastButton;
import com.coordcast.castui.CastColor;
import com.coordcast.castui.CastMotion;
import com.coordcast.castui.CastSegmentedButton;
import com.coordcast.castui.CastShape;
import com.coordcast.castui.CastTextField;
import com.coordcast.castmap.AmapLauncher;
import com.coordcast.castmap.AmapUris;
import com.coordcast.castmap.MapApp;
import com.coordcast.castmap.MapAppFinder;
import com.coordcast.castmap.MapLinks;

/**
 * Two fields, two actions.
 *
 * <p>Latitude and longitude are entered separately, but nothing has to be entered
 * carefully: anything pasted into either field is scanned by {@link CoordText}, and
 * when the text turns out to hold both values they are split across the fields and
 * normalised to decimal degrees. Feedback appears directly under the fields, never
 * anywhere else on the screen.</p>
 */
public class MainActivity extends Activity {

    private static final int KIND_HINT = 0;
    private static final int KIND_OK = 1;
    private static final int KIND_WARN = 2;

    private CoordEditText latInput;
    private CoordEditText lngInput;
    private CastTextField latField;
    private CastTextField lngField;
    private CastSegmentedButton segDatum;
    private ImageView messageIcon;
    private TextView messageText;
    private View scroll;
    private View btnView;
    private View btnRoute;

    private CoordSystem datum = CoordSystem.WGS84;
    private CoordPoint point;

    /** Guards the two-way text synchronisation below. */
    private boolean syncing;
    /** Set by the text selection menu's paste item, consumed by the next text change. */
    private boolean pasteExpected;
    /** Set when a multi-character edit carrying digits arrives, i.e. a keyboard paste. */
    private boolean bulkInsert;

    private int sysTop;
    private int sysBottom;
    private int imeExtra;
    private final int[] scrollBase = new int[4];

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        configureSystemBars();

        if (savedInstanceState != null) {
            datum = CoordSystem.fromIndex(savedInstanceState.getInt("datum", 0));
            latInput.setText(savedInstanceState.getString("lat", ""));
            lngInput.setText(savedInstanceState.getString("lng", ""));
        }
        segDatum.setItems(new String[]{
                CoordSystem.WGS84.display(), CoordSystem.GCJ02.display(), CoordSystem.BD09.display()},
                datum.ordinal());
        segDatum.setOnSegmentSelected(index -> {
            datum = CoordSystem.fromIndex(index);
            updateMessage();
        });

        wireField(latInput, latField);
        wireField(lngInput, lngField);

        CastButton paste = findViewById(R.id.btn_paste);
        CastButton clear = findViewById(R.id.btn_clear);
        paste.setVariant(CastButton.TONAL);
        clear.setVariant(CastButton.TEXT);
        paste.setOnClickListener(v -> pasteFromClipboard());
        clear.setOnClickListener(v -> clearFields());

        ((CastButton) btnView).setVariant(CastButton.OUTLINED);
        ((CastButton) btnRoute).setVariant(CastButton.FILLED);
        btnView.setOnClickListener(v -> openMap(false));
        btnRoute.setOnClickListener(v -> openMap(true));

        installInsets();
        handleIntent(getIntent());
        updateMessage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("lat", latInput.getText().toString());
        outState.putString("lng", lngInput.getText().toString());
        outState.putInt("datum", datum.ordinal());
    }

    // --------------------------------------------------------------- wiring

    private void bindViews() {
        latInput = findViewById(R.id.input_latitude);
        lngInput = findViewById(R.id.input_longitude);
        latField = findViewById(R.id.field_latitude);
        lngField = findViewById(R.id.field_longitude);
        segDatum = findViewById(R.id.seg_datum);
        messageIcon = findViewById(R.id.message_icon);
        messageText = findViewById(R.id.message_text);
        scroll = findViewById(R.id.scroll);
        btnView = findViewById(R.id.btn_view);
        btnRoute = findViewById(R.id.btn_route);

        // The M3 outlined text field floats this label into a notch in its own border.
        latField.setLabel(getString(R.string.label_latitude));
        lngField.setLabel(getString(R.string.label_longitude));

        scrollBase[0] = scroll.getPaddingLeft();
        scrollBase[1] = scroll.getPaddingTop();
        scrollBase[2] = scroll.getPaddingRight();
        scrollBase[3] = scroll.getPaddingBottom();
    }

    private void wireField(final CoordEditText field, final CastTextField wrapper) {
        // A paste reaches the app by one of two routes. The text selection menu reports
        // itself through CoordEditText; a keyboard's own clipboard panel instead commits
        // the text like ordinary input, and is only visible as a sudden multi-character
        // edit. Both signals are honoured. Requiring a digit in the inserted run is what
        // keeps ordinary typing out — a swipe-typed or autocorrected word is letters.
        field.setOnPaste(() -> pasteExpected = true);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bulkInsert = false;
                if (count >= 3) {
                    for (int i = start; i < start + count && i < s.length(); i++) {
                        if (Character.isDigit(s.charAt(i))) {
                            bulkInsert = true;
                            break;
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing) {
                    return;
                }
                if (pasteExpected || bulkInsert) {
                    pasteExpected = false;
                    bulkInsert = false;
                    // One paste can arrive as several edits, so decide once the text has
                    // settled; acting on the first callback would see only half of it.
                    field.post(() -> {
                        if (!syncing) {
                            onPasted(field);
                        }
                    });
                    return;
                }
                updateMessage();
            }
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !syncing) {
                // Split first, normalise second: the other way round a same-line pair is
                // collapsed to its latitude before it ever gets the chance to be split.
                distribute();
                normaliseFields();
                updateMessage();
            }
        });
    }

    /**
     * Handles a paste wherever it landed.
     *
     * <p>What was pasted is now in the field, so that is read first: it is what the user
     * can see, and it is correct even when the text came from a keyboard's own clipboard
     * history rather than the system clipboard. Taking a same-line
     * {@code 31.2304,121.4737} whole is what puts it across the right two fields.</p>
     *
     * <p>Only when the field holds nothing readable does the system clipboard get a turn —
     * that is the case where the paste was inserted into existing text and mangled by the
     * concatenation.</p>
     */
    private void onPasted(CoordEditText field) {
        CoordText.Scan pasted = CoordText.scan(field.getText().toString());
        if (pasted.hasLat() || pasted.hasLng()) {
            distribute();
            updateMessage();
            return;
        }
        String clip = readClipboard();
        if (clip != null) {
            CoordText.Scan fromClip = CoordText.scan(clip);
            if (fromClip.hasLat() || fromClip.hasLng()) {
                applyText(clip);
                return;
            }
        }
        distribute();
        updateMessage();
    }

    /** The clipboard's text, or null when there is nothing readable on it. */
    private String readClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        return TextUtils.isEmpty(text) ? null : text.toString();
    }

    /** Shares a text selection from another app into this one. */
    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (!TextUtils.isEmpty(shared)) {
            applyText(shared);
        }
    }

    // ------------------------------------------------------------ field sync

    /**
     * Moves a value to the field it belongs in.
     *
     * <p>Only called for a real paste or when a field loses focus — never while typing,
     * so a half-finished number is never reinterpreted and moved out from under the
     * cursor. The policy itself lives in {@link FieldBinder}, where it is unit tested.</p>
     */
    private void distribute() {
        if (syncing) {
            return;
        }
        apply(FieldBinder.distribute(latInput.getText().toString(), lngInput.getText().toString()));
    }

    private void normaliseFields() {
        apply(FieldBinder.normalise(latInput.getText().toString(), lngInput.getText().toString()));
    }

    /** Writes a computed pair of field values back, without re-entering the watchers. */
    private void apply(FieldBinder.Fields fields) {
        syncing = true;
        try {
            setText(latInput, fields.latitude);
            setText(lngInput, fields.longitude);
        } finally {
            syncing = false;
        }
    }

    private void setText(EditText field, String value) {
        if (!value.equals(field.getText().toString())) {
            field.setText(value);
            field.setSelection(value.length());
        }
    }

    private void clearFields() {
        syncing = true;
        try {
            latInput.setText("");
            lngInput.setText("");
        } finally {
            syncing = false;
        }
        updateMessage();
    }

    private void pasteFromClipboard() {
        String text = readClipboard();
        if (text == null) {
            message(KIND_WARN, getString(R.string.status_clipboard_empty));
            return;
        }
        applyText(text);
    }

    /** Fills whichever fields the text can supply, then reports. */
    private void applyText(String text) {
        apply(FieldBinder.fromPasted(text));
        updateMessage();
    }

    // ------------------------------------------------------------- feedback

    /**
     * The one place that decides what the user is told, always rendered in the row
     * directly beneath the two input fields.
     */
    private void updateMessage() {
        String latText = latInput.getText().toString().trim();
        String lngText = lngInput.getText().toString().trim();
        CoordText.Scan latScan = CoordText.scan(latText);
        CoordText.Scan lngScan = CoordText.scan(lngText);

        if (latScan.issue == CoordText.Issue.MINUTES || lngScan.issue == CoordText.Issue.MINUTES) {
            message(KIND_WARN, getString(R.string.msg_minutes));
            return;
        }
        if (latScan.issue == CoordText.Issue.RANGE || lngScan.issue == CoordText.Issue.RANGE) {
            message(KIND_WARN, getString(R.string.msg_range));
            return;
        }

        Double lat;
        Double lng;
        if (latScan.complete()) {
            // A whole pair sitting in one field — a same-line paste that has not been
            // split yet. Read it as it is rather than reporting a missing half.
            lat = latScan.latitude;
            lng = latScan.longitude;
        } else if (lngScan.complete()) {
            lat = lngScan.latitude;
            lng = lngScan.longitude;
        } else {
            lat = latScan.hasLat() ? latScan.latitude : null;
            lng = lngScan.hasLng() ? lngScan.longitude : null;
        }

        if (lat != null && lng != null) {
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180 || (lat == 0 && lng == 0)) {
                point = null;
                message(KIND_WARN, getString(R.string.msg_invalid));
                return;
            }
            point = new CoordPoint(lat, lng, datum);
            // Name the system the input was read as, then the value the map apps get.
            // Showing only the GCJ-02 number made picking BD-09 look like it did nothing.
            String text = datum == CoordSystem.GCJ02
                    ? getString(R.string.msg_ok_same, point.gcj02Text())
                    : getString(R.string.msg_ok_convert, datum.display(), point.gcj02Text());
            double shift = point.shiftMeters();
            if (shift >= 1) {
                text = text + " · " + getString(R.string.msg_shift, Formats.meters(shift));
            }
            message(KIND_OK, text);
            return;
        }

        point = null;
        boolean swapped = (latScan.hasLng() && !latScan.hasLat())
                || (lngScan.hasLat() && !lngScan.hasLng());
        if (swapped) {
            message(KIND_WARN, getString(R.string.msg_swapped));
        } else if (lat == null && lng == null) {
            boolean blank = latText.isEmpty() && lngText.isEmpty();
            message(KIND_HINT, getString(blank ? R.string.msg_idle : R.string.msg_empty));
        } else if (lat != null) {
            // Show what the value was understood as, so an unusual spelling such as
            // 31.13.49.4 can be confirmed at a glance.
            message(KIND_HINT, getString(R.string.msg_lat_only, Formats.shortCoord(lat)));
        } else {
            message(KIND_HINT, getString(R.string.msg_lng_only, Formats.shortCoord(lng)));
        }
    }

    /**
     * Supporting text under the fields. M3 colours it by meaning: the fields' own
     * {@code error} role when something is wrong, {@code primary} when the input is good,
     * and {@code onSurfaceVariant} for plain guidance.
     */
    private void message(int kind, CharSequence text) {
        messageText.setText(text);
        CastColor scheme = CastColor.get();
        int color;
        switch (kind) {
            case KIND_OK:
                messageIcon.setImageResource(R.drawable.ic_tick);
                color = scheme.primary;
                break;
            case KIND_WARN:
                messageIcon.setImageResource(R.drawable.ic_alert);
                color = scheme.error;
                break;
            default:
                messageIcon.setImageResource(R.drawable.ic_alert);
                color = scheme.onSurfaceVariant;
                break;
        }
        messageIcon.setImageTintList(ColorStateList.valueOf(color));
        messageText.setTextColor(color);
        if (latField != null && lngField != null) {
            latField.setError(null);
            lngField.setError(null);
        }
    }

    // -------------------------------------------------------------- actions

    /**
     * Shows the point, or plans a route to it, in whichever map app the user picks.
     *
     * <p>One installed app means no question to ask, so it is used directly. Several means
     * an action sheet — the choice is never guessed and never remembered behind the user's
     * back.</p>
     */
    private void openMap(boolean route) {
        hideKeyboard();
        if (point == null) {
            message(KIND_WARN, getString(R.string.need_point));
            return;
        }
        List<MapAppFinder.Entry> targets = MapAppFinder.find(this);
        if (targets.isEmpty()) {
            showWebFallbackDialog(MapApp.AMAP, route);
            return;
        }
        if (targets.size() == 1) {
            launchMap(targets.get(0), route);
            return;
        }
        showMapChooser(targets, route);
    }

    /**
     * Hands the point to one app.
     *
     * <p>Each target gets the datum it wants: GCJ-02 for the Chinese apps and for Google,
     * whose China tiles are GCJ-02 aligned, and WGS-84 for anything reached through the
     * standard {@code geo:} URI.</p>
     */
    private void launchMap(MapAppFinder.Entry target, boolean route) {
        MapApp app = target.app;
        CoordPoint here = point;
        double[] c = app.wantsGcj02()
                ? here.gcj02()
                : GeoConverter.convert(here.lat, here.lng, here.source, CoordSystem.WGS84);
        String uri = route
                ? MapLinks.route(app, null, c[0], c[1])
                : MapLinks.view(app, null, c[0], c[1]);
        if (AmapLauncher.launch(this, uri, target.packageName)) {
            return;
        }
        // The app would not take the link: hand the same point to the browser instead.
        showWebFallbackDialog(app, route);
    }

    /**
     * The M3 modal bottom sheet entrance: the sheet rises from the bottom edge of the
     * screen on the emphasised-decelerate curve while the scrim fades in behind it.
     * A sheet comes from the edge that owns it — it does not grow out of a button.
     */
    private void animateSheetIn(Dialog dialog) {
        final View sheet = dialog.findViewById(R.id.sheet_surface);
        if (sheet == null || !CastMotion.animationsEnabled(this)) {
            return;
        }
        sheet.post(() -> {
            float travel = sheet.getHeight() + CastShape.dp(this, 32);
            sheet.setTranslationY(travel);
            sheet.animate()
                    .translationY(0f)
                    .setDuration(CastMotion.LONG2)
                    .setInterpolator(CastMotion.EMPHASIZED_DECELERATE)
                    .start();
        });
    }

    /** Lists every app that can show the point. */
    private void showMapChooser(List<MapAppFinder.Entry> targets, final boolean route) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_map_apps);
        applyDialogWindow(dialog);
        dialog.setOnShowListener(d -> animateSheetIn(dialog));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            // Keep the sheet clear of the gesture / navigation bar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsets.Type.navigationBars());
                    v.setPadding(0, 0, 0, bars.bottom);
                    return insets;
                });
            }
        }

        LinearLayout rows = dialog.findViewById(R.id.sheet_rows);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final MapAppFinder.Entry target : targets) {
            View row = inflater.inflate(R.layout.item_map_app, rows, false);
            ImageView icon = row.findViewById(R.id.row_icon);
            if (target.icon != null) {
                icon.setImageDrawable(target.icon);
            }
            ((TextView) row.findViewById(R.id.row_label)).setText(target.label);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                launchMap(target, route);
            });
            rows.addView(row);
        }
        // An M3 sheet is dismissed by the scrim, the back gesture, or a swipe down —
        // there is no cancel row at the bottom.
        dialog.show();
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        }
    }

    // --------------------------------------------------------------- dialog

    /** Offers the browser when no map app could take the point. */
    private void showWebFallbackDialog(MapApp app, final boolean route) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_amap_missing);
        applyDialogWindow(dialog);
        styleDialogButtons(dialog);

        CoordPoint here = point;
        double[] c = app.wantsGcj02()
                ? here.gcj02()
                : GeoConverter.convert(here.lat, here.lng, here.source, CoordSystem.WGS84);
        final String uri = route
                ? MapLinks.webRoute(app, null, c[0], c[1])
                : MapLinks.web(app, null, c[0], c[1]);

        dialog.findViewById(R.id.dialog_primary).setOnClickListener(v -> {
            dialog.dismiss();
            openUri(uri);
        });
        dialog.findViewById(R.id.dialog_secondary).setOnClickListener(v -> {
            dialog.dismiss();
            launchStore();
        });
        dialog.findViewById(R.id.dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openUri(String uri) {
        Intent intent = AmapLauncher.toIntent(uri, null);
        if (AmapLauncher.canHandle(this, intent)) {
            startActivity(intent);
        } else {
            showMessageDialog(getString(R.string.dlg_msg_no_handler));
        }
    }

    private void showMessageDialog(String message) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_amap_missing);
        applyDialogWindow(dialog);
        styleDialogButtons(dialog);
        ((TextView) dialog.findViewById(R.id.dialog_message)).setText(message);
        dialog.findViewById(R.id.dialog_primary).setVisibility(View.GONE);
        dialog.findViewById(R.id.dialog_secondary).setVisibility(View.GONE);
        TextView cancel = dialog.findViewById(R.id.dialog_cancel);
        cancel.setText(R.string.action_cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * M3 puts dialog actions bottom-right and makes them <b>text</b> buttons: the last one
     * is the label the user is most likely to want, so it carries the emphasis.
     */
    private void styleDialogButtons(Dialog dialog) {
        CastButton cancel = dialog.findViewById(R.id.dialog_cancel);
        CastButton primary = dialog.findViewById(R.id.dialog_primary);
        CastButton secondary = dialog.findViewById(R.id.dialog_secondary);
        if (cancel != null) {
            cancel.setVariant(CastButton.TEXT);
        }
        if (primary != null) {
            primary.setVariant(CastButton.TEXT);
        }
        if (secondary != null) {
            secondary.setVariant(CastButton.TEXT);
        }
    }

    private void applyDialogWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.dimAmount = 0.55f;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(lp);
        window.setGravity(Gravity.CENTER);
    }

    /** Opens the system app store page for Amap, falling back to its website. */
    private void launchStore() {
        Intent market = new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=" + AmapUris.PKG_PHONE));
        if (AmapLauncher.canHandle(this, market)) {
            startActivity(market);
            return;
        }
        Intent web = new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://mobile.amap.com/"));
        if (AmapLauncher.canHandle(this, web)) {
            startActivity(web);
        }
    }

    // ---------------------------------------------------------- system bars

    private void configureSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // The M3 dark surface is always dark, so the system bars always want light icons.
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(0, mask);
            }
        } else {
            window.setStatusBarColor(CastColor.get().surface);
            window.setNavigationBarColor(CastColor.get().surface);
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void installInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
            sysTop = bars.top;
            sysBottom = bars.bottom;
            imeExtra = Math.max(0, ime.bottom - bars.bottom);
            applyInsets();
            return windowInsets;
        });
    }

    private void applyInsets() {
        // The bottom bar is docked, so the scroll content has to clear it: its own height,
        // the system bar it sits above, the keyboard when it is up, and the M3 16dp margin.
        View bar = findViewById(R.id.action_bar);
        int barHeight = bar == null ? 0 : bar.getHeight();
        if (barHeight == 0) {
            barHeight = CastShape.dp(this, 40 + 24);
        }
        int bottom = barHeight + sysBottom + imeExtra + CastShape.dp(this, 16);
        int paddingBottom = Math.max(scrollBase[3], bottom);
        if (scroll.getPaddingBottom() != paddingBottom
                || scroll.getPaddingTop() != scrollBase[1] + sysTop) {
            scroll.setPadding(scrollBase[0], scrollBase[1] + sysTop, scrollBase[2], paddingBottom);
        }

        if (bar != null) {
            // The bar itself keeps clear of the navigation bar…
            int pad = sysBottom;
            if (bar.getPaddingBottom() != pad + CastShape.dp(this, 12)) {
                bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(),
                        bar.getPaddingRight(), pad + CastShape.dp(this, 12));
            }
            // …and rides above the keyboard rather than being buried by it.
            if (bar.getTranslationY() != -imeExtra) {
                bar.setTranslationY(-imeExtra);
            }
        }
    }
}
