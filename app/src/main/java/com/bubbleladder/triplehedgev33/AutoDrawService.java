package com.bubbleladder.triplehedgev33;

import android.app.*;
import android.content.*;
import android.os.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoDrawService extends Service {
    public static final String CHANNEL_ID="bubble_triple_hedge_v33_live";
    public static final int NOTI_ID=3321;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ExecutorService ex=Executors.newSingleThreadExecutor();
    private boolean syncing=false;
    private int retry=0;

    private final Runnable notificationTick=new Runnable(){
        @Override public void run(){
            updateNotification();
            long left=TripleCore.millisToNextDraw();
            h.postDelayed(this,left<=30000L?1000L:5000L);
        }
    };
    private final Runnable fetchTask=new Runnable(){@Override public void run(){doSync();}};

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(NOTI_ID,buildNotification());
        h.post(notificationTick);
        h.post(fetchTask);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!TripleCore.prefs(this).getBoolean(TripleCore.K_AUTO,true)){stopSelf();return START_NOT_STICKY;}
        if(!syncing){ h.removeCallbacks(fetchTask); h.post(fetchTask); }
        return START_STICKY;
    }

    private void doSync(){
        if(syncing)return;syncing=true;
        ex.execute(()->{
            boolean advanced=false;
            try{TripleCore.SyncResult sr=TripleCore.sync(this);advanced=sr.newRoundResolved;}catch(Exception ignored){}
            final boolean ok=advanced;
            h.post(()->{
                syncing=false;
                sendBroadcast(new Intent(TripleCore.ACTION_UPDATED).setPackage(getPackageName()));
                updateNotification();
                h.removeCallbacks(fetchTask);
                if(ok){retry=0;scheduleAtNextDraw();}
                else if(retry<3){retry++;h.postDelayed(fetchTask,12000L);}
                else{retry=0;scheduleAtNextDraw();}
            });
        });
    }

    private void scheduleAtNextDraw(){h.postDelayed(fetchTask,TripleCore.millisToNextDraw()+7000L);}

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"삼치기 V3.3 Final 자동추첨",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("다음 추첨 남은시간, V3.3 Final 제외조합, 현재상황, 등급과 실전 성공률을 표시합니다.");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.content.SharedPreferences sp=TripleCore.prefs(this);
        int exc=sp.getInt(TripleCore.K_LAST_EXCLUDE,0);
        String triple=sp.getString(TripleCore.K_LAST_TRIPLE,"분석 대기");
        String grade=sp.getString(TripleCore.K_LAST_GRADE,"-");
        String context=sp.getString(TripleCore.K_LAST_CONTEXT,"-");
        String text="다음 "+TripleCore.countdownText()+" · 제외 "+(exc>=1&&exc<=4?TripleCore.COMBO[exc]:"-")+" · "+grade+" · "+context+" · "+triple+" · 실전 "+TripleCore.liveRate(this);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("보글사다리3 삼치기 Hedge V3.3 Final · 백그라운드 ON")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }

    private void updateNotification(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTI_ID,buildNotification());}

    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);ex.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
