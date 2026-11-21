package org.mhatta.rakkyo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.subgraph.orchid.CircuitInfo;
import com.subgraph.orchid.GuardInfo;
import com.subgraph.orchid.RelayInfo;

/**
 * The Circuits tab: the circuits Orchid uses, with the country of each
 * relay, and a summary of the entry guards.
 *
 * <p>Only country codes are shown here: no fingerprints or nicknames, and
 * never the destination of a stream (Orchid's CircuitInfo does not carry
 * it, not even for onion services).  Directory circuits are left out; they
 * would show the guards once more.
 */
final class CircuitsPage extends MainActivity.Page {
    private static final long INTERVAL = 2000;
    /** More circuits than this are not shown; Orchid keeps a few spare ones. */
    private static final int MAX_SHOWN = 12;

    private final TorController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout list;
    private final TextView empty;
    private final View guardCard;
    private final TextView guardSummary;
    private final View guardBlocked;
    private TorController.Status status;
    private boolean shown;
    /** What the list shows now, to rebuild it only when something changed. */
    private String shownKey = "";

    private final Runnable poll = new Runnable() {
        public void run() {
            query();
            if (shown) {
                handler.postDelayed(this, INTERVAL);
            }
        }
    };

    CircuitsPage(final MainActivity activity) {
        super(activity, R.layout.page_circuits);
        controller = TorController.get(activity);
        header(R.string.circuits_title, R.string.circuits_subtitle);
        list = find(R.id.circuit_list);
        empty = find(R.id.circuits_empty);
        guardCard = find(R.id.guard_card);
        guardSummary = find(R.id.guard_summary);
        guardBlocked = find(R.id.guard_blocked);
        find(R.id.guard_set_up_bridges).setOnClickListener(v -> activity.showTab(MainActivity.TAB_SETTINGS));
        find(R.id.guard_details).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, GuardsActivity.class)));
    }

    @Override
    void onShown() {
        shown = true;
        handler.removeCallbacks(poll);
        poll.run();
    }

    @Override
    void onHidden() {
        shown = false;
        handler.removeCallbacks(poll);
    }

    @Override
    void onStatus(TorController.Status s) {
        status = s;
        if (s.state != TorController.State.RUNNING) {
            list.removeAllViews();
            shownKey = "";
            empty.setText(R.string.circuits_not_connected);
            UiUtil.show(empty, true);
            UiUtil.show(guardCard, false);
        } else if (shown) {
            query();
        }
    }

    private void query() {
        if (status == null || status.state != TorController.State.RUNNING) {
            return;
        }
        controller.circuits(this::showCircuits);
        controller.guards(this::showGuards);
    }

    private void showCircuits(List<CircuitInfo> all) {
        if (status == null || status.state != TorController.State.RUNNING) {
            return;
        }
        final List<CircuitInfo> circuits = new ArrayList<CircuitInfo>();
        for (CircuitInfo c : all) {
            final CircuitInfo.State st = c.getState();
            if (st != CircuitInfo.State.CLOSED && st != CircuitInfo.State.FAILED
                    && c.getPurpose() != CircuitInfo.Purpose.DIRECTORY) {
                circuits.add(c);
            }
        }
        Collections.sort(circuits, ORDER);
        if (circuits.size() > MAX_SHOWN) {
            circuits.subList(MAX_SHOWN, circuits.size()).clear();
        }

        final StringBuilder key = new StringBuilder();
        for (CircuitInfo c : circuits) {
            key.append(c.getPurpose()).append(c.getState()).append(c.getStreamCount()).append(c.isIsolated())
                    .append(Formats.minutes(c.getSecondsDirty()));
            for (RelayInfo r : c.getPath()) {
                key.append(r.getCountryCode());
            }
            key.append('|');
        }
        UiUtil.show(empty, circuits.isEmpty());
        empty.setText(R.string.circuits_none);
        if (key.toString().equals(shownKey)) {
            return;
        }
        shownKey = key.toString();
        list.removeAllViews();
        final LayoutInflater inflater = activity.getLayoutInflater();
        for (CircuitInfo c : circuits) {
            list.addView(circuitView(inflater, c));
        }
    }

    /** Onion service first, then the exits in use, idle and on standby, then the rest. */
    private static final Comparator<CircuitInfo> ORDER = new Comparator<CircuitInfo>() {
        public int compare(CircuitInfo a, CircuitInfo b) {
            return rank(a) - rank(b);
        }

        private int rank(CircuitInfo c) {
            switch (c.getPurpose()) {
            case ONION_SERVICE: return 0;
            case EXIT: return c.getStreamCount() > 0 ? 1 : c.getSecondsDirty() > 0 ? 2 : 3;
            case ONION_SERVICE_INTRODUCTION: return 4;
            case ONION_SERVICE_DIRECTORY: return 5;
            case INTERNAL: return 6;
            default: return 7;
            }
        }
    };

    private View circuitView(LayoutInflater inflater, CircuitInfo c) {
        final View v = inflater.inflate(R.layout.item_circuit, list, false);
        ((TextView) v.findViewById(R.id.circuit_purpose)).setText(purpose(c));
        stateChip((TextView) v.findViewById(R.id.circuit_state), c);

        final ViewGroup hops = v.findViewById(R.id.circuit_hops);
        final List<RelayInfo> path = c.getPath();
        final StringBuilder spoken = new StringBuilder(activity.getString(R.string.cd_path));
        for (int i = 0; i < path.size(); i++) {
            final View hop = inflater.inflate(R.layout.item_hop, hops, false);
            final String role = activity.getString(role(c, i, path.size()));
            final String cc = path.get(i).getCountryCode();
            ((TextView) hop.findViewById(R.id.hop_role)).setText(role);
            ((TextView) hop.findViewById(R.id.hop_cc)).setText(cc == null ? "??" : cc);
            hops.addView(hop);
            spoken.append(", ").append(role).append(' ').append(countryName(cc));
            if (i < path.size() - 1) {
                final ImageView arrow = new ImageView(activity);
                arrow.setImageResource(R.drawable.ic_chevron_right);
                arrow.setColorFilter(activity.getColor(R.color.text_muted));
                arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                final int size = UiUtil.dp(activity, 14);
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginStart(UiUtil.dp(activity, 4));
                lp.setMarginEnd(UiUtil.dp(activity, 4));
                hops.addView(arrow, lp);
            }
        }
        hops.setContentDescription(spoken);
        for (int i = 0; i < hops.getChildCount(); i++) {
            hops.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        hops.setFocusable(true);

        ((TextView) v.findViewById(R.id.circuit_detail)).setText(detail(c));
        return v;
    }

    private int purpose(CircuitInfo c) {
        switch (c.getPurpose()) {
        case EXIT:
            // In use: streams on it now.  Idle: used before (it serves its
            // isolation group again until it is 10 minutes old).  Standby:
            // built in advance, so that a new connection need not wait.
            if (c.getStreamCount() > 0) {
                return R.string.purpose_exit;
            }
            return c.getSecondsDirty() > 0 ? R.string.purpose_exit_idle : R.string.purpose_exit_spare;
        // Onion circuits, named like the exit ones: kind (state).
        case INTERNAL: return R.string.purpose_internal; // built in advance, not used yet
        case ONION_SERVICE_DIRECTORY: return R.string.purpose_hsdir;
        case ONION_SERVICE_INTRODUCTION: return R.string.purpose_intro;
        case ONION_SERVICE: return R.string.purpose_onion;
        default: return R.string.purpose_directory;
        }
    }

    private void stateChip(TextView chip, CircuitInfo c) {
        final int text, fg, bg;
        switch (c.getState()) {
        case BUILDING:
            text = R.string.state_building; fg = R.color.accent; bg = R.color.accent_soft;
            break;
        case CLOSING:
            text = R.string.state_closing; fg = R.color.text_muted; bg = R.color.chip;
            break;
        default:
            if (c.getStreamCount() > 0) {
                text = R.string.state_in_use; fg = R.color.success; bg = R.color.success_soft;
            } else {
                text = R.string.state_ready; fg = R.color.text_tertiary; bg = R.color.chip;
            }
            break;
        }
        chip.setText(text);
        chip.setTextColor(activity.getColor(fg));
        chip.getBackground().mutate().setTint(activity.getColor(bg));
    }

    /** The role of hop i of n; a circuit being built has fewer hops than it will. */
    private int role(CircuitInfo c, int i, int n) {
        if (i == 0) {
            return status != null && status.settings != null && status.settings.useBridges
                    ? R.string.hop_bridge : R.string.hop_guard;
        }
        final boolean last = i == n - 1 && n >= 3;
        if (last && c.getPurpose() == CircuitInfo.Purpose.EXIT) {
            return R.string.hop_exit;
        }
        if (last && c.getPurpose() == CircuitInfo.Purpose.ONION_SERVICE) {
            return R.string.hop_rendezvous;
        }
        return R.string.hop_middle;
    }

    private String detail(CircuitInfo c) {
        final List<String> parts = new ArrayList<String>();
        final int streams = c.getStreamCount();
        if (streams > 0) {
            parts.add(activity.getResources().getQuantityString(R.plurals.circuit_streams, streams, streams));
        }
        if (c.isIsolated()) {
            parts.add(activity.getString(R.string.circuit_isolated));
        }
        final int dirty = c.getSecondsDirty();
        if (dirty > 0) {
            final int minutes = Formats.minutes(dirty);
            parts.add(minutes >= 1
                    ? activity.getResources().getQuantityString(R.plurals.circuit_age, minutes, minutes)
                    : activity.getString(R.string.circuit_age_new));
        } else if (streams == 0 && c.getState() == CircuitInfo.State.OPEN) {
            parts.add(activity.getString(R.string.circuit_unused));
        }
        switch (c.getPurpose()) {
        case ONION_SERVICE:
        case ONION_SERVICE_INTRODUCTION:
        case ONION_SERVICE_DIRECTORY:
            parts.add(activity.getString(R.string.circuit_onion_hidden));
            break;
        default:
            break;
        }
        return join(parts);
    }

    private static String join(List<String> parts) {
        final StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /*
     * One unreachable guard is normal: Tor moves on to the next one and
     * tries it again later, so it is not shown here (the details screen
     * has it).  What matters to the user is when none of the primary
     * guards can be reached: the network probably blocks Tor, and bridges
     * are the way around it.  It can also be a sign of someone pushing the
     * client away from its guards.
     */
    private void showGuards(List<GuardInfo> guards) {
        if (status == null || status.state != TorController.State.RUNNING) {
            return;
        }
        UiUtil.show(guardCard, true);
        int primary = 0, reachable = 0, unreachable = 0;
        for (GuardInfo g : guards) {
            if (!g.isPrimary()) {
                continue;
            }
            primary++;
            if (g.getReachability() == GuardInfo.Reachability.REACHABLE) {
                reachable++;
            } else if (g.getReachability() == GuardInfo.Reachability.UNREACHABLE) {
                unreachable++;
            }
        }
        final boolean bridges = status.settings != null && status.settings.useBridges;
        final boolean blocked = !bridges && primary > 0 && unreachable == primary;
        final int text;
        int fg = R.color.text_tertiary, bg = R.color.chip;
        if (bridges) {
            text = R.string.guards_bridges;
        } else if (primary == 0) {
            text = R.string.guards_none;
        } else if (blocked) {
            text = R.string.guards_unreachable;
            fg = R.color.error;
            bg = R.color.error_soft;
        } else if (reachable > 0) {
            text = R.string.guards_ok;
            fg = R.color.success;
            bg = R.color.success_soft;
        } else {
            text = R.string.guards_unknown;
        }
        guardSummary.setText(text);
        guardSummary.setTextColor(activity.getColor(fg));
        guardSummary.getBackground().mutate().setTint(activity.getColor(bg));
        UiUtil.show(guardBlocked, blocked);
    }

    private String countryName(String cc) {
        if (cc == null || cc.length() != 2 || "??".equals(cc)) {
            return activity.getString(R.string.country_unknown);
        }
        return new Locale("", cc).getDisplayCountry();
    }
}
