package ru.dmitry.batterymonitor42;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;

/** Единая точка проверки порогов и отправки предупреждений из Activity и фоновой службы. */
final class AlertManager {
    private static final String CHANNEL="battery42_alerts";
    private static final int NOTIFICATION_ID=42;
    private static final int NONE=0,LOW=1,HOT=2,FULL=3;
    private AlertManager(){}

    static void ensureChannel(Context c){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Состояние аккумулятора",NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Предупреждения о температуре и уровне заряда");
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    static int percent(Intent b){
        if(b==null)return -1;
        int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        return level>=0&&scale>0?Math.round(level*100f/scale):-1;
    }
    static synchronized void check(Context c,Intent b){
        if(b==null)return;
        SharedPreferences p=c.getSharedPreferences("battery42",Context.MODE_PRIVATE);
        if(!p.getBoolean("alerts_enabled",true))return;
        int level=percent(b),temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
        int status=b.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN);
        int lowLimit=p.getInt("alert_low",10),tempLimit=p.getInt("alert_temp",420),previous=p.getInt("last_alert",NONE);
        boolean charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;
        boolean hot=temp>=tempLimit||(previous==HOT&&temp>=tempLimit-20);
        boolean low=!charging&&level>=0&&(level<=lowLimit||(previous==LOW&&level<=lowLimit+2));
        int alert=hot?HOT:low?LOW:status==BatteryManager.BATTERY_STATUS_FULL?FULL:NONE;
        if(alert==previous)return;
        p.edit().putInt("last_alert",alert).apply();
        if(alert==NONE)return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        String title=alert==HOT?"Перегрев аккумулятора":alert==LOW?"Низкий заряд":"Зарядка завершена";
        String body=alert==HOT?String.format(java.util.Locale.getDefault(),"Температура %.1f °C. Дай телефону остыть.",temp/10f)
            :alert==LOW?"Осталось "+level+"% заряда. Подключи зарядное устройство.":"Аккумулятор заряжен до 100%. Можно отключать питание.";
        Intent open=new Intent(c,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        nb.setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,nb.build());
    }
}
