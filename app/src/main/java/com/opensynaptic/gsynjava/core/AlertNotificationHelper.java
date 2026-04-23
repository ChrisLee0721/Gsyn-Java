package com.opensynaptic.gsynjava.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.opensynaptic.gsynjava.ui.MainActivity;

/**
 * Sends local push notifications for incoming alerts.
 * Channel creation is guarded to API 26+ via {@link #createChannelsO}.
 */
public final class AlertNotificationHelper {

    public static final String CHANNEL_CRITICAL = "gsyn_critical";
    public static final String CHANNEL_WARNING  = "gsyn_warning";

    private static final int NOTIF_ID_CRITICAL = 1001;
    private static final int NOTIF_ID_WARNING  = 1002;

    private AlertNotificationHelper() {}

    /** No-op below API 26; creates notification channels on API 26+. */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannelsO(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static void createChannelsO(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel critical = new NotificationChannel(
                CHANNEL_CRITICAL, "Critical Alerts", NotificationManager.IMPORTANCE_HIGH);
        critical.setDescription("Critical-level device alerts");
        critical.enableVibration(true);
        nm.createNotificationChannel(critical);
        NotificationChannel warning = new NotificationChannel(
                CHANNEL_WARNING, "Warning Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        warning.setDescription("Warning-level device alerts");
        nm.createNotificationChannel(warning);
    }

    /**
     * Posts a notification; suppresses Info (level 0).
     * Silently ignores SecurityException when POST_NOTIFICATIONS not yet granted.
     */
    public static void notify(Context context, int level, String message, int deviceAid) {
        if (level < 1) return;
        String channel = (level == 2) ? CHANNEL_CRITICAL : CHANNEL_WARNING;
        int    notifId = (level == 2) ? NOTIF_ID_CRITICAL : NOTIF_ID_WARNING;
        String title   = (level == 2) ? "⛔ CRITICAL — AID " + deviceAid
                                      : "⚠ WARNING — AID " + deviceAid;
        int priority   = (level == 2) ? NotificationCompat.PRIORITY_HIGH
                                      : NotificationCompat.PRIORITY_DEFAULT;
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, notifId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setContentIntent(pending)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException ignored) {}
    }
}
