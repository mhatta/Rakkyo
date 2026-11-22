package org.mhatta.rakkyo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.subgraph.orchid.Tor;

/**
 * The Settings tab.  The passwords and the isolation apply to the running
 * client at once; ports and bridges are read when Orchid starts, so while
 * it runs, a banner offers to reconnect once they differ.
 */
final class SettingsPage extends MainActivity.Page {
    private final Prefs prefs;
    private final LinearLayout list;
    private final View restartBanner;
    private final LayoutInflater inflater;
    private TorController.Status status;

    private Row socksPort, http, httpPort, isolate, keepPorts, bridges, bridgeLines, boot, battery, geoip;
    /** The Apps section, rebuilt by refresh(): the password switch, a row per profile, "Add an app". */
    private ViewGroup apps;

    SettingsPage(final MainActivity activity) {
        super(activity, R.layout.page_settings);
        prefs = new Prefs(activity);
        inflater = activity.getLayoutInflater();
        header(R.string.settings_title, R.string.settings_subtitle);
        list = find(R.id.settings_list);
        restartBanner = find(R.id.restart_banner);
        find(R.id.restart_now).setOnClickListener(v -> TorService.restart(activity));
        build();
        refresh();
    }

    @Override
    void onShown() {
        // The user may come back from the battery optimization screen.
        refresh();
    }

    @Override
    void onStatus(TorController.Status s) {
        status = s;
        updateBanner();
    }

    private void build() {
        ViewGroup section = section(R.string.section_proxy);
        socksPort = row(section, R.string.setting_socks_port);
        socksPort.onClick(v -> askPort(true));
        http = row(section, R.string.setting_http);
        http.toggle(on -> {
            prefs.setHttpEnabled(on);
            refresh();
        });
        httpPort = row(section, R.string.setting_http_port);
        httpPort.onClick(v -> askPort(false));

        apps = section(R.string.section_apps);

        section = section(R.string.section_privacy);
        isolate = row(section, R.string.setting_isolate);
        isolate.summary(activity.getString(R.string.setting_isolate_summary));
        isolate.toggle(on -> {
            prefs.setIsolateDest(on);
            refresh();
        });
        keepPorts = row(section, R.string.setting_keep_ports);
        keepPorts.summary(activity.getString(R.string.setting_keep_ports_summary));
        keepPorts.toggle(on -> {
            prefs.setKeepPorts(on);
            if (!on && status != null && status.state == TorController.State.PAUSED) {
                TorService.stop(activity);
            }
            refresh();
        });

        section = section(R.string.section_connection);
        bridges = row(section, R.string.setting_bridges);
        bridges.summary(activity.getString(R.string.setting_bridges_summary));
        bridges.toggle(on -> {
            if (on && BridgeLines.parse(prefs.getBridges()).bridges.isEmpty()) {
                // Nothing to use yet: ask for the lines, and turn bridges on if some are given.
                askBridges(true);
            } else {
                prefs.setUseBridges(on);
            }
            refresh();
        });
        bridgeLines = row(section, R.string.setting_bridge_lines);
        bridgeLines.chevron();
        bridgeLines.onClick(v -> askBridges(false));
        boot = row(section, R.string.setting_boot);
        boot.summary(activity.getString(R.string.setting_boot_summary));
        boot.toggle(on -> prefs.setStartOnBoot(on));
        battery = row(section, R.string.setting_battery);
        battery.summary(activity.getString(R.string.setting_battery_summary));
        battery.chevron();
        battery.onClick(v -> openBatterySettings());

        section = section(R.string.section_about);
        row(section, R.string.setting_version).value(versionName());
        row(section, R.string.setting_orchid_version).value(Tor.getVersion());
        geoip = row(section, R.string.setting_geoip);
        geoip.summary(activity.getString(R.string.setting_geoip_summary));
        TorController.get(activity).background(GeoIp::generated, date ->
                geoip.value(date == null ? activity.getString(R.string.setting_geoip_missing)
                        : activity.getString(R.string.setting_geoip_value, date)));
        final Row licenses = row(section, R.string.setting_license);
        licenses.value(activity.getString(R.string.setting_license_value));
        licenses.chevron();
        licenses.onClick(v -> showLicenses());
        final Row source = row(section, R.string.setting_source);
        source.chevron();
        source.onClick(v -> UiUtil.openUrl(activity, activity.getString(R.string.source_url)));
    }

    /** Show the current settings. */
    private void refresh() {
        socksPort.value(String.valueOf(prefs.getSocksPort()));
        final boolean httpOn = prefs.isHttpEnabled();
        http.checked(httpOn);
        http.summary(activity.getString(R.string.setting_http_summary, prefs.getHttpPort()));
        httpPort.value(String.valueOf(prefs.getHttpPort()));
        httpPort.visible(httpOn);
        refreshApps();
        isolate.checked(prefs.isIsolateDest());
        keepPorts.checked(prefs.isKeepPorts());
        bridges.checked(prefs.isUseBridges());
        final int n = BridgeLines.parse(prefs.getBridges()).bridges.size();
        bridgeLines.value(n == 0 ? activity.getString(R.string.bridge_lines_none)
                : activity.getResources().getQuantityString(R.plurals.bridge_lines_count, n, n));
        bridgeLines.visible(prefs.isUseBridges() || n > 0);
        boot.checked(prefs.isStartOnBoot());
        battery.visible(!isIgnoringBatteryOptimizations());
        // Passwords and isolation take effect at once; the banner offers
        // to reconnect for the rest.
        TorController.get(activity).applySettings();
        updateBanner();
    }

    /* The Apps section: whether a password is required, and the profiles. */
    private void refreshApps() {
        apps.removeAllViews();
        final List<Profiles.Profile> profiles = prefs.getProfiles();
        final boolean required = !profiles.isEmpty();
        final Row require = row(apps, R.string.setting_require_password);
        require.summary(activity.getString(required ? R.string.setting_require_password_on
                : R.string.setting_require_password_off));
        require.toggle(on -> {
            if (on) {
                prefs.setPasswordRequired(true, activity.getString(R.string.profile_default_name));
                refresh();
            } else {
                confirmNoPassword();
            }
        });
        require.checked(required);
        for (final Profiles.Profile p : profiles) {
            final Row r = row(apps, 0);
            r.title.setText(p.name);
            r.chevron();
            r.onClick(v -> showProfile(p.name));
        }
        if (required && profiles.size() < Profiles.MAX) {
            final Row add = row(apps, R.string.profile_add);
            add.summary(activity.getString(R.string.profile_add_summary));
            add.chevron();
            add.onClick(v -> askProfileName());
        }
    }

    /* Turning the password off lets every app on the device use the proxy: say so first. */
    private void confirmNoPassword() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.no_password_title)
                .setMessage(R.string.no_password_message)
                .setPositiveButton(R.string.no_password_confirm, (d, w) -> {
                    prefs.setPasswordRequired(false, null);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> refresh())
                .show();
    }

    private void askProfileName() {
        final EditText edit = new EditText(activity);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        edit.setSingleLine();
        edit.setHint(R.string.profile_name_hint);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.profile_add)
                .setMessage(R.string.profile_add_message)
                .setView(padded(edit))
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String name = edit.getText().toString().trim();
                final Profiles.NameProblem problem = Profiles.checkName(name, prefs.getProfiles());
                if (problem != null) {
                    edit.setError(nameProblem(problem));
                    return;
                }
                final Profiles.Profile p = prefs.addProfile(name);
                dialog.dismiss();
                refresh();
                UiUtil.copy(activity, activity.getString(R.string.clip_label_password), p.password, true);
                Toast.makeText(activity, activity.getString(R.string.profile_added, p.name), Toast.LENGTH_LONG).show();
            });
        });
        dialog.show();
    }

    private String nameProblem(Profiles.NameProblem problem) {
        switch (problem) {
        case TOO_LONG: return activity.getString(R.string.profile_name_too_long, Profiles.MAX_NAME);
        case TAKEN: return activity.getString(R.string.profile_name_taken);
        case CONTROL: return activity.getString(R.string.profile_name_invalid);
        default: return activity.getString(R.string.profile_name_empty);
        }
    }

    /* One profile: copy its password, make a new one, or delete it. */
    private void showProfile(final String name) {
        final Profiles.Profile p = Profiles.find(prefs.getProfiles(), name);
        if (p == null) {
            refresh();
            return;
        }
        final String[] items = {
            activity.getString(R.string.profile_copy),
            activity.getString(R.string.profile_renew),
            activity.getString(R.string.profile_delete),
        };
        new AlertDialog.Builder(activity)
                .setTitle(p.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                    case 0:
                        UiUtil.copy(activity, activity.getString(R.string.clip_label_password), p.password, true);
                        break;
                    case 1:
                        confirm(R.string.profile_renew, activity.getString(R.string.profile_renew_message, p.name), () -> {
                            prefs.renewPassword(p.name);
                            refresh();
                            final Profiles.Profile renewed = Profiles.find(prefs.getProfiles(), p.name);
                            if (renewed != null) {
                                UiUtil.copy(activity, activity.getString(R.string.clip_label_password), renewed.password, true);
                            }
                        });
                        break;
                    default:
                        if (prefs.getProfiles().size() == 1) {
                            // The last one: deleting it would turn the password off.
                            confirmNoPassword();
                        } else {
                            confirm(R.string.profile_delete, activity.getString(R.string.profile_delete_message, p.name), () -> {
                                prefs.removeProfile(p.name);
                                refresh();
                            });
                        }
                        break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirm(int title, String message, Runnable action) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> action.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateBanner() {
        if (restartBanner == null || prefs == null) {
            return;
        }
        final TorController.Status s = status;
        final boolean show = s != null && s.isActive() && s.settings != null
                && TorController.needsRestart(s.settings, prefs.snapshot());
        UiUtil.show(restartBanner, show);
    }

    private void askPort(final boolean socks) {
        final EditText edit = new EditText(activity);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setSingleLine();
        UiUtil.noKeyboardLearning(edit);
        edit.setText(String.valueOf(socks ? prefs.getSocksPort() : prefs.getHttpPort()));
        edit.setSelectAllOnFocus(true);
        edit.setHint(activity.getString(R.string.port_dialog_hint, Prefs.MIN_PORT, Prefs.MAX_PORT));
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(socks ? R.string.setting_socks_port : R.string.setting_http_port)
                .setView(padded(edit))
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            final Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                int port;
                try {
                    port = Integer.parseInt(edit.getText().toString().trim());
                } catch (NumberFormatException e) {
                    port = -1;
                }
                if (!Prefs.isValidPort(port)) {
                    edit.setError(activity.getString(R.string.port_error_range, Prefs.MIN_PORT, Prefs.MAX_PORT));
                    return;
                }
                final int other = socks ? prefs.getHttpPort() : prefs.getSocksPort();
                if (port == other && (!socks || prefs.isHttpEnabled())) {
                    edit.setError(activity.getString(R.string.port_error_same));
                    return;
                }
                if (socks) {
                    prefs.setSocksPort(port);
                } else {
                    prefs.setHttpPort(port);
                }
                dialog.dismiss();
                refresh();
            });
        });
        dialog.show();
    }

    /** @param enable turn bridges on if the user enters usable lines */
    private void askBridges(final boolean enable) {
        final EditText edit = new EditText(activity);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        UiUtil.noKeyboardLearning(edit);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(13);
        edit.setMinLines(3);
        edit.setHint("192.0.2.1:443 0123456789ABCDEF0123456789ABCDEF01234567");
        edit.setText(prefs.getBridges());
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.bridge_dialog_title)
                .setMessage(R.string.bridge_dialog_message)
                .setView(padded(edit))
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        UiUtil.secure(dialog);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String text = edit.getText().toString();
            final BridgeLines.Result result = BridgeLines.parse(text);
            if (!result.isValid()) {
                final int message;
                switch (result.problem) {
                case TRANSPORT: message = R.string.bridge_error_transport; break;
                case IPV6: message = R.string.bridge_error_ipv6; break;
                default: message = R.string.bridge_error_malformed; break;
                }
                edit.setError(activity.getString(message, result.badLine));
                return;
            }
            if (enable && result.bridges.isEmpty()) {
                edit.setError(activity.getString(R.string.bridges_needed));
                return;
            }
            prefs.setBridges(text.trim());
            if (enable) {
                prefs.setUseBridges(true);
            } else if (result.bridges.isEmpty()) {
                prefs.setUseBridges(false);
            }
            dialog.dismiss();
            refresh();
        }));
        dialog.setOnDismissListener(d -> refresh());
        dialog.show();
    }

    private View padded(View content) {
        final FrameLayout frame = new FrameLayout(activity);
        final int pad = UiUtil.dp(activity, 20);
        frame.setPaddingRelative(pad, UiUtil.dp(activity, 8), pad, 0);
        frame.addView(content);
        return frame;
    }

    private boolean isIgnoringBatteryOptimizations() {
        final PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    /*
     * The list of apps of Android's battery optimization settings.  Not
     * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, whose permission Google
     * Play allows only for some kinds of apps.
     */
    private void openBatterySettings() {
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(activity, R.string.setting_battery_done, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLicenses() {
        final TextView text = new TextView(activity);
        text.setText(readRaw(R.raw.notices));
        text.setTextSize(12);
        // The link to torproject.org is part of the trademark notice.
        text.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        text.setText(text.getText());
        text.setTextColor(activity.getColor(R.color.text_tertiary));
        final ScrollView scroll = new ScrollView(activity);
        final int pad = UiUtil.dp(activity, 20);
        scroll.setPaddingRelative(pad, UiUtil.dp(activity, 8), pad, 0);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.licenses_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String readRaw(int id) {
        final StringBuilder sb = new StringBuilder();
        try (InputStream in = activity.getResources().openRawResource(id);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            // Leave it short
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private String versionName() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private ViewGroup section(int title) {
        final View v = inflater.inflate(R.layout.item_setting_section, list, false);
        ((TextView) v.findViewById(R.id.section_title)).setText(title);
        list.addView(v);
        return v.findViewById(R.id.section_body);
    }

    private Row row(ViewGroup section, int title) {
        final View v = inflater.inflate(R.layout.item_setting_row, section, false);
        section.addView(v);
        final Row row = new Row(v);
        if (title != 0) {
            row.title.setText(title);
        }
        return row;
    }

    interface Toggle {
        void changed(boolean on);
    }

    /** One row of item_setting_row.xml. */
    private final class Row {
        final View view;
        final TextView title;
        final TextView summary;
        final TextView value;
        final Switch toggle;
        final View segment;
        final TextView segmentA;
        final TextView segmentB;
        private boolean updating;
        private Toggle segmentListener;

        Row(View view) {
            this.view = view;
            title = view.findViewById(R.id.row_title);
            summary = view.findViewById(R.id.row_summary);
            value = view.findViewById(R.id.row_value);
            toggle = view.findViewById(R.id.row_switch);
            segment = view.findViewById(R.id.row_segment);
            segmentA = view.findViewById(R.id.segment_a);
            segmentB = view.findViewById(R.id.segment_b);
            view.setClickable(false);
            view.setBackground(null);
        }

        void summary(String text) {
            summary.setText(text);
            UiUtil.show(summary, true);
        }

        void value(String text) {
            value.setText(text);
            UiUtil.show(value, true);
        }

        void chevron() {
            UiUtil.show(view.findViewById(R.id.row_chevron), true);
        }

        void onClick(View.OnClickListener l) {
            view.setBackgroundResource(resolveSelectable());
            view.setClickable(true);
            view.setFocusable(true);
            view.setOnClickListener(l);
        }

        /** A switch; the whole row toggles it, and it is announced as one control. */
        void toggle(final Toggle listener) {
            UiUtil.show(toggle, true);
            toggle.setClickable(false);
            toggle.setFocusable(false);
            toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            onClick(v -> {
                toggle.setChecked(!toggle.isChecked());
                listener.changed(toggle.isChecked());
            });
            announceSwitch();
        }

        void checked(boolean on) {
            toggle.setChecked(on);
        }

        /* Announce the row as a switch, since the Switch itself is hidden from TalkBack. */
        private void announceSwitch() {
            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Switch.class.getName());
                    info.setCheckable(true);
                    info.setChecked(toggle.isChecked());
                }
            });
        }

        /** Two choices (false = a, true = b), as the segmented control of the mock-up. */
        void segment(int a, int b, Toggle listener) {
            UiUtil.show(segment, true);
            segmentA.setText(a);
            segmentB.setText(b);
            segmentListener = listener;
            segmentA.setOnClickListener(v -> choose(false));
            segmentB.setOnClickListener(v -> choose(true));
        }

        private void choose(boolean second) {
            if (updating) {
                return;
            }
            selected(second);
            segmentListener.changed(second);
        }

        void selected(boolean second) {
            updating = true;
            style(segmentA, !second);
            style(segmentB, second);
            updating = false;
        }

        private void style(TextView t, boolean on) {
            t.setBackgroundResource(on ? R.drawable.bg_segment_selected : 0);
            t.setTextColor(activity.getColor(on ? R.color.text : R.color.text_tertiary));
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            t.setSelected(on);
            t.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(android.widget.RadioButton.class.getName());
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
        }

        void visible(boolean visible) {
            UiUtil.show(view, visible);
        }

        private int resolveSelectable() {
            final android.util.TypedValue tv = new android.util.TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            return tv.resourceId;
        }
    }
}
