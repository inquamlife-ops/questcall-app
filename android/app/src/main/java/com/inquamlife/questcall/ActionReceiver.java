package com.inquamlife.questcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONException;
import org.json.JSONObject;

// 알림에서 "거절"을 누르면: 소리를 끄고, 설정한 간격 뒤에 다시 울리도록 예약 (앱이 열리면 미룬 횟수에 반영)
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        NotificationManagerCompat.from(c).cancel(AlarmStore.NOTIFICATION_ID);
        String quest = in.getStringExtra(AlarmStore.EXTRA_QUEST);
        int minutes = AlarmStore.prefs(c).getInt(AlarmStore.KEY_SNOOZE, 10);
        long until = System.currentTimeMillis() + minutes * 60_000L;
        try {
            JSONObject alarm = new JSONObject()
                    .put("id", 1_999_000_000 + (int) ((until / 1000) % 999_000))
                    .put("at", until)
                    .put("questId", quest)
                    .put("title", in.getStringExtra("title"))
                    .put("body", in.getStringExtra("body"));
            AlarmStore.schedule(c, alarm);
            AlarmStore.addEvent(c, new JSONObject().put("type", "snooze").put("questId", quest).put("until", until));
        } catch (JSONException ignored) {
        }
    }
}
