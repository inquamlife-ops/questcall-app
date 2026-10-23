package com.inquamlife.questcall;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONException;

// 웹 화면(today.js)과 안드로이드 알람을 잇는 다리
@CapacitorPlugin(
    name = "QuestAlarm",
    permissions = { @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications") }
)
public class QuestAlarmPlugin extends Plugin {
    private static QuestAlarmPlugin instance;
    private static String pendingQuest;
    private static String pendingAction;

    @Override
    public void load() {
        instance = this;
    }

    static void onCall(String questId, String action) {
        pendingQuest = questId;
        pendingAction = action;
        QuestAlarmPlugin p = instance;
        if (p != null && p.hasListeners("call")) {
            JSObject o = new JSObject();
            o.put("questId", questId);
            o.put("action", action);
            p.notifyListeners("call", o);
            pendingQuest = null;
            pendingAction = null;
        }
    }

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray alarms = call.getArray("alarms", new JSArray());
        int snooze = call.getInt("snoozeMinutes", 10);
        Context c = getContext();
        AlarmStore.prefs(c).edit().putInt(AlarmStore.KEY_SNOOZE, snooze).apply();
        try {
            AlarmStore.replaceAll(c, new JSONArray(alarms.toString()));
        } catch (JSONException e) {
            call.reject("bad alarms");
            return;
        }
        JSObject r = new JSObject();
        r.put("count", alarms.length());
        call.resolve(r);
    }

    @PluginMethod
    public void checkStatus(PluginCall call) {
        Context c = getContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        JSObject r = new JSObject();
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        r.put("exactAlarm", Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms()));
        r.put("fullScreen", Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent()));
        call.resolve(r);
    }

    @PluginMethod
    public void openSetting(PluginCall call) {
        String type = call.getString("type", "");
        Context c = getContext();
        String pkg = c.getPackageName();
        Intent i = null;
        if ("notifications".equals(type)) {
            if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
                requestPermissionForAlias("notifications", call, "notificationsResult");
                return;
            }
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        } else if ("exactAlarm".equals(type) && Build.VERSION.SDK_INT >= 31) {
            i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg));
        } else if ("fullScreen".equals(type) && Build.VERSION.SDK_INT >= 34) {
            i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkg));
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
            } catch (Exception e) {
                Intent app = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(app);
            }
        }
        call.resolve();
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void getLaunchQuest(PluginCall call) {
        JSObject r = new JSObject();
        if (pendingQuest != null) {
            r.put("questId", pendingQuest);
            r.put("action", pendingAction);
        }
        pendingQuest = null;
        pendingAction = null;
        call.resolve(r);
    }

    @PluginMethod
    public void consumeEvents(PluginCall call) {
        JSObject r = new JSObject();
        try {
            r.put("events", new JSArray(AlarmStore.takeEvents(getContext()).toString()));
        } catch (JSONException e) {
            r.put("events", new JSArray());
        }
        call.resolve(r);
    }

    @PluginMethod
    public void stopRinging(PluginCall call) {
        NotificationManagerCompat.from(getContext()).cancel(AlarmStore.NOTIFICATION_ID);
        call.resolve();
    }

    // 갓생 카드 공유: 웹 화면이 만든 PNG(base64)를 캐시 폴더에 저장하고 안드로이드 공유 시트를 띄움 (file_paths.xml 의 cache-path)
    @PluginMethod
    public void shareImage(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("name", "questcall.png").replaceAll("[^A-Za-z0-9._-]", "_");
        String text = call.getString("text", "");
        if (data == null || data.isEmpty()) {
            call.reject("no image");
            return;
        }
        try {
            File dir = new File(getContext().getCacheDir(), "share");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                call.reject("no share dir");
                return;
            }
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(Base64.decode(data, Base64.DEFAULT));
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            if (text != null && !text.isEmpty()) send.putExtra(Intent.EXTRA_TEXT, text);
            send.setClipData(ClipData.newRawUri("", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, "갓생 카드 공유");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("share failed: " + e.getMessage(), e);
        }
    }
}
