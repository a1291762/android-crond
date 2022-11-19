package net.yasmar.crond;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "AlarmReceiver START onReceive");
        Intent serviceIntent = new Intent(context, JobRunnerService.class);
        serviceIntent.setAction("start");
        serviceIntent.putExtras(intent);
        context.startForegroundService(serviceIntent);
        Log.i(TAG, "AlarmReceiver STOP onReceive");
    }

}
