package org.mhatta.rakkyo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the proxy when the device has booted, if the user asked for it.
 * Android 15 forbids starting some foreground service types from
 * BOOT_COMPLETED (dataSync among them), but not specialUse.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (new Prefs(context).isStartOnBoot()) {
            TorService.start(context);
        }
    }
}
