package io.github.wangtong10086.qixin.service;
public final class Native {
 static {System.load("/data/adb/qixin/libqixin.so");}
 public static native void socketLabel(boolean enabled)throws java.io.IOException;
}
