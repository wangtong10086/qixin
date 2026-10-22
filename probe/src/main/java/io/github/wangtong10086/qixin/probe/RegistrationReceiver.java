package io.github.wangtong10086.qixin.probe;
import android.content.*;
/** Local fixture: receipt is not a real MiPush registration. */
public final class RegistrationReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  byte[] payload=intent.getByteArrayExtra("mipush_payload");
  var prefs=context.getSharedPreferences("registration-test",0);
  prefs.edit().putInt("requests",prefs.getInt("requests",0)+1).putInt("payloadBytes",payload==null?0:payload.length).apply();
 }
}
