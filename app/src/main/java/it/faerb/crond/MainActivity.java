package it.faerb.crond;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import eu.chainfire.libsuperuser.Shell;

import static android.view.View.VISIBLE;
import static it.faerb.crond.Constants.PREFERENCES_FILE;
import static it.faerb.crond.Constants.PREF_ENABLED;
import static it.faerb.crond.Constants.PREF_NOTIFICATION_ENABLED;
import static it.faerb.crond.Constants.PREF_USE_WAKE_LOCK;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final Handler refreshHandler = new Handler();

    private Crond crond = null;

    private SharedPreferences sharedPrefs = null;
    private boolean rootAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new RootChecker(this).execute();
    }

    private static class RootChecker extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<MainActivity> contextRef = null;

        public RootChecker(MainActivity context) {
            contextRef = new WeakReference<>(context);
        }
        @Override
        protected Boolean doInBackground(Void... params) {
            contextRef.get().rootAvailable = Shell.SU.available();
            return contextRef.get().rootAvailable;
        }

        @Override
        protected void onPostExecute(Boolean rootAvail) {
            if (rootAvail) {
                contextRef.get().init();
            }
        }
    }

    private void init() {
        crond = new Crond(this);
        sharedPrefs = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);

        LinearLayout layout = findViewById(R.id.root_layout);
        for (int i = 0; i<layout.getChildCount(); i++) {
            View view = layout.getChildAt(i);
            view.setEnabled(true);
            view.setVisibility(VISIBLE);
        }
        final TextView crontabLabel = findViewById(R.id.text_label_crontab);
        crontabLabel.setText(getString(R.string.crontab_label, IO.getCrontabPath()));

        final TextView crontabContent = findViewById(R.id.text_content_crontab);
        crontabContent.setMovementMethod(new ScrollingMovementMethod());

        final TextView crondlogLabel = findViewById(R.id.text_label_crond_log);
        crondlogLabel.setText(getString(R.string.crond_log_label, IO.getLogPath()));

        final TextView crondLog = findViewById(R.id.text_content_crond_log);
        crondLog.setMovementMethod(new ScrollingMovementMethod());

        final CheckBox notificationCheckBox = findViewById(
                R.id.check_notification_setting);
        notificationCheckBox.setChecked(sharedPrefs.getBoolean(PREF_NOTIFICATION_ENABLED, false));
        notificationCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sharedPrefs.edit().putBoolean(PREF_NOTIFICATION_ENABLED,
                        notificationCheckBox.isChecked()).apply();
            }
        });

        final CheckBox wakeLockCheckBox = findViewById(
                R.id.check_wakelock_setting);
        wakeLockCheckBox.setChecked(sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false));
        wakeLockCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharedPrefs.edit().putBoolean(PREF_USE_WAKE_LOCK,
                        wakeLockCheckBox.isChecked()).apply();
            }
        });

        final Button enableButton = findViewById(R.id.button_enable);
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean oldEnabled = sharedPrefs.getBoolean(PREF_ENABLED, false);
                sharedPrefs.edit().putBoolean(PREF_ENABLED, !oldEnabled).apply();
                updateEnabled();
                crond.scheduleCrontab();
                refreshImmediately();
            }
        });

        final Button clearButton = findViewById(R.id.button_clear_log);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.dialog_clean_title)
                        .setMessage(R.string.dialog_clean_message)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new LogClearer().execute();
                                refreshImmediately();
                            }
                        })
                        .show();
            }
        });

        updateEnabled();
        refreshHandler.post(refresh);
    }

    @Override
    public void onResume() {
       super.onResume();
        refreshHandler.removeCallbacksAndMessages(null);
        if (rootAvailable) {
            refreshHandler.post(refresh);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacksAndMessages(null);
    }

    private void refreshImmediately() {
        refreshHandler.removeCallbacksAndMessages(null);
        refreshHandler.post(refresh);
    }

    private void updateEnabled() {
        boolean enabled = sharedPrefs.getBoolean(PREF_ENABLED, false);
        final TextView crontabContent = findViewById(R.id.text_content_crontab);
        final TextView crondLog = findViewById(R.id.text_content_crond_log);
        final Button enableButton = findViewById(R.id.button_enable);

        if (enabled) {
            crontabContent.setBackgroundColor(Util.getColor(this, R.color.colorBackgroundActive));
            crondLog.setBackgroundColor(Util.getColor(this, R.color.colorBackgroundActive));
            enableButton.setText(getString(R.string.button_label_enabled));
        }
        else {
            crontabContent.setBackgroundColor(Util.getColor(this, R.color.colorBackgroundInactive));
            crondLog.setBackgroundColor(Util.getColor(this, R.color.colorBackgroundInactive));
            enableButton.setText(getString(R.string.button_label_disabled));
        }
    }

    static public void showNotification(Context context, String msg) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationManager.notify(1,
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_notification_icon)
                        .setColor(Util.getColor(context, R.color.colorPrimary))
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(msg)
                        .setContentIntent(pendingIntent)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .build());
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            new FileReader(MainActivity.this).execute();
            refreshHandler.postDelayed(refresh, 10000);
        }
    };

    private static class FileReader extends AsyncTask<Void, Void, CharSequence[]> {
        private WeakReference<MainActivity> contextRef = null;

        public FileReader(MainActivity context) {
            contextRef = new WeakReference<>(context);
        }

        @Override
        protected CharSequence[] doInBackground(Void... params) {
            MainActivity context = contextRef.get();
            if (context == null || context.isFinishing()) {
                cancel(true);
            }
            CharSequence[] ret = new CharSequence[2];
            Crond crond = context.crond;
            crond.setCrontab(IO.readFileContents(IO.getCrontabPath()));
            ret[0] = crond.processCrontab();

            ret[1] = IO.readFileContents(IO.getLogPath());
            return ret;
        }

        @Override
        protected void onPostExecute(CharSequence[] sequences) {
            MainActivity context = contextRef.get();
            if (context == null || context.isFinishing()) {
                return;
            }
            final TextView crontabContent = context.findViewById(R.id.text_content_crontab);
            crontabContent.setText(sequences[0]);

            final TextView crondLog = context.findViewById(R.id.text_content_crond_log);
            crondLog.setText(sequences[1]);
            final int scrollAmount = crondLog.getLayout().getLineTop(crondLog.getLineCount()) - crondLog.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                crondLog.scrollTo(0, scrollAmount);
            else
                crondLog.scrollTo(0, 0);
        }
    }

    private static class LogClearer extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            IO.clearLogFile();
            return null;
        }
    }
}
