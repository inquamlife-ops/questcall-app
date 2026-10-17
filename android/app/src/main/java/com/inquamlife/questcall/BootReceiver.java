package com.inquamlife.questcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// 폰을 껐다 켜거나 앱을 업데이트하면 AlarmManager 예약이 지워지므로 저장해 둔 목록으로 다시 예약
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        AlarmStore.scheduleAll(c, AlarmStore.load(c));
    }
}
