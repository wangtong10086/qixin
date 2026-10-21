import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.media.AudioAttributes;
import android.net.Uri;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Restore the known XMSF notification channel snapshot using Android's root API. */
public final class RestoreNotificationChannels {
 public static void main(String[] args)throws Exception {
  if(android.system.Os.getuid()!=0)throw new SecurityException("Root required");
  JSONObject snapshot=new JSONObject(Files.readString(Path.of(args[0])));int uid=Integer.parseInt(args[1]);
  Class<?> api=Class.forName("android.app.INotificationManager"),slice=Class.forName("android.content.pm.ParceledListSlice");
  Object manager=Class.forName("android.app.NotificationManager").getMethod("getService").invoke(null);
  String pkg="com.xiaomi.xmsf";
  List<NotificationChannelGroup> groups=new ArrayList<>();
  for(int i=0;i<snapshot.getJSONArray("groups").length();i++){
   JSONObject group=snapshot.getJSONArray("groups").getJSONObject(i);
   groups.add(new NotificationChannelGroup(group.getString("id"),group.getString("name")));
  }
  for(NotificationChannelGroup group:groups)
   api.getMethod("updateNotificationChannelGroupForPackage",String.class,int.class,NotificationChannelGroup.class).invoke(manager,pkg,uid,group);
  for(int i=0;i<snapshot.getJSONArray("channels").length();i++){
   JSONObject row=snapshot.getJSONArray("channels").getJSONObject(i);
   NotificationChannel channel=new NotificationChannel(row.getString("id"),row.getString("name"),row.getInt("importance"));
   channel.setGroup(row.getString("group"));channel.setShowBadge(row.getBoolean("badge"));
   channel.enableVibration(row.getBoolean("vibrate"));channel.enableLights(row.getBoolean("lights"));
   channel.setLightColor(row.getInt("lightColor"));channel.setLockscreenVisibility(row.getInt("visibility"));
   channel.setBypassDnd(row.getBoolean("bypassDnd"));
   channel.setSound(Uri.parse(row.getString("sound")),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
   api.getMethod("createNotificationChannelsForPackage",String.class,int.class,slice).invoke(manager,pkg,uid,slice.getConstructor(List.class).newInstance(List.of(channel)));
   api.getMethod("updateNotificationChannelForPackage",String.class,int.class,NotificationChannel.class).invoke(manager,pkg,uid,channel);
  }
  System.out.println("Restored XMSF channel settings: "+snapshot.getJSONArray("channels").length());
 }
}
