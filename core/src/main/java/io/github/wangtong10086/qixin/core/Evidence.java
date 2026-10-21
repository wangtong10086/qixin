package io.github.wangtong10086.qixin.core;
import org.json.*;
import java.util.Map;

/** Independent stages: a connected transport or cached token never proves delivery. */
public final class Evidence {
 public static JSONObject summarize(JSONArray events){
  Map<String,String> stages=Map.of("registration-callback","registration","business-token-upload","businessUpload",
   "business-token-batch","businessUpload","dispatch-result","delivery","notification","notification",
   "click-route","clickRoute","click","click");
  JSONObject result=new JSONObject();
  for(String key:stages.values())result.put(key,new JSONObject().put("result","unknown"));
  for(int i=0;i<events.length();i++){
   JSONObject e=events.getJSONObject(i);String key=stages.get(e.optString("stage"));if(key==null)continue;
   JSONObject old=result.getJSONObject(key);
   if(e.optLong("time")>=old.optLong("time"))result.put(key,new JSONObject(e.toString()));
  }
  return result;
 }
}
