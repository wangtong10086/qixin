package io.github.wangtong10086.qixin.core;

import org.json.JSONObject;
import java.util.Set;

/** Classification uses notification semantics, never application names or displayed text. */
public final class NotificationKind {
 public static String classify(JSONObject event) {
  String stage=event.optString("stage");
  if(stage.equals("notification")&&event.optString("result").equals("accepted")){
   if(!event.has("notificationKey"))return "legacy";
   String category=event.optString("notificationCategory"),style=event.optString("notificationStyle");
   if(category.equals("msg")||category.equals("call")||style.endsWith("MessagingStyle"))return "message";
   if(event.optBoolean("hasProgress")||Set.of("navigation","transport","progress").contains(category)||style.endsWith("MediaStyle"))return "task";
   if(event.optBoolean("ongoing")||event.optBoolean("foregroundService")||category.equals("service"))return "service";
   return "message";
  }
  if(Set.of("hook-ready","connection").contains(stage))return "status";
  if(Set.of("imported-send-message","imported-notification").contains(stage))return "message";
  return "diagnostic";
 }
 public static boolean snapshot(String kind){return Set.of("task","service","status").contains(kind);}
}
