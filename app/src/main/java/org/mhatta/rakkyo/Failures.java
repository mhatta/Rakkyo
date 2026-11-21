package org.mhatta.rakkyo;

import android.content.Context;

/** Why the proxy could not start, in words. */
final class Failures {
    private Failures() {
    }

    static String message(Context context, TorController.Status status) {
        if (status.failure == null) {
            return context.getString(R.string.failed_other);
        }
        switch (status.failure) {
        case PORT_IN_USE:
            return context.getString(R.string.failed_port, status.failedPort);
        case BAD_BRIDGES:
            return context.getString(R.string.failed_bridges);
        default:
            return context.getString(R.string.failed_other);
        }
    }
}
