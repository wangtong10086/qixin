package io.github.wangtong10086.qixin.core;
/** EVENT.type in the installed MiPush 0.6.1 schema. Imported records are not new deliveries. */
public final class MiPushRecord {
 public static String stage(int type){return "imported-"+switch(type){
  case 0->"send-message";case 2->"registration";case 3->"subscription";case 4->"unsubscription";
  case 6->"ack";case 7->"config";case 8->"feedback";case 9->"notification";case 10->"command";
  case 11->"multi-connection-broadcast";case 12->"multi-connection-result";
  case 20->"unregistration";case 21->"registration-result";default->"unknown-"+type;
 };}
 public static String result(int result){return switch(result){case 0->"ok";case 1->"denied-disabled";case 2->"denied-user";default->"unknown-"+result;};}
 public static String registered(int state){return switch(state){case 0->"not-registered";case 1->"registered";case 2->"unregistered";default->"unknown-"+state;};}
}
