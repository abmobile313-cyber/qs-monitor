package com.quranicscience.qsmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int MAROON = Color.parseColor("#5C0A14");
    private static final int GOLD = Color.parseColor("#C9A227");
    private static final int CYAN = Color.parseColor("#1BA3C6");
    private static final int BG = Color.parseColor("#1A0508");

    private TextView status;
    private EditText message;
    private EditText interval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);

        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("QS Monitor");
        title.setTextColor(GOLD);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Bacchon ka screen time monitor");
        sub.setTextColor(Color.parseColor("#E8D9B0"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(18));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(18);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(14), dp(10), dp(14));
        root.addView(status);

        root.addView(label("Kitne minute baad bole?"));
        interval = new EditText(this);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        styleInput(interval);
        root.addView(interval);

        root.addView(label("Kya bolna hai? (Devanagari mein likho to awaaz sahi aati hai)"));
        message = new EditText(this);
        message.setMinLines(3);
        styleInput(message);
        root.addView(message);

        SharedPreferences p = Prefs.get(this);
        interval.setText(String.valueOf(p.getInt(Prefs.KEY_INTERVAL, Prefs.DEFAULT_INTERVAL)));
        message.setText(p.getString(Prefs.KEY_MESSAGE, Prefs.DEFAULT_MESSAGE));

        root.addView(button("▶  Monitor CHALU karo", MAROON, new View.OnClickListener() {
            @Override public void onClick(View v) { startMonitor(); }
        }));
        root.addView(button("🔊  Abhi TEST karo", CYAN, new View.OnClickListener() {
            @Override public void onClick(View v) { save(); MonitorService.test(MainActivity.this);
                Prefs.get(MainActivity.this).edit().putBoolean(Prefs.KEY_ENABLED, true).apply();
                refresh(); }
        }));
        root.addView(button("🔋  Battery se rok-tok band karo", Color.parseColor("#7A5A00"), new View.OnClickListener() {
            @Override public void onClick(View v) { askBattery(); }
        }));
        root.addView(button("■  Monitor BAND karo", Color.parseColor("#333333"), new View.OnClickListener() {
            @Override public void onClick(View v) { stopMonitor(); }
        }));

        TextView help = new TextView(this);
        help.setText("Kaise kaam karta hai:\n• Screen lagatar ON rahe to har " +
                "set kiye minute baad 3 second beep + message.\n" +
                "• Screen 2 minute se zyada band rahe to time dobara 0 se shuru.\n" +
                "• Phone restart ho to khud chalu ho jata hai.\n" +
                "• Awaaz ALARM volume par aati hai.");
        help.setTextColor(Color.parseColor("#BFAF8A"));
        help.setPadding(0, dp(18), 0, 0);
        root.addView(help);

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        refresh();
    }

    private void save() {
        int min = Prefs.DEFAULT_INTERVAL;
        try { min = Integer.parseInt(interval.getText().toString().trim()); } catch (Exception ignored) { }
        if (min < 1) min = 1;
        String msg = message.getText().toString().trim();
        if (msg.isEmpty()) msg = Prefs.DEFAULT_MESSAGE;
        Prefs.get(this).edit().putInt(Prefs.KEY_INTERVAL, min).putString(Prefs.KEY_MESSAGE, msg).apply();
    }

    private void startMonitor() {
        save();
        Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, true).apply();
        MonitorService.stop(this);   // naya interval lagane ke liye restart
        MonitorService.start(this);
        Toast.makeText(this, "Monitor chalu ho gaya", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void stopMonitor() {
        Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, false).apply();
        MonitorService.stop(this);
        Toast.makeText(this, "Monitor band ho gaya", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void askBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Ye pehle se theek hai ✓", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void refresh() {
        boolean on = Prefs.get(this).getBoolean(Prefs.KEY_ENABLED, false);
        status.setText(on ? "✓ Monitor ON hai" : "Monitor OFF hai");
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(on ? Color.parseColor("#1E5E2A") : Color.parseColor("#3A1015"));
        g.setStroke(dp(2), GOLD);
        status.setBackground(g);
        status.setTextColor(Color.WHITE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    private TextView label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(GOLD);
        tv.setPadding(0, dp(16), 0, dp(6));
        return tv;
    }

    private void styleInput(EditText e) {
        e.setTextColor(Color.WHITE);
        e.setTextSize(17);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(Color.parseColor("#2A0B10"));
        g.setStroke(dp(1), GOLD);
        e.setBackground(g);
    }

    private Button button(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(color);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
