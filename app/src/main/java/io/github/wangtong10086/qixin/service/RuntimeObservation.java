package io.github.wangtong10086.qixin.service;

import java.nio.file.*;
import org.json.*;

/** Bounded migration observation; records local status only, never notification bodies. */
public final class RuntimeObservation {
 private final Path directory,stateFile;
 public RuntimeObservation(Path directory){this.directory=directory;stateFile=directory.resolve("runtime-observation.json");}
 public synchronized JSONObject status()throws Exception{
  if(!Files.exists(stateFile))return new JSONObject().put("started",false);
  JSONObject state=new JSONObject(Text.read(stateFile));
  return state.put("started",true).put("samplingComplete",System.currentTimeMillis()>=state.getLong("endsAt"));
 }
 public synchronized JSONObject start()throws Exception{
  JSONObject current=status();if(current.optBoolean("started")&&!current.optBoolean("samplingComplete"))return current;
  long now=System.currentTimeMillis();
  JSONObject state=new JSONObject().put("startedAt",now).put("endsAt",now+86400000L).put("lastSampleAt",0)
   .put("samples",0).put("unavailableSamples",0).put("disconnectedSamples",0)
   .put("log",directory.resolve("runtime-observation-"+now+".jsonl").toString());
  Store.atomic(stateFile,state.toString());return status();
 }
 public synchronized void sample(JSONObject runtime)throws Exception{
  JSONObject state=status();if(!state.optBoolean("started")||state.optBoolean("samplingComplete"))return;
  long now=System.currentTimeMillis();if(now-state.getLong("lastSampleAt")<300000)return;
  JSONObject sample=new JSONObject(runtime.toString()).put("sampledAt",now);
  Text.write(Path.of(state.getString("log")),sample.toString()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  state.put("lastSampleAt",now).put("samples",state.getInt("samples")+1);
  if(!runtime.optBoolean("connected"))state.put("unavailableSamples",state.getInt("unavailableSamples")+1);
  else if(!runtime.optString("connectionState").equals("connected"))state.put("disconnectedSamples",state.getInt("disconnectedSamples")+1);
  Store.atomic(stateFile,state.toString());
 }
}
