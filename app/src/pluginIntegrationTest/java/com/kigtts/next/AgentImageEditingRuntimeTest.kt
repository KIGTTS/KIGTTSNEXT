package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.plugins.control.uiAssets
import com.kigtts.plugin.api.*
import com.kigtts.tools.agent.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AgentImageEditingRuntimeTest {
    private fun environment(rejectPreview: Boolean = false): PluginEnvironment {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        return object : PluginEnvironment {
            override val storage = model.pluginManager.paths.storage("tools.cloud-agent")
            override val assets = storage.root
            override val appBroker = object : AppBroker {
                override suspend fun request(command: AppCommand): AppReply =
                    if (rejectPreview) AppReply.Failure("RENDER_FAILED", "实际渲染失败")
                    else uiAssets(model, "tools.cloud-agent", command as AppCommand.UiAssets)
            }
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("No network in image editing")
        }
    }
    private suspend fun edit(tools: AgentTools, name: String, args: JsonObject) = tools.execute(ToolCall("image-test", name, args.toString()))

    @Test fun realPngCutoutPreservesEnclosedDetailsAndOriginalFile() = runBlocking {
        val env = environment(); val tools = AgentTools(env)
        val source = env.storage.file("imports/original.png").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 1..3) for (x in 1..3) bitmap.setPixel(x, y, Color.BLACK)
        bitmap.setPixel(2, 2, Color.WHITE)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        val original = source.readBytes()
        val result = edit(tools, "image_edit", buildJsonObject {
            put("path", "imports/original.png"); put("operation", "remove_background"); put("tolerance", 0); put("destination", "workspace/cutout.png")
        })
        val data = PluginJson.parseToJsonElement(result.content).jsonObject
        assertTrue(result.content, data.getValue("ok").jsonPrimitive.boolean)
        assertEquals(16, data.getValue("nonOpaquePixels").jsonPrimitive.int)
        assertEquals(1, result.images.size)
        val output = BitmapFactory.decodeFile(env.storage.file("workspace/cutout.png").path)
        assertEquals(0, Color.alpha(output.getPixel(0, 0))); assertEquals(Color.WHITE, output.getPixel(2, 2)); output.recycle()
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun cropCoordinatesUseExifOrientedImageAxes() = runBlocking {
        val env = environment(); val tools = AgentTools(env)
        val source = env.storage.file("imports/rotated.jpg").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }; bitmap.recycle()
        ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val original = source.readBytes()
        val result = edit(tools, "image_edit", buildJsonObject {
            put("path", "imports/rotated.jpg"); put("operation", "crop"); put("x", 0); put("y", 2); put("width", 4); put("height", 4)
            put("destination", "workspace/oriented-crop.png")
        })
        val data = PluginJson.parseToJsonElement(result.content).jsonObject
        assertTrue(result.content, data.getValue("ok").jsonPrimitive.boolean)
        assertEquals(4, data.getValue("width").jsonPrimitive.int); assertEquals(4, data.getValue("height").jsonPrimitive.int)
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun svgEditUsesRealHostRendererAndReportsPreviewFailureHonestly() = runBlocking {
        val env = environment(); val tools = AgentTools(env)
        val source = env.storage.file("workspace/original.svg").apply { parentFile!!.mkdirs() }
        val original = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><rect width='100' height='100' fill='#000000'/></svg>"
        source.writeText(original, Charsets.UTF_8)
        val args = buildJsonObject { put("path", "workspace/original.svg"); put("color", "#ff0000"); put("viewBox", "10 20 40 50"); put("destination", "workspace/recolored.svg") }
        val result = edit(tools, "svg_edit", args)
        val data = PluginJson.parseToJsonElement(result.content).jsonObject
        assertTrue(result.content, data.getValue("ok").jsonPrimitive.boolean)
        assertEquals(1, result.images.size)
        assertTrue(env.storage.file(result.images.single().path).length() > 0)
        assertTrue(env.storage.file("workspace/recolored.svg").readText().contains("#ff0000"))
        assertEquals(original, source.readText())
        val failedEnv = environment(rejectPreview = true)
        val failed = edit(AgentTools(failedEnv), "svg_edit", JsonObject(args + ("destination" to JsonPrimitive("workspace/preview-failed.svg"))))
        val failure = PluginJson.parseToJsonElement(failed.content).jsonObject
        assertFalse(failed.content, failure.getValue("ok").jsonPrimitive.boolean)
        assertTrue(failure.getValue("saved").jsonPrimitive.boolean)
        assertTrue(failed.images.isEmpty())
    }
}
