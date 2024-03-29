package net.yasmar.crond;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Base64;
import android.util.Log;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;

import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NAME;
import static net.yasmar.crond.Constants.INTENT_EXTRA_LINE_NO_NAME;
import static net.yasmar.crond.Constants.PREFERENCES_FILE;
import static net.yasmar.crond.Constants.PREF_CRONTAB_HASH;
import static net.yasmar.crond.Constants.PREF_ENABLED;
import static net.yasmar.crond.Constants.PREF_NOTIFICATION_ENABLED;


class Crond {

    private static final String TAG = "Crond";

    private final CronParser parser =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    private CronDescriptor descriptor;

    private final Context context;
    private final AlarmManager alarmManager;
    private final SharedPreferences sharedPrefs;
    private String crontab = "";

    private static final String PREF_CRONTAB_LINE_COUNT = "old_tab_line_count";
    private static final String HASH_ALGO = "sha-256";

    public Crond(Context context) {
        this.context = context;
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        sharedPrefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        try {
            descriptor = CronDescriptor.instance(Locale.getDefault());
        }
        catch (MissingResourceException e) {
            Log.w(TAG, "Cannot find locale \"" + Locale.getDefault()
                    + "\". Switching to default locale.");
            descriptor = CronDescriptor.instance();
        }
    }

    public SpannableStringBuilder processCrontab() {
        SpannableStringBuilder ret = new SpannableStringBuilder();
        String hashedTab = "";
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGO);
            messageDigest.update(crontab.getBytes());
            hashedTab = Base64.encodeToString(messageDigest.digest(), Base64.DEFAULT).trim();
        }
        catch (NoSuchAlgorithmException e) {
            Log.e(TAG, String.format("Algorithm %s not found:", HASH_ALGO));
            e.printStackTrace();
        }
        if (!hashedTab.equals(sharedPrefs.getString(PREF_CRONTAB_HASH, ""))
                && !crontab.isEmpty()) {
            // only schedule when enabled
            if (sharedPrefs.getBoolean(PREF_ENABLED, false)) {
                IO.logToLogFile(context.getString(R.string.log_crontab_change_detected));
                scheduleCrontab(false, true, false);
            }
            // save in any case such that on installation the crontab is not "new"
            sharedPrefs.edit().putString(PREF_CRONTAB_HASH, hashedTab).apply();
        }
        if (crontab.isEmpty()) {
            return ret;
        }
        for (String line : crontab.split("\n")){
            ret.append(line + "\n",
                    new TypefaceSpan("monospace"), Spanned.SPAN_COMPOSING);
            ret.append(describeLine(line));
        }
        return ret;
    }

    public void setCrontab(String crontab) {
        this.crontab = crontab;
    }

    public void scheduleCrontab(boolean isEnable, boolean isChange, boolean isBoot) {
        cancelAllAlarms(sharedPrefs.getInt(PREF_CRONTAB_LINE_COUNT, 0));
        // check here, because this can get called directly
        if (!sharedPrefs.getBoolean(PREF_ENABLED, false)) {
            return;
        }
        int i = 0;
        for (String line : crontab.split("\n")) {
            scheduleLine(line, i, isEnable, isChange, isBoot);
            i++;
        }
        sharedPrefs.edit().putInt(PREF_CRONTAB_LINE_COUNT, crontab.split("\n").length).apply();
    }

    public void scheduleLine(String line, int lineNo, boolean isEnable, boolean isChange, boolean isBoot) {
        ParsedLine parsedLine = parseLine(line);
        if (parsedLine == null) {
            return;
        }
        DateTime next;
        switch (parsedLine.cronExpr) {
            case "@enable":
                if (!isEnable) return;
                next = DateTime.now().plusSeconds(1);
                break;
            case "@change":
                if (!isChange) return;
                next = DateTime.now().plusSeconds(1);
                break;
            case "@reboot":
                if (!isBoot) return;
                next = DateTime.now().plusSeconds(1);
                break;
            default:
                ExecutionTime time;
                try {
                    time = ExecutionTime.forCron(parser.parse(parsedLine.cronExpr));
                } catch (IllegalArgumentException e) {
                    return;
                }
                next = time.nextExecution(DateTime.now());
                break;
        }
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(INTENT_EXTRA_LINE_NAME, line);
        intent.putExtra(INTENT_EXTRA_LINE_NO_NAME, lineNo);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        // update current to replace the one used
        // for cancelling any previous set alarms
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, lineNo, intent, flags);
        alarmManager.cancel(alarmIntent);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getMillis(), alarmIntent);
        IO.logToLogFile(context.getString(R.string.log_scheduled_v2, lineNo + 1, parsedLine.runExpr,
                DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSS").print(next)));
    }

    public void executeLine(String line, int lineNo) {
        ParsedLine parsedLine = parseLine(line);
        if (parsedLine == null) {
            return;
        }
        IO.logToLogFile(context.getString(R.string.log_execute_pre_v2, lineNo + 1,
                parsedLine.runExpr));
        IO.CommandResult res = IO.executeCommand(parsedLine.runExpr);
        String log = context.getString(R.string.log_execute_post_v2, lineNo + 1,
                parsedLine.runExpr, res.getExitCode());
        IO.logToLogFile(log);
        if (sharedPrefs.getBoolean(PREF_NOTIFICATION_ENABLED, false)) {
            MainActivity.showNotification(context, log);
        }
    }
    private SpannableStringBuilder describeLine(String line) {
        SpannableStringBuilder ret = new SpannableStringBuilder();
        ParsedLine parsedLine = parseLine(line);
        boolean invalid = false;
        if (parsedLine == null) {
            invalid = true;
        }
        else {
            try {
                ret.append(context.getResources().getString(R.string.run) + " ",
                        new StyleSpan(Typeface.ITALIC), Spanned.SPAN_COMPOSING);
                ret.append(parsedLine.runExpr + " ",
                        new TypefaceSpan("monospace"), Spanned.SPAN_COMPOSING);
                String description;
                switch (parsedLine.cronExpr) {
                    case "@enable":
                        description = context.getString(R.string.when_enabling);
                        break;
                    case "@change":
                        description = context.getString(R.string.when_crontab_changes);
                        break;
                    case "@reboot":
                        description = context.getString(R.string.at_reboot);
                        break;
                    default:
                        description = descriptor.describe(parser.parse(parsedLine.cronExpr));
                        break;
                }
                ret.append(description + "\n",
                        new StyleSpan(Typeface.ITALIC), Spanned.SPAN_COMPOSING);
            }
            catch (IllegalArgumentException e) {
                ret = new SpannableStringBuilder();
                invalid = true;
            }
        }
        if (invalid) {
            ret.append(context.getResources().getString(R.string.invalid_cron) + "\n",
                    new StyleSpan(Typeface.ITALIC), Spanned.SPAN_COMPOSING);
        }

        ret.setSpan(new ForegroundColorSpan(
                        context.getColor(R.color.colorPrimaryDark)), 0,
                ret.length(), Spanned.SPAN_COMPOSING);
        return ret;
    }

    private void cancelAllAlarms(int oldTabLineCount) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        for (int i = 0; i<oldTabLineCount; i++) {
            int flags = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, i, intent, flags);
            alarmManager.cancel(alarmIntent);
        }
    }

    private ParsedLine parseLine(String line) {
        line = line.trim();
        if (line.isEmpty()) {
            return null;
        }
        if (line.charAt(0) != '*'
                && line.charAt(0) != '@'
                && !Character.isDigit(line.charAt(0))) {
            return null;
        }
        String [] splitLine = line.split(" ");
        int timeFields = 5;
        if (splitLine.length >= 1 && (splitLine[0].equals("@enable") || splitLine[0].equals("@change") || splitLine[0].equals("@reboot"))) {
            timeFields = 1;
        }
        if (splitLine.length <= timeFields) {
            return null;
        }
        String[] cronExpr = Arrays.copyOfRange(splitLine, 0, timeFields);
        String[] runExpr = Arrays.copyOfRange(splitLine, timeFields, splitLine.length);
        String joinedCronExpr = TextUtils.join(" ", cronExpr);
        String joinedRunExpr = TextUtils.join(" ", runExpr);
        return new ParsedLine(joinedCronExpr, joinedRunExpr);
    }

    private static class ParsedLine {
        final String cronExpr;
        final String runExpr;
        ParsedLine(String cronExpr, String runExpr) {
            this.cronExpr = cronExpr;
            this.runExpr = runExpr;
        }
    }
}
