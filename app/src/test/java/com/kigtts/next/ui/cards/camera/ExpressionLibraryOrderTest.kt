package com.kigtts.next.ui.cards.camera

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.camera.expressions.ExpressionLibrary
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpressionLibraryOrderTest {
    @Test fun defaultOrderCanBeRearrangedForBothConsumers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = ExpressionLibrary.get(context)
        val ids = library.choices().map { it.id }
        assertEquals(listOf("builtin.giggle", "builtin.not_giggle", "builtin.wise"), ids.take(3))
        assertEquals(listOf("builtin.agni", "builtin.side_eye", "builtin.kim", "builtin.lau"), ids.takeLast(4))

        val reordered = ids.drop(1) + ids.first()
        library.reorder(reordered)
        assertEquals(reordered, library.choices().map { it.id })
        assertEquals(reordered, com.kigtts.next.state.ManagementOrderStore(context).ordered("camera-face-stickers", ids))
    }
}
