package com.internetpowerful.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * V45 - Reprograma los compromisos guardados después del arranque
 * del teléfono o de una actualización de la aplicación.
 */
public class CommitmentBootReceiver extends BroadcastReceiver {

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

        // Usa el único método de recuperación, que lee las mismas claves
        // que CommitmentReminderReceiver.schedule() guarda.
        CommitmentReminderReceiver.rescheduleSavedCommitments(context);
    }
}
