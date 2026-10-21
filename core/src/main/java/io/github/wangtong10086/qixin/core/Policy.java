package io.github.wangtong10086.qixin.core;

import org.json.*;
import java.util.Set;

public final class Policy {
    public static JSONObject initial(){return new JSONObject().put("schema",1).put("revision",0).put("mode","observe")
        .put("recovery","soft").put("historyDays",7).put("apps",new JSONObject()).put("takeover",new JSONObject());}
    public static void oneOf(String value,String... allowed){for(String s:allowed)if(s.equals(value))return;throw new IllegalArgumentException("Unsupported value: "+value);}
    public static void packageName(String name){if(!name.equals("android")&&!name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Invalid package name");}
    public static JSONObject app(JSONObject policy,String name){
        JSONObject result=new JSONObject().put("recordBody",false).put("deliveryLease",true).put("proxy",false).put("sound",false).put("vibrate",false).put("island",true);
        JSONObject specific=policy.getJSONObject("apps").optJSONObject(name);
        if(specific!=null)for(String key:Json.keys(specific))result.put(key,specific.get(key));return result;
    }
    public static JSONObject island(JSONObject policy){return policy.optJSONObject("island")!=null?policy.getJSONObject("island"):new JSONObject().put("enabled",false).put("timeoutSeconds",5);}
    public static void validate(JSONObject policy){
        if(policy.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported schema");
        oneOf(policy.getString("mode"),"observe","manage");oneOf(policy.getString("recovery"),"soft","manual");
        if(policy.getInt("historyDays")!=7)throw new IllegalArgumentException("This version retains seven days");
        for(String name:Json.keys(policy.getJSONObject("apps"))){
            packageName(name);JSONObject app=policy.getJSONObject("apps").getJSONObject(name);
            for(String field:Json.keys(app)){
                if(!Set.of("recordBody","deliveryLease","proxy","sound","vibrate","island").contains(field))throw new IllegalArgumentException("Unknown app setting: "+field);
                if(!(app.get(field) instanceof Boolean))throw new IllegalArgumentException("Expected boolean: "+field);
            }
        }
        for(String key:Json.keys(policy.getJSONObject("takeover"))){
            oneOf(key,"connection","notifications","registration","clicks","delivery");
            if(!(policy.getJSONObject("takeover").get(key) instanceof Boolean))throw new IllegalArgumentException("Expected takeover boolean");
        }
        JSONObject island=island(policy);if(!(island.get("enabled") instanceof Boolean))throw new IllegalArgumentException("Expected island enabled boolean");
        int timeout=island.getInt("timeoutSeconds");if(timeout<1||timeout>30)throw new IllegalArgumentException("Island timeout must be 1..30 seconds");
        for(String key:Json.keys(island))if(!Set.of("enabled","timeoutSeconds").contains(key))throw new IllegalArgumentException("Unknown island setting: "+key);
        for(String key:Json.keys(policy))if(!Set.of("schema","revision","mode","recovery","historyDays","apps","takeover","island").contains(key))throw new IllegalArgumentException("Unknown setting: "+key);
    }
    public static JSONObject replace(JSONObject before,JSONObject proposed,long expected){
        if(before.getLong("revision")!=expected)throw new IllegalArgumentException("配置已更新，请重新加载");
        JSONObject next=new JSONObject(proposed.toString());validate(next);return next.put("revision",expected+1);
    }
}
