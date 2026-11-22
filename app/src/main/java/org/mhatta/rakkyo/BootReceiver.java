package org.mhatta.rakkyo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the proxy when the device has booted, if the user asked for it,
 * or pauses it again (holding the ports) if it was left paused.
 * Android 15 forbids starting some foreground service types from
 * BOOT_COMPLETED (dataSync among them), but not specialUse.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        final Prefs prefs = new Prefs(context);
        if (prefs.isStartOnBoot()) {
            TorService.start(context);
        } else if (prefs.wantsPaused() && prefs.isKeepPorts()) {
            // Left paused: hold the ports again before another app can.
            TorService.pause(context);
        }
    }
}
