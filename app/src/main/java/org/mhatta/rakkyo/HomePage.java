package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.AlertDialog;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.subgraph.orchid.CircuitInfo;
import com.subgraph.orchid.TrafficInfo;

/** The Home tab: the power button, the state, the proxy addresses. */
final class HomePage extends MainActivity.Page {
    private static final long TRAFFIC_INTERVAL = 1000;
    private static final long CIRCUITS_INTERVAL = 3000;

    private final TorController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TorController.Status status;
    private boolean shown;
    private long lastCircuitQuery;
    private int exitCircuits = -1;

    private final View powerButton;
    private final ImageView powerIcon;
    private final TextView powerPercent;
    private final TextView statusTitle;
    private final TextView statusDetail;
    private final ProgressBar progress;
    private final View chips;
    private final TextView chipCircuits;
    private final View chipIsolation;
    private final View chipBridges;
    private final TextView traffic;
    private final View stepsCard;
    private final View[] steps = new View[4];
    private final View connectingNote;
    private final View cancelButton;
    private final View closePortsButton;
    private final View proxyCard;
    private final TextView socksValue;
    private final View httpDivider;
    private final View httpRow;
    private final TextView httpValue;
    private final TextView passwordText;
    private final View passwordCopy;
    private final View actions;
    private final View leakCard;
    private final TextView leakText;
    private long shownLeaks = -1;
    private final View newIdentityButton;

    private final Runnable tick = new Runnable() {
        public void run() {
            refreshLive();
            if (shown) {
                handler.postDelayed(this, TRAFFIC_INTERVAL);
            }
        }
    };

    HomePage(final MainActivity activity) {
        super(activity, R.layout.page_home);
        controller = TorController.get(activity);
        ((TextView) find(R.id.header_title)).setText(R.string.app_name);
        ((TextView) find(R.id.header_subtitle)).setText(R.string.app_tagline);
        find(R.id.header_settings).setOnClickListener(v -> activity.showTab(MainActivity.TAB_SETTINGS));

        powerButton = find(R.id.power_button);
        powerIcon = find(R.id.power_icon);
        powerPercent = find(R.id.power_percent);
        statusTitle = find(R.id.status_title);
        statusDetail = find(R.id.status_detail);
        progress = find(R.id.bootstrap_progress);
        chips = find(R.id.chips);
        chipCircuits = find(R.id.chip_circuits);
        chipIsolation = find(R.id.chip_isolation);
        chipBridges = find(R.id.chip_bridges);
        traffic = find(R.id.traffic_text);
        stepsCard = find(R.id.steps_card);
        steps[0] = find(R.id.step_1);
        steps[1] = find(R.id.step_2);
        steps[2] = find(R.id.step_3);
        steps[3] = find(R.id.step_4);
        connectingNote = find(R.id.connecting_note);
        cancelButton = find(R.id.cancel_button);
        closePortsButton = find(R.id.close_ports_button);
        proxyCard = find(R.id.proxy_card);
        socksValue = find(R.id.socks_value);
        httpDivider = find(R.id.http_divider);
        httpRow = find(R.id.http_row);
        httpValue = find(R.id.http_value);
        passwordText = find(R.id.password_text);
        passwordCopy = find(R.id.password_copy);
        actions = find(R.id.actions);
        leakCard = find(R.id.leak_card);
        leakText = find(R.id.leak_text);
        newIdentityButton = find(R.id.new_identity_button);

        final int[] stepNames = { R.string.step_directory, R.string.step_consensus, R.string.step_descriptors, R.string.step_circuit };
        for (int i = 0; i < steps.length; i++) {
            ((TextView) steps[i].findViewById(R.id.step_text)).setText(stepNames[i]);
        }

        powerButton.setOnClickListener(v -> {
            if (status == null) {
                return;
            }
            switch (status.state) {
            case STOPPED:
            case FAILED:
            case PAUSED:
                activity.connect();
                break;
            case STARTING:
            case RUNNING:
                TorService.disconnect(activity);
                break;
            case STOPPING:
                break;
            }
        });
        cancelButton.setOnClickListener(v -> TorService.disconnect(activity));
        closePortsButton.setOnClickListener(v -> TorService.stop(activity));
        find(R.id.socks_copy).setOnClickListener(v -> UiUtil.copy(activity,
                activity.getString(R.string.clip_label_address), socksValue.getText().toString(), false));
        find(R.id.http_copy).setOnClickListener(v -> UiUtil.copy(activity,
                activity.getString(R.string.clip_label_address), httpValue.getText().toString(), false));
        passwordCopy.setOnClickListener(v -> copyPassword());
        newIdentityButton.setOnClickListener(v -> {
            newIdentityButton.setEnabled(false);
            controller.newIdentity(delay -> {
                newIdentityButton.setEnabled(true);
                Toast.makeText(activity, NewIdentity.message(activity, delay), Toast.LENGTH_SHORT).show();
                lastCircuitQuery = 0;
            });
        });
        find(R.id.view_circuits_button).setOnClickListener(v -> activity.showTab(MainActivity.TAB_CIRCUITS));
        find(R.id.guide_link).setOnClickListener(v -> activity.openGuide());
    }

    @Override
    void onShown() {
        shown = true;
        lastCircuitQuery = 0;
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override
    void onHidden() {
        shown = false;
        handler.removeCallbacks(tick);
    }

    @Override
    void onStatus(TorController.Status s) {
        final boolean wasRunning = status != null && status.state == TorController.State.RUNNING;
        status = s;
        if (s.state != TorController.State.RUNNING) {
            exitCircuits = -1;
        } else if (!wasRunning) {
            lastCircuitQuery = 0;
        }
        render();
        refreshLive();
    }

    private void render() {
        final TorController.Status s = status;
        final boolean starting = s.state == TorController.State.STARTING;
        final boolean running = s.state == TorController.State.RUNNING;

        UiUtil.show(powerPercent, starting);
        UiUtil.show(powerIcon, !starting);
        UiUtil.show(progress, starting);
        UiUtil.show(stepsCard, starting);
        UiUtil.show(connectingNote, starting);
        UiUtil.show(cancelButton, starting);
        UiUtil.show(closePortsButton, s.state == TorController.State.PAUSED);
        UiUtil.show(proxyCard, running);
        UiUtil.show(actions, running);
        UiUtil.show(chips, running);
        UiUtil.show(traffic, running);
        if (!running) {
            UiUtil.show(leakCard, false);
            shownLeaks = -1;
        }
        powerButton.setEnabled(s.state != TorController.State.STOPPING);

        final int titleColor;
        switch (s.state) {
        case RUNNING:
            powerButton.setBackgroundResource(R.drawable.bg_power_on);
            powerIcon.setColorFilter(activity.getColor(R.color.success));
            powerButton.setContentDescription(activity.getString(R.string.cd_power_disconnect));
            statusTitle.setText(s.dormant ? R.string.status_dormant : R.string.status_connected);
            statusDetail.setText(s.dormant ? R.string.status_dormant_detail : R.string.status_connected_detail);
            titleColor = R.color.success;
            renderProxy(s);
            break;
        case STARTING:
            powerButton.setBackgroundResource(R.drawable.bg_power_connecting);
            powerButton.setContentDescription(activity.getString(R.string.cd_power_disconnect));
            powerPercent.setText(String.format(Locale.getDefault(), "%d%%", s.percent));
            progress.setProgress(s.percent);
            statusTitle.setText(R.string.status_connecting);
            statusDetail.setText(phaseMessage(Bootstrap.phase(s.percent)));
            titleColor = R.color.text;
            renderSteps(s.percent);
            break;
        case STOPPING:
            powerButton.setBackgroundResource(R.drawable.bg_power_off);
            powerIcon.setColorFilter(activity.getColor(R.color.text_muted));
            statusTitle.setText(R.string.status_stopping);
            statusDetail.setText("");
            titleColor = R.color.text;
            break;
        case PAUSED:
            powerButton.setBackgroundResource(R.drawable.bg_power_off);
            powerIcon.setColorFilter(activity.getColor(R.color.text_secondary));
            powerButton.setContentDescription(activity.getString(R.string.cd_power_connect));
            statusTitle.setText(R.string.status_paused);
            statusDetail.setText(s.settings == null ? activity.getString(R.string.status_paused_detail_short)
                    : activity.getString(R.string.status_paused_detail, portsText(s.settings)));
            titleColor = R.color.text;
            break;
        case FAILED:
            powerButton.setBackgroundResource(R.drawable.bg_power_failed);
            powerIcon.setColorFilter(activity.getColor(R.color.error));
            powerButton.setContentDescription(activity.getString(R.string.cd_power_connect));
            statusTitle.setText(R.string.status_failed);
            statusDetail.setText(Failures.message(activity, s));
            titleColor = R.color.error;
            break;
        default:
            powerButton.setBackgroundResource(R.drawable.bg_power_off);
            powerIcon.setColorFilter(activity.getColor(R.color.text_secondary));
            powerButton.setContentDescription(activity.getString(R.string.cd_power_connect));
            statusTitle.setText(R.string.status_stopped);
            statusDetail.setText(R.string.status_stopped_detail);
            titleColor = R.color.text;
            break;
        }
        statusTitle.setTextColor(activity.getColor(titleColor));
    }

    private void renderProxy(TorController.Status s) {
        final Prefs.Snapshot settings = s.settings;
        if (settings == null) {
            return;
        }
        socksValue.setText(TorController.LISTEN_ADDRESS + ":" + settings.socksPort);
        final boolean http = settings.httpPort != 0;
        UiUtil.show(httpDivider, http);
        UiUtil.show(httpRow, http);
        if (http) {
            httpValue.setText(TorController.LISTEN_ADDRESS + ":" + settings.httpPort);
        }
        final int n = settings.passwords.size();
        if (n == 0) {
            passwordText.setText(R.string.password_none);
        } else if (n == 1) {
            passwordText.setText(R.string.password_set);
        } else {
            passwordText.setText(activity.getResources().getQuantityString(R.plurals.password_profiles, n, n));
        }
        UiUtil.show(passwordCopy, n > 0);
        UiUtil.show(chipIsolation, settings.isolateDest);
        UiUtil.show(chipBridges, settings.useBridges);
    }

    private static String portsText(Prefs.Snapshot settings) {
        return settings.httpPort == 0 ? String.valueOf(settings.socksPort)
                : settings.socksPort + ", " + settings.httpPort;
    }

    /* The password of the only profile, or of the one chosen. */
    private void copyPassword() {
        final Prefs.Snapshot settings = status != null ? status.settings : null;
        if (settings == null || settings.passwords.isEmpty()) {
            return;
        }
        final List<Map.Entry<String, String>> entries = new ArrayList<Map.Entry<String, String>>(settings.passwords.entrySet());
        if (entries.size() == 1) {
            UiUtil.copy(activity, activity.getString(R.string.clip_label_password), entries.get(0).getValue(), true);
            return;
        }
        final String[] names = new String[entries.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = entries.get(i).getKey();
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.cd_copy_password)
                .setItems(names, (d, which) -> UiUtil.copy(activity,
                        activity.getString(R.string.clip_label_password), entries.get(which).getValue(), true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderSteps(int percent) {
        final Bootstrap.Step[] all = Bootstrap.Step.values();
        for (int i = 0; i < all.length; i++) {
            final ImageView icon = steps[i].findViewById(R.id.step_icon);
            final TextView text = steps[i].findViewById(R.id.step_text);
            switch (Bootstrap.stateOf(all[i], percent)) {
            case DONE:
                icon.setImageResource(R.drawable.ic_check);
                icon.setColorFilter(activity.getColor(R.color.success));
                icon.setContentDescription(activity.getString(R.string.cd_step_done));
                text.setTextColor(activity.getColor(R.color.text));
                text.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
            case ACTIVE:
                icon.setImageResource(R.drawable.ic_step_active);
                icon.setColorFilter(activity.getColor(R.color.accent));
                icon.setContentDescription(activity.getString(R.string.cd_step_active));
                text.setTextColor(activity.getColor(R.color.text));
                text.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            default:
                icon.setImageResource(R.drawable.ic_step_pending);
                icon.setColorFilter(activity.getColor(R.color.text_muted));
                icon.setContentDescription(activity.getString(R.string.cd_step_pending));
                text.setTextColor(activity.getColor(R.color.text_muted));
                text.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
            }
        }
    }

    private String phaseMessage(Bootstrap.Phase phase) {
        switch (phase) {
        case CONNECTING_DIRECTORY: return activity.getString(R.string.phase_connecting_directory);
        case ENCRYPTING_DIRECTORY: return activity.getString(R.string.phase_encrypting_directory);
        case REQUESTING_CONSENSUS: return activity.getString(R.string.phase_requesting_consensus);
        case LOADING_CONSENSUS: return activity.getString(R.string.phase_loading_consensus);
        case LOADING_KEYS: return activity.getString(R.string.phase_loading_keys);
        case REQUESTING_DESCRIPTORS: return activity.getString(R.string.phase_requesting_descriptors);
        case LOADING_DESCRIPTORS: return activity.getString(R.string.phase_loading_descriptors);
        case CONNECTING_RELAY: return activity.getString(R.string.phase_connecting_relay);
        case BUILDING_CIRCUIT: return activity.getString(R.string.phase_building_circuit);
        case DONE: return activity.getString(R.string.phase_done);
        default: return activity.getString(R.string.phase_starting);
        }
    }

    /** The traffic every second, and the number of exit circuits every few seconds. */
    private void refreshLive() {
        if (status == null || status.state != TorController.State.RUNNING) {
            return;
        }
        final TrafficInfo t = controller.traffic();
        if (t != null) {
            final Locale locale = Locale.getDefault();
            traffic.setText(activity.getString(R.string.traffic,
                    Formats.bytes(t.getBytesRead(), locale), Formats.bytes(t.getBytesWritten(), locale)));
        }
        renderCircuitChip();
        renderLeaks(AddressOnly.of(controller.statistics()));
        final long now = System.currentTimeMillis();
        if (shown && now - lastCircuitQuery >= CIRCUITS_INTERVAL) {
            lastCircuitQuery = now;
            controller.circuits(this::countExitCircuits);
        }
    }

    /* Which apps gave only an IP address since connecting (Orchid counts them by profile). */
    private void renderLeaks(AddressOnly leaks) {
        UiUtil.show(leakCard, leaks.total > 0);
        if (leaks.total == 0 || leaks.total == shownLeaks) {
            return;
        }
        shownLeaks = leaks.total;
        final int n = (int) Math.min(Integer.MAX_VALUE, leaks.total);
        final StringBuilder text = new StringBuilder(activity.getResources().getQuantityString(R.plurals.leak_text, n, n));
        final StringBuilder apps = new StringBuilder();
        for (AddressOnly.Entry e : leaks.byProfile) {
            if (apps.length() > 0) {
                apps.append(activity.getString(R.string.list_separator));
            }
            final String name = e.profile.isEmpty() ? activity.getString(R.string.leak_no_profile) : e.profile;
            apps.append(activity.getString(R.string.leak_app_count, name, e.count));
        }
        if (apps.length() > 0) {
            text.append("\n\n").append(activity.getString(R.string.leak_by_app, apps));
        }
        if (leaks.refused > 0) {
            text.append("\n").append(activity.getString(R.string.leak_refused, leaks.refused));
        }
        leakText.setText(text);
    }

    private void countExitCircuits(List<CircuitInfo> circuits) {
        int n = 0;
        for (CircuitInfo c : circuits) {
            if (c.getPurpose() == CircuitInfo.Purpose.EXIT && c.getState() == CircuitInfo.State.OPEN) {
                n++;
            }
        }
        exitCircuits = n;
        renderCircuitChip();
    }

    private void renderCircuitChip() {
        UiUtil.show(chipCircuits, exitCircuits >= 0);
        if (exitCircuits >= 0) {
            chipCircuits.setText(activity.getResources().getQuantityString(R.plurals.chip_circuits, exitCircuits, exitCircuits));
        }
    }
}
