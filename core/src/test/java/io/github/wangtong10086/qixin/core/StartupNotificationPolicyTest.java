package io.github.wangtong10086.qixin.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupNotificationPolicyTest {
    @Test public void offlinePushDoesNotRequireKeepalive(){
        JSONObject policy=Policy.initial().put("mode","manage");
        assertTrue(Policy.preserveStartupNotifications(policy,true,false));
        policy.put("nativeKeepalive",false);
        assertTrue(Policy.preserveStartupNotifications(policy,true,false));
        policy.put("nativeKeepalive",true);
        assertTrue(Policy.preserveStartupNotifications(policy,true,false));
    }
    @Test public void readingAndOtherCleanupPathsRemainAvailable(){
        JSONObject policy=Policy.initial().put("mode","manage");
        assertFalse(Policy.preserveStartupNotifications(policy,true,true));
        assertFalse(Policy.preserveStartupNotifications(policy,false,false));
        assertFalse(Policy.preserveStartupNotifications(policy,false,true));
    }
    @Test public void observationAndUnloadedPolicyDoNotInterfere(){
        assertFalse(Policy.preserveStartupNotifications(Policy.initial(),true,false));
        assertFalse(Policy.preserveStartupNotifications(new JSONObject(),true,false));
    }
}
