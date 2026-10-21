package io.github.wangtong10086.qixin.core;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PolicyOverlayTest {
 @Test public void disableRestoresInheritanceAndPreservesLaterEdits(){
  JSONObject before=new JSONObject().put("resident",true).put("cpuNice",4);
  JSONObject applied=new JSONObject().put("autostart","allow").put("resident",true).put("background","normal");
  JSONObject current=new JSONObject(before.toString()).put("autostart","allow").put("background","restricted").put("cpuNice",8);
  JSONObject restored=PolicyOverlay.restore(current,before,applied);
  assertFalse(restored.has("autostart"));assertTrue(restored.getBoolean("resident"));
  assertEquals("restricted",restored.getString("background"));assertEquals(8,restored.getInt("cpuNice"));
 }
}
