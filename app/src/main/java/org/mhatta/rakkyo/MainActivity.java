package org.mhatta.rakkyo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * The three tabs of the mock-up (Home, Circuits, Settings) in one activity,
 * with the bottom navigation.  Each tab is a {@link Page}.
 *
 * <p>The Circuits tab shows the countries of the entry guards, which
 * identify the device for months, so the window is kept out of screenshots
 * and the recent apps screen while it is shown (FLAG_SECURE).
 */
public final class MainActivity extends Activity implements TorController.Listener {
    static final int TAB_HOME = 0;
    static final int TAB_CIRCUITS = 1;
    static final int TAB_SETTINGS = 2;
    static final String EXTRA_TAB = "org.mhatta.rakkyo.extra.TAB";
    private static final String STATE_TAB = "tab";
    private static final int REQUEST_NOTIFICATIONS = 1;

    private TorController controller;
    private ViewGroup container;
    private final Page[] pages = new Page[3];
    private int tab = -1;
    private boolean started;
    /** A BackToHome, registered while a tab other than Home is shown (Android 13+). */
    private Object backCallback;
    private boolean backRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        controller = TorController.get(this);
        container = findViewById(R.id.page_container);
        UiUtil.edgeToEdge(this, container, findViewById(R.id.nav));

        findViewById(R.id.tab_home).setOnClickListener(v -> showTab(TAB_HOME));
        findViewById(R.id.tab_circuits).setOnClickListener(v -> showTab(TAB_CIRCUITS));
        findViewById(R.id.tab_settings).setOnClickListener(v -> showTab(TAB_SETTINGS));

        int initial = getIntent().getIntExtra(EXTRA_TAB, TAB_HOME);
        if (savedInstanceState != null) {
            initial = savedInstanceState.getInt(STATE_TAB, initial);
        }
        showTab(initial);
        if (savedInstanceState == null && !new Prefs(this).isOnboarded()) {
            askUse();
        }
    }

    /*
     * The first-run choice, for new installs: apps that can send a
     * password (the default, a password per app and no HTTP port), or the
     * Wi-Fi proxy setting, which can send none and needs the HTTP port.
     * Settings can change either later.
     */
    private void askUse() {
        final Prefs prefs = new Prefs(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.onboarding_title)
                .setMessage(R.string.onboarding_message)
                .setPositiveButton(R.string.onboarding_apps, (d, w) -> {
                    prefs.setOnboarded();
                    openGuide();
                })
                .setNeutralButton(R.string.onboarding_wifi, (d, w) -> {
                    prefs.setPasswordRequired(false, null);
                    prefs.setHttpEnabled(true);
                    prefs.setOnboarded();
                    openGuide();
                })
                // Back keeps the default.
                .setOnCancelListener(d -> prefs.setOnboarded())
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_TAB)) {
            showTab(intent.getIntExtra(EXTRA_TAB, TAB_HOME));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, tab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        controller.addListener(this);
        pages[tab].onShown();
    }

    @Override
    protected void onStop() {
        started = false;
        pages[tab].onHidden();
        controller.removeListener(this);
        super.onStop();
    }

    public void onStatusChanged(TorController.Status status) {
        for (Page page : pages) {
            if (page != null) {
                page.onStatus(status);
            }
        }
    }

    void showTab(int which) {
        if (which < TAB_HOME || which > TAB_SETTINGS) {
            which = TAB_HOME;
        }
        if (which == tab) {
            return;
        }
        if (tab >= 0 && started) {
            pages[tab].onHidden();
        }
        tab = which;
        if (pages[tab] == null) {
            pages[tab] = createPage(tab);
            pages[tab].onStatus(controller.status());
        }
        container.removeAllViews();
        container.addView(pages[tab].view);

        if (tab == TAB_CIRCUITS) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        styleTab(R.id.tab_home, R.id.tab_home_icon, R.id.tab_home_label, tab == TAB_HOME);
        styleTab(R.id.tab_circuits, R.id.tab_circuits_icon, R.id.tab_circuits_label, tab == TAB_CIRCUITS);
        styleTab(R.id.tab_settings, R.id.tab_settings_icon, R.id.tab_settings_label, tab == TAB_SETTINGS);
        if (started) {
            pages[tab].onShown();
        }
        updateBackCallback();
    }

    /*
     * Back on another tab goes to Home; on Home, it leaves the app, with
     * the system's predictive animation.  So the callback is registered
     * only while it has something to do: a registered callback would stop
     * the animation on Home too.  Android 13+ calls these callbacks instead
     * of onBackPressed() (enableOnBackInvokedCallback in the manifest, the
     * default with targetSdk 36 on Android 16); older versions call
     * onBackPressed().
     */
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        final boolean wanted = tab != TAB_HOME;
        if (wanted == backRegistered) {
            return;
        }
        if (backCallback == null) {
            backCallback = new BackToHome(this);
        }
        BackToHome.register(this, (BackToHome) backCallback, wanted);
        backRegistered = wanted;
    }

    @TargetApi(33)
    private static final class BackToHome implements OnBackInvokedCallback {
        private final MainActivity activity;

        BackToHome(MainActivity activity) {
            this.activity = activity;
        }

        public void onBackInvoked() {
            activity.showTab(TAB_HOME);
        }

        static void register(Activity activity, BackToHome callback, boolean register) {
            final OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            if (register) {
                dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            } else {
                dispatcher.unregisterOnBackInvokedCallback(callback);
            }
        }
    }

    private Page createPage(int which) {
        switch (which) {
        case TAB_CIRCUITS:
            return new CircuitsPage(this);
        case TAB_SETTINGS:
            return new SettingsPage(this);
        default:
            return new HomePage(this);
        }
    }

    private void styleTab(int tabId, int iconId, int labelId, boolean selected) {
        final int color = getColor(selected ? R.color.accent : R.color.text_secondary);
        ((ImageView) findViewById(iconId)).setColorFilter(color);
        final TextView label = findViewById(labelId);
        label.setTextColor(color);
        label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        findViewById(tabId).setSelected(selected);
    }

    /** Connect, asking first (once) for permission to show the notification. */
    void connect() {
        final Prefs prefs = new Prefs(this);
        if (Build.VERSION.SDK_INT >= 33 && !prefs.hasAskedForNotifications()
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.setAskedForNotifications();
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, REQUEST_NOTIFICATIONS);
        }
        // The proxy works without the notification; it is only not shown.
        TorService.start(this);
    }

    void openGuide() {
        startActivity(new Intent(this, GuideActivity.class));
    }

    /** Android 12 and older (see updateBackCallback). */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tab != TAB_HOME) {
            showTab(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    /** One tab. */
    abstract static class Page {
        final MainActivity activity;
        final View view;

        Page(MainActivity activity, int layout) {
            this.activity = activity;
            this.view = activity.getLayoutInflater().inflate(layout, activity.container, false);
        }

        /** The tab is on screen and the activity started: start polling. */
        void onShown() {
        }

        /** Stop polling. */
        void onHidden() {
        }

        /** Called for every new status, shown or not. */
        void onStatus(TorController.Status status) {
        }

        @SuppressWarnings("unchecked")
        <T extends View> T find(int id) {
            return (T) view.findViewById(id);
        }

        void header(int title, int subtitle) {
            ((TextView) find(R.id.header_title)).setText(title);
            ((TextView) find(R.id.header_subtitle)).setText(subtitle);
            final View settings = find(R.id.header_settings);
            if (this instanceof SettingsPage) {
                settings.setVisibility(View.GONE);
            } else {
                settings.setOnClickListener(v -> activity.showTab(TAB_SETTINGS));
            }
        }
    }
}
