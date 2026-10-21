package io.github.wangtong10086.qixin.core;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class NotificationKindTest {
 private JSONObject notification(){return new JSONObject().put("stage","notification").put("result","accepted").put("notificationKey","test");}
 @Test public void vpnStatusIsNotAMessage(){assertEquals("service",NotificationKind.classify(notification().put("ongoing",true).put("foregroundService",true).put("notificationCategory","service")));}
 @Test public void ongoingConversationRemainsMessage(){assertEquals("message",NotificationKind.classify(notification().put("ongoing",true).put("notificationCategory","msg")));}
 @Test public void progressAndNavigationAreTasks(){assertEquals("task",NotificationKind.classify(notification().put("hasProgress",true)));assertEquals("task",NotificationKind.classify(notification().put("notificationCategory","navigation")));}
 @Test public void transientErrorFromVpnIsStillVisible(){assertEquals("message",NotificationKind.classify(notification().put("package","com.v2ray.ang").put("notificationCategory","err")));}
 @Test public void oldUnknownNotificationIsNotGuessed(){assertEquals("legacy",NotificationKind.classify(new JSONObject().put("stage","notification").put("result","accepted")));}
 @Test public void heartbeatIsSnapshot(){assertEquals("status",NotificationKind.classify(new JSONObject().put("stage","hook-ready")));}
}
