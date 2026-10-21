package io.github.wangtong10086.qixin.core;
import java.util.ArrayDeque;
/** Calls count attempts even if the provider rejects the reconnect command. */
public final class RecoveryWindow {
    private final ArrayDeque<Long> attempts=new ArrayDeque<>();
    private long nextAllowed;
    public boolean eligible(long now){while(!attempts.isEmpty()&&now-attempts.peekFirst()>=1800000)attempts.removeFirst();return attempts.size()<3&&now>=nextAllowed;}
    public void attempted(long now){if(!eligible(now))throw new IllegalStateException("Recovery cooling down");attempts.addLast(now);nextAllowed=now+new long[]{30000,120000,300000}[attempts.size()-1];}
    public org.json.JSONArray snapshot(){return new org.json.JSONArray(attempts);}
    public void restore(org.json.JSONArray saved,long now){
        attempts.clear();nextAllowed=0;
        if(saved.length()>3)throw new IllegalArgumentException("Invalid recovery history");
        for(int i=0;i<saved.length();i++){
            long time=Math.min(saved.getLong(i),now);
            if(now-time<1800000)attempts.addLast(time);
        }
        if(!attempts.isEmpty())nextAllowed=attempts.peekLast()+new long[]{30000,120000,300000}[attempts.size()-1];
    }
    public long nextAllowed(){return nextAllowed;}
}
