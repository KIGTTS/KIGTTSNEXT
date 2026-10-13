package com.kigtts.next

import com.kigtts.next.plugins.control.ScopedResourceDelivery
import com.kigtts.plugin.api.PluginStorage
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class ScopedResourceDeliveryTest {
    @Test fun copiedDraftRemainsIndependentAndCannotReadAnotherOwner() {
        val root = Files.createTempDirectory("delivery").toFile()
        try {
            val source = PluginStorage(root.resolve("source")); val target = PluginStorage(root.resolve("target"))
            source.directory("draft/assets"); source.file("draft/main.lua").writeText("return {}")
            source.file("draft/assets/example.txt").writeText("asset")
            val path = ScopedResourceDelivery.copy(source, target, "draft")
            source.file("draft/main.lua").writeText("changed")
            assertEquals("return {}", target.file("$path/main.lua").readText())
            assertEquals("asset", target.file("$path/assets/example.txt").readText())
            assertThrows(IllegalArgumentException::class.java) { ScopedResourceDelivery.copy(source, target, "../target/secret") }
        } finally { root.deleteRecursively() }
    }
}
