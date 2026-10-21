package io.github.wangtong10086.qixin.probe;
import android.content.*;
import android.app.*;
public final class ProbeReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent incoming){
  var manager=context.getSystemService(NotificationManager.class);
  manager.createNotificationChannel(new NotificationChannel("test","本地验证",NotificationManager.IMPORTANCE_DEFAULT));
  Intent target=new Intent(context,DetailActivity.class).putExtra("page","message-detail");
  PendingIntent click=PendingIntent.getActivity(context,1,target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String kind=incoming.getStringExtra("kind");int id=incoming.getIntExtra("id",1);
  if("cancel-all".equals(kind)){manager.cancelAll();return;}
  if("cancel".equals(kind)){manager.cancel(id);return;}
  var notification=new Notification.Builder(context,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("栖信本地测试").setContentText("仅验证本地投递和目标页面，不代表云推送成功").setContentIntent(click).setAutoCancel(true);
  if("service".equals(kind))notification.setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).setContentText("连接状态 "+incoming.getIntExtra("sequence",0));
  if("progress".equals(kind))notification.setOngoing(true).setProgress(100,incoming.getIntExtra("sequence",40),false);
  manager.notify(id,notification.build());
 }
}
