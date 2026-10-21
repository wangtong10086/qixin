package io.github.wangtong10086.qixin.service;
import android.net.*;
import android.os.SystemClock;
import org.json.*;
import java.util.*;
import io.github.wangtong10086.qixin.core.*;
public final class Health {
 final RuntimeObservation observation;
 private final Inventory inventory;private final Store store;private final History history;
 private volatile JSONObject snapshot=new JSONObject();
 private volatile long xmsfSeen;private volatile String xmsfState="unknown";
 private final Map<String,RecoveryWindow> windows=Map.of("fcm",new RecoveryWindow(),"mipush",new RecoveryWindow());
 private final Map<String,Long> disconnected=new HashMap<>();
 private boolean wasUserUnlocked;
 private String networkId="";private long networkChanged;
 public Health(Inventory inventory,Store store,History history)throws Exception{
  observation=new RuntimeObservation(store.directory);
  this.inventory=inventory;this.store=store;this.history=history;wasUserUnlocked=inventory.context.getSystemService(android.os.UserManager.class).isUserUnlocked();
  var path=store.directory.resolve("recovery.json");
  if(java.nio.file.Files.exists(path)){JSONObject saved=new JSONObject(Text.read(path));for(String channel:windows.keySet())windows.get(channel).restore(saved.getJSONArray(channel),System.currentTimeMillis());}
 }
 private void persistRecovery()throws Exception{JSONObject saved=new JSONObject();for(String channel:windows.keySet())saved.put(channel,windows.get(channel).snapshot());Store.atomic(store.directory.resolve("recovery.json"),saved.toString());}
 public JSONObject snapshot()throws JSONException{return new JSONObject(snapshot.toString());}
 public void xmsf(JSONObject event)throws JSONException{if(event.getString("stage").equals("connection")){xmsfSeen=SystemClock.elapsedRealtime();xmsfState=event.getString("result");}}
 public synchronized void inspect()throws Exception{
  boolean unlocked=inventory.context.getSystemService(android.os.UserManager.class).isUserUnlocked();if(unlocked&&!wasUserUnlocked)inventory.refresh(history,store);wasUserUnlocked=unlocked;
  long now=SystemClock.elapsedRealtime();var cm=inventory.context.getSystemService(ConnectivityManager.class);Network network=cm.getActiveNetwork();NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);
  String id=network==null?"":network.toString();if(!id.equals(networkId)){networkId=id;networkChanged=now;}
  boolean available=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  String dump=Shell.run("dumpsys","activity","service","com.google.android.gms/.gcm.GcmService");
  String state=dump.contains("Is client connected: true")?"authenticated":dump.contains("Is client connected: false")?"disconnected":"unknown";
  JSONObject fcm=new JSONObject().put("state",available?state:"no-network").put("evidence","GcmService diagnostic").put("sampledAt",System.currentTimeMillis());
  String xs=now-xmsfSeen<180000?xmsfState:"unknown";
  String miEvidence=xmsfSeen==0?"awaiting-adapter":"XMSF connection callback";
  if(java.nio.file.Files.exists(store.directory.resolve("runtime-migrated"))){
   JSONObject runtime=inventory.runtime.status();
   observation.sample(runtime);
   xs=runtime.optBoolean("connected")?runtime.getString("connectionState"):"unknown";
   miEvidence=runtime.optBoolean("connected")?"XMSF runtime Binder":runtime.optString("error");
  }
  JSONObject mi=new JSONObject().put("state",available?xs:"no-network").put("evidence",miEvidence).put("sampledAt",System.currentTimeMillis());
  snapshot=new JSONObject().put("fcm",fcm).put("mipush",mi).put("networkAvailable",available).put("vpn",caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
  JSONObject config=store.get();
  for(String channel:new String[]{"fcm","mipush"}){
   String value=snapshot.getJSONObject(channel).getString("state");
   if(!value.equals("disconnected")){disconnected.remove(channel);continue;}
   long since=disconnected.computeIfAbsent(channel,k->now);
   if(config.getString("mode").equals("manage")&&config.getString("recovery").equals("soft")&&config.getJSONObject("takeover").optBoolean("connection")&&now-since>=30000&&now-networkChanged>=15000&&windows.get(channel).eligible(System.currentTimeMillis()))reconnect(channel,true);
  }
 }
 public synchronized JSONObject reconnect(String channel,boolean automatic)throws Exception{
  Policy.oneOf(channel,"fcm","mipush");long now=System.currentTimeMillis();RecoveryWindow window=windows.get(channel);if(!window.eligible(now))throw new IllegalStateException("恢复冷却中，请稍后重试");window.attempted(now);persistRecovery();
  JSONObject event=new JSONObject().put("package",channel.equals("fcm")?Inventory.GMS:Inventory.XMSF).put("channel",channel).put("stage","recovery").put("source","controller").put("reason",automatic?"automatic-soft":"manual-soft");
  try{
   if(channel.equals("fcm")){
    Shell.run("am","startservice","--user","0","-n","com.google.android.gms/.gcm.GcmService");
    Shell.run("am","broadcast","--user","0","-a","com.google.android.gms.gcm.ACTION_TRIGGER_CONNECT","--ez","force","true","-p",Inventory.GMS);
   }else if(java.nio.file.Files.exists(store.directory.resolve("runtime-migrated")))inventory.runtime.reconnect();
   else Shell.run("am","startservice","--user","0","-n","com.xiaomi.xmsf/com.xiaomi.push.service.XMPushService","-a","com.xiaomi.push.RESET_CONN");
   event.put("result","requested");history.append(event,store.get());return new JSONObject().put("requested",true).put("channel",channel);
  }catch(Exception failure){event.put("result","failed").put("reason",failure.getClass().getSimpleName());history.append(event,store.get());throw failure;}
 }
}
