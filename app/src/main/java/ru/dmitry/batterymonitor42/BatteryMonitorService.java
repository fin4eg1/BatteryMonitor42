package ru.dmitry.batterymonitor42;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

public class BatteryMonitorService extends Service {
        static final String CHANNEL="battery42_monitor";
        final Handler handler=new Handler(Looper.getMainLooper());
        BatteryManager manager;
        HistoryStore historyStore;
        Intent latest;
        BroadcastReceiver batteryReceiver;
        final Runnable sampler=new Runnable(){@Override public void run(){getSharedPreferences("battery42",MODE_PRIVATE).edit().putLong("service_heartbeat",System.currentTimeMillis()).apply();sampleAndSave();handler.postDelayed(this,60000);}};
        @Override public void onCreate(){super.onCreate();manager=(BatteryManager)getSystemService(BATTERY_SERVICE);historyStore=new HistoryStore(this);if(!historyStore.migrateLegacy(getSharedPreferences("battery42",MODE_PRIVATE)))getSharedPreferences("battery42",MODE_PRIVATE).edit().putString("last_sample_error","Не удалось безопасно перенести историю; исходные данные сохранены").apply();createChannel();getSharedPreferences("battery42",MODE_PRIVATE).edit().putLong("service_started",System.currentTimeMillis()).putInt("service_running",1).apply();batteryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){latest=i;AlertManager.check(c,i);getSharedPreferences("battery42",MODE_PRIVATE).edit().putLong("last_battery_broadcast",System.currentTimeMillis()).apply();sampleAndSave();}};latest=registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
        @Override public int onStartCommand(Intent intent,int flags,int startId){startForeground(43,buildNotification());getSharedPreferences("battery42",MODE_PRIVATE).edit().putInt("service_running",1).putLong("service_heartbeat",System.currentTimeMillis()).apply();sampleAndSave();handler.removeCallbacks(sampler);handler.postDelayed(sampler,60000);return START_STICKY;}
        Notification buildNotification(){Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,42,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle("Battery Monitor 42 активен").setContentText("Фоновый мониторинг аккумулятора").setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();}
        void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Фоновый мониторинг",NotificationManager.IMPORTANCE_LOW);c.setDescription("Постоянное уведомление о работе мониторинга аккумулятора");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
        void migrateLegacyHistory(android.content.SharedPreferences p,long now){String current=p.getString("history_v2","");if(!current.isEmpty())return;String legacy=p.getString("history","");if(legacy==null||legacy.trim().isEmpty())return;String cleaned=legacy.replace("[","").replace("]","").trim();if(cleaned.isEmpty())return;ArrayList<Integer> values=new ArrayList<>();for(String token:cleaned.split(",")){try{int value=Integer.parseInt(token.trim());if(value>=0&&value<=100)values.add(value);}catch(Exception ignored){}}if(values.isEmpty())return;StringBuilder migrated=new StringBuilder();for(int i=0;i<values.size();i++){if(migrated.length()>0)migrated.append(";");migrated.append(now-(values.size()-1-i)*60000L).append(":").append(values.get(i)).append(":-1:").append(Integer.MIN_VALUE).append(":-1:1");}boolean saved=p.edit().putString("history_v2",migrated.toString()).putBoolean("history_legacy_migrated",true).commit();if(!saved)p.edit().putString("last_sample_error","Не удалось перенести старую историю").apply();}
        void sampleAndSave(){
            android.content.SharedPreferences p=getSharedPreferences("battery42",MODE_PRIVATE);
            long now=System.currentTimeMillis();
            p.edit().putLong("service_heartbeat",now).putLong("last_sample_attempt",now).apply();
            Intent fresh=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(fresh!=null)latest=fresh;
            if(latest==null){p.edit().putString("last_sample_error","Нет системного события батареи").apply();return;}
            int lev=latest.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=latest.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            int capacity=manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int percent=capacity>=0&&capacity<=100?capacity:(lev>=0&&scale>0?Math.round(lev*100f/scale):-1);
            if(percent<0){p.edit().putString("last_sample_error","Не удалось получить процент заряда").apply();return;}
            int temp=latest.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1),volt=latest.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
            int status=latest.getIntExtra(BatteryManager.EXTRA_STATUS,1),current=manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            ArrayList<HistoryStore.Sample> rows=historyStore.readAll();
            if(!rows.isEmpty()){
                HistoryStore.Sample last=rows.get(rows.size()-1);
                if(now-last.time<50000&&last.level==percent&&last.status==status){
                    p.edit().putLong("last_duplicate_attempt",now).putInt("duplicate_sample_count",p.getInt("duplicate_sample_count",0)+1)
                     .putString("last_sample_error","Повторный замер пропущен: данные не изменились").apply();
                    return;
                }
            }
            if(!historyStore.append(new HistoryStore.Sample(now,percent,temp,current,volt,status))){
                p.edit().putString("last_sample_error","Не удалось записать историю в файл").apply();return;
            }
            boolean saved=p.edit().putLong("last_sample_time",now).putInt("last_sample_level",percent)
                .putInt("last_sample_status",status).putInt("sample_count",p.getInt("sample_count",0)+1)
                .putString("last_sample_error","").commit();
            if(!saved){p.edit().putString("last_sample_error","Замер записан, но метаданные не обновлены").apply();return;}
            if(status==5||percent<=10||temp>=450)updateNotification("Аккумулятор: "+percent+"% · "+(temp<0?"—":(temp/10f)+" °C"));
            else updateNotification();
        }
        void updateNotification(){updateNotification("Фоновый мониторинг аккумулятора");}
        void updateNotification(String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(43,buildNotification(text));}
        Notification buildNotification(String text){Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,42,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle("Battery Monitor 42 активен").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();}
        @Override public void onTaskRemoved(Intent rootIntent){handler.removeCallbacks(sampler);handler.postDelayed(sampler,60000);super.onTaskRemoved(rootIntent);}
        @Override public void onDestroy(){handler.removeCallbacks(sampler);getSharedPreferences("battery42",MODE_PRIVATE).edit().putInt("service_running",0).putLong("service_stopped",System.currentTimeMillis()).apply();if(batteryReceiver!=null)try{unregisterReceiver(batteryReceiver);}catch(Exception ignored){}super.onDestroy();}
        @Override public android.os.IBinder onBind(Intent intent){return null;}
    }
