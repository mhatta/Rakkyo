package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.subgraph.orchid.GuardInfo;
import com.subgraph.orchid.RelayInfo;

/**
 * The entry guards in detail.  They identify the device for months, so,
 * as Orchid's RelayInfo asks, they are only shown: the window is kept out
 * of screenshots and of the recent apps screen, nothing is logged, and
 * nothing can be copied.
 */
public final class GuardsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_guards);
        UiUtil.edgeToEdge(this, findViewById(R.id.root), findViewById(R.id.scroll));
        ((TextView) findViewById(R.id.header_title)).setText(R.string.guards_title);
        findViewById(R.id.header_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        TorController.get(this).guards(this::show);
    }

    private void show(List<GuardInfo> guards) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        final ViewGroup list = findViewById(R.id.guard_list);
        list.removeAllViews();
        UiUtil.show(findViewById(R.id.guards_empty), guards.isEmpty());
        final LayoutInflater inflater = getLayoutInflater();
        for (GuardInfo g : guards) {
            list.addView(guardView(inflater, list, g));
        }
    }

    private View guardView(LayoutInflater inflater, ViewGroup parent, GuardInfo g) {
        final View v = inflater.inflate(R.layout.item_guard, parent, false);
        final RelayInfo relay = g.getRelay();
        final String nickname = relay.getNickname();
        ((TextView) v.findViewById(R.id.guard_name)).setText(nickname == null ? getString(R.string.guard_no_name) : nickname);
        ((TextView) v.findViewById(R.id.guard_fingerprint)).setText(Formats.groupedFingerprint(relay.getFingerprint()));
        final String cc = relay.getCountryCode();
        ((TextView) v.findViewById(R.id.guard_country)).setText(cc == null || "??".equals(cc)
                ? getString(R.string.country_unknown)
                : new Locale("", cc).getDisplayCountry() + " (" + cc + ")");

        final TextView reach = v.findViewById(R.id.guard_reachability);
        final int text, fg, bg;
        switch (g.getReachability()) {
        case REACHABLE:
            text = R.string.guard_reachable; fg = R.color.success; bg = R.color.success_soft;
            break;
        case UNREACHABLE:
            text = R.string.guard_unreachable; fg = R.color.error; bg = R.color.error_soft;
            break;
        default:
            text = R.string.guard_untried; fg = R.color.text_tertiary; bg = R.color.chip;
            break;
        }
        reach.setText(text);
        reach.setTextColor(getColor(fg));
        reach.getBackground().mutate().setTint(getColor(bg));

        final List<String> flags = new ArrayList<String>();
        if (g.isPrimary()) {
            flags.add(getString(R.string.guard_primary));
        }
        if (g.isConfirmed()) {
            flags.add(getString(R.string.guard_confirmed));
        }
        if (!g.isListed()) {
            flags.add(getString(R.string.guard_unlisted));
        }
        final Date since = g.getConfirmedOn() != null ? g.getConfirmedOn() : g.getSampledOn();
        if (since != null) {
            // Month and year only: the day says more than the user needs.
            final String pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMM");
            flags.add(getString(R.string.guard_sampled,
                    new java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(since)));
        }
        final StringBuilder sb = new StringBuilder();
        for (String f : flags) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(f);
        }
        ((TextView) v.findViewById(R.id.guard_flags)).setText(sb);
        return v;
    }
}
