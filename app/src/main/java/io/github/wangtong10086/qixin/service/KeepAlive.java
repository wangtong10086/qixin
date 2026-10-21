package io.github.wangtong10086.qixin.service;

import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.os.UserManager;
import java.nio.file.*;
import org.json.*;
import io.github.wangtong10086.qixin.core.*;

/** Opt-in native Feishu connection recovery, separate from cloud push delivery. */
public final class KeepAlive {
 private static final String PACKAGE="com.ss.android.lark";
 private static final String QIXU="/data/adb/fuxicontrol/fuxictl";
 private final Store store;private final Inventory inventory;private final History history;
 private final Path file;private final RecoveryWindow recovery=new RecoveryWindow();
 private JSONObject state;
 private static JSONObject patch()throws JSONException{return new JSONObject().put("autostart","allow").put("background","normal").put("resident",true).put("network","allow").put("cleanupOnTaskRemoved",false);}
 public KeepAlive(Store store,Inventory inventory,History history)throws Exception{
  this.store=store;this.inventory=inventory;this.history=history;file=store.directory.resolve("feishu-keepalive.json");
  state=Files.exists(file)?new JSONObject(Text.read(file)):new JSONObject().put("enabled",false).put("revision",0);
  recovery.restore(state.optJSONArray("attempts")==null?new JSONArray():state.getJSONArray("attempts"),System.currentTimeMillis());
 }
 private JSONObject qixu(JSONObject request)throws Exception{return new JSONObject(Shell.run(QIXU,"rpc",request.toString()));}
 private void persist()throws Exception{state.put("attempts",recovery.snapshot());Store.atomic(file,state.toString());}
 public synchronized JSONObject status()throws JSONException{return new JSONObject().put("enabled",state.getBoolean("enabled")).put("revision",state.getLong("revision")).put("restorePending",state.optBoolean("restorePending")).put("result",state.optString("result","disabled")).put("error",state.optString("error")).put("lastAttempt",state.optLong("lastAttempt")).put("nextAllowed",recovery.nextAllowed());}
 public synchronized JSONObject set(boolean enabled,long expected)throws Exception{
  if(state.getLong("revision")!=expected)throw new IllegalStateException("保活配置已更新，请重新加载");
  if(enabled==state.getBoolean("enabled")&&(enabled||!state.optBoolean("restorePending")))return status();
  if(enabled){
   if(!store.get().getString("mode").equals("manage"))throw new IllegalStateException("请先启用管理模式");
   if(state.optBoolean("restorePending"))throw new IllegalStateException("请先关闭并完成旧策略恢复");
   JSONObject config=qixu(new JSONObject().put("command","config.get"));
   String profile=config.getString("activeProfile");JSONObject previous=config.getJSONObject("apps").optJSONObject(PACKAGE);
   state.put("previous",previous==null?new JSONObject():previous).put("profile",profile).put("restorePending",true);persist();
   qixu(new JSONObject().put("command","app.patch").put("package",PACKAGE).put("patch",patch()).put("expectedRevision",config.getLong("revision")));
   state.put("enabled",true).put("result","waiting");
  }else{
   // Disable recovery first; a failed restoration must never keep launching an app.
   state.put("enabled",false).put("restorePending",true);persist();
   JSONObject config=qixu(new JSONObject().put("command","config.get"));String profile=state.getString("profile");
   JSONObject target=config.getString("activeProfile").equals(profile)?config:config.getJSONObject("profiles").getJSONObject(profile).getJSONObject("config");
   JSONObject apps=target.getJSONObject("apps"),current=apps.optJSONObject(PACKAGE);
   JSONObject restored=PolicyOverlay.restore(current==null?new JSONObject():current,state.getJSONObject("previous"),patch());
   if(restored.length()==0)apps.remove(PACKAGE);else apps.put(PACKAGE,restored);
   qixu(new JSONObject().put("command","config.replace").put("config",config).put("expectedRevision",config.getLong("revision")));
   state.put("restorePending",false).put("result","disabled");
  }
  state.put("revision",expected+1).put("error","");persist();return status();
 }
 public synchronized void tick()throws Exception{
  if(!state.getBoolean("enabled")||!store.get().getString("mode").equals("manage"))return;
  if(!inventory.context.getSystemService(UserManager.class).isUserUnlocked()){state.put("result","locked");return;}
  ApplicationInfo app=inventory.pm.getApplicationInfo(PACKAGE,0);
  if(!app.enabled||(app.flags&ApplicationInfo.FLAG_STOPPED)!=0){state.put("result","user-stopped");return;}
  for(var process:inventory.context.getSystemService(ActivityManager.class).getRunningAppProcesses())if(process.processName.equals(PACKAGE)){state.put("result","running").put("error","");return;}
  long now=System.currentTimeMillis();if(!recovery.eligible(now)){state.put("result","cooling-down");return;}
  JSONObject config=qixu(new JSONObject().put("command","config.get"));
  if(!config.getString("activeProfile").equals(state.getString("profile"))){state.put("result","profile-changed");return;}
  JSONObject overrides=config.getJSONObject("apps").optJSONObject(PACKAGE);
  for(String key:Json.keys(patch()))if(overrides==null||!patch().get(key).equals(overrides.opt(key))){state.put("result","policy-changed");return;}
  recovery.attempted(now);state.put("lastAttempt",now).put("result","starting");persist();
  Shell.run("cmd","deviceidle","tempwhitelist","-d","30000",PACKAGE);
  Shell.run("am","startservice","--user","0","-n",PACKAGE+"/.wschannel.WschannelLaunchService");
  state.put("result","start-requested").put("error","");persist();
  history.append(new JSONObject().put("package",PACKAGE).put("channel","system").put("source","keepalive").put("stage","native-connection-recovery").put("result","start-requested"),store.get());
 }
 public synchronized void failed(Exception failure)throws Exception{state.put("error",failure.getClass().getSimpleName()+": "+failure.getMessage()).put("result","failed");persist();}
}
