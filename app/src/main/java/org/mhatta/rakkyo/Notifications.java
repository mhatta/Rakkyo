package org.mhatta.rakkyo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * The notifications.  They name the state and the local proxy address only:
 * never relays, guards or traffic.
 *
 * <p>They are not shown on a secure lock screen (VISIBILITY_SECRET).  Their
 * text says that Tor is in use, and the app name Rakkyo, chosen not to say
 * so, would not help if the lock screen did: anyone who holds the phone, at
 * a border or at work, could read it.  VISIBILITY_PRIVATE would not be
 * enough, since with Android's default lock screen setting ("show all
 * notification content") private notifications are shown in full.
 */
final class Notifications {
    static final int ID_ONGOING = 1;
    static final int ID_FAILED = 2;

    private static final String CHANNEL_STATUS = "status";
    private static final String CHANNEL_ALERTS = "alerts";

    private Notifications() {
    }

    static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel status = new NotificationChannel(CHANNEL_STATUS,
                context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW);
        status.setDescription(context.getString(R.string.channel_status_description));
        status.setShowBadge(false);
        // For new installs; an existing channel keeps its settings, but
        // each notification is VISIBILITY_SECRET anyway.
        status.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        final NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT);
        alerts.setDescription(context.getString(R.string.channel_alerts_description));
        alerts.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(status);
        nm.createNotificationChannel(alerts);
    }

    static Notification ongoing(Context context, TorController.Status status) {
        final Notification.Builder b = builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentIntent(openApp(context))
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET);

        if (status.state == TorController.State.RUNNING) {
            final int title = status.dormant ? R.string.notification_dormant : R.string.notification_connected;
            b.setContentTitle(context.getString(title));
            if (status.settings != null) {
                b.setContentText(context.getString(R.string.notification_socks,
                        TorController.LISTEN_ADDRESS + ":" + status.settings.socksPort));
            }
            b.addAction(new Notification.Action.Builder(null, context.getString(R.string.new_identity),
                    service(context, TorService.ACTION_NEW_IDENTITY, 1)).build());
        } else {
            b.setContentTitle(context.getString(R.string.notification_starting, status.percent));
            b.setProgress(100, status.percent, false);
        }
        b.addAction(new Notification.Action.Builder(null, context.getString(R.string.action_stop),
                service(context, TorService.ACTION_STOP, 2)).build());
        return b.build();
    }

    static Notification failed(Context context, TorController.Status status) {
        final String message = Failures.message(context, status);
        return builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_failed))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context context, String channel) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(context, channel);
        }
        return new Notification.Builder(context).setPriority(Notification.PRIORITY_LOW);
    }

    private static PendingIntent openApp(Context context) {
        final Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent service(Context context, String action, int requestCode) {
        final Intent intent = new Intent(context, TorService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
        }
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }
}
