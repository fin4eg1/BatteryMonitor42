package ru.dmitry.batterymonitor42;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    TextView percent, status, temp, voltage, current, technology, health, capacity;
    BatteryView batteryView;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { update(i); }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12,18,32));
        getWindow().setNavigationBarColor(Color.rgb(12,18,32));
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent i = registerReceiver(receiver, f);
        if (i != null) update(i);
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12,18,32));
        LinearLayout page = new LinearLayout(this);
        page.setPadding(dp(20), dp(28), dp(20), dp(24));
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);
        TextView title = text("МОНИТОР АККУМУЛЯТОРА", 20, Color.WHITE, true);
        page.addView(title);
        TextView subtitle = text("Проект №42 • состояние и зарядка", 13, 0xff9aa9c2, false);
        page.addView(subtitle);

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(16),dp(18),dp(16),dp(18));
        batteryView = new BatteryView();
        hero.addView(batteryView, new LinearLayout.LayoutParams(-1, dp(145)));
        percent = text("--%", 42, Color.WHITE, true);
        percent.setGravity(Gravity.CENTER);
        hero.addView(percent);
        status = text("Получение данных…", 14, 0xff54d6a0, true);
        status.setGravity(Gravity.CENTER);
        hero.addView(status);
        page.addView(hero);

        TextView h = text("ПАРАМЕТРЫ АККУМУЛЯТОРА", 13, 0xff9aa9c2, true);
        h.setPadding(2,dp(22),0,dp(8)); page.addView(h);
        LinearLayout row1 = new LinearLayout(this);
        row1.addView(tile("ТЕМПЕРАТУРА", "-- °C"), new LinearLayout.LayoutParams(0,dp(100),1));
        row1.addView(tile("НАПРЯЖЕНИЕ", "-- В"), new LinearLayout.LayoutParams(0,dp(100),1));
        page.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.addView(tile("ТОК ЗАРЯДКИ", "Нет данных"), new LinearLayout.LayoutParams(0,dp(100),1));
        row2.addView(tile("СОСТОЯНИЕ", "--"), new LinearLayout.LayoutParams(0,dp(100),1));
        page.addView(row2);
        LinearLayout row3 = new LinearLayout(this);
        row3.addView(tile("ТИП ПОДКЛЮЧЕНИЯ", "--"), new LinearLayout.LayoutParams(0,dp(100),1));
        row3.addView(tile("ТЕХНОЛОГИЯ", "--"), new LinearLayout.LayoutParams(0,dp(100),1));
        page.addView(row3);

        TextView note = text("Примечание: Android показывает только те параметры батареи, которые предоставляет производитель устройства. Точный процент износа и ёмкость могут быть недоступны.", 12, 0xff9aa9c2, false);
        note.setPadding(dp(4),dp(18),dp(4),0); page.addView(note);
        setContentView(scroll);
    }

    View tile(String label, String value) {
        LinearLayout box = card();
        box.setPadding(dp(14),dp(14),dp(8),dp(10));
        TextView l = text(label, 10, 0xff8fa1bd, true);
        TextView v = text(value, 18, Color.WHITE, true);
        box.addView(l); box.addView(v);
        v.setTag(label);
        if (label.equals("ТЕМПЕРАТУРА")) temp=v;
        if (label.equals("НАПРЯЖЕНИЕ")) voltage=v;
        if (label.equals("ТОК ЗАРЯДКИ")) current=v;
        if (label.equals("СОСТОЯНИЕ")) health=v;
        if (label.equals("ТИП ПОДКЛЮЧЕНИЯ")) capacity=v;
        if (label.equals("ТЕХНОЛОГИЯ")) technology=v;
        return box;
    }

    void update(Intent i) {
        int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        int pct=(level>=0 && scale>0)? Math.round(level*100f/scale):0;
        percent.setText(pct+"%");
        batteryView.setLevel(pct);
        int st=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        boolean charging=st==BatteryManager.BATTERY_STATUS_CHARGING || st==BatteryManager.BATTERY_STATUS_FULL;
        status.setText(st==BatteryManager.BATTERY_STATUS_FULL?"Полностью заряжен":charging?"Идёт зарядка":"Работает от аккумулятора");
        int t=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
        temp.setText(t>=0?String.format(Locale.getDefault(),"%.1f °C",t/10f):"Нет данных");
        int v=i.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
        voltage.setText(v>0?String.format(Locale.getDefault(),"%.2f В",v/1000f):"Нет данных");
        int healthCode=i.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
        String hs="Неизвестно";
        if(healthCode==BatteryManager.BATTERY_HEALTH_GOOD) hs="Хорошее";
        else if(healthCode==BatteryManager.BATTERY_HEALTH_OVERHEAT) hs="Перегрев";
        else if(healthCode==BatteryManager.BATTERY_HEALTH_DEAD) hs="Неисправно";
        else if(healthCode==BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) hs="Перенапряжение";
        else if(healthCode==BatteryManager.BATTERY_HEALTH_COLD) hs="Переохлаждение";
        health.setText(hs);
        int plug=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        String ps=plug==BatteryManager.BATTERY_PLUGGED_USB?"USB":plug==BatteryManager.BATTERY_PLUGGED_AC?"Сеть":plug==BatteryManager.BATTERY_PLUGGED_WIRELESS?"Беспроводная":"Не подключено";
        capacity.setText(ps);
        technology.setText(i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)!=null?i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY):"Неизвестно");
        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
        int microA=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        current.setText(microA==Integer.MIN_VALUE?"Нет данных":String.format(Locale.getDefault(),"%.0f мА",Math.abs(microA)/1000f));
    }

    LinearLayout card() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(0xff1b2940,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(14),0,0); l.setLayoutParams(p);
        return l;
    }
    GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if(bold)v.setTypeface(null,1); return v;
    }
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){} super.onDestroy();}

    class BatteryView extends View {
        Paint p=new Paint(3); int level=0;
        BatteryView(){super(MainActivity.this);}
        void setLevel(int x){level=x;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float w=getWidth(), h=getHeight();
            float bw=Math.min(w*.52f,dp(190)), bh=dp(75), x=(w-bw)/2, y=(h-bh)/2;
            p.setColor(0xff34445f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));
            RectF r=new RectF(x,y,x+bw,y+bh);c.drawRoundRect(r,dp(12),dp(12),p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xff34445f);
            c.drawRoundRect(x+bw+dp(3),y+bh*.32f,x+bw+dp(11),y+bh*.68f,dp(3),dp(3),p);
            p.setColor(level<=15?0xffff6b6b:0xff54d6a0);
            RectF fill=new RectF(x+dp(6),y+dp(6),x+dp(6)+(bw-dp(12))*level/100f,y+bh-dp(6));
            c.drawRoundRect(fill,dp(7),dp(7),p);
        }
    }
}
