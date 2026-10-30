package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.identity.AvatarFrames
import com.kigtts.next.state.SettingsCategory
import com.kigtts.next.ui.settings.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class AboutDocumentsTest {
    @Test fun legalPagesAndAllLicenseReferencesAreAvailableOfflineForTheCurrentApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val documents = aboutLegalDocuments.values + aboutLicenses
        documents.forEach { document ->
            val text = app.assets.open(document.asset).bufferedReader().use { it.readText() }
            assertTrue(document.asset, text.length > 100)
            assertFalse(text.contains("com.lhtstudio.kigtts.app"))
        }
        val privacy = app.assets.open(aboutLegalDocuments.getValue(SettingsCategory.PRIVACY_POLICY).asset)
            .bufferedReader().use { it.readText() }
        listOf("com.kigtts.next", "云端", "AI", "位置", "系统相册", "面对面").forEach {
            assertTrue("Privacy must describe $it", it in privacy)
        }
    }

    @Test fun backgroundSelectionCoversTheWholeMaterialShapeCatalogWithoutRepeatingATappedShape() {
        val random = Random(456)
        val seen = mutableSetOf<String>()
        var previous = emptyList<String>()
        repeat(250) {
            val next = randomAboutShapes(previous, random)
            assertEquals(4, next.toSet().size)
            next.forEachIndexed { index, shape -> assertNotEquals(previous.getOrNull(index), shape) }
            seen += next
            previous = next
        }
        assertEquals(AvatarFrames.names.keys, seen)
    }

    @Test fun oneFlowerChangesWithoutReplacingAnyNeighbor() {
        val random = Random(17)
        val flowers = randomAboutShapes(random = random, count = 6)
        val next = changeAboutShape(flowers, 3, random)
        assertEquals(6, next.distinct().size)
        flowers.indices.forEach { if (it == 3) assertNotEquals(flowers[it], next[it]) else assertEquals(flowers[it], next[it]) }
    }
}
