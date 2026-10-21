package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.plugins.control.AgentAccessPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class AgentAccessPolicyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { app.getSharedPreferences("agent-access", 0).edit().clear().commit() }
    @Test fun fullAccessRequiresFirstNativeConfirmationAndSurvivesRestartOnlyForTheSamePublisher() {
        val policy = AgentAccessPolicy(app) { "$it:publisher-A" }
        assertThrows(IllegalArgumentException::class.java) { policy.userAction("assistant", mapOf("_agentAccess" to "full")) }
        policy.userAction("assistant", mapOf("_agentAccess" to "full", "_agentFullConfirmed" to "true"))
        repeat(4) { assertTrue(policy.consume("assistant")) }
        assertTrue(AgentAccessPolicy(app) { "$it:publisher-A" }.full("assistant"))
        assertFalse(AgentAccessPolicy(app) { "$it:publisher-B" }.full("assistant"))
        policy.userAction("assistant", mapOf("_agentAccess" to "ask")); assertFalse(policy.consume("assistant"))
        policy.userAction("assistant", mapOf("_agentAccess" to "full")); assertTrue(policy.full("assistant"))
    }
    @Test fun pluginForwardedFieldsCannotGrantAccessAndDelegatedLuaPermissionsEndWithTheCall() {
        val policy = AgentAccessPolicy(app)
        policy.userAction("assistant", mapOf("_callerPlugin" to "untrusted", "_agentAccess" to "full", "_agentFullConfirmed" to "true"))
        assertFalse(policy.full("assistant")); assertNull(policy.delegate("assistant", "runtime.lua", "tools.test"))
        policy.userAction("assistant", mapOf("_agentAccess" to "full", "_agentFullConfirmed" to "true"))
        val lease = policy.delegate("assistant", "runtime.lua", "tools.test")!!
        assertTrue(policy.delegated("runtime.lua", "tools.test")); assertFalse(policy.delegated("runtime.lua", "tools.other"))
        lease.close(); assertFalse(policy.delegated("runtime.lua", "tools.test"))
        policy.userAction("assistant", mapOf("_agentAccess" to "ask", "_agentApprove" to "true"))
        assertTrue(policy.consume("assistant")); assertFalse(policy.consume("assistant"))
    }
}
