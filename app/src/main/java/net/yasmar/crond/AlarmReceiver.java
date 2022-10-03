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

        Log.i(TAG, "AlarmReceiver START onReceive");
        //new LineExecutor(context).execute(intent);
        Intent serviceIntent = new Intent(context, JobRunnerService.class);
        serviceIntent.setAction("start");
        serviceIntent.putExtras(intent);
        context.startForegroundService(serviceIntent);
        Log.i(TAG, "AlarmReceiver STOP onReceive");
    }

}
