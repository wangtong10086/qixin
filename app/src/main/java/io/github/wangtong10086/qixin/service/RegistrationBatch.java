package io.github.wangtong10086.qixin.service;
import org.json.*;
import java.util.concurrent.Executors;
import io.github.wangtong10086.qixin.core.*;
/** One serial, user-triggered job. Restarting the service never replays requests. */
final class RegistrationBatch {
 private final Inventory inventory;private final History history;private final Store store;
 private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
 private JSONObject job=new JSONObject();private boolean running,cancelled;
 RegistrationBatch(Inventory inventory,History history,Store store){this.inventory=inventory;this.history=history;this.store=store;}
 synchronized JSONObject status()throws JSONException{return new JSONObject(job.toString()).put("running",running);}
 synchronized JSONObject cancel()throws JSONException{cancelled=true;return status();}
 synchronized JSONObject start(JSONObject request)throws Exception{
  if(running)throw new IllegalStateException("注册任务正在执行，请等待或取消");
  String name=request.optString("package");if(!name.isEmpty())Policy.packageName(name);
  JSONArray rows=RegistrationPlan.create(inventory.pushApps(history,store),name);
  JSONObject next=new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("startedAt",System.currentTimeMillis()).put("items",rows);
  if(request.optBoolean("dryRun"))return next.put("dryRun",true).put("running",false);
  if(!store.get().getString("mode").equals("manage"))throw new IllegalStateException("请先切换到管理模式");
  job=next;cancelled=false;running=true;worker.execute(this::run);return status();
 }
 private void run(){
  try{
   int count; synchronized(this){count=job.getJSONArray("items").length();}
   for(int i=0;i<count;i++){
    JSONObject row;
    synchronized(this){row=job.getJSONArray("items").getJSONObject(i);if(!row.getString("state").equals("pending"))continue;if(cancelled){row.put("state","cancelled").put("reason","已取消，未发送请求");continue;}}
    String state,reason;
    try{
     String name=row.getString("package");JSONObject config=store.get();
     if(!config.getString("mode").equals("manage")||Policy.app(config,name).getBoolean("pushExcluded")){state="skipped";reason="当前配置已排除此应用或退出管理模式";}
     else {
      JSONObject app=inventory.runtime.application(name);
      if(app.getBoolean("blocked")||app.getInt("type")!=2){state="manual_required";reason="小米注册策略未允许自动注册，请在应用详情检查";}
      else {String result=inventory.runtime.registerBackground(name).getString("details");state=result;reason=result.equals("request_sent")?"请求已发送，等待应用 SDK 注册；不代表注册成功":"没有可用的后台接收器，请手动打开";}
     }
    }catch(Exception error){state="failed";reason=error.getClass().getSimpleName()+": "+error.getMessage();}
    synchronized(this){row.put("state",state).put("reason",reason).put("finishedAt",System.currentTimeMillis());}
   }
  }catch(JSONException invalid){throw new IllegalStateException("Invalid registration job",invalid);}
  finally{synchronized(this){running=false;}}
 }
}
