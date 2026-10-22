package io.github.wangtong10086.qixin.service;
import org.json.*;
import java.util.concurrent.ConcurrentHashMap;
import io.github.wangtong10086.qixin.core.*;
public final class Commands {
 final RegistrationBatch registrationBatch;
 final ConnectionMigration migration;
 final KeepAlive keepAlive;
 final Store store;final History history;final Inventory inventory;final Health health;
 final ConcurrentHashMap<String,JSONObject> registrationsPending=new ConcurrentHashMap<>();
 final ConcurrentHashMap<String,JSONObject> hooks=new ConcurrentHashMap<>();
 volatile String inventoryError="",healthError="";
 public Commands(Store s,History h,Inventory i,Health health)throws Exception{store=s;history=h;inventory=i;this.health=health;this.migration=new ConnectionMigration(s);this.keepAlive=new KeepAlive(s,i,h);this.registrationBatch=new RegistrationBatch(i,h,s);}
 public JSONObject execute(JSONObject request,String role)throws Exception{
  String cmd=request.getString("command");
  boolean admin=role.equals("root")||role.equals("manager");
  if(!admin&&!java.util.Set.of("hook.config","event","registration.poll").contains(cmd))throw new SecurityException("Hook peer is restricted to observation");
  return switch(cmd){
   case "runtime.observation.start" -> health.observation.start();
   case "runtime.observation.status" -> health.observation.status();
   case "runtime.status" -> inventory.runtime.status();
   case "runtime.application" -> inventory.runtime.application(request.getString("package"));
   case "runtime.application.set" -> inventory.runtime.updateApplication(request);
   case "runtime.channels" -> inventory.runtime.channels(request.getString("package"));
   case "runtime.reconnect" -> health.reconnect("mipush",false);
   case "runtime.replay" -> inventory.runtime.replay(request.getString("package"),request.getLong("eventId"));
   case "runtime.logs" -> inventory.runtime.exportLogs();
   case "keepalive.get" -> keepAlive.status();
   case "keepalive.set" -> keepAlive.set(request.getBoolean("enabled"),request.getLong("expectedRevision"));
   case "registration.batch" -> registrationBatch.start(request);
   case "registration.status" -> registrationBatch.status();
   case "registration.cancel" -> registrationBatch.cancel();
   case "registration.retry" -> {
    String name=request.getString("package");if(!name.equals("com.ss.android.lark"))throw new IllegalArgumentException("此应用尚无注册适配器");
    JSONObject config=store.get();if(!config.getString("mode").equals("manage")||!config.getJSONObject("takeover").optBoolean("registration"))throw new IllegalStateException("请先启用飞书注册兼容");
    String channel=request.optString("channel","mipush");Policy.oneOf(channel,"mipush");
    JSONObject pending=new JSONObject().put("channel",channel).put("id",java.util.UUID.randomUUID().toString()).put("expiresAt",System.currentTimeMillis()+300000);
    if(!request.optBoolean("dryRun"))registrationsPending.put(name,pending);
    yield new JSONObject().put("queued",!request.optBoolean("dryRun")).put("expiresAt",pending.getLong("expiresAt")).put("instruction","保持飞书打开，等待执行原生注册；不发送聊天消息");
   }
   case "registration.poll" -> {
    if(!role.startsWith("app:"))throw new SecurityException("Application adapter required");
    JSONObject pending=registrationsPending.remove(role.substring(4));
    yield pending!=null&&pending.getLong("expiresAt")>=System.currentTimeMillis()?pending:new JSONObject();
   }
   case "connection.takeover" -> migration.apply(request.optBoolean("dryRun"));
   case "connection.rollback" -> migration.rollback(request.optBoolean("dryRun"));
   case "status" -> new JSONObject().put("connected",true).put("userUnlocked",inventory.context.getSystemService(android.os.UserManager.class).isUserUnlocked()).put("mode",store.get().getString("mode")).put("channels",health.snapshot()).put("hooks",new JSONObject(hooks)).put("keepalive",keepAlive.status()).put("events",history.count()).put("messages",history.messageCount()).put("maintenanceError",String.join("; ",java.util.stream.Stream.of(inventoryError,healthError).filter(v->!v.isEmpty()).toList())).put("importError",inventory.importError());
   case "config.get" -> store.get();
   case "hook.config" -> {
    JSONObject value=store.get();
    value.put("nativeKeepalive",keepAlive.status().getBoolean("enabled"));
    if(role.startsWith("app:")){
     String name=role.substring(4);JSONObject apps=new JSONObject();JSONObject own=value.getJSONObject("apps").optJSONObject(name);if(own!=null)apps.put(name,own);
     value.put("apps",apps).put("takeover",new JSONObject().put("registration",value.getJSONObject("takeover").optBoolean("registration")));
    }
    yield value;
   }
   case "config.replace" -> store.replace(request.getJSONObject("config"),request.getLong("expectedRevision"),request.optBoolean("dryRun"));
   case "apps" -> new JSONObject().put("apps",inventory.pushApps(history,store)).put("registrations",history.registrations(""));
   case "registrations" -> new JSONObject().put("registrations",history.registrations(request.optString("package")));
   case "events" -> history.events(request);
   case "notifications.observed" -> {
    JSONObject event=request.getJSONObject("event");
    if(!event.getString("stage").equals("notification")||!event.has("notificationKey"))throw new IllegalArgumentException("Notification metadata required");
    event.put("source","notification-listener");history.append(event,store.get());yield new JSONObject().put("recorded",true);
   }
   case "notifications.reconcile" -> {history.reconcileNotifications(request.getJSONArray("activeKeys"));yield new JSONObject().put("reconciled",true);}
   case "notifications.removed" -> {history.removeState(request.getString("notificationKey"));yield new JSONObject().put("removed",true);}
   case "notifications.expand" -> {Shell.run("cmd","statusbar","expand-notifications");yield new JSONObject().put("opened",true);}
   case "messages" -> history.events(new JSONObject(request.toString()).put("kind","message"));
   case "states" -> new JSONObject().put("states",history.states(request.optString("package")));
   case "history.clear" -> {history.clear(request.optString("package"));yield new JSONObject().put("cleared",true);}
   case "channel.reconnect" -> request.optBoolean("dryRun")?new JSONObject().put("action","soft-reconnect").put("channel",request.getString("channel")):health.reconnect(request.getString("channel"),false);
   case "refresh" -> {inventory.refresh(history,store);health.inspect();yield new JSONObject().put("refreshed",true);}
   case "diagnose" -> diagnose(request.optString("package"));
   case "event" -> {
    if(admin&&!request.optBoolean("test"))throw new SecurityException("Use an authenticated hook peer");
    JSONObject event=request.getJSONObject("event");
    if(role.startsWith("app:")&&!event.optString("package").equals(role.substring(4))&&!event.optString("stage").equals("hook-ready"))throw new SecurityException("Application may report only its own events");
    event.put("source",admin?"synthetic-test":role);
    if(event.getString("stage").equals("hook-ready"))hooks.put(role+":"+event.optString("scope"),new JSONObject().put("time",System.currentTimeMillis()).put("adapter",event.optString("adapter")).put("hooks",event.optString("hooks")).put("queueDrops",event.optLong("queueDrops")));
    if(role.equals("xmsf"))health.xmsf(event);
    history.append(event,store.get());yield new JSONObject().put("recorded",true);
   }
   default -> throw new IllegalArgumentException("Unknown command: "+cmd);
  };
 }
 private JSONObject diagnose(String name)throws Exception{
  JSONObject result=new JSONObject().put("channels",health.snapshot()).put("hooks",new JSONObject(hooks));
  if(!name.isEmpty()){
   Policy.packageName(name);var app=inventory.pm.getApplicationInfo(name,0);
   result.put("package",name).put("enabled",app.enabled).put("stopped",(app.flags&android.content.pm.ApplicationInfo.FLAG_STOPPED)!=0).put("settings",Policy.app(store.get(),name)).put("registrations",history.registrations(name));
   result.put("notificationPermission",inventory.pm.checkPermission("android.permission.POST_NOTIFICATIONS",name)==android.content.pm.PackageManager.PERMISSION_GRANTED);
   JSONArray events=history.events(new JSONObject().put("package",name).put("limit",100).put("order","time")).getJSONArray("events");
   result.put("events",events).put("evidence",io.github.wangtong10086.qixin.core.Evidence.summarize(events));
  }
  return result.put("serverRoute","unknown");
 }
}
