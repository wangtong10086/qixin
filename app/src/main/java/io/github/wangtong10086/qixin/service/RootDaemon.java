package io.github.wangtong10086.qixin.service;
import android.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.json.*;
public final class RootDaemon {
 public static void main(String[] args)throws Exception{
  if(android.system.Os.getuid()!=0)throw new SecurityException("Root required");
  Path directory=Path.of(args[0]);var lockChannel=java.nio.channels.FileChannel.open(directory.resolve("daemon.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=lockChannel.tryLock();if(lock==null)throw new IllegalStateException("Already running");
  Store store=new Store(directory);History history=new History(directory);Inventory inventory=new Inventory(AndroidContext.system());Health health=new Health(inventory,store,history);Commands commands=new Commands(store,history,inventory,health);
  String signer=Text.read(directory.resolve("manager.sha256")).trim();
  ScheduledExecutorService maintenance=Executors.newSingleThreadScheduledExecutor();
  maintenance.scheduleWithFixedDelay(()->{try{inventory.refresh(history,store);history.purge(System.currentTimeMillis());commands.inventoryError="";}catch(Exception e){commands.inventoryError=e.getClass().getSimpleName()+": "+e.getMessage();android.util.Log.e("Qixin","Inventory failed",e);}},0,300,TimeUnit.SECONDS);
  maintenance.scheduleWithFixedDelay(()->{try{health.inspect();commands.healthError="";}catch(Exception e){commands.healthError=e.getClass().getSimpleName()+": "+e.getMessage();android.util.Log.e("Qixin","Health failed",e);}},2,30,TimeUnit.SECONDS);
  maintenance.scheduleWithFixedDelay(()->{try{commands.keepAlive.tick();}catch(Exception e){try{commands.keepAlive.failed(e);}catch(Exception writeFailure){e.addSuppressed(writeFailure);}android.util.Log.e("Qixin","Keepalive failed",e);}},10,30,TimeUnit.SECONDS);
  var pool=Executors.newFixedThreadPool(8);var slots=new Semaphore(8);LocalServerSocket listener;
  Native.socketLabel(true);try{listener=new LocalServerSocket(Wire.ADDRESS);}finally{Native.socketLabel(false);}
  try(LocalServerSocket server=listener){
   Store.atomic(directory.resolve("service.ready"),Integer.toString(android.os.Process.myPid()));
   while(true){
    LocalSocket socket=server.accept();int uid=socket.getPeerCredentials().getUid();String role;
    if(uid==0)role="root";else if(uid==1000)role="system";else if(inventory.manager(uid,signer))role="manager";else if(inventory.ownsUid(Inventory.XMSF,uid))role="xmsf";else if(inventory.ownsUid("com.ss.android.lark",uid))role="app:com.ss.android.lark";else{socket.close();continue;}
    if(!slots.tryAcquire()){socket.close();continue;}
    final String peer=role;pool.execute(()->{
     try(socket){socket.setSoTimeout(15000);var reader=Wire.reader(socket);StringBuilder line=new StringBuilder();int ch;
      while((ch=reader.read())!=-1&&ch!='\n'){if(line.length()>=256*1024)throw new IllegalArgumentException("Request exceeds 256 KiB");line.append((char)ch);}
      try{Wire.send(socket,new JSONObject().put("ok",true).put("result",commands.execute(new JSONObject(line.toString()),peer)));}
      catch(Exception e){Wire.send(socket,new JSONObject().put("ok",false).put("error",e.getClass().getSimpleName()+": "+e.getMessage()));}
     }catch(Exception e){android.util.Log.w("Qixin","IPC failed: "+e.getClass().getSimpleName());}finally{slots.release();}
    });
   }
  }
 }
}
