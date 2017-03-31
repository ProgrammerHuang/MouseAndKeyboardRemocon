package com.androidapp.santtu.remotemouseandkeyboard;

import android.content.Context;
import android.widget.Toast;

/**
 * Singleton for creating toast messages. Prevents having too many toast messages being queued.
 */

public class SingleToast {

    private static Toast mToast;

    /**
     * Creates a toast message
     * @param context the context where the call came from
     * @param text the message to be displayed
     * @param duration how long the message should be shown
     */
    public static void show(Context context, String text, int duration) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, text, duration);
        mToast.show();
    }
}
