package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import org.junit.Test
import org.junit.Assert.*

class PackageDescriptorTest {
    private val legacy = PackageManifest("test.legacy", "Legacy", "1.0", 1, "test.Legacy", apiVersion = 9, files = emptyMap())
    @Test fun oldDefaultConstructorBindsToItsSignedPackageApi() {
        val runtimeDefault = PluginDescriptor("test.legacy", "Legacy", "test", version = "1.0")
        assertEquals(10, runtimeDefault.apiVersion)
        assertEquals(9, packageDescriptor(runtimeDefault, legacy).apiVersion)
        assertEquals(9, packageDescriptor(runtimeDefault.copy(apiVersion = 9), legacy).apiVersion)
    }
    @Test fun mismatchedIdentityOrVersionIsStillRejected() {
        val actual = PluginDescriptor("test.legacy", "Legacy", "test", version = "1.0")
        assertThrows(IllegalArgumentException::class.java) { packageDescriptor(actual.copy(id = "other.plugin"), legacy) }
        assertThrows(IllegalArgumentException::class.java) { packageDescriptor(actual.copy(version = "2.0"), legacy) }
        assertThrows(IllegalArgumentException::class.java) { packageDescriptor(actual.copy(apiVersion = 8), legacy) }
    }
}
