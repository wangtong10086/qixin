package io.github.wangtong10086.qixin.service;
import java.nio.file.*;
import java.util.*;
import org.json.*;
public final class Cli {
 public static void main(String[] argv)throws Exception{
  try{run(argv);}catch(Wire.CommandException error){System.err.println(error.getMessage());System.exit(1);}
 }
 private static void run(String[] argv)throws Exception{
  if(android.system.Os.getuid()!=0)throw new SecurityException("Root required");
  List<String> args=new ArrayList<>(List.of(argv));boolean dry=args.remove("--dry-run"),follow=args.remove("--follow");
  if(args.isEmpty())throw new IllegalArgumentException("qixin status|apps|registrations [PACKAGE]|events [PACKAGE]|diagnose [PACKAGE]|channel reconnect fcm|mipush|config export/import FILE|app configure PACKAGE JSON|history clear [PACKAGE]|refresh|rpc JSON [--dry-run] [--follow]");
  String cmd=args.get(0);JSONObject request=new JSONObject();
  switch(cmd){
   case "status","apps","refresh" -> request.put("command",cmd);
   case "events","registrations","diagnose" -> {request.put("command",cmd);if(args.size()>1)request.put("package",args.get(1));}
   case "registration" -> {String action=args.get(1);if(!Set.of("retry","batch","status","cancel").contains(action))throw new IllegalArgumentException("Expected retry/batch/status/cancel");request.put("command","registration."+action);if(args.size()>2)request.put("package",args.get(2));}
   case "connection" -> {String action=args.get(1);if(!Set.of("takeover","rollback").contains(action))throw new IllegalArgumentException("Expected takeover/rollback");request.put("command","connection."+action);}
   case "channel" -> {if(!args.get(1).equals("reconnect"))throw new IllegalArgumentException("Expected reconnect");request.put("command","channel.reconnect").put("channel",args.get(2));}
   case "config" -> {
    if(args.get(1).equals("export")){String value=Wire.call(new JSONObject().put("command","config.get")).toString(2);Text.write(Path.of(args.get(2)),value,StandardOpenOption.CREATE_NEW);System.out.println("Exported");return;}
    if(!args.get(1).equals("import"))throw new IllegalArgumentException("Expected export/import");JSONObject current=Wire.call(new JSONObject().put("command","config.get"));request.put("command","config.replace").put("expectedRevision",current.getLong("revision")).put("config",new JSONObject(Text.read(Path.of(args.get(2)))));
   }
   case "app" -> {
    if(!args.get(1).equals("configure"))throw new IllegalArgumentException("Expected configure");JSONObject current=Wire.call(new JSONObject().put("command","config.get"));JSONObject patch=new JSONObject(args.get(3));JSONObject app=current.getJSONObject("apps").optJSONObject(args.get(2));if(app==null)app=new JSONObject();
    java.util.Iterator<String> keys=patch.keys();while(keys.hasNext()){String k=keys.next();app.put(k,patch.get(k));}current.getJSONObject("apps").put(args.get(2),app);request.put("command","config.replace").put("expectedRevision",current.getLong("revision")).put("config",current);
   }
   case "history" -> {if(!args.get(1).equals("clear"))throw new IllegalArgumentException("Expected clear");request.put("command","history.clear").put("package",args.size()>2?args.get(2):"");}
   case "rpc" -> request=new JSONObject(args.get(1));
   default -> throw new IllegalArgumentException("Unknown command: "+cmd);
  }
  request.put("dryRun",dry||request.optBoolean("dryRun"));
  if(follow&&!cmd.equals("events"))throw new IllegalArgumentException("--follow is only supported for events");
  if(follow)request.put("ascending",true);
  long cursor=0;
  do{
   JSONObject response=Wire.call(request);
   if(follow){
    JSONArray rows=response.getJSONArray("events");
    for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);System.out.println(row);cursor=Math.max(cursor,row.getLong("id"));}
    request.put("after",cursor);Thread.sleep(2000);
   }else System.out.println(response.toString(2));
  }while(follow);
 }
}
