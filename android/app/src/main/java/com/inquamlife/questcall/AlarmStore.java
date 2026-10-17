package com.inquamlife.questcall;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// 모닝콜 예약 목록 저장 · AlarmManager 예약 · 알림 채널
final class AlarmStore {
    static final String PREFS = "questcall_alarms";
    static final String KEY_ALARMS = "alarms";
    static final String KEY_EVENTS = "events";
    static final String KEY_SNOOZE = "snooze_minutes";
    static final String CHANNEL = "questcall_call_v1";
    static final int NOTIFICATION_ID = 7001;
    static final String EXTRA_QUEST = "questcall_quest";
    static final String EXTRA_ACTION = "questcall_action";
    static final String ACTION_ALARM = "com.inquamlife.questcall.ALARM";
    static final String ACTION_DECLINE = "com.inquamlife.questcall.DECLINE";

    private AlarmStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static JSONArray load(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_ALARMS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void replaceAll(Context c, JSONArray alarms) {
        cancel(c, load(c));
        prefs(c).edit().putString(KEY_ALARMS, alarms.toString()).apply();
        scheduleAll(c, alarms);
    }

    static void scheduleAll(Context c, JSONArray alarms) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < alarms.length(); i++) {
            JSONObject o = alarms.optJSONObject(i);
            if (o != null && o.optLong("at") > now) schedule(c, o);
        }
    }

    static void schedule(Context c, JSONObject o) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent fire = pending(c, o);
        long at = o.optLong("at");
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, 0, show, PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), fire);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    static void cancel(Context c, JSONArray alarms) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < alarms.length(); i++) {
            JSONObject o = alarms.optJSONObject(i);
            if (o != null) am.cancel(pending(c, o));
        }
    }

    static PendingIntent pending(Context c, JSONObject o) {
        int id = o.optInt("id");
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        i.setData(Uri.parse("questcall://alarm/" + id));
        i.putExtra("id", id);
        i.putExtra(EXTRA_QUEST, o.optString("questId"));
        i.putExtra("title", o.optString("title"));
        i.putExtra("body", o.optString("body"));
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void addEvent(Context c, JSONObject e) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_EVENTS, "[]"));
            a.put(e);
            prefs(c).edit().putString(KEY_EVENTS, a.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static synchronized JSONArray takeEvents(Context c) {
        JSONArray a;
        try {
            a = new JSONArray(prefs(c).getString(KEY_EVENTS, "[]"));
        } catch (JSONException e) {
            a = new JSONArray();
        }
        prefs(c).edit().putString(KEY_EVENTS, "[]").apply();
        return a;
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "모닝콜", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("루틴 시간에 전화처럼 울리는 알람");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[] {0, 800, 400, 800, 400, 800});
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }
}
