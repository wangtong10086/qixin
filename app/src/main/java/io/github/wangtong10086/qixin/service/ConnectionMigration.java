package io.github.wangtong10086.qixin.service;
import java.nio.file.*;
import org.json.*;
/** Changes only the legacy connectivity guard; registration and notification modules stay enabled. */
public final class ConnectionMigration {
 private final Store store;
 private static final Path GUARD=Path.of("/data/adb/modules/fcm_connectivity_guard");
 public ConnectionMigration(Store store){this.store=store;}
 public synchronized JSONObject apply(boolean dryRun)throws Exception{
  JSONObject current=store.get();
  if(current.getJSONObject("takeover").optBoolean("connection"))return new JSONObject().put("changed",false);
  if(dryRun)return new JSONObject().put("action","take-over-connection-recovery").put("legacyGuardPresent",Files.isDirectory(GUARD));
  Path backup=store.directory.resolve("connection-migration.json");
  if(!Files.exists(backup))Store.atomic(backup,new JSONObject().put("previousConfig",current).put("guardDisabled",Files.exists(GUARD.resolve("disable"))).put("guardPresent",Files.isDirectory(GUARD)).toString());
  if(Files.isDirectory(GUARD)){
   Text.write(GUARD.resolve("disable"),"");
   Path pidFile=Path.of("/data/adb/fcm-connectivity-guard/health.pid");
   if(Files.exists(pidFile)){
    int pid=Integer.parseInt(Text.read(pidFile).trim());Path command=Path.of("/proc",Integer.toString(pid),"cmdline");
    if(Files.exists(command)){
     String[] arguments=Text.read(command).split("\u0000");
     if(java.util.Arrays.asList(arguments).contains(GUARD.resolve("service.sh").toString()))android.system.Os.kill(pid,android.system.OsConstants.SIGTERM);
     else throw new IllegalStateException("Legacy guard PID changed; inspect before takeover");
    }
   }
  }
  JSONObject next=new JSONObject(current.toString()).put("mode","manage");next.getJSONObject("takeover").put("connection",true);
  store.replace(next,current.getLong("revision"),false);
  return new JSONObject().put("changed",true).put("connectionRecovery","qixin-soft").put("rollbackAvailable",true);
 }
 public synchronized JSONObject rollback(boolean dryRun)throws Exception{
  Path backup=store.directory.resolve("connection-migration.json");JSONObject saved=new JSONObject(Text.read(backup));
  if(dryRun)return new JSONObject().put("action","restore-legacy-connection-guard");
  JSONObject current=store.get(),previous=saved.getJSONObject("previousConfig");
  if(!current.getJSONObject("takeover").optBoolean("connection"))return new JSONObject().put("restored",false);
  current.getJSONObject("takeover").put("connection",previous.getJSONObject("takeover").optBoolean("connection"));
  store.replace(current,current.getLong("revision"),false);
  if(saved.getBoolean("guardPresent")&&!saved.getBoolean("guardDisabled")){
   Files.deleteIfExists(GUARD.resolve("disable"));
   Shell.run("sh","-c","nohup sh /data/adb/modules/fcm_connectivity_guard/service.sh >/dev/null 2>&1 </dev/null &");
  }
  return new JSONObject().put("restored",true);
 }
}
