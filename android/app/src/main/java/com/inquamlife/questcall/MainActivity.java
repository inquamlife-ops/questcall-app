package com.inquamlife.questcall;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(QuestAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        handleCall(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCall(intent);
    }

    // 모닝콜로 열렸으면 잠금화면 위에 화면을 켜서 보여주고, 웹 화면에 어떤 퀘스트인지 알려줌
    private void handleCall(Intent intent) {
        if (intent == null) return;
        String questId = intent.getStringExtra(AlarmStore.EXTRA_QUEST);
        if (questId == null) return;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        QuestAlarmPlugin.onCall(questId, intent.getStringExtra(AlarmStore.EXTRA_ACTION));
        intent.removeExtra(AlarmStore.EXTRA_QUEST);
    }
}
