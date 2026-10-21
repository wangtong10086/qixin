package io.github.wangtong10086.qixin.core;
import org.json.JSONObject;
import java.util.*;
public final class Json {
 public static Set<String> keys(JSONObject value){Set<String> result=new LinkedHashSet<>();Iterator<String> keys=value.keys();while(keys.hasNext())result.add(keys.next());return result;}
}
