package net.yasmar.crond;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;

import java.lang.ref.WeakReference;

import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NAME;
import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NO_NAME;
import static net.yasmar.crond.Constants.PREFERENCES_FILE;
import static net.yasmar.crond.Constants.PREF_USE_WAKE_LOCK;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        // TODO
        // Start a foreground service
        // service starts async tasks
        // async task ends and calls service
        // when no tasks left, service ends

        Log.i(TAG, "AlarmReceiver START onReceive");
        new LineExecutor(context).execute(intent);
        Log.i(TAG, "AlarmReceiver STOP onReceive");
    }

    private static class LineExecutor extends AsyncTask<Intent, Void, Void> {
        private WeakReference<Context> contextRef = null;

        public LineExecutor(Context context) {
            contextRef = new WeakReference<>(context);
        }

        @SuppressLint("WakelockTimeout")
        @Override
        protected Void doInBackground(Intent... intent) {
            Log.i(TAG, "AlarmReceiver START doInBackground");
            Context context = contextRef.get();
            SharedPreferences sharedPrefs = context.getSharedPreferences(PREFERENCES_FILE,
                    Context.MODE_PRIVATE);
            PowerManager.WakeLock wakeLock = null;
            if (sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false)) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                wakeLock.acquire();
            }
            Crond crond = new Crond(context);
            String line = intent[0].getExtras().getString(INTENT_EXTRA_LINE_NAME);
            int lineNo = intent[0].getExtras().getInt(INTENT_EXTRA_LINE_NO_NAME);
            Log.i(TAG, "AlarmReceiver START executeLine");
            crond.executeLine(line, lineNo);
            Log.i(TAG, "AlarmReceiver STOP executeLine");
            crond.scheduleLine(line, lineNo, false, false, false);
            if (sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false)) {
                wakeLock.release();
            }
            Log.i(TAG, "AlarmReceiver STOP doInBackground");
            return null;
        }
    }
}
