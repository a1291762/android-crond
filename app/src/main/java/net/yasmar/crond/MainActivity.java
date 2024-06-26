package net.yasmar.crond;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import eu.chainfire.libsuperuser.Shell;

import static android.view.View.VISIBLE;
import static net.yasmar.crond.Constants.POST_NOTIFICATIONS;
import static net.yasmar.crond.Constants.PREFERENCES_FILE;
import static net.yasmar.crond.Constants.PREF_ENABLED;
import static net.yasmar.crond.Constants.PREF_NOTIFICATION_ENABLED;
import static net.yasmar.crond.Constants.PREF_ROOT_WARNING;
import static net.yasmar.crond.Constants.PREF_USE_WAKE_LOCK;

public class MainActivity extends AppCompatActivity {

    //private static final String TAG = "MainActivity";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private Crond crond = null;

    private SharedPreferences sharedPrefs = null;
    private boolean rootAvailable = false;
    private boolean inited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPrefs = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
        checkRoot();
    }

    private void checkRoot() {
        executor.execute(() -> {
            rootAvailable = Shell.SU.available();
            handler.post(this::postCheckRoot);
        });
    }

    private void postCheckRoot() {
        final Runnable next = this::checkNotifications;
        if (rootAvailable) {
            boolean hasWarned = sharedPrefs.getBoolean(PREF_ROOT_WARNING, false);
            if (!hasWarned) {
                new AlertDialog.Builder(this)
                        .setTitle("Root not detected")
                        .setMessage("Without root, functionality will be extremely limited.")
                        .setNeutralButton(android.R.string.ok, (d, w) -> {
                            SharedPreferences.Editor editor = sharedPrefs.edit();
                            editor.putBoolean(PREF_ROOT_WARNING, true);
                            editor.apply();
                            next.run();
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return;
            }
        }
        next.run();
    }

    private void checkNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean showNotification = sharedPrefs.getBoolean(PREF_NOTIFICATION_ENABLED, false);
            if (showNotification) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, POST_NOTIFICATIONS);
                }
            }
        }
        if (!inited && rootAvailable) {
            init();
        }
    }

    private void init() {
        inited = true;
        crond = new Crond(this);

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
        notificationCheckBox.setOnClickListener(view -> {
            sharedPrefs.edit().putBoolean(PREF_NOTIFICATION_ENABLED,
                    notificationCheckBox.isChecked()).apply();
            if (notificationCheckBox.isChecked()) {
                checkNotifications();
            }
        });

        final CheckBox wakeLockCheckBox = findViewById(
                R.id.check_wakelock_setting);
        wakeLockCheckBox.setChecked(sharedPrefs.getBoolean(PREF_USE_WAKE_LOCK, false));
        wakeLockCheckBox.setOnClickListener(v -> sharedPrefs.edit().putBoolean(PREF_USE_WAKE_LOCK,
                wakeLockCheckBox.isChecked()).apply());

        final Button enableButton = findViewById(R.id.button_enable);
        enableButton.setOnClickListener(view -> {
            boolean oldEnabled = sharedPrefs.getBoolean(PREF_ENABLED, false);
            sharedPrefs.edit().putBoolean(PREF_ENABLED, !oldEnabled).apply();
            updateEnabled();
            crond.scheduleCrontab(true, false, false);
            refreshImmediately();
        });

        final Button clearButton = findViewById(R.id.button_clear_log);
        clearButton.setOnClickListener(view -> new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.dialog_clean_title)
                .setMessage(R.string.dialog_clean_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    executor.execute(IO::clearLogFile);
                    refreshImmediately();
                })
                .show());

        updateEnabled();
        refreshImmediately();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (inited && !rootAvailable) {
            checkRootGranted();
        }
        refreshHandler.removeCallbacksAndMessages(null);
        if (inited && rootAvailable) {
            refreshImmediately();
        }
    }

    private void checkRootGranted() {
        executor.execute(() -> {
            if (Shell.SU.available()) {
                handler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Root granted")
                        .setMessage("The app will close. Please re-open the app again.")
                        .setNeutralButton(android.R.string.ok, (d, w) -> {
                            finish();
                            System.exit(0);
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show());
            }
        });
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
            crontabContent.setBackgroundColor(getColor(R.color.colorBackgroundActive));
            crondLog.setBackgroundColor(getColor(R.color.colorBackgroundActive));
            enableButton.setText(getString(R.string.button_label_enabled));
        }
        else {
            crontabContent.setBackgroundColor(getColor(R.color.colorBackgroundInactive));
            crondLog.setBackgroundColor(getColor(R.color.colorBackgroundInactive));
            enableButton.setText(getString(R.string.button_label_disabled));
        }
    }

    static public void showNotification(Context context, String msg) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                flags);
        NotificationChannel channel = new NotificationChannel("post_run_notification", "Post-run notification", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("This channel is used for the post-run notification");
        notificationManager.createNotificationChannel(channel);
        notificationManager.notify(1,
                new NotificationCompat.Builder(context, "post_run_notification")
                        .setSmallIcon(R.drawable.ic_notification_icon)
                        .setColor(context.getColor(R.color.colorPrimary))
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
            executor.execute(() -> {
                crond.setCrontab(IO.readFileContents(IO.getCrontabPath()));
                CharSequence crontab = crond.processCrontab();
                CharSequence log = IO.readFileContents(IO.getLogPath());
                handler.post(() -> {
                    final TextView crontabContent = findViewById(R.id.text_content_crontab);
                    crontabContent.setText(crontab);

                    final TextView crondLog = findViewById(R.id.text_content_crond_log);
                    crondLog.setText(log);
                    Layout layout = crondLog.getLayout();
                    if (layout == null) {
                        // When launching with the screen off... we get here. Avoid crashing!
                        return;
                    }
                    final int scrollAmount = layout.getLineTop(crondLog.getLineCount()) - crondLog.getHeight();
                    // if there is no need to scroll, scrollAmount will be <=0
                    crondLog.scrollTo(0, Math.max(scrollAmount, 0));
                });
            });
            refreshHandler.postDelayed(refresh, 10000);
        }
    };
}
