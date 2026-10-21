package io.github.wangtong10086.qixin.core;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class EvidenceTest {
 @Test public void repeatedClicksRemainSeparateWhileDeliveryDeduplicates(){
  JSONObject e=new JSONObject().put("package","example.app").put("channel","mipush").put("source","system").put("stage","click-route").put("messageKey","same-payload");
  assertNotEquals(Events.key(e),Events.key(e));
  e.put("stage","dispatch");assertEquals(Events.key(e),Events.key(e));
 }

 @Test public void cachedRegistrationDoesNotProveUploadOrDelivery(){
  JSONObject v=Evidence.summarize(new JSONArray().put(new JSONObject().put("stage","imported-registration-result").put("result","ok").put("time",100)));
  assertEquals("unknown",v.getJSONObject("businessUpload").getString("result"));
  assertEquals("unknown",v.getJSONObject("delivery").getString("result"));
 }
 @Test public void eventTimeWinsOverImportOrder(){
  JSONArray rows=new JSONArray().put(new JSONObject().put("stage","business-token-upload").put("time",200).put("result","server-accepted"))
   .put(new JSONObject().put("stage","business-token-batch").put("time",100).put("result","skipped-or-failed"));
  assertEquals("server-accepted",Evidence.summarize(rows).getJSONObject("businessUpload").getString("result"));
 }
 @Test public void clickDispatchDoesNotProveDestination(){
  JSONObject v=Evidence.summarize(new JSONArray().put(new JSONObject().put("stage","click").put("time",10).put("result","0")));
  assertEquals("unknown",v.getJSONObject("clickRoute").getString("result"));
 }
}
