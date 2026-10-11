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
import android.util.Log;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

public class CommitmentReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_COMMITMENT_REMINDER =
            "com.internetpowerful.app.ACTION_COMMITMENT_REMINDER";

    private static final String PREFS_NAME = "internet_powerful_commitments";
    private static final String KEY_PREFIX = "commitment_";
    private static final String CHANNEL_ID = "commitments";
    private static final String TAG = "IPCommitmentReminder";

    public static void schedule(Context context, String clientId, String clientName,
                                String date, String time, String note) {
        if (clientId == null || clientId.trim().isEmpty()
                || date == null || date.trim().isEmpty()) {
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

            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_PREFIX + clientId, data.toString()).apply();

            scheduleAlarm(context.getApplicationContext(), clientId,
                    clientName, date, time, note);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo guardar/programar el compromiso", e);
        }
    }

    public static void cancel(Context context, String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            PendingIntent pendingIntent = buildPendingIntent(
                    context, clientId, clientId, "", "", "");
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PREFIX + clientId).apply();
    }

    private static void scheduleAlarm(Context context, String clientId,
                                      String clientName, String date,
                                      String time, String note) {
        Calendar calendar = Calendar.getInstance();
        try {
            String[] dateParts = date.split("-");
            String[] timeParts = time.split(":");
            if (dateParts.length != 3 || timeParts.length < 2) {
                Log.w(TAG, "Formato de fecha/hora no válido: " + date + " " + time);
                return;
            }

            calendar.set(Calendar.YEAR, Integer.parseInt(dateParts[0]));
            calendar.set(Calendar.MONTH, Integer.parseInt(dateParts[1]) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateParts[2]));
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo interpretar fecha/hora", e);
            return;
        }

        long triggerAtMillis = calendar.getTimeInMillis();
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.i(TAG, "Compromiso vencido; no se programa: " + clientId);
            return;
        }

        AlarmManager alarmManager = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager no disponible");
            return;
        }

        PendingIntent pendingIntent = buildPendingIntent(
                context, clientId, clientId, clientName, date, time);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.i(TAG, "Alarma programada para " + clientId + " " + date + " " + time);
        } catch (SecurityException e) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Falló la programación alternativa", fallbackError);
            }
        }
    }

    private static PendingIntent buildPendingIntent(Context context, String clientId,
                                                    String extraClientId, String clientName,
                                                    String date, String time) {
        Intent intent = new Intent(context, CommitmentReminderReceiver.class);
        intent.setAction(ACTION_COMMITMENT_REMINDER);
        intent.putExtra("clientId", extraClientId);
        intent.putExtra("clientName", clientName);
        intent.putExtra("date", date);
        intent.putExtra("time", time);

        return PendingIntent.getBroadcast(
                context, requestCode(clientId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(String clientId) {
        return clientId.hashCode() & 0x7fffffff;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (ACTION_COMMITMENT_REMINDER.equals(intent.getAction())) {
            String clientId = intent.getStringExtra("clientId");
            String clientName = intent.getStringExtra("clientName");
            String date = intent.getStringExtra("date");
            String time = intent.getStringExtra("time");

            if (clientName == null || clientName.trim().isEmpty()) {
                clientName = "Cliente";
            }

            showNotification(context, clientId, clientName, date, time);

            if (clientId != null && !clientId.isEmpty()) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().remove(KEY_PREFIX + clientId).apply();
            }
        }
    }

    /**
     * Único método de recuperación. Lee el formato que schedule() guarda.
     * Lo llama CommitmentBootReceiver después de BOOT_COMPLETED o actualización.
     */
    public static void rescheduleSavedCommitments(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        int restored = 0;

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || !key.startsWith(KEY_PREFIX)
                    || !(value instanceof String)) {
                continue;
            }

            try {
                JSONObject data = new JSONObject((String) value);
                String clientId = data.optString("clientId", "");
                if (clientId.trim().isEmpty()) {
                    clientId = key.substring(KEY_PREFIX.length());
                }

                String clientName = data.optString("clientName", "Cliente");
                String date = data.optString("date", "");
                String time = data.optString("time", "09:00");
                String note = data.optString("note", "");

                if (!clientId.trim().isEmpty() && !date.trim().isEmpty()) {
                    scheduleAlarm(context.getApplicationContext(), clientId,
                            clientName, date, time, note);
                    restored++;
                }
            } catch (Exception e) {
                Log.e(TAG, "No se pudo recuperar el compromiso guardado: " + key, e);
            }
        }

        Log.i(TAG, "Compromisos encontrados para recuperar: " + restored);
    }

    private static void showNotification(Context context, String clientId,
                                         String clientName, String date, String time) {
        createChannel(context);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, requestCode(clientId != null ? clientId : "general"),
                openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = "Hoy indicó que realizará su pago"
                + (time != null && !time.isEmpty() ? " a las " + time : "") + ".";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("🤝 Compromiso de pago: " + clientName)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);
        } else {
            builder = new Notification.Builder(context)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("🤝 Compromiso de pago: " + clientName)
                    .setContentText(body)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH);
        }

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(requestCode(clientId != null ? clientId : "general"),
                    builder.build());
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Compromisos de pago", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Recordatorios de fechas de pago prometidas por clientes.");
        channel.enableVibration(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
