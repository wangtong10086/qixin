package io.github.wangtong10086.qixin.service;
import android.content.Context;
import android.content.pm.*;
import android.database.sqlite.SQLiteDatabase;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import io.github.wangtong10086.qixin.core.Events;
import io.github.wangtong10086.qixin.core.MiPushRecord;
public final class Inventory {
 public static final String MANAGER="io.github.wangtong10086.qixin",GMS="com.google.android.gms",XMSF="com.xiaomi.xmsf";
 final RuntimeClient runtime;
 final Context context;final PackageManager pm;private volatile JSONArray apps=new JSONArray();private volatile String importError="";
 public Inventory(Context context){this.context=context;pm=context.getPackageManager();runtime=new RuntimeClient(context);}
 public String importError(){return importError;}
 public boolean ownsUid(String pkg,int uid){try{return uid==uid(pkg);}catch(PackageManager.NameNotFoundException absent){return false;}}
 public int uid(String pkg)throws PackageManager.NameNotFoundException{return pm.getApplicationInfo(pkg,0).uid;}
 public JSONArray apps()throws JSONException{return new JSONArray(apps.toString());}
 public void refresh(History history,Store store)throws Exception{
  JSONArray result=new JSONArray();java.util.List<String> failures=new java.util.ArrayList<>();long now=System.currentTimeMillis();
  for(ApplicationInfo app:pm.getInstalledApplications(0)){
   if(app.uid<10000)continue;
   result.put(new JSONObject().put("package",app.packageName).put("label",pm.getApplicationLabel(app).toString()).put("uid",app.uid).put("enabled",app.enabled).put("stopped",(app.flags&ApplicationInfo.FLAG_STOPPED)!=0));
   Path path=Path.of("/data/user/0",app.packageName,"shared_prefs/com.google.android.gms.appid.xml");
   if(!Files.isRegularFile(path))continue;
   try(var input=Files.newInputStream(path)){
    XmlPullParser parser=Xml.newPullParser();parser.setInput(input,"UTF-8");
    for(int type=parser.next();type!=XmlPullParser.END_DOCUMENT;type=parser.next())if(type==XmlPullParser.START_TAG&&parser.getName().equals("string")){
     String key=parser.getAttributeValue(null,"name");String value=parser.nextText();
     if(key==null||!key.contains("|T|")||value.isEmpty())continue;
     String token=value;long timestamp=0;
     if(value.startsWith("{")){JSONObject cached=new JSONObject(value);token=cached.optString("token");timestamp=cached.optLong("timestamp");}
     if(token.isEmpty())continue;
     history.registration(new JSONObject().put("package",app.packageName).put("channel","fcm").put("state","cached-registration").put("source","sdk-cache").put("senderFingerprint",Events.digest(key)).put("tokenFingerprint",Events.digest(token)).put("observedAt",now).put("cacheTime",timestamp).put("businessUpload","unknown"));
    }
   }catch(org.xmlpull.v1.XmlPullParserException|JSONException invalid){failures.add(app.packageName+": invalid registration cache ("+invalid.getClass().getSimpleName()+")");}catch(java.nio.file.NoSuchFileException removed){/* App data was removed during discovery. */}
  }
  apps=result;
  if(Files.exists(store.directory.resolve("runtime-migrated"))){
   runtime.importHistory(history,store.get());importError=String.join("; ",failures);return;
  }
  Path db=Path.of("/data/user/0/com.xiaomi.xmsf/databases/db");
  if(Files.exists(db))try(SQLiteDatabase source=SQLiteDatabase.openDatabase(db.toString(),null,SQLiteDatabase.OPEN_READONLY)){
   try(var c=source.rawQuery("SELECT pkg,registered_type,blocked,island_enabled,island_focus_notification FROM REGISTERED_APPLICATION",null)){
    while(c.moveToNext())history.registration(new JSONObject().put("package",c.getString(0)).put("channel","mipush").put("state",MiPushRecord.registered(c.getInt(1))).put("registeredType",c.getInt(1)).put("blocked",c.getInt(2)!=0).put("islandEnabled",c.getInt(3)!=0).put("focusEnabled",c.getInt(4)!=0).put("source","xmsf-cache").put("observedAt",now).put("businessUpload","unknown"));
   }
   try(var c=source.rawQuery("SELECT id,pkg,type,date,result FROM EVENT WHERE date>=? ORDER BY id DESC LIMIT 1000",new String[]{Long.toString(now-7L*86400000)})){
    JSONObject config=store.get();while(c.moveToNext())history.append(new JSONObject().put("package",c.getString(1)).put("channel","mipush").put("stage",MiPushRecord.stage(c.getInt(2))).put("source","xmsf-cache").put("sourceId",c.getLong(0)).put("time",c.getLong(3)).put("result",MiPushRecord.result(c.getInt(4))),config);
   }

  }catch(android.database.sqlite.SQLiteException incompatible){failures.add("XMSF import: "+incompatible.getMessage());}
  importError=String.join("; ",failures);
 }
 public boolean manager(int uid,String signer)throws Exception{
  if(uid!=uid(MANAGER))return false;
  PackageInfo p=pm.getPackageInfo(MANAGER,PackageManager.GET_SIGNING_CERTIFICATES);
  return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(p.signingInfo.getApkContentsSigners()[0].toByteArray())).equals(signer);
 }
}
