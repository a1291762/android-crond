package net.yasmar.crond;

import android.content.Context;
import android.os.Build;

class Util {
    public static int getColor(Context context, int id) {
        if (Build.VERSION.SDK_INT >= 23) {
            return context.getColor(id);
        } else {
            return context.getResources().getColor(id);
        }
    }
}
