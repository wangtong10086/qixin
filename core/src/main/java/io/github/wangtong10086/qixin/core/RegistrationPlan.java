package io.github.wangtong10086.qixin.core;
import org.json.*;
/** Discovery is evidence of SDK support, never proof of a server subscription. */
public final class RegistrationPlan {
 public static JSONArray create(JSONArray apps,String onlyPackage)throws JSONException{
  JSONArray rows=new JSONArray();
  for(int i=0;i<apps.length();i++){
   JSONObject app=apps.getJSONObject(i);String name=app.getString("package");
   if(app.optBoolean("pushExcluded")||(!onlyPackage.isEmpty()&&!onlyPackage.equals(name)))continue;
   JSONObject evidence=app.getJSONObject("pushEvidence");
   for(String channel:new String[]{"mipush","fcm"})if(evidence.has(channel)){
    boolean background=channel.equals("mipush")&&app.getBoolean("enabled")&&!app.getBoolean("stopped");
    String reason=!app.getBoolean("enabled")?"应用已停用":app.getBoolean("stopped")?"应用已停止，需手动打开":channel.equals("fcm")?"由应用自身 SDK 注册，请手动打开":"等待发送后台请求";
    rows.put(new JSONObject().put("package",name).put("label",app.getString("label")).put("channel",channel).put("state",background?"pending":"manual_required").put("reason",reason));
   }
  }
  return rows;
 }
}
