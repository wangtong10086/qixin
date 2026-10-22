package io.github.wangtong10086.qixin.island;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.github.wangtong10086.qixin.core.Events;
import io.github.wangtong10086.qixin.service.Wire;

/** Records native notification state without replacing, cancelling or re-posting notifications.
 * The component name is retained so existing notification-access grants survive upgrades. */
public final class IslandListener extends NotificationListenerService {
 private static final int FLAG_AUTOGROUP_SUMMARY=0x400;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();

 @Override public void onListenerConnected(){
  JSONArray active=new JSONArray();
  for(StatusBarNotification notification:getActiveNotifications()){
   if((notification.getNotification().flags&FLAG_AUTOGROUP_SUMMARY)!=0)continue;
   active.put(key(notification));update(notification);
  }
  worker.execute(()->{
   try{Wire.call(new JSONObject().put("command","notifications.reconcile").put("activeKeys",active));}
   catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinNotifications","Snapshot reconciliation failed",failure);}
  });
 }
 @Override public void onNotificationPosted(StatusBarNotification notification,RankingMap ranks){update(notification);}
 private JSONObject metadata(StatusBarNotification s)throws JSONException{
  Notification n=s.getNotification();return new JSONObject().put("package",s.getPackageName()).put("channel","unknown").put("time",s.getPostTime()).put("userId",s.getUser().hashCode()).put("notificationChannel",n.getChannelId()).put("title",n.extras.getCharSequence(Notification.EXTRA_TITLE,"").toString()).put("body",n.extras.getCharSequence(Notification.EXTRA_TEXT,"").toString()).put("messageKey",Events.digest(key(s)+":"+n.when+":"+n.extras.getCharSequence(Notification.EXTRA_TITLE,"")+":"+n.extras.getCharSequence(Notification.EXTRA_TEXT,""))).put("stage","notification").put("result","accepted").put("notificationKey",key(s)).put("notificationCategory",n.category==null?"":n.category).put("notificationStyle",n.extras.getString("android.template","")).put("ongoing",s.isOngoing()).put("foregroundService",(n.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0).put("hasProgress",n.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0)>0||n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false));
 }
 private String key(StatusBarNotification s){return Events.digest(s.getUser().hashCode()+":"+s.getPackageName()+":"+s.getId()+":"+s.getTag());}

 private void update(StatusBarNotification notification){
  // Synthetic group summaries must not duplicate their child messages in history.
  if((notification.getNotification().flags&FLAG_AUTOGROUP_SUMMARY)!=0)return;
  try{
   JSONObject event=metadata(notification);
   worker.execute(()->{
    try{Wire.call(new JSONObject().put("command","notifications.observed").put("event",event));}
    catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinNotifications","Notification not recorded",failure);}
   });
  }catch(JSONException failure){throw new IllegalStateException(failure);}
 }
 @Override public void onNotificationRemoved(StatusBarNotification notification){
  String notificationKey=key(notification);
  worker.execute(()->{
   try{Wire.call(new JSONObject().put("command","notifications.removed").put("notificationKey",notificationKey));}
   catch(java.io.IOException|JSONException failure){android.util.Log.w("QixinNotifications","Removal not recorded",failure);}
  });
 }
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
