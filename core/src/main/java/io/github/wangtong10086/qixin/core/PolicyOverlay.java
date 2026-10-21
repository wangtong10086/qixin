package io.github.wangtong10086.qixin.core;
import org.json.JSONObject;
/** Restore our overrides while retaining edits made after enabling the feature. */
public final class PolicyOverlay {
 public static JSONObject restore(JSONObject current,JSONObject previous,JSONObject applied){
  JSONObject restored=new JSONObject(current.toString());
  for(String key:Json.keys(applied))if(current.has(key)&&current.get(key).equals(applied.get(key))){
   if(previous.has(key))restored.put(key,previous.get(key));else restored.remove(key);
  }
  return restored;
 }
}
