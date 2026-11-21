package ru.dmitry.batterymonitor42;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.util.ArrayList;

/** Append-only local history with verified, non-destructive migration from SharedPreferences. */
final class HistoryStore {
    static final int MAX_SAMPLES=10080;
    private static final long COMPACT_BYTES=480000L;
    private static final Object LOCK=new Object();
    private final File file;

    static final class Sample {
        final long time; final int level,temp,current,volt,status;
        Sample(long t,int l,int te,int c,int v,int s){time=t;level=l;temp=te;current=c;volt=v;status=s;}
        String line(){return time+":"+level+":"+temp+":"+current+":"+volt+":"+status;}
        static Sample parse(String line){
            String[] a=line.split(":"); if(a.length!=6)return null;
            try{long t=Long.parseLong(a[0]);int l=Integer.parseInt(a[1]);if(t<=0||l<0||l>100)return null;
                return new Sample(t,l,Integer.parseInt(a[2]),Integer.parseInt(a[3]),Integer.parseInt(a[4]),Integer.parseInt(a[5]));
            }catch(NumberFormatException e){return null;}
        }
    }
    HistoryStore(Context c){file=new File(c.getApplicationContext().getFilesDir(),"history.log");}
    boolean append(Sample s){synchronized(LOCK){try(FileOutputStream out=new FileOutputStream(file,true)){
        out.write((s.line()+"\n").getBytes("UTF-8"));out.getFD().sync();
    }catch(IOException e){return false;}if(file.length()>COMPACT_BYTES)compactLocked();return true;}}
    ArrayList<Sample> readAll(){synchronized(LOCK){return readLocked();}}
    void clear(){synchronized(LOCK){File bak=new File(file.getPath()+".bak");if(file.exists())file.delete();if(bak.exists())bak.delete();}}
    /** Keep old preferences as a backup; mark migration only after the file is written and verified. */
    boolean migrateLegacy(SharedPreferences p){synchronized(LOCK){
        if(file.exists()&&file.length()>0)return p.edit().putBoolean("history_file_migrated",true).commit();
        if(p.getBoolean("history_file_migrated",false))return file.exists();
        ArrayList<Sample> rows=new ArrayList<>();String v2=p.getString("history_v2","");
        if(v2!=null&&!v2.isEmpty())for(String row:v2.split(";")){Sample s=Sample.parse(row);if(s!=null)rows.add(s);}
        if(rows.isEmpty()){String old=p.getString("history","");if(old!=null){
            String cleaned=old.replace("[","").replace("]","").trim();ArrayList<Integer> values=new ArrayList<>();
            if(!cleaned.isEmpty())for(String token:cleaned.split(","))try{int n=Integer.parseInt(token.trim());if(n>=0&&n<=100)values.add(n);}catch(NumberFormatException ignored){}
            long now=System.currentTimeMillis();for(int i=0;i<values.size();i++)rows.add(new Sample(now-(values.size()-1-i)*60000L,values.get(i),-1,Integer.MIN_VALUE,-1,1));
        }}
        if(rows.isEmpty())return p.edit().putBoolean("history_file_migrated",true).commit();
        if(!writeAtomicallyLocked(rows))return false;
        if(readLocked().size()!=rows.size())return false;
        return p.edit().putBoolean("history_file_migrated",true).commit();
    }}
    private ArrayList<Sample> readLocked(){ArrayList<Sample> rows=new ArrayList<>();if(!file.exists())return rows;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String line;while((line=r.readLine())!=null){Sample s=Sample.parse(line);if(s!=null)rows.add(s);}}catch(IOException ignored){}
        int extra=rows.size()-MAX_SAMPLES;if(extra>0)rows.subList(0,extra).clear();return rows;
    }
    private void compactLocked(){writeAtomicallyLocked(readLocked());}
    private boolean writeAtomicallyLocked(ArrayList<Sample> rows){
        File tmp=new File(file.getPath()+".tmp"),bak=new File(file.getPath()+".bak");
        try(FileOutputStream out=new FileOutputStream(tmp)){int start=Math.max(0,rows.size()-MAX_SAMPLES);
            for(int i=start;i<rows.size();i++)out.write((rows.get(i).line()+"\n").getBytes("UTF-8"));out.getFD().sync();
        }catch(IOException e){tmp.delete();return false;}
        if(bak.exists())bak.delete();boolean had=file.exists();
        if(had&&!file.renameTo(bak)){tmp.delete();return false;}
        if(!tmp.renameTo(file)){if(had&&bak.exists())bak.renameTo(file);tmp.delete();return false;}
        return true;
    }
}
