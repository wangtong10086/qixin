package io.github.wangtong10086.qixin.island;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.service.notification.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import io.github.wangtong10086.qixin.core.*;
import io.github.wangtong10086.qixin.service.Wire;

/** Live notification rendering only. Content and PendingIntents never enter persistent storage. */
public final class IslandListener extends NotificationListenerService {
 private static final int FLAG_AUTOGROUP_SUMMARY=0x400;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final LinkedHashMap<String,StatusBarNotification> tasks=new LinkedHashMap<>();
 private JSONObject config=new JSONObject();
 private WindowManager windows;private LinearLayout view;private StatusBarNotification message;
 private boolean connected,expanded;private ScheduledFuture<?> refresh;
 private final Runnable expire=()->{message=null;expanded=false;render();};
 private final android.content.BroadcastReceiver screen=new android.content.BroadcastReceiver(){public void onReceive(Context c,Intent i){render();}};
 @Override public void onCreate(){super.onCreate();windows=getSystemService(WindowManager.class);IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);}
 @Override public void onListenerConnected(){connected=true;JSONArray active=new JSONArray();for(StatusBarNotification n:getActiveNotifications()){active.put(key(n));update(n,false);}worker.execute(()->{try{Wire.call(new JSONObject().put("command","notifications.reconcile").put("activeKeys",active));}catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinIsland","Snapshot reconciliation failed",failure);}});refresh=worker.scheduleWithFixedDelay(this::loadConfig,0,30,TimeUnit.SECONDS);}
 @Override public void onListenerDisconnected(){connected=false;if(refresh!=null)refresh.cancel(false);tasks.clear();message=null;hide();}
 private void loadConfig(){try{JSONObject value=Wire.call(new JSONObject().put("command","config.get"));main.post(()->{config=value;render();});}catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinIsland","Configuration unavailable",failure);main.post(this::hide);}}
 @Override public void onNotificationPosted(StatusBarNotification notification,RankingMap ranks){update(notification,true);}
 private JSONObject metadata(StatusBarNotification s)throws JSONException{
  Notification n=s.getNotification();return new JSONObject().put("package",s.getPackageName()).put("channel","unknown").put("time",s.getPostTime()).put("userId",s.getUser().hashCode()).put("notificationChannel",n.getChannelId()).put("title",n.extras.getCharSequence(Notification.EXTRA_TITLE,"").toString()).put("body",n.extras.getCharSequence(Notification.EXTRA_TEXT,"").toString()).put("messageKey",Events.digest(key(s)+":"+n.when+":"+n.extras.getCharSequence(Notification.EXTRA_TITLE,"")+":"+n.extras.getCharSequence(Notification.EXTRA_TEXT,""))).put("stage","notification").put("result","accepted").put("notificationKey",key(s)).put("notificationCategory",n.category==null?"":n.category).put("notificationStyle",n.extras.getString("android.template","")).put("ongoing",s.isOngoing()).put("foregroundService",(n.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0).put("hasProgress",n.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0)>0||n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false));
 }
 private String key(StatusBarNotification s){return Events.digest(s.getUser().hashCode()+":"+s.getPackageName()+":"+s.getId()+":"+s.getTag());}
 private void update(StatusBarNotification s,boolean fresh){
  // Android synthesizes an aggregate notification; its children are recorded individually.
  if((s.getNotification().flags&FLAG_AUTOGROUP_SUMMARY)!=0){onNotificationRemoved(s);return;}
  try{
   JSONObject event=metadata(s);String kind=NotificationKind.classify(event);
   worker.execute(()->{try{Wire.call(new JSONObject().put("command","notifications.observed").put("event",event));}catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinIsland","Notification not recorded",failure);}});
   if(kind.equals("task"))tasks.put(s.getKey(),s);else tasks.remove(s.getKey());
   Ranking rank=new Ranking();boolean mayAlert=getCurrentRanking().getRanking(s.getKey(),rank)&&rank.matchesInterruptionFilter()&&rank.getImportance()>=NotificationManager.IMPORTANCE_DEFAULT;
   if(fresh&&kind.equals("message")&&mayAlert&&enabled(s)){
    message=s;expanded=false;main.removeCallbacks(expire);main.postDelayed(expire,Policy.island(config).getInt("timeoutSeconds")*1000L);
   }
   render();
  }catch(JSONException failure){throw new IllegalStateException(failure);}
 }
 @Override public void onNotificationRemoved(StatusBarNotification s){tasks.remove(s.getKey());if(message!=null&&message.getKey().equals(s.getKey())){message=null;main.removeCallbacks(expire);}render();worker.execute(()->{try{Wire.call(new JSONObject().put("command","notifications.removed").put("notificationKey",key(s)));}catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinIsland","Removal not recorded",failure);}});}
 private boolean enabled(StatusBarNotification n){try{return config.has("apps")&&Policy.app(config,n.getPackageName()).getBoolean("island");}catch(JSONException failure){throw new IllegalStateException(failure);}}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setMaxLines(expanded?4:1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
 private void render(){
  try{
   if(!connected||!Policy.island(config).getBoolean("enabled")||!Settings.canDrawOverlays(this)||getSystemService(KeyguardManager.class).isKeyguardLocked()||!getSystemService(PowerManager.class).isInteractive()){hide();return;}
   StatusBarNotification n=message!=null&&enabled(message)?message:null;
   if(n==null)for(StatusBarNotification task:tasks.values())if(enabled(task)){n=task;break;}
   if(n==null){hide();return;}
   hide();final StatusBarNotification selected=n;Notification notice=n.getNotification();
   view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(16),dp(10),dp(16),dp(10));
   GradientDrawable background=new GradientDrawable();background.setColor(0xFF211F26);background.setCornerRadius(dp(24));view.setBackground(background);view.setElevation(dp(4));
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
   ImageView icon=new ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(n.getPackageName()));}catch(android.content.pm.PackageManager.NameNotFoundException removed){view=null;return;}
   row.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));
   CharSequence title=notice.extras.getCharSequence(Notification.EXTRA_TITLE,"");TextView label=text(title.toString(),14);label.setPadding(dp(10),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,dp(36),1));view.addView(row);
   row.setOnClickListener(v->{expanded=!expanded;render();});
   int progressMax=notice.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0);
   boolean indeterminate=notice.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false);
   if(progressMax>0||indeterminate){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(indeterminate);if(progressMax>0){progress.setMax(progressMax);progress.setProgress(notice.extras.getInt(Notification.EXTRA_PROGRESS,0));}progress.setContentDescription(indeterminate?"正在进行":notice.extras.getInt(Notification.EXTRA_PROGRESS,0)+" / "+progressMax);view.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));}
   if(expanded){
    TextView body=text(notice.extras.getCharSequence(Notification.EXTRA_TEXT,"").toString(),14);body.setMinHeight(dp(48));body.setOnClickListener(v->send(selected,notice.contentIntent));view.addView(body);
    if(notice.contentIntent!=null){Button open=new Button(this);open.setText("打开");open.setOnClickListener(v->send(selected,notice.contentIntent));view.addView(open);}
    if(notice.actions!=null)for(Notification.Action action:notice.actions){Button button=new Button(this);button.setText(action.title+(action.getRemoteInputs()!=null?"（通知栏）":""));button.setOnClickListener(v->{if(action.getRemoteInputs()!=null){hide();worker.execute(()->{try{Wire.call(new JSONObject().put("command","notifications.expand"));}catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinIsland","Cannot open notification shade",failure);}});}else send(selected,action.actionIntent);});view.addView(button);}
    TextView dismiss=text("收起",12);dismiss.setMinHeight(dp(48));dismiss.setOnClickListener(v->{expanded=false;render();});view.addView(dismiss);
   }
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(expanded?340:260),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
   p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(4);p.setTitle("栖信通知岛");windows.addView(view,p);
  }catch(JSONException failure){throw new IllegalStateException(failure);}
 }
 private void send(StatusBarNotification notification,PendingIntent action){
  if(action==null)return;
  try{ActivityOptions options=ActivityOptions.makeBasic();options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);action.send(this,0,null,null,null,null,options.toBundle());message=null;expanded=false;render();}
  catch(PendingIntent.CanceledException expired){Toast.makeText(this,"通知操作已失效，请从通知栏查看",Toast.LENGTH_SHORT).show();}
 }
 private void hide(){if(view!=null){windows.removeView(view);view=null;}}
 @Override public void onDestroy(){if(refresh!=null)refresh.cancel(false);worker.shutdownNow();main.removeCallbacks(expire);unregisterReceiver(screen);hide();super.onDestroy();}
}
