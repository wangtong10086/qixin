package io.github.wangtong10086.qixin.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.json.*;

/** A whitelist prevents raw Bundles, token material and deep-link parameters entering history. */
public final class Events {
    private static final Set<String> FIELDS=Set.of("package","channel","stage","source","messageKey","component","action","result","reason","priority","originalPriority","ttl","senderFingerprint","tokenFingerprint","surface","adapter","version","scope","sourceId","sampledAt","receivedAt","sentAt","queueDrops","hooks","clickMarker","sdkChannel","notificationKey","notificationChannel","notificationCategory","notificationStyle","ongoing","foregroundService","hasProgress","userId","kind");
    public static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static JSONObject sanitize(JSONObject event,JSONObject config,long now){
        String pkg=event.optString("package");if(!pkg.isEmpty())Policy.packageName(pkg);
        Policy.oneOf(event.getString("channel"),"fcm","mipush","system","unknown");
        JSONObject out=new JSONObject().put("time",now);
        for(String key:FIELDS)if(event.has(key)){
            Object value=event.get(key);
            if(value instanceof String s)out.put(key,s.substring(0,Math.min(s.length(),2048)));
            else if(value instanceof Number || value instanceof Boolean)out.put(key,value);
        }
        out.put("package",pkg);
        if(!pkg.isEmpty()&&Policy.app(config,pkg).getBoolean("recordBody")){
            for(String key: new String[]{"title","body"})if(event.has(key)){String value=event.getString(key);out.put(key,value.substring(0,Math.min(value.length(),8192)));}
        }
        if(!out.has("stage"))throw new IllegalArgumentException("Missing stage");
        return out;
    }
    public static String key(JSONObject event){
        if(event.optString("stage").equals("notification")&&!event.optString("messageKey").isEmpty())return "notification:"+event.getString("messageKey");
        if(event.has("sourceId"))return event.getString("source")+":"+event.get("sourceId");
        if(Set.of("click-request","click-route","dispatch-result").contains(event.optString("stage")))return java.util.UUID.randomUUID().toString();
        if(!event.optString("messageKey").isEmpty())return event.optString("package")+":"+event.getString("channel")+":"+event.getString("stage")+":"+event.optString("source")+":"+event.getString("messageKey");
        return java.util.UUID.randomUUID().toString();
    }
}
