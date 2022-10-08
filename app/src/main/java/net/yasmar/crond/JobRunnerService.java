package net.yasmar.crond;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NAME;
import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NO_NAME;
import static net.yasmar.crond.Constants.PREFERENCES_FILE;
import static net.yasmar.crond.Constants.PREF_USE_WAKE_LOCK;

public class JobRunnerService
        extends Service {

    private static final String TAG = "JobRunnerService";
    private static final String PERSISTENT_CHANNEL = "persistent";

    Context context;
    SharedPreferences sharedPrefs;
    PowerManager.WakeLock wakeLock;
    Handler handler;
    NotificationManager notificationManager;
    Notification notification;
    int jobs = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        context = this;

        sharedPrefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);

        if (sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false)) {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            wakeLock.acquire();
        }

        handler = new Handler(Looper.getMainLooper());

        notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            throw new NullPointerException("notificationManager");
        }
        createNotificationChannels();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null) {
            wakeLock.release();
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (notification == null) {
            notification = createNotification();
            startForeground(1, notification);
        }
        jobs++;
        wrapInTask(() -> runJob(intent));
        return START_NOT_STICKY;
    }

    public void createNotificationChannels() {
        String channelId = PERSISTENT_CHANNEL;
        if (notificationManager.getNotificationChannel(channelId) == null) {
            CharSequence name = "Persistent Notification";
            String description = "Used to ensure the app does not die in the background. Can be hidden.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(channelId, name, importance);
            mChannel.setDescription(description);
            mChannel.setSound(null, null);
            mChannel.enableLights(false);
            notificationManager.createNotificationChannel(mChannel);
        }
    }

    Notification createNotification() {
        Notification.Builder b = new Notification.Builder(context, PERSISTENT_CHANNEL);
        b.setSmallIcon(R.drawable.ic_notification_icon);
        b.setContentTitle("Service Notification");
        b.setContentText("Tap to hide.");

        Intent i = new Intent();
        i.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        i.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
        PendingIntent ci = PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(ci);

        return b.build();
    }

    static class TaskWrapper
            extends AsyncTask<Runnable, Object, Object> {
        @Override
        protected Object doInBackground(Runnable... args) {
            Runnable callback = args[0];
            callback.run();
            return null;
        }
    }

    void wrapInTask(Runnable callback) {
        TaskWrapper wrapper = new TaskWrapper();
        wrapper.execute(callback);
    }

    void runJob(Intent intent) {
        Crond crond = new Crond(context);
        String line = intent.getExtras().getString(INTENT_EXTRA_LINE_NAME);
        int lineNo = intent.getExtras().getInt(INTENT_EXTRA_LINE_NO_NAME);
        Log.i(TAG, "AlarmReceiver START executeLine");
        crond.executeLine(line, lineNo);
        Log.i(TAG, "AlarmReceiver STOP executeLine");
        crond.scheduleLine(line, lineNo, false, false, false);

        handler.post(() -> finishJob());
    }

    void finishJob() {
        jobs--;
        if (jobs == 0) {
            stopForeground(true);
            stopSelf();
        }
    }
}
