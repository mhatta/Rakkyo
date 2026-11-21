package org.mhatta.rakkyo;

import android.content.Context;

/** The message after "New identity", shared by the home screen and the notification. */
final class NewIdentity {
    private NewIdentity() {
    }

    /** @param delay the result of {@link TorController#newIdentity}: 0, milliseconds, or -1 */
    static String message(Context context, Long delay) {
        if (delay == null || delay < 0) {
            return context.getString(R.string.new_identity_not_running);
        } else if (delay == 0) {
            return context.getString(R.string.new_identity_done);
        }
        final int seconds = (int) Math.max(1, (delay + 999) / 1000);
        return context.getResources().getQuantityString(R.plurals.new_identity_delayed, seconds, seconds);
    }
}
