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
        SharedPreferences sharedPrefs = context.getSharedPreferences(PREFERENCES_FILE,
                Context.MODE_PRIVATE);
        final PowerManager.WakeLock wakeLock;
        if (sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false)) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG+":wakeLock");
            wakeLock.acquire();
        } else {
            wakeLock = null;
        }
        Crond crond = new Crond(context);
        String line = intent.getExtras().getString(INTENT_EXTRA_LINE_NAME);
        int lineNo = intent.getExtras().getInt(INTENT_EXTRA_LINE_NO_NAME);
        executor.execute(() -> {
            IO.rootAvailable = Shell.SU.available();
            IO.nonRootPrefix = context.getExternalFilesDir(null);
            crond.executeLine(line, lineNo);
            handler.post(() -> {
                crond.scheduleLine(line, lineNo, false, false, false);
                if (wakeLock != null) {
                    wakeLock.release();
                }
            });
        });
    }
}
