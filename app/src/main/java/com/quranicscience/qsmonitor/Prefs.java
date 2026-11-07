package com.quranicscience.qsmonitor;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_INTERVAL = "interval_min";
    public static final String KEY_MESSAGE = "message";

    public static final int DEFAULT_INTERVAL = 30;

    // Urdu zabaan, Devanagari likhawat (taake phone ki awaaz sahi bole)
    // Aswad (اسود) aur Arwa (ارویٰ)
    public static final String DEFAULT_MESSAGE =
            "असवद, अरवा! तुम दोनों में से जिसके पास भी फ़ोन है, फ़ोन रख दो। बहुत देर हो गई है।";

    private Prefs() { }

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences("qs_monitor", Context.MODE_PRIVATE);
    }
}
