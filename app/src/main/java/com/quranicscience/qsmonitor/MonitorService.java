package com.quranicscience.qsmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Background service jo screen ON rehne ka time ginti hai.
 * Har X minute (default 30) lagatar screen ON rehne par:
 * 3 second beep + Aswad aur Arwa ka naam lekar message bolta hai.
 */
public class MonitorService extends Service implements TextToSpeech.OnInitListener {

    public static final String ACTION_TEST = "com.quranicscience.qsmonitor.TEST";
    private static final String CHANNEL_ID = "qs_monitor";
    private static final int NOTIF_ID = 1;
    // Itni der se kam screen band rahe to time reset nahi hota
    private static final long RESET_GAP_MS = 2 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private BroadcastReceiver screenReceiver;

    private long usedMs = 0;      // is session mein kitna time screen ON rahi
    private long lastOnAt = 0;    // screen kab ON hui
    private long offAt = 0;       // screen kab OFF hui

    private final Runnable alertRunnable = new Runnable() {
        @Override
        public void run() {
            doAlert();
            usedMs = 0;
            lastOnAt = System.currentTimeMillis();
            handler.postDelayed(this, intervalMs());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        tts = new TextToSpeech(this, this);

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(a)) onScreenOn();
                else if (Intent.ACTION_SCREEN_OFF.equals(a)) onScreenOff();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            offAt = 0;
            onScreenOn();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TEST.equals(intent.getAction())) {
            doAlert();
        }
        return START_STICKY;
    }

    private long intervalMs() {
        int min = Prefs.get(this).getInt(Prefs.KEY_INTERVAL, Prefs.DEFAULT_INTERVAL);
        if (min < 1) min = 1;
        return min * 60 * 1000L;
    }

    private void onScreenOn() {
        long now = System.currentTimeMillis();
        if (offAt == 0 || now - offAt > RESET_GAP_MS) {
            usedMs = 0;
        }
        lastOnAt = now;
        handler.removeCallbacks(alertRunnable);
        long remaining = intervalMs() - usedMs;
        if (remaining < 5000) remaining = 5000;
        handler.postDelayed(alertRunnable, remaining);
    }

    private void onScreenOff() {
        long now = System.currentTimeMillis();
        if (lastOnAt > 0) usedMs += now - lastOnAt;
        offAt = now;
        handler.removeCallbacks(alertRunnable);
    }

    private void doAlert() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            try {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            } catch (Exception ignored) { }
        }
        playBeep();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                speak();
            }
        }, 3300);
    }

    /** 3 second ki beep-beep awaaz (alarm volume par). */
    private void playBeep() {
        try {
            final int rate = 44100;
            final int seconds = 3;
            short[] buf = new short[rate * seconds];
            double freq = 1200.0;
            int onSamples = (int) (rate * 0.25);   // 0.25s beep
            int offSamples = (int) (rate * 0.10);  // 0.10s chup
            int cycle = onSamples + offSamples;
            for (int i = 0; i < buf.length; i++) {
                if (i % cycle < onSamples) {
                    buf[i] = (short) (Math.sin(2 * Math.PI * freq * i / rate) * 30000);
                } else {
                    buf[i] = 0;
                }
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            final AudioTrack track = new AudioTrack(attrs, fmt, buf.length * 2,
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.write(buf, 0, buf.length);
            track.play();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try { track.stop(); } catch (Exception ignored) { }
                    track.release();
                }
            }, seconds * 1000L + 200);
        } catch (Exception ignored) { }
    }

    private void speak() {
        String msg = Prefs.get(this).getString(Prefs.KEY_MESSAGE, Prefs.DEFAULT_MESSAGE);
        // Default message = ElevenLabs (Viraj) ki recorded awaaz
        if (Prefs.DEFAULT_MESSAGE.equals(msg) && playViraj()) return;
        if (tts == null || !ttsReady) return;
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "qs_msg");
    }

    private boolean playViraj() {
        try {
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.reminder);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer p) { p.release(); }
            });
            mp.prepare();
            mp.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        int r = tts.setLanguage(new Locale("hi", "IN"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        tts.setSpeechRate(0.9f);
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        ttsReady = true;
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "QS Monitor",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Screen time monitor chal raha hai");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("QS Monitor ON hai")
                .setContentText("Screen time dekh raha hai")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, MonitorService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, MonitorService.class));
    }

    public static void test(Context c) {
        Intent i = new Intent(c, MonitorService.class);
        i.setAction(ACTION_TEST);
        c.startForegroundService(i);
    }

    static SharedPreferences prefs(Context c) {
        return Prefs.get(c);
    }
}
