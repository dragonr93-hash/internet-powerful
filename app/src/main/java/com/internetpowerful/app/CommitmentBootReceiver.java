package com.internetpowerful.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

public class CommitmentBootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME =
            "internet_powerful_commitments";

    private static final String KEY_PREFIX =
            "commitment_";

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

        for (Map.Entry<String, ?> entry :
                prefs.getAll().entrySet()) {

            if (!entry.getKey().startsWith(KEY_PREFIX)) {
                continue;
            }

            try {
                JSONObject data = new JSONObject(
                        String.valueOf(entry.getValue())
                );

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
                // recuperar los demás compromisos.
            }
        }
    }
}
