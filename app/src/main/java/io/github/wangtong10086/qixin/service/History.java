package io.github.wangtong10086.qixin.service;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.nio.file.Path;
import org.json.*;
import io.github.wangtong10086.qixin.core.Events;
import io.github.wangtong10086.qixin.core.NotificationKind;
public final class History implements AutoCloseable {
 private final SQLiteDatabase db;
 public History(Path path){db=SQLiteDatabase.openOrCreateDatabase(path.resolve("history.db").toFile(),null);
  db.execSQL("CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT UNIQUE NOT NULL,time INTEGER NOT NULL,package TEXT NOT NULL,channel TEXT NOT NULL,stage TEXT NOT NULL,payload TEXT NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS events_lookup ON events(package,channel,time)");
  db.execSQL("CREATE TABLE IF NOT EXISTS registrations(identity TEXT PRIMARY KEY,package TEXT NOT NULL,channel TEXT NOT NULL,payload TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS states(identity TEXT PRIMARY KEY,kind TEXT NOT NULL,package TEXT NOT NULL,time INTEGER NOT NULL,payload TEXT NOT NULL)");
  try(var columns=db.rawQuery("SELECT * FROM events LIMIT 0",null)){
   if(columns.getColumnIndex("kind")==-1){
    db.beginTransaction();try{
     db.execSQL("ALTER TABLE events ADD COLUMN kind TEXT NOT NULL DEFAULT 'legacy'");
     db.execSQL("UPDATE events SET kind='message' WHERE stage IN ('imported-send-message','imported-notification')");
     db.execSQL("UPDATE events SET kind='diagnostic' WHERE kind='legacy' AND stage!='notification'");
     db.setTransactionSuccessful();
    }finally{db.endTransaction();}
   }
  }
  db.execSQL("CREATE INDEX IF NOT EXISTS events_kind ON events(kind,time,id)");
  db.setMaximumSize(128L*1024*1024);
 }
 public synchronized void append(JSONObject input,JSONObject config)throws Exception{
  JSONObject e=Events.sanitize(input,config,input.optLong("time",System.currentTimeMillis()));
  String kind=NotificationKind.classify(e);e.put("kind",kind);
  if(NotificationKind.snapshot(kind)){
   String identity=e.optString("notificationKey",e.optString("source")+":"+e.optString("scope")+":"+e.optString("channel")+":"+e.getString("stage"));
   ContentValues state=new ContentValues();state.put("identity",identity);state.put("kind",kind);state.put("package",e.getString("package"));state.put("time",e.getLong("time"));state.put("payload",e.toString());
   db.insertWithOnConflict("states",null,state,SQLiteDatabase.CONFLICT_REPLACE);return;
  }
  if(e.has("notificationKey"))removeState(e.getString("notificationKey"));
  ContentValues v=new ContentValues();
  v.put("event_key",Events.key(e));v.put("time",e.getLong("time"));v.put("package",e.getString("package"));v.put("channel",e.getString("channel"));v.put("stage",e.getString("stage"));v.put("payload",e.toString());try(var statement=db.compileStatement("INSERT OR IGNORE INTO events(event_key,time,package,channel,stage,payload,kind) VALUES(?,?,?,?,?,?,?)")){statement.bindString(1,v.getAsString("event_key"));statement.bindLong(2,e.getLong("time"));statement.bindString(3,e.getString("package"));statement.bindString(4,e.getString("channel"));statement.bindString(5,e.getString("stage"));statement.bindString(6,e.toString());statement.bindString(7,kind);statement.executeInsert();}
 }
 public synchronized void registration(JSONObject value)throws JSONException{
  ContentValues v=new ContentValues();String id=value.getString("package")+":"+value.getString("channel")+":"+value.optString("senderFingerprint");
  v.put("identity",id);v.put("package",value.getString("package"));v.put("channel",value.getString("channel"));v.put("payload",value.toString());db.insertWithOnConflict("registrations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
 }
 public synchronized JSONArray registrations(String pkg)throws JSONException{
  JSONArray result=new JSONArray();try(var c=db.rawQuery("SELECT payload FROM registrations"+(pkg.isEmpty()?"":" WHERE package=?")+" ORDER BY package,channel",pkg.isEmpty()?null:new String[]{pkg})){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
 }
 public synchronized JSONObject events(JSONObject query)throws JSONException{
  int limit=query.optInt("limit",100);if(limit<1||limit>500)throw new IllegalArgumentException("limit must be 1..500");
  String where="time>=? AND id<? AND id>?";java.util.ArrayList<String> args=new java.util.ArrayList<>();args.add(Long.toString(query.optLong("since",System.currentTimeMillis()-7L*86400000)));args.add(Long.toString(query.optString("order","id").equals("time")?Long.MAX_VALUE:query.optLong("before",Long.MAX_VALUE)));args.add(Long.toString(query.optLong("after",0)));
  for(String field:new String[]{"package","channel","stage","kind"})if(!query.optString(field).isEmpty()){where+=" AND "+field+"=?";args.add(query.getString(field));}
  boolean byTime=query.optString("order","id").equals("time");
  io.github.wangtong10086.qixin.core.Policy.oneOf(query.optString("order","id"),"id","time");
  if(byTime&&query.has("beforeTime")){
   where+=" AND (time<? OR (time=? AND id<?))";
   args.add(Long.toString(query.getLong("beforeTime")));args.add(Long.toString(query.getLong("beforeTime")));args.add(Long.toString(query.getLong("before")));
  }
  JSONArray rows=new JSONArray();long before=0,beforeTime=0;
  try(var c=db.rawQuery("SELECT id,payload,kind FROM events WHERE "+where+" ORDER BY "+(byTime?"time DESC,id DESC":"id "+(query.optBoolean("ascending")?"ASC":"DESC"))+" LIMIT "+limit,args.toArray(new String[0]))){while(c.moveToNext()){before=c.getLong(0);JSONObject row=new JSONObject(c.getString(1)).put("id",before).put("kind",c.getString(2));beforeTime=row.getLong("time");rows.put(row);}}
  return new JSONObject().put("events",rows).put("nextBefore",before).put("nextBeforeTime",beforeTime);
 }
 public synchronized void reconcileNotifications(JSONArray keys)throws JSONException{
  java.util.Set<String> active=new java.util.HashSet<>();for(int i=0;i<keys.length();i++)active.add(keys.getString(i));
  java.util.List<String> removed=new java.util.ArrayList<>();
  try(var c=db.rawQuery("SELECT identity FROM states WHERE kind IN ('service','task')",null)){while(c.moveToNext())if(!active.contains(c.getString(0)))removed.add(c.getString(0));}
  db.beginTransaction();try{for(String key:removed)removeState(key);db.setTransactionSuccessful();}finally{db.endTransaction();}
 }
 public synchronized void removeState(String key){db.delete("states","identity=?",new String[]{key});}
 public synchronized JSONArray states(String pkg)throws JSONException{
  JSONArray out=new JSONArray();try(var c=db.rawQuery("SELECT payload FROM states"+(pkg.isEmpty()?"":" WHERE package=?")+" ORDER BY time DESC",pkg.isEmpty()?null:new String[]{pkg})){while(c.moveToNext())out.put(new JSONObject(c.getString(0)));}return out;
 }
 public synchronized long messageCount(){try(var c=db.rawQuery("SELECT COUNT(*) FROM events WHERE kind='message'",null)){c.moveToFirst();return c.getLong(0);}}
 public synchronized long count(){try(var c=db.rawQuery("SELECT COUNT(*) FROM events",null)){c.moveToFirst();return c.getLong(0);}}
 public synchronized void purge(long now){db.delete("states","time<?",new String[]{Long.toString(now-86400000)});db.delete("events","time<?",new String[]{Long.toString(now-7L*86400000)});
  long size;try(var c=db.rawQuery("PRAGMA page_count",null)){c.moveToFirst();size=c.getLong(0);}long free;try(var c=db.rawQuery("PRAGMA freelist_count",null)){c.moveToFirst();free=c.getLong(0);}long page;try(var c=db.rawQuery("PRAGMA page_size",null)){c.moveToFirst();page=c.getLong(0);}
  if((size-free)*page>112L*1024*1024)db.execSQL("DELETE FROM events WHERE id IN (SELECT id FROM events ORDER BY id LIMIT (SELECT MAX(1,COUNT(*)/10) FROM events))");
 }
 public synchronized void clear(String pkg){db.delete("events",pkg.isEmpty()?null:"package=?",pkg.isEmpty()?null:new String[]{pkg});}
 @Override public void close(){db.close();}
}
