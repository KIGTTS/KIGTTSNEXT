package com.kigtts.manager.protocol

import org.junit.Test
import org.junit.Assert.*

class AdminContractTest {
    @Test fun thirdPartyAppsAndOtherSameSignedPackagesCannotControlTheHost() {
        assertFalse(AdminContract.authorized(20000,10000,false,listOf(AdminContract.MANAGER)))
        assertFalse(AdminContract.authorized(20000,10000,true,listOf("other.debug.app")))
        assertFalse(AdminContract.authorized(2000,10000,false,listOf("com.android.shell")))
        assertTrue(AdminContract.authorized(20000,10000,true,listOf(AdminContract.MANAGER)))
        assertTrue(AdminContract.authorized(10000,10000,false,emptyList()))
    }
}
