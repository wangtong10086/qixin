package io.github.wangtong10086.qixin.core;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class RegistrationPlanTest {
 private JSONObject app(String name,boolean stopped)throws Exception{return new JSONObject().put("package",name).put("label",name).put("enabled",true).put("stopped",stopped).put("pushEvidence",new JSONObject().put("fcm","SDK").put("mipush","SDK"));}
 @Test public void fcmIsManualAndStoppedAppsNeverWake()throws Exception{
  JSONArray rows=RegistrationPlan.create(new JSONArray().put(app("test.one",false)).put(app("test.two",true)),"");
  assertEquals("pending",rows.getJSONObject(0).getString("state"));
  for(int i=1;i<4;i++)assertEquals("manual_required",rows.getJSONObject(i).getString("state"));
 }
 @Test public void exclusionsUnsupportedAppsAndSingleTargetAreRespected()throws Exception{
  JSONArray apps=new JSONArray().put(app("test.one",false).put("pushExcluded",true)).put(app("test.two",false)).put(app("test.none",false).put("pushEvidence",new JSONObject()));
  assertEquals(2,RegistrationPlan.create(apps,"").length());
  assertEquals(0,RegistrationPlan.create(apps,"test.one").length());
  assertEquals(2,RegistrationPlan.create(apps,"test.two").length());
 }
 @Test public void exclusionSettingSurvivesValidation()throws Exception{
  JSONObject config=Policy.initial();config.getJSONObject("apps").put("test.one",new JSONObject().put("pushExcluded",true));Policy.validate(config);
  assertTrue(Policy.app(config,"test.one").getBoolean("pushExcluded"));assertFalse(Policy.app(config,"test.two").getBoolean("pushExcluded"));
 }
}
