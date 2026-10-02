package com.internetpowerful.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * V45 - Reprograma los compromisos de pago después de reiniciar Android
 * o después de actualizar/reinstalar la aplicación.
 */
public class CommitmentBootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "internet_powerful_commitments";
    private static final String KEY_COMMITMENTS = "scheduled_commitments";

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

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String json = prefs.getString(KEY_COMMITMENTS, "[]");

        try {
            JSONArray commitments = new JSONArray(json);

            for (int i = 0; i < commitments.length(); i++) {

                JSONObject item = commitments.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                String clientId = item.optString("clientId", "");
                String clientName = item.optString("clientName", "");
                String date = item.optString("date", "");
                String time = item.optString("time", "09:00");
                String note = item.optString("note", "");

                if (clientId.isEmpty()
                        || date.isEmpty()
                        || time.isEmpty()) {
                    continue;
                }

                CommitmentReminderReceiver.schedule(
                        context,
                        clientId,
                        clientName,
                        date,
                        time,
                        note
                );
            }

        } catch (Exception ignored) {
            // Una entrada dañada no debe impedir
            // que las demás alarmas se reprogramen.
        }
    }
}
