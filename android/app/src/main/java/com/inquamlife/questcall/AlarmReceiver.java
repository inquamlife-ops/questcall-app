package com.inquamlife.questcall;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

// 예약한 시간이 되면: 잠금화면 위 전체 화면(전화 수신 화면) + 계속 울리는 알림
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        AlarmStore.ensureChannel(c);
        int id = in.getIntExtra("id", 0);
        String quest = in.getStringExtra(AlarmStore.EXTRA_QUEST);
        String title = in.getStringExtra("title");
        String body = in.getStringExtra("body");

        PendingIntent full = PendingIntent.getActivity(c, id, open(c, quest, "call"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent accept = PendingIntent.getActivity(c, id + 1, open(c, quest, "accept"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dec = new Intent(c, ActionReceiver.class).setAction(AlarmStore.ACTION_DECLINE).putExtra(AlarmStore.EXTRA_QUEST, quest)
                .putExtra("title", title).putExtra("body", body);
        PendingIntent decline = PendingIntent.getBroadcast(c, id + 2, dec, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, AlarmStore.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(title == null || title.isEmpty() ? "퀘스트콜" : title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(90_000)
                .addAction(0, "거절", decline)
                .addAction(0, "통화 받기", accept);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;
        try {
            NotificationManagerCompat.from(c).notify(AlarmStore.NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // 알림 권한이 없으면 울릴 수 없음 (앱 설정 화면에서 안내)
        }
    }

    static Intent open(Context c, String quest, String action) {
        return new Intent(c, MainActivity.class)
                .setAction("com.inquamlife.questcall.CALL_" + action)
                .putExtra(AlarmStore.EXTRA_QUEST, quest)
                .putExtra(AlarmStore.EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
