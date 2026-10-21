package io.github.wangtong10086.qixin.core;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class PolicyTest {
 @Test public void acceptsSystemNotificationPackage(){JSONObject event=new JSONObject().put("package","android").put("channel","system").put("stage","notification");assertEquals("android",Events.sanitize(event,Policy.initial(),1).getString("package"));}
 @Test public void defaultsDoNotCaptureBodyOrTakeOver(){JSONObject p=Policy.initial();Policy.validate(p);assertEquals("observe",p.getString("mode"));assertFalse(Policy.app(p,"com.example.app").getBoolean("recordBody"));assertEquals(0,p.getJSONObject("takeover").length());}
 @Test public void staleWriteFails(){assertThrows(IllegalArgumentException.class,()->Policy.replace(Policy.initial(),Policy.initial(),1));}
 @Test public void unknownFlagsAndNonBooleanAreRejected(){JSONObject p=Policy.initial();p.getJSONObject("apps").put("com.example.app",new JSONObject().put("recordBody","true"));assertThrows(IllegalArgumentException.class,()->Policy.validate(p));}
 @Test public void stripsPrivateExtras(){JSONObject e=new JSONObject().put("package","com.example.app").put("channel","fcm").put("stage","received").put("token","secret").put("uri","secret").put("body","private");JSONObject clean=Events.sanitize(e,Policy.initial(),123);assertFalse(clean.has("token"));assertFalse(clean.has("uri"));assertFalse(clean.has("body"));assertEquals(123,clean.getLong("time"));}
 @Test public void bodyIsOptInPerApp(){JSONObject p=Policy.initial();p.getJSONObject("apps").put("com.example.app",new JSONObject().put("recordBody",true));JSONObject e=new JSONObject().put("package","com.example.app").put("channel","fcm").put("stage","received").put("body","text");assertEquals("text",Events.sanitize(e,p,1).getString("body"));}
 @Test public void dedupDoesNotMergeDifferentChannelsOrApps(){JSONObject a=new JSONObject().put("package","com.a.app").put("channel","fcm").put("stage","receive").put("source","system").put("messageKey","id");JSONObject b=new JSONObject(a.toString()).put("channel","mipush");assertNotEquals(Events.key(a),Events.key(b));b=new JSONObject(a.toString()).put("package","com.b.app");assertNotEquals(Events.key(a),Events.key(b));}
 @Test public void recoverySurvivesRestartAndClockRollback(){RecoveryWindow old=new RecoveryWindow();old.attempted(100000);RecoveryWindow restored=new RecoveryWindow();restored.restore(old.snapshot(),110000);assertFalse(restored.eligible(120000));assertTrue(restored.eligible(130000));restored.restore(old.snapshot(),1000);assertFalse(restored.eligible(1000));}
 @Test public void recoveryIsBounded(){RecoveryWindow r=new RecoveryWindow();assertTrue(r.eligible(0));r.attempted(0);assertFalse(r.eligible(29999));r.attempted(30000);r.attempted(150000);assertFalse(r.eligible(500000));assertTrue(r.eligible(1800000));}
}
