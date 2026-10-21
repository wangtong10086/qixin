package io.github.wangtong10086.qixin.hook;
import android.app.*;
import android.content.*;
import android.os.*;
import io.github.libxposed.api.*;
import io.github.wangtong10086.qixin.service.Wire;
import io.github.wangtong10086.qixin.core.Events;
import io.github.wangtong10086.qixin.core.Policy;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Hooks enqueue bounded metadata; no filesystem or IPC runs inside framework locks. */
public final class ModuleEntry extends XposedModule {
 private final LinkedBlockingQueue<JSONObject> queue=new LinkedBlockingQueue<>(512);
 private volatile JSONObject config=new JSONObject();
 private final Map<String,Integer> installed=new ConcurrentHashMap<>();
 private String scope="";private final java.util.concurrent.atomic.AtomicLong drops=new java.util.concurrent.atomic.AtomicLong();private volatile Object xmsf;
 private volatile int gmsUid=-1,xmsfUid=-1;
 private ClassLoader larkLoader;
 private volatile boolean larkPushStarted;
 private volatile Object larkUserManager;
 private volatile long pendingRegistrationUntil;
 private final ThreadLocal<Boolean> clickDispatch=new ThreadLocal<>();
 private final ThreadLocal<Boolean> batchUpload=new ThreadLocal<>();
 private final ThreadLocal<Integer> manualUpload=new ThreadLocal<>();
 private volatile boolean networkObserved;
 private final ThreadLocal<Boolean> startupNotificationCleanup=new ThreadLocal<>();
 private Context systemContext; private int systemUiUid=-1,qixinUid=-1;
 private Object activityManagerInternal; private Method tempAllowlist;
 private record ClickLease(long deadline,int senderPid,int senderUid) {}
 private final Map<String,ClickLease> clickedPayloads=new ConcurrentHashMap<>();
 private final ThreadLocal<String> uploadPath=new ThreadLocal<>();
 private final ThreadLocal<Integer> uploadChannel=new ThreadLocal<>();
 private final ThreadLocal<Boolean> uploadAccepted=new ThreadLocal<>();
 private final Map<Object,JSONObject> actions=Collections.synchronizedMap(new WeakHashMap<>());
 private void start(String name){
  scope=name;
  Thread worker=new Thread(()->{
   long refresh=0;
   while(true)try{
    long now=SystemClock.elapsedRealtime();
    if(now-refresh>=30000){if(scope.equals("system_server")&&systemContext==null)initializeSystem();if(scope.equals("system_server"))installed.put("delivery-lease","1".equals(System.getProperty("qixu.push.lease.api"))?1:0);config=Wire.call(new JSONObject().put("command","hook.config"));refresh=now;
     emit(event("system","hook-ready","").put("scope",scope).put("adapter","android16-vector102-island-v6").put("hooks",installed.toString()).put("queueDrops",drops.get()));
     if(scope.equals("com.ss.android.lark")){
      JSONObject request=Wire.call(new JSONObject().put("command","registration.poll"));
      if(request.has("id"))new Handler(Looper.getMainLooper()).post(()->observeSafely(()->{retryLark();}));
     }
     if(xmsf!=null){boolean connected=(boolean)xmsf.getClass().getMethod("isConnected").invoke(xmsf);emit(event("mipush","connection","com.xiaomi.xmsf").put("result",connected?"authenticated":"disconnected"));}
    }
    JSONObject e=queue.poll(2,TimeUnit.SECONDS);
    if(e!=null)try{Wire.call(new JSONObject().put("command","event").put("event",e));}
     catch(java.io.IOException failure){drops.incrementAndGet();throw failure;}
   }catch(InterruptedException stop){Thread.currentThread().interrupt();return;}
    catch(Exception failure){android.util.Log.w("QixinHook","Bridge unavailable: "+failure.getClass().getSimpleName());try{Thread.sleep(5000);}catch(InterruptedException stop){return;}}
  },"QixinObserver");worker.setDaemon(true);worker.start();
 }
 private JSONObject event(String channel,String stage,String pkg)throws JSONException{return new JSONObject().put("channel",channel).put("stage",stage).put("package",pkg).put("time",System.currentTimeMillis());}
 private void emit(JSONObject e){if(!queue.offer(e))drops.incrementAndGet();}
 private boolean manage(String feature){return config.optString("mode").equals("manage")&&config.optJSONObject("takeover")!=null&&config.optJSONObject("takeover").optBoolean(feature);}
 private boolean body(String pkg){JSONObject apps=config.optJSONObject("apps");return apps!=null&&apps.optJSONObject(pkg)!=null&&apps.optJSONObject(pkg).optBoolean("recordBody");}
 private interface Observation {void run() throws Exception;}
 // Observability must never turn a delivered notification into an application failure.
 private void observeSafely(Observation action){try{action.run();}catch(Exception failure){drops.incrementAndGet();android.util.Log.e("QixinHook","Observation failed",failure);}}
 private interface Interceptor {Object call(XposedInterface.Chain chain)throws Throwable;}
 private void install(ClassLoader loader,String label,String cls,String method,java.util.function.Predicate<Method> match,Interceptor fn){
  try{int count=0;for(Method m:Class.forName(cls,false,loader).getDeclaredMethods())if(m.getName().equals(method)&&match.test(m)){hook(m).setPriority(10000).setId("qixin."+label+"."+count).intercept(fn::call);count++;}installed.put(label,count);}
  catch(ClassNotFoundException|LinkageError missing){installed.put(label,0);android.util.Log.w("QixinHook","Adapter missing: "+label);}
 }
 @Override public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param){
  ClassLoader loader=param.getClassLoader();start("system_server");
  install(loader,"dispatch-service","com.android.server.am.ActiveServices","startServiceLocked",m->m.getParameterCount()==14,c->{clickDispatch.remove();observeSafely(()->observe((Intent)c.getArg(1),(int)c.getArg(4)));
   try{Object result=c.proceed();observeSafely(()->dispatchResult((Intent)c.getArg(1),result));return result;}
   catch(Throwable failure){if(Boolean.TRUE.equals(clickDispatch.get()))observeSafely(()->emit(event("mipush","dispatch-result",((Intent)c.getArg(1)).getComponent().getPackageName()).put("result",failure.getClass().getSimpleName())));throw failure;}
   finally{clickDispatch.remove();}});
  install(loader,"click-start-mode","com.android.server.am.ActivityManagerService","getAppStartModeLOSP",m->m.getParameterCount()==7,c->{
   Object result=c.proceed();if(Boolean.TRUE.equals(clickDispatch.get()))observeSafely(()->emit(event("mipush","click-start-mode",(String)c.getArg(1)).put("result",String.valueOf(result)).put("reason","alwaysRestrict="+c.getArg(4)+",forcedStandby="+c.getArg(6))));return result;
  });
  install(loader,"dispatch-broadcast","com.android.server.am.BroadcastController","broadcastIntentLocked",m->m.getParameterCount()==25,c->{observeSafely(()->observe((Intent)c.getArg(3),(int)c.getArg(18)));try{return c.proceed();}finally{clickDispatch.remove();}});
  install(loader,"notification","com.android.server.notification.NotificationManagerService","enqueueNotificationInternal",m->m.getReturnType()==boolean.class&&Arrays.asList(m.getParameterTypes()).contains(Notification.class),c->{
   Object result=c.proceed();observeSafely(()->{String pkg=(String)c.getArg(0);Notification n=null;for(Object arg:c.getArgs())if(arg instanceof Notification v)n=v;
   if(n!=null){int index=c.getArgs().indexOf(n);
    String key=Events.digest(c.getArg(index+1)+":"+pkg+":"+c.getArg(index-1)+":"+c.getArg(index-2));
    JSONObject e=event("unknown","notification",pkg).put("result",Boolean.TRUE.equals(result)?"accepted":"rejected")
     .put("notificationKey",key).put("userId",c.getArg(index+1)).put("notificationChannel",n.getChannelId())
     .put("notificationCategory",n.category==null?"":n.category).put("notificationStyle",n.extras.getString("android.template",""))
     .put("ongoing",(n.flags&Notification.FLAG_ONGOING_EVENT)!=0).put("foregroundService",(n.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0)
     .put("hasProgress",n.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0)>0||n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false))
     .put("messageKey",Events.digest(key+":"+n.when+":"+n.extras.getCharSequence(Notification.EXTRA_TITLE,"")+":"+n.extras.getCharSequence(Notification.EXTRA_TEXT,"")));
    if(body(pkg)){CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE),text=n.extras.getCharSequence(Notification.EXTRA_TEXT);if(title!=null)e.put("title",title.toString());if(text!=null)e.put("body",text.toString());}
    if(n.contentIntent!=null){Object target=PendingIntent.class.getMethod("getTarget").invoke(n.contentIntent);actions.put(target,e);}
    if(!Boolean.TRUE.equals(result)){e.put("stage","notification-enqueue");emit(e);}
   }});return result;
  });
  install(loader,"click","com.android.server.am.PendingIntentRecord","sendInner",m->true,c->{
   observeSafely(()->prepareClick(c.getThisObject()));
   JSONObject source=actions.get(c.getThisObject());Object result=c.proceed();
   if(source!=null)observeSafely(()->emit(event(source.getString("channel"),"click",source.getString("package")).put("result",String.valueOf(result)).put("surface","notification-or-card")));return result;
  });
 }
 private void observe(Intent intent,int caller)throws Exception{
  if(intent==null)return;String action=intent.getAction();if(action==null)return;
  String channel,stage;
  if(action.equals("com.google.android.c2dm.intent.RECEIVE")||action.equals("com.google.firebase.MESSAGING_EVENT")){channel="fcm";stage="dispatch";}
  else if(action.equals("com.google.android.c2dm.intent.REGISTRATION")){channel="fcm";stage="registration-callback";}
  else if(action.equals("com.xiaomi.mipush.RECEIVE_MESSAGE")||action.equals("com.xiaomi.mipush.MESSAGE_ARRIVED")){channel="mipush";stage="dispatch";}
  else return;
  if(systemContext==null)return;
  if(caller!=0&&caller!=1000&&caller!=(channel.equals("fcm")?gmsUid:xmsfUid))return;
  String pkg=intent.getComponent()!=null?intent.getComponent().getPackageName():intent.getPackage();if(pkg==null)return;
  if(stage.equals("dispatch")&&channel.equals("mipush")&&caller==xmsfUid&&manage("clicks")){
   byte[] payload=intent.getByteArrayExtra("mipush_payload");
   ClickLease click=payload==null?null:clickedPayloads.get(payloadKey(payload));
   if(click!=null&&click.deadline()>SystemClock.elapsedRealtime()){
    intent.putExtra("mipush_notified",true);
    clickDispatch.set(true);
    System.setProperty("qixin.push.lease."+pkg,Long.toString(SystemClock.elapsedRealtime()+30000));
    int uid=systemContext.getPackageManager().getApplicationInfo(pkg,0).uid;
    tempAllowlist.invoke(activityManagerInternal,click.senderPid(),click.senderUid(),uid,30000L,0,
     Class.forName("android.os.PowerExemptionManager").getField("REASON_NOTIFICATION_SERVICE").getInt(null),"qixin-notification-click");
    emit(event("mipush","click-route",pkg).put("result","click-marker-restored").put("ttl",30).put("messageKey",payloadKey(payload)));
   }
  }
  if(stage.equals("dispatch")&&manage("delivery")&&Policy.app(config,pkg).getBoolean("deliveryLease")){
   if("1".equals(System.getProperty("qixu.push.lease.api"))){
    System.setProperty("qixin.push.lease."+pkg,Long.toString(SystemClock.elapsedRealtime()+30000));
    emit(event(channel,"delivery-lease",pkg).put("result","granted").put("ttl",30));
   }else emit(event(channel,"delivery-lease",pkg).put("result","qixu-adapter-unavailable"));
  }
  JSONObject e=event(channel,stage,pkg).put("action",action).put("result","requested");
  try{
   String id=intent.getStringExtra("google.message_id");if(id==null)id=intent.getStringExtra("message_id");if(id!=null)e.put("messageKey",Events.digest(pkg+":"+id));
   String registration=intent.getStringExtra("registration_id");if(registration!=null)e.put("tokenFingerprint",Events.digest(registration));
   if(intent.getComponent()!=null)e.put("component",intent.getComponent().flattenToShortString());
   Bundle extras=intent.getExtras();if(extras!=null){for(String key:new String[]{"google.ttl","google.delivered_priority","google.original_priority"}){Object value=extras.get(key);if(value instanceof String||value instanceof Number)e.put(key.equals("google.ttl")?"ttl":key.equals("google.delivered_priority")?"priority":"originalPriority",value);}}
   if(body(pkg)){String title=intent.getStringExtra("gcm.n.title"),text=intent.getStringExtra("gcm.n.body");if(text==null)text=intent.getStringExtra("alert");if(title!=null)e.put("title",title);if(text!=null)e.put("body",text);}
  }catch(BadParcelableException invalid){e.put("result","invalid-extras");}
  emit(e);
 }
 private void initializeSystem()throws Exception{
  Object thread=Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
  Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
  gmsUid=context.getPackageManager().getApplicationInfo("com.google.android.gms",0).uid;
  xmsfUid=context.getPackageManager().getApplicationInfo("com.xiaomi.xmsf",0).uid;
  systemUiUid=context.getPackageManager().getApplicationInfo("com.android.systemui",0).uid;
  qixinUid=context.getPackageManager().getApplicationInfo("io.github.wangtong10086.qixin",0).uid;
  Class<?> type=Class.forName("android.app.ActivityManagerInternal");
  activityManagerInternal=Class.forName("com.android.server.LocalServices").getMethod("getService",Class.class).invoke(null,type);
  tempAllowlist=type.getMethod("tempAllowlistForPendingIntent",int.class,int.class,int.class,long.class,int.class,int.class,String.class);
  systemContext=context;
 }
 private static Object field(Object value,String name)throws Exception{
  Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
 }
 private static String payloadKey(byte[] payload)throws Exception{
  return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
 }
 private void prepareClick(Object record)throws Exception{
  if(systemContext==null||(int)field(record,"uid")!=xmsfUid)return;
  int caller=Binder.getCallingUid();
  if(caller!=systemUiUid&&(caller!=qixinUid||!actions.containsKey(record)))return;
  Intent intent=(Intent)field(field(record,"key"),"requestIntent");
  if(intent==null)return;
  byte[] payload=intent.getByteArrayExtra("mipush_payload");
  if(payload==null)return;
  String key=payloadKey(payload);long now=SystemClock.elapsedRealtime();
  clickedPayloads.entrySet().removeIf(e->e.getValue().deadline()<now);
  if(clickedPayloads.size()>=128){drops.incrementAndGet();return;}
  clickedPayloads.put(key,new ClickLease(now+30000,Binder.getCallingPid(),Binder.getCallingUid()));
  emit(event("mipush","click-request","com.xiaomi.xmsf").put("messageKey",key)
   .put("result",caller==systemUiUid?"system-ui-click":"qixin-island-click").put("clickMarker",intent.getBooleanExtra("mipush_notified",false)));
 }
 private void dispatchResult(Intent intent,Object result)throws Exception{
  if(intent==null||!"com.xiaomi.mipush.RECEIVE_MESSAGE".equals(intent.getAction()))return;
  String pkg=intent.getComponent()!=null?intent.getComponent().getPackageName():intent.getPackage();if(pkg==null)return;
  String state=result==null?"no-component":result instanceof ComponentName n&&!n.getPackageName().equals(pkg)?"rejected":"service-started";
  JSONObject e=event("mipush","dispatch-result",pkg).put("result",state).put("clickMarker",intent.getBooleanExtra("mipush_notified",false));
  byte[] payload=intent.getByteArrayExtra("mipush_payload");if(payload!=null)e.put("messageKey",payloadKey(payload));emit(e);
 }
 private void retryLark()throws Exception{
  if(!larkPushStarted){pendingRegistrationUntil=System.currentTimeMillis()+300000;emit(event("mipush","registration-request","com.ss.android.lark").put("result","waiting-for-sdk"));return;}

  Context context=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
  if(context==null){emit(event("mipush","registration-request","com.ss.android.lark").put("result","application-not-ready"));return;}
  Class<?> adapter=Class.forName("com.xm.MiPushAdapter",false,larkLoader);int channel=(int)adapter.getMethod("getMiPush").invoke(null);
  adapter.getMethod("registerPush",Context.class,int.class).invoke(adapter.getDeclaredConstructor().newInstance(),context,channel);
  new Handler(Looper.getMainLooper()).postDelayed(()->observeSafely(()->{
   Class<?> client=Class.forName("com.xiaomi.mipush.sdk.MiPushClient",false,larkLoader);
   String token=(String)client.getMethod("getRegId",Context.class).invoke(null,context);
   if(token==null||token.isEmpty()){emit(event("mipush","registration-token","com.ss.android.lark").put("result","not-ready"));return;}
   emit(event("mipush","registration-token","com.ss.android.lark").put("result","present").put("tokenFingerprint",Events.digest(token)));
   Object service=Class.forName("com.bytedance.push.PushSupporter",false,larkLoader).getMethod("thirdService").invoke(null);
   service.getClass().getMethod("registerSenderSuccessAndUploadToken",Context.class,int.class,String.class).invoke(service,context,channel,token);
   emit(event("mipush","registration-request","com.ss.android.lark").put("result","native-method-returned"));
   // Refresh through the installed SDK endpoint: its response, not a forced allow-list,
   // decides which transports are enabled. Never perform network work on the main thread.
   new Thread(()->observeSafely(()->refreshLarkRegistration(service,channel,token)),"QixinRegistrationRefresh").start();
  }),10000);
 }
 private static String pushChannel(int id){return id==1?"mipush":id==5?"fcm":"unknown";}
 private void refreshLarkRegistration(Object service,int channel,String token)throws Exception{
  if(!manage("registration"))return;
  if(!larkPushStarted){
   emit(event(pushChannel(channel),"business-token-batch","com.ss.android.lark").put("result","sdk-not-started"));return;
  }
  observeLarkNetwork();
  Class<?> channelHelper=Class.forName("com.bytedance.push.third.PushChannelHelper",false,larkLoader);
  Context current=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
  Object helperInstance=channelHelper.getMethod("inst",Context.class).invoke(null,current);
  emit(event(pushChannel(channel),"business-local-capability","com.ss.android.lark").put("result","observed").put("reason",String.valueOf(channelHelper.getMethod("buildApplogHeader").invoke(helperInstance))));
  Object supporter=Class.forName("com.bytedance.push.PushSupporter",false,larkLoader).getMethod("get").invoke(null);
  Map<?,?> common=(Map<?,?>)supporter.getClass().getMethod("getCommonParams").invoke(supporter);
  String alias=(String)common.get("alias");String account="";
  if(larkUserManager!=null){Method currentAccount=larkUserManager.getClass().getDeclaredMethod("Pq");currentAccount.setAccessible(true);account=(String)currentAccount.invoke(larkUserManager);}
  emit(event(pushChannel(channel),"business-account-binding","com.ss.android.lark").put("result",account.isEmpty()?"account-not-ready":account.equals(alias)?"account-matches":"account-mismatch").put("reason","sdkAliasPresent="+(alias!=null&&!alias.isEmpty())));
  // This Feishu release returns HTTP 404 for batch_update. Use its active legacy
  // request, including native account binding and success-only cache updates.
  Context context=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
  Class<?> tokenType=Class.forName("com.bytedance.push.third.d",false,larkLoader);
  Class<?> tokenImpl=Class.forName("com.bytedance.push.j$b",false,larkLoader);
  Constructor<?> tokenConstructor=tokenImpl.getDeclaredConstructor(Class.forName("com.bytedance.push.j",false,larkLoader),String.class,int.class);
  tokenConstructor.setAccessible(true);Object nativeToken=tokenConstructor.newInstance(service,token,channel);
  Constructor<?> taskConstructor=Class.forName("ql1.c",false,larkLoader).getDeclaredConstructor(Context.class,tokenType);
  taskConstructor.setAccessible(true);Runnable request=(Runnable)taskConstructor.newInstance(context,nativeToken);
  manualUpload.set(channel);batchUpload.set(true);
  try{request.run();}finally{manualUpload.remove();batchUpload.remove();}
  Class<?> helper=Class.forName("com.bytedance.push.third.a",false,larkLoader);
  Method supports=helper.getDeclaredMethod("isServerSupportChannel",int.class);supports.setAccessible(true);
  for(int id:new int[]{1,5,7})emit(event(pushChannel(id),"business-channel-support","com.ss.android.lark")
   .put("sdkChannel",id).put("result",(boolean)supports.invoke(null,id)?"supported":"not-supported"));
 }
 private synchronized void observeLarkNetwork()throws Exception{
  if(networkObserved)return;
  Object wrapper=Class.forName("com.bytedance.common.support.impl.PushNetworkClient",false,larkLoader).getMethod("getDefault").invoke(null);
  Method delegate=wrapper.getClass().getDeclaredMethod("getNetworkClient");delegate.setAccessible(true);
  Object client=delegate.invoke(wrapper);
  for(Method m:client.getClass().getMethods())if((m.getName().equals("post")||m.getName().equals("get"))&&m.getReturnType()==String.class){
   hook(m).intercept(c->{
    if(!Boolean.TRUE.equals(batchUpload.get()))return c.proceed();
    try{observeSafely(()->emit(event("mipush","business-http-request","com.ss.android.lark").put("component",client.getClass().getName()).put("action",android.net.Uri.parse((String)c.getArg(0)).getPath()).put("result","requested")));Object result=c.proceed();observeSafely(()->{
     String response=(String)result;String state=response==null||response.isEmpty()?"empty-response":"response-received";
     JSONObject e=event("mipush","business-http","com.ss.android.lark").put("result",state);
     if(response!=null&&response.trim().startsWith("{")){
      JSONObject parsed=new JSONObject(response);e.put("result","success".equals(parsed.optString("message"))?"server-accepted":"server-rejected");
      Object code=parsed.opt("code");if(code instanceof Number)e.put("reason","code="+code);
     }emit(e);
    });return result;}
    catch(Throwable failure){observeSafely(()->emit(event("mipush","business-http","com.ss.android.lark").put("result",failure.getClass().getSimpleName())));throw failure;}
   });
  }
  install(larkLoader,"lark-http-response","s62.a","d",m->m.getParameterCount()==2,c->{
   Object response=c.proceed();if(Boolean.TRUE.equals(batchUpload.get()))observeSafely(()->{
    JSONObject e=event("mipush","business-http-status","com.ss.android.lark").put("result",response==null?"no-response":"response-received");
    if(response!=null)e.put("reason","http="+response.getClass().getMethod("code").invoke(response)+",body="+(response.getClass().getMethod("body").invoke(response)!=null));
    emit(e);
   });return response;
  });
  install(larkLoader,"lark-http-code","com.larksuite.framework.http.call.CallProcessor","d",m->m.getParameterCount()==4,c->{
   if(Boolean.TRUE.equals(batchUpload.get()))observeSafely(()->emit(event("mipush","business-http-code","com.ss.android.lark").put("result","http-response").put("reason","http="+c.getArg(3).getClass().getMethod("code").invoke(c.getArg(3)))));
   return c.proceed();
  });
  networkObserved=true;
 }
 private void lark(ClassLoader loader){
  larkLoader=loader;
  install(loader,"lark-startup-cleanup","com.ss.android.lark.framework.assembly.offlinepush.UpdateOfflinePushConfigTask$a","run",m->m.getParameterCount()==0,c->{
   if(!installed.containsKey("lark-preserve-background-notifications")){
    Object api=Class.forName("com.ss.android.lark.utils.ApiUtils",false,loader).getMethod("getApi",Class.class).invoke(null,Class.forName("rk6.a",false,loader));
    Method clear=api.getClass().getMethod("clearNotification");
    hook(clear).setPriority(10000).setId("qixin.lark-preserve-background-notifications").intercept(call->{
     if(Boolean.TRUE.equals(startupNotificationCleanup.get())&&config.optBoolean("nativeKeepalive")&&config.optString("mode").equals("manage")){
      ActivityManager.RunningAppProcessInfo process=new ActivityManager.RunningAppProcessInfo();ActivityManager.getMyMemoryState(process);
      if(process.importance>ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND){
       observeSafely(()->emit(event("system","startup-notification-cleanup","com.ss.android.lark").put("result","preserved-background-notifications")));
       return null;
      }
     }
     return call.proceed();
    });
    installed.put("lark-preserve-background-notifications",1);
   }
   startupNotificationCleanup.set(true);
   try{return c.proceed();}finally{startupNotificationCleanup.remove();}
  });
  install(loader,"lark-push-start","com.bytedance.push.j","onPushStart",m->m.getParameterCount()==1,c->{
   Object result=c.proceed();larkPushStarted=true;
   new Handler(Looper.getMainLooper()).post(()->{if(pendingRegistrationUntil>System.currentTimeMillis()){pendingRegistrationUntil=0;observeSafely(this::retryLark);}});
   return result;
  });
  install(loader,"lark-user-binding","com.ss.lark.android.module.offlinepush.v2.userscope.i","Yq",m->m.getParameterCount()==1,c->{
   Object result=c.proceed();larkUserManager=c.getThisObject();return result;
  });
  start("com.ss.android.lark");
  install(loader,"lark-manual-upload","ql1.c","e",m->m.getParameterCount()==1,c->{
   Object original=c.proceed();
   // A user-requested refresh may attempt registration despite the cached filter.
   // The global server-support query and the response remain unchanged.
   if(manualUpload.get()!=null&&manualUpload.get()==(int)c.getArg(0))return true;
   return original;
  });
  install(loader,"lark-token-old","ql1.c","k",m->m.getParameterCount()==1,c->{
   uploadPath.set("old");uploadAccepted.set(false);
   Method getType=Class.forName("com.bytedance.push.third.d",false,loader).getMethod("getType");getType.setAccessible(true);
   uploadChannel.set((int)getType.invoke(c.getArg(0)));
   try{Object result=c.proceed();observeSafely(()->emit(event(pushChannel(uploadChannel.get()),"business-token-upload","com.ss.android.lark")
    .put("adapter","old").put("sdkChannel",uploadChannel.get()).put("result",Boolean.TRUE.equals(uploadAccepted.get())?"server-accepted":"no-server-ack-observed")));return result;}
   finally{uploadPath.remove();uploadAccepted.remove();uploadChannel.remove();}
  });
  install(loader,"lark-token-ack","ql1.e","d",m->m.getParameterCount()==2,c->{
   Object result=c.proceed();if(uploadPath.get()!=null)uploadAccepted.set(true);return result;
  });
  install(loader,"lark-channel-support","com.bytedance.push.third.a","isServerSupportChannel",m->m.getParameterCount()==1,c->{
   Object result=c.proceed();if(uploadPath.get()!=null)observeSafely(()->emit(event(pushChannel((int)c.getArg(0)),"business-channel-support","com.ss.android.lark")
    .put("sdkChannel",(int)c.getArg(0)).put("result",Boolean.TRUE.equals(result)?"supported":"not-supported")));return result;
  });
  install(loader,"lark-token-cache","ql1.e","c",m->m.getParameterCount()==2,c->{
   Object result=c.proceed();if(uploadPath.get()!=null)observeSafely(()->emit(event("unknown","business-token-cache","com.ss.android.lark").put("result",result==null?"absent":"present")));return result;
  });
  // This installed SDK returns true only after a successful batch-update response.
  install(loader,"lark-token-batch","ql1.a","b",m->m.getParameterCount()==0&&m.getReturnType()==boolean.class,c->{
   batchUpload.set(true);
   try{Object result=c.proceed();observeSafely(()->emit(event("unknown","business-token-batch","com.ss.android.lark")
    .put("result",Boolean.TRUE.equals(result)?"server-accepted":"skipped-or-failed")));return result;}
   finally{batchUpload.remove();}
  });
  install(loader,"lark-native-token","com.bytedance.push.j","registerSenderSuccessAndUploadToken",m->m.getParameterCount()==3,c->{
   Object result=c.proceed();observeSafely(()->{int channel=(int)c.getArg(1);String token=(String)c.getArg(2);emit(event(pushChannel(channel),"registration-token","com.ss.android.lark").put("sdkChannel",channel).put("result",token==null||token.isEmpty()?"empty":"present").put("tokenFingerprint",token==null?"":Events.digest(token)));});return result;
  });
  install(loader,"lark-mi-availability","com.xm.MiPushAdapter","isPushAvailable",m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{Context.class,int.class}),c->{
   Object original=c.proceed();if(Boolean.TRUE.equals(original))return original;
   boolean enabled=false;
   try{JSONObject current=Wire.call(new JSONObject().put("command","hook.config"));enabled=current.optString("mode").equals("manage")&&current.getJSONObject("takeover").optBoolean("registration");}
   catch(java.io.IOException unavailable){android.util.Log.w("QixinHook","Lark adapter cannot read policy");}
   if(!enabled)return original;
   Context context=(Context)c.getArg(0);
   try{if(!context.getPackageManager().getApplicationInfo("com.xiaomi.xmsf",0).enabled)return original;}
   catch(android.content.pm.PackageManager.NameNotFoundException missing){return original;}
   observeSafely(()->emit(event("mipush","registration-availability","com.ss.android.lark").put("result","xmsf-available").put("adapter","lark-miui-gate")));
   return true;
  });
  install(loader,"lark-mi-register","com.xm.MiPushAdapter","registerPush",m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{Context.class,int.class}),c->{
   Object result=c.proceed();observeSafely(()->emit(event("mipush","registration-request","com.ss.android.lark").put("result","native-method-returned")));return result;
  });
  install(loader,"lark-mi-unregister","com.xm.MiPushAdapter","unregisterPush",m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{Context.class,int.class}),c->{
   Object result=c.proceed();observeSafely(()->emit(event("mipush","unregistration-request","com.ss.android.lark").put("result","native-method-returned")));return result;
  });
  install(loader,"lark-mi-callback","com.xiaomi.push.service.receivers.MIPushMessageHandler","onCommandResult",m->m.getParameterCount()==2,c->{
   Object result=c.proceed();observeSafely(()->{
    Object message=c.getArg(1);
    if(message==null||!"register".equals(message.getClass().getMethod("getCommand").invoke(message)))return;
    long code=((Number)message.getClass().getMethod("getResultCode").invoke(message)).longValue();
    emit(event("mipush","registration-callback","com.ss.android.lark").put("result",code));
   });return result;
  });
 }
 @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){
  if(param.getPackageName().equals("com.ss.android.lark")&&param.isFirstPackage()&&Application.getProcessName().equals("com.ss.android.lark")){lark(param.getClassLoader());return;}
  if(!param.getPackageName().equals("com.xiaomi.xmsf")||!param.isFirstPackage())return;
  ClassLoader loader=param.getClassLoader();start(Application.getProcessName());
  install(loader,"connection-service","com.xiaomi.push.service.XMPushService","onCreate",m->m.getParameterCount()==0,c->{Object r=c.proceed();xmsf=c.getThisObject();return r;});
  for(String method:new String[]{"connectionStarted","connectionClosed"})install(loader,method,"com.xiaomi.push.service.XMPushService",method,m->true,c->{Object r=c.proceed();xmsf=c.getThisObject();emit(event("mipush","connection","com.xiaomi.xmsf").put("result",(boolean)xmsf.getClass().getMethod("isConnected").invoke(xmsf)?"authenticated":"disconnected"));return r;});
  install(loader,"activity-intent","android.app.PendingIntent","getActivity",m->m.getParameterCount()==5,c->{
   Intent intent=(Intent)c.getArg(2);Context context=(Context)c.getArg(0);
   if(manage("clicks")&&intent.getData()!=null&&"mipush-action".equals(intent.getScheme())&&context.getPackageManager().resolveActivity(intent,0)==null){
    Intent repaired=new Intent(intent);repaired.setIdentifier(intent.getData().toString());repaired.setData(null);
    if(context.getPackageManager().resolveActivity(repaired,0)!=null){Object[] args=c.getArgs().toArray();args[2]=repaired;emit(event("mipush","click-route",repaired.getComponent()!=null?repaired.getComponent().getPackageName():repaired.getPackage()==null?"com.xiaomi.xmsf":repaired.getPackage()).put("result","synthetic-uri-repaired"));return c.proceed(args);}
   }
   return c.proceed();
  });
 }
}
