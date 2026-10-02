package com.internetpowerful.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

public class CommitmentReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_COMMITMENT_REMINDER =
            "com.internetpowerful.app.ACTION_COMMITMENT_REMINDER";

    private static final String PREFS_NAME =
            "internet_powerful_commitments";

    private static final String KEY_PREFIX =
            "commitment_";

    private static final String CHANNEL_ID =
            "commitments";

    public static void schedule(
            Context context,
            String clientId,
            String clientName,
            String date,
            String time,
            String note
    ) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }

        if (date == null || date.trim().isEmpty()) {
            return;
        }

        if (time == null || time.trim().isEmpty()) {
            time = "09:00";
        }

        try {
            JSONObject data = new JSONObject();
            data.put("clientId", clientId);
            data.put("clientName", clientName != null ? clientName : "Cliente");
            data.put("date", date);
            data.put("time", time);
            data.put("note", note != null ? note : "");

            SharedPreferences prefs =
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            prefs.edit()
                    .putString(KEY_PREFIX + clientId, data.toString())
                    .apply();

            scheduleAlarm(
                    context.getApplicationContext(),
                    clientId,
                    clientName,
                    date,
                    time,
                    note
            );

        } catch (Exception ignored) {
        }
    }

    public static void cancel(
            Context context,
            String clientId
    ) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager != null) {
            PendingIntent pendingIntent =
                    buildPendingIntent(
                            context,
                            clientId,
                            clientId,
                            "",
                            "",
                            ""
                    );

            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .remove(KEY_PREFIX + clientId)
                .apply();
    }

    private static void scheduleAlarm(
            Context context,
            String clientId,
            String clientName,
            String date,
            String time,
            String note
    ) {
        Calendar calendar = Calendar.getInstance();

        try {
            String[] dateParts = date.split("-");
            String[] timeParts = time.split(":");

            if (dateParts.length != 3 || timeParts.length < 2) {
                return;
            }

            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]) - 1;
            int day = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

        } catch (Exception ignored) {
            return;
        }

        long triggerAtMillis = calendar.getTimeInMillis();

        // Si la fecha/hora ya pasó, no dejamos una notificación atrasada.
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent =
                buildPendingIntent(
                        context,
                        clientId,
                        clientId,
                        clientName,
                        date,
                        time
                );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    );
                } else {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            }

        } catch (SecurityException e) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    );
                } else {
                    alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static PendingIntent buildPendingIntent(
            Context context,
            String clientId,
            String extraClientId,
            String clientName,
            String date,
            String time
    ) {
        Intent intent =
                new Intent(context, CommitmentReminderReceiver.class);

        intent.setAction(ACTION_COMMITMENT_REMINDER);
        intent.putExtra("clientId", extraClientId);
        intent.putExtra("clientName", clientName);
        intent.putExtra("date", date);
        intent.putExtra("time", time);

        return PendingIntent.getBroadcast(
                context,
                requestCode(clientId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int requestCode(String clientId) {
        return clientId.hashCode() & 0x7fffffff;
    }

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        if (intent == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            rescheduleAll(context);
            return;
        }

        if (!ACTION_COMMITMENT_REMINDER.equals(intent.getAction())) {
            return;
        }

        String clientId =
                intent.getStringExtra("clientId");

        String clientName =
                intent.getStringExtra("clientName");

        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Cliente";
        }

        String date =
                intent.getStringExtra("date");

        String time =
                intent.getStringExtra("time");

        showNotification(
                context,
                clientId,
                clientName,
                date,
                time
        );

        if (clientId != null && !clientId.isEmpty()) {
            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            prefs.edit()
                    .remove(KEY_PREFIX + clientId)
                    .apply();
        }
    }

    private static void showNotification(
            Context context,
            String clientId,
            String clientName,
            String date,
            String time
    ) {
        createChannel(context);

        Intent openApp =
                new Intent(context, MainActivity.class);

        openApp.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode(clientId != null ? clientId : "general"),
                        openApp,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        String body =
                "Hoy indicó que realizará su pago"
                        + (time != null && !time.isEmpty()
                        ? " a las " + time
                        : "")
                        + ".";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder =
                    new Notification.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(
                                    "🤝 Compromiso de pago: " + clientName
                            )
                            .setContentText(body)
                            .setStyle(
                                    new Notification.BigTextStyle()
                                            .bigText(body)
                            )
                            .setContentIntent(contentIntent)
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH);

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.notify(
                        requestCode(clientId != null ? clientId : "general"),
                        builder.build()
                );
            }

        } else {
            Notification.Builder builder =
                    new Notification.Builder(context)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(
                                    "🤝 Compromiso de pago: " + clientName
                            )
                            .setContentText(body)
                            .setContentIntent(contentIntent)
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH);

            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager != null) {
                manager.notify(
                        requestCode(clientId != null ? clientId : "general"),
                        builder.build()
                );
            }
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Compromisos de pago",
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Recordatorios de fechas de pago prometidas por clientes."
        );
        channel.enableVibration(true);

        NotificationManager manager =
                context.getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private static void rescheduleAll(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        for (Map.Entry<String, ?> entry :
                prefs.getAll().entrySet()) {

            if (!entry.getKey().startsWith(KEY_PREFIX)) {
                continue;
            }

            String json = String.valueOf(entry.getValue());

            try {
                JSONObject data =
                        new JSONObject(json);

                String clientId =
                        data.optString("clientId", "");

                String clientName =
                        data.optString("clientName", "Cliente");

                String date =
                        data.optString("date", "");

                String time =
                        data.optString("time", "09:00");

                String note =
                        data.optString("note", "");

                if (!clientId.isEmpty() && !date.isEmpty()) {
                    scheduleAlarm(
                            context.getApplicationContext(),
                            clientId,
                            clientName,
                            date,
                            time,
                            note
                    );
                }

            } catch (Exception ignored) {
            }
        }
    }
}
