package io.github.wangtong10086.qixin.service;
import java.nio.file.*;
import org.json.*;
import io.github.wangtong10086.qixin.core.Policy;
public final class Store {
 final Path directory;
 private JSONObject config;
 public Store(Path directory)throws Exception{this.directory=directory;Files.createDirectories(directory);Path file=directory.resolve("config.json");config=Files.exists(file)?new JSONObject(Text.read(file)):Policy.initial();Policy.validate(config);if(!Files.exists(file))atomic(file,config.toString());}
 public static void atomic(Path path,String value)throws Exception{Path tmp=path.resolveSibling(path.getFileName()+".tmp");Text.write(tmp,value);try(var file=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){file.force(true);}Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
 public synchronized JSONObject get()throws JSONException{return new JSONObject(config.toString());}
 public synchronized JSONObject replace(JSONObject next,long expected,boolean dryRun)throws Exception{JSONObject value=Policy.replace(config,next,expected);if(!dryRun){atomic(directory.resolve("config.json"),value.toString());config=value;}return new JSONObject().put("config",value).put("dryRun",dryRun);}
}
