package io.github.wangtong10086.qixin.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class MiPushRecordTest {
 @Test public void registrationAndMessageAreDistinct(){assertEquals("imported-registration-result",MiPushRecord.stage(21));assertEquals("imported-send-message",MiPushRecord.stage(0));assertEquals("unregistered",MiPushRecord.registered(2));}
 @Test public void unknownValuesStayUnknown(){assertEquals("imported-unknown-999",MiPushRecord.stage(999));assertEquals("unknown-999",MiPushRecord.result(999));}
}
