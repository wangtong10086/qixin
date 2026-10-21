package io.github.wangtong10086.qixin.service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public final class Shell {
 public static String run(String... args)throws Exception{
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  var reader=Executors.newSingleThreadExecutor();
  try {
   Future<byte[]> output=reader.submit(()->{try(var in=p.getInputStream()){byte[] b=in.readNBytes(1024*1024+1);if(b.length>1024*1024)throw new IOException("Command output exceeds 1 MiB");return b;}});
   if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Command timeout: "+args[0]);}
   String text=new String(output.get(2,TimeUnit.SECONDS),StandardCharsets.UTF_8);
   if(p.exitValue()!=0)throw new IOException(args[0]+" failed ("+p.exitValue()+"): "+text.substring(0,Math.min(text.length(),512)));
   if(args[0].equals("am")&&java.util.regex.Pattern.compile("(?m)^Error:").matcher(text).find())throw new IOException("Android rejected command: "+text.substring(0,Math.min(text.length(),512)));
   return text;
  }finally{reader.shutdownNow();if(p.isAlive())p.destroyForcibly();}
 }
}
