package com.internetpowerful.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

/**
 * V45 - Recupera los compromisos guardados y vuelve a programar
 * sus alarmas después de reiniciar Android o actualizar la app.
 */
public class CommitmentBootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "internet_powerful_commitments";
    private static final String KEY_PREFIX = "commitment_";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(KEY_PREFIX)
                    || !(entry.getValue() instanceof String)) {
                continue;
            }

            try {
                JSONObject data = new JSONObject((String) entry.getValue());
                String clientId = data.optString("clientId", "");
                if (clientId.trim().isEmpty()) {
                    clientId = key.substring(KEY_PREFIX.length());
                }

                String clientName = data.optString("clientName", "Cliente");
                String date = data.optString("date", "");
                String time = data.optString("time", "09:00");
                String note = data.optString("note", "");

                if (!clientId.trim().isEmpty() && !date.trim().isEmpty()) {
                    CommitmentReminderReceiver.schedule(
                            context, clientId, clientName, date, time, note);
                }
            } catch (Exception ignored) {
                // Una entrada dañada no debe impedir recuperar las demás.
            }
        }
    }
}
