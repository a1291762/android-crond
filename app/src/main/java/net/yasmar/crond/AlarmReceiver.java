package net.yasmar.crond;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.chainfire.libsuperuser.Shell;

import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NAME;
import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NO_NAME;
import static net.yasmar.crond.Constants.PREFERENCES_FILE;
import static net.yasmar.crond.Constants.PREF_USE_WAKE_LOCK;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        SharedPreferences sharedPrefs = context.getSharedPreferences(PREFERENCES_FILE,
                Context.MODE_PRIVATE);
        final PowerManager.WakeLock wakeLock;
        if (sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false)) {
            Log.i(TAG, "grabbing a wakelock");
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG+":wakeLock");
            wakeLock.acquire();
        } else {
            Log.i(TAG, "not grabbing a wakelock");
            wakeLock = null;
        }
        Crond crond = new Crond(context);
        String line = intent.getExtras().getString(INTENT_EXTRA_LINE_NAME);
        int lineNo = intent.getExtras().getInt(INTENT_EXTRA_LINE_NO_NAME);
        Log.i(TAG, "spinning up a thread");
        executor.execute(() -> {
            Log.i(TAG, "checking for root");
            IO.rootAvailable = Shell.SU.available();
            IO.nonRootPrefix = context.getExternalFilesDir(null);
            Log.i(TAG, "execute line "+line+" lineNo" + lineNo);
            crond.executeLine(line, lineNo);
            Log.i(TAG, "posting to the main thread");
            handler.post(() -> {
                Log.i(TAG, "schedule line "+line+" lineNo" + lineNo);
                crond.scheduleLine(line, lineNo, false, false, false);
                if (wakeLock != null) {
                    Log.i(TAG, "releasing wakelock");
                    wakeLock.release();
                }
            });
        });
    }
}
