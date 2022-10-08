package net.yasmar.crond;

import android.text.TextUtils;
import android.util.Log;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import eu.chainfire.libsuperuser.Shell;

class IO implements Shell.OnCommandResultListener2 {

    private static final String TAG = "IO";

    private static final String ROOT_PREFIX = "/data/";
    private static final String CRONTAB_FILE_NAME= "crontab";
    private static final String LOG_FILE_NAME = "crond.log";

    static boolean rootAvailable = false;
    static File nonRootPrefix;

    public static String getLogPath() {
        if (rootAvailable) {
            return new File(ROOT_PREFIX, LOG_FILE_NAME).getAbsolutePath();
        } else {
            return new File(nonRootPrefix, LOG_FILE_NAME).getAbsolutePath();
        }
    }

    public static String getCrontabPath() {
        if (rootAvailable) {
            return new File(ROOT_PREFIX, CRONTAB_FILE_NAME).getAbsolutePath();
        } else {
            return new File(nonRootPrefix, CRONTAB_FILE_NAME).getAbsolutePath();
        }
    }

    static void clearLogFile() {
        Log.i(TAG, executeCommand("echo -n \"\" > " + getLogPath()).getOutput());
    }

    static String readFileContents(String filePath) {
        return executeCommand("cat " + filePath).getOutput();
    }

    synchronized static CommandResult executeCommand(String cmd) {
        get().shell.addCommand(cmd, 0, get());
        get().shell.waitForIdle();
        if (!get().lastResult.success()) {
            Log.w(TAG, String.format("Error while executing command:\"%s\":\n%s",
                    cmd, get().lastResult.getOutput()));
        }
        return get().lastResult;
    }

    private static final Escaper shellEscaper;
    static {
        final Escapers.Builder builder = Escapers.builder();
        builder.addEscape('\'', "'\"'\"'");
        shellEscaper = builder.build();
    }

    static void logToLogFile(String msg) {
        msg = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSS", Locale.US).format(new Date()) + " " + msg;
        Log.i(TAG, executeCommand("echo '" + shellEscaper.escape(msg) + "' >> "
                + getLogPath()).getOutput());
    }

    class CommandResult {
        private final int exitCode;
        private final String output;
        CommandResult(int returnCode, List<String> output) {
            this.exitCode = returnCode;
            this.output = TextUtils.join("\n", output);
        }

        boolean success() {
            return exitCode == 0;
        }

        int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }
    }

    private static IO instance = null;
    private CommandResult lastResult = null;
    private Shell.Interactive shell;

    private IO() {
        Shell.Builder builder = new Shell.Builder()
                .setHandler(null)
                .setAutoHandler(false);
        if (rootAvailable) {
            builder.useSU();
        } else {
            builder.useSH();
        }
        builder.setMinimalLogging(true);
        shell = builder.open();
    }

    private static IO get() {
        if (instance == null) {
            synchronized (IO.class) {
                return instance = new IO();
            }
        }
        return instance;
    }

    @Override
    public void onCommandResult(int commandCode, int exitCode, @NonNull List<String> output, @NonNull List<String> error) {
        get().lastResult = new CommandResult(exitCode, output);
    }

}
