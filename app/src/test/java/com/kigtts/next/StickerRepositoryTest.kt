@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.plugins.stickers.StickerHost
import com.kigtts.next.ui.cards.CardStore
import com.kigtts.next.ui.cards.ContactCard
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerRepositoryTest {
    private lateinit var app:Application;private lateinit var paths:PluginPaths;private lateinit var media:MediaRepository
    private lateinit var repo:StickerRepository;private lateinit var host:StickerHost;private lateinit var broker:StickerBroker
    private var editing:String?=null
    private val capabilities=setOf("stickers-read","stickers-write","quick-text-read","quick-text-write")
    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher());app=ApplicationProvider.getApplicationContext();paths=PluginPaths(app)
        val root=File(app.getExternalFilesDir(null),"appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(requireNotNull(app.getExternalFilesDir(null)).canonicalFile.toPath()));root.deleteRecursively()
        app.getSharedPreferences("contact-cards",0).edit().clear().commit()
        listOf("test.stickers","test.other").forEach {id->paths.storage(id).root.deleteRecursively()}
        media=MediaRepository(app,paths);repo=StickerRepository(app,paths,media);host=StickerHost(repo,paths,editingCard={editing})
        broker=host.forPlugin("test.stickers",capabilities)
    }
    @After fun end() {Dispatchers.resetMain()}
    private fun png(target:File,color:Int=Color.GREEN):File {
        target.parentFile!!.mkdirs();val image=Bitmap.createBitmap(24,12,Bitmap.Config.ARGB_8888);image.eraseColor(color)
        target.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle();return target
    }
    private fun decoded(file:File):Bitmap=file.inputStream().use {requireNotNull(BitmapFactory.decodeStream(it))}
    private suspend fun image():StickerImage {
        png(paths.storage("test.stickers").file("imports/photo.png"))
        return (broker.request(StickerCommand.PublishImage(PluginResource("imports/photo.png"))) as StickerReply.Image).value
    }
    private suspend fun prepare(edits:List<StickerEdit>,transaction:String="transaction")=
        broker.request(StickerCommand.Prepare(transaction,repo.snapshot.value.revision,edits))
    @Test fun durableImagesAndDesignLayersSurviveSourceDeletionAndRestartWithoutPathsInDtos()=runBlocking {
        val image=image();val item=StickerItem(libraryId=StickerRepository.DEFAULT_LIBRARY,name="表情",imageId=image.id)
        val card=PolaroidCard(title="我的名片",imageId=image.id,layers=listOf(CardLayer(kind=CardLayerKind.TEXT,text="你好",width=.07)))
        val plan=prepare(listOf(StickerEdit.PutItem(item),StickerEdit.PutCard(card))) as StickerReply.Prepared
        assertTrue(broker.request(StickerCommand.Commit(plan.value.token)) is StickerReply.Applied)
        paths.storage("test.stickers").file("imports/photo.png").delete()
        val restored=StickerRepository(app,paths,media)
        assertEquals(card,restored.cards.value.single());assertEquals(item,restored.items.value.single())
        assertTrue(restored.imageFile(image.id)!!.isFile)
        val encoded=PluginJson.encodeToString(restored.snapshot.value)
        assertFalse(encoded.contains(app.filesDir.absolutePath));assertFalse(encoded.contains("images/"));assertFalse(encoded.contains("source"))
        val exported=broker.request(StickerCommand.ReadImage(image.id,"exports/photo.png")) as StickerReply.Resource
        assertEquals("test.stickers",exported.source.pluginId);assertArrayEquals(restored.imageFile(image.id)!!.readBytes(),paths.storage("test.stickers").file(exported.source.path).readBytes())
    }
    @Test fun resourceOwnershipDestinationAndCapabilityAreEnforced()=runBlocking {
        png(paths.storage("test.other").file("imports/secret.png"))
        assertEquals(StickerError.PERMISSION_DENIED,(broker.request(StickerCommand.PublishImage(PluginResource("imports/secret.png","test.other"))) as StickerReply.Failure).code)
        assertTrue(broker.request(StickerCommand.PublishImage(PluginResource("../test.other/imports/secret.png"))) is StickerReply.Failure)
        val image=image();val read=host.forPlugin("test.other",setOf("stickers-read"))
        assertEquals(StickerError.PERMISSION_DENIED,(read.request(StickerCommand.PublishImage(PluginResource("imports/secret.png"))) as StickerReply.Failure).code)
        assertEquals(StickerError.PERMISSION_DENIED,(read.request(StickerCommand.ReadImage(image.id,"config/settings.json")) as StickerReply.Failure).code)
        assertFalse(paths.storage("test.other").file("config/settings.json").exists())
        assertEquals(StickerError.PERMISSION_DENIED,(read.request(StickerCommand.PublishQuickMedia(image.id)) as StickerReply.Failure).code)
        assertTrue(read.request(StickerCommand.ReadImage(image.id,"exports/copy.png")) is StickerReply.Resource)
    }
    @Test fun imageAndQuickTextConversionPreservesBytesAndIsIndependentOfOriginalAttachment()=runBlocking {
        val image=image();val result=broker.request(StickerCommand.PublishQuickMedia(image.id)) as StickerReply.Media
        val attachment=media.get(result.value.id)!!;assertArrayEquals(repo.imageFile(image.id)!!.readBytes(),media.file(attachment)!!.readBytes())
        val copied=(broker.request(StickerCommand.ImportMedia(result.value.id)) as StickerReply.Image).value
        media.remove(result.value.id);assertTrue(repo.imageFile(copied.id)!!.exists())
        assertArrayEquals(repo.imageFile(image.id)!!.readBytes(),repo.imageFile(copied.id)!!.readBytes())
        val plain=host.forPlugin("test.other",setOf("stickers-write"))
        assertEquals(StickerError.PERMISSION_DENIED,(plain.request(StickerCommand.ImportMedia(result.value.id)) as StickerReply.Failure).code)
    }
    @Test fun invalidBatchAndStalePlanCannotPartiallySaveAnything()=runBlocking {
        val before=repo.snapshot.value;val good=PolaroidCard(title="不会部分保存")
        val bad=good.copy(id="bad",layers=listOf(CardLayer(kind=CardLayerKind.TEXT,text="坏位置",x=Double.NaN)))
        assertTrue(prepare(listOf(StickerEdit.PutCard(good),StickerEdit.PutCard(bad))) is StickerReply.Failure)
        assertEquals(before,repo.snapshot.value)
        val plan=prepare(listOf(StickerEdit.PutCard(good))) as StickerReply.Prepared
        repo.apply(listOf(StickerEdit.PutLibrary(StickerLibrary(name="用户修改"))))
        assertEquals(StickerError.CONFLICT,(broker.request(StickerCommand.Commit(plan.value.token)) as StickerReply.Failure).code)
        assertTrue(repo.cards.value.isEmpty())
    }
    @Test fun removalRequiresNativeApprovalAndCommitIsOwnerBoundIdempotentAfterRestart()=runBlocking {
        val card=PolaroidCard(title="需要确认")
        repo.apply(listOf(StickerEdit.PutCard(card)))
        val prepared=prepare(listOf(StickerEdit.RemoveCards(listOf(card.id)))) as StickerReply.Prepared
        assertTrue(prepared.value.requiresConfirmation)
        assertEquals(StickerError.CONFIRMATION_REQUIRED,(broker.request(StickerCommand.Commit(prepared.value.token)) as StickerReply.Failure).code)
        val other=host.forPlugin("test.other",capabilities)
        assertEquals(StickerError.PERMISSION_DENIED,(other.request(StickerCommand.Commit(prepared.value.token)) as StickerReply.Failure).code)
        val applied=host.approve(prepared.value.token);assertTrue(applied is StickerReply.Applied)
        val revision=repo.snapshot.value.revision;assertEquals(applied,broker.request(StickerCommand.Commit(prepared.value.token)));assertEquals(revision,repo.snapshot.value.revision)
        val restored=StickerHost(StickerRepository(app,paths,media),paths)
        assertEquals(applied,restored.forPlugin("test.stickers",capabilities).request(StickerCommand.Commit(prepared.value.token)))
        assertEquals(applied,restored.forPlugin("test.stickers",capabilities).request(StickerCommand.Transaction("transaction")))
        assertEquals(StickerError.CONFLICT,(broker.request(StickerCommand.Discard(prepared.value.token)) as StickerReply.Failure).code)
        assertEquals(StickerError.PERMISSION_DENIED,(other.request(StickerCommand.Commit(prepared.value.token)) as StickerReply.Failure).code)
    }
    @Test fun orderAndDraftChecksUseStableIdsAndTransactionsCannotBeReusedWithDifferentEdits()=runBlocking {
        val first=PolaroidCard(title="第一张");val second=PolaroidCard(title="第二张")
        repo.apply(listOf(StickerEdit.PutCard(first),StickerEdit.PutCard(second)))
        editing=first.id
        assertEquals(StickerError.BUSY,(prepare(listOf(StickerEdit.PutCard(first.copy(title="覆盖草稿")))) as StickerReply.Failure).code)
        editing=null
        assertTrue(prepare(listOf(StickerEdit.OrderCards(listOf(first.id,first.id)))) is StickerReply.Failure)
        val command=StickerCommand.Prepare("sort",repo.snapshot.value.revision,listOf(StickerEdit.OrderCards(listOf(second.id,first.id))))
        val plan=broker.request(command) as StickerReply.Prepared;assertEquals(plan,broker.request(command))
        val applied=broker.request(StickerCommand.Commit(plan.value.token));assertEquals(applied,broker.request(command))
        assertEquals(listOf(second.id,first.id),repo.cards.value.map {it.id})
        assertEquals(StickerError.CONFLICT,(broker.request(command.copy(edits=listOf(StickerEdit.OrderCards(listOf(first.id,second.id))))) as StickerReply.Failure).code)
    }
    @Test fun libraryRemovalRehomesItemsWhileCardReferencesRemainValid()=runBlocking {
        val image=image();val library=StickerLibrary(name="其它图库");val item=StickerItem(libraryId=library.id,name="贴纸",imageId=image.id)
        val card=PolaroidCard(title="保留名片",layers=listOf(CardLayer(kind=CardLayerKind.IMAGE,imageId=image.id)))
        repo.apply(listOf(StickerEdit.PutLibrary(library),StickerEdit.PutItem(item),StickerEdit.PutCard(card)))
        val plan=prepare(listOf(StickerEdit.RemoveLibraries(listOf(library.id),StickerRepository.DEFAULT_LIBRARY))) as StickerReply.Prepared
        assertTrue(host.approve(plan.value.token) is StickerReply.Applied)
        assertEquals(StickerRepository.DEFAULT_LIBRARY,repo.items.value.single().libraryId);assertEquals(card,repo.cards.value.single());assertTrue(repo.imageFile(image.id)!!.exists())
    }
    @Test fun legacyCardsMigrateExactlyOnceAndBrokenPhotosKeepText()=runBlocking {
        val photo=png(File(app.cacheDir,"old-photo.png"))
        CardStore(app).save(listOf(ContactCard("旧名片","旧说明",Uri.fromFile(photo).toString()),ContactCard("失效图片","文字保留","content://missing/photo")))
        CardRepository(app,repo).migrateLegacy();assertEquals(2,repo.cards.value.size);assertEquals(1,repo.images.value.size)
        photo.delete();val restored=StickerRepository(app,paths,media);CardRepository(app,restored).migrateLegacy()
        assertEquals(repo.cards.value,restored.cards.value);assertEquals(1,restored.images.value.size);assertTrue(restored.imageFile(restored.cards.value.first().imageId!!)!!.exists())
        assertEquals("文字保留",restored.cards.value.last().description)
    }
    @Test fun referencedImagesCannotBeRemovedAndConfirmedCompoundRemovalReleasesOnlyThatResource()=runBlocking {
        val image=image();val item=StickerItem(libraryId=StickerRepository.DEFAULT_LIBRARY,name="保留引用",imageId=image.id)
        repo.apply(listOf(StickerEdit.PutItem(item)));val original=repo.imageFile(image.id)!!
        assertTrue(prepare(listOf(StickerEdit.RemoveImages(listOf(image.id))),"invalid-remove") is StickerReply.Failure)
        assertTrue(original.exists());assertEquals(item,repo.items.value.single())
        val plan=prepare(listOf(StickerEdit.RemoveItems(listOf(item.id)),StickerEdit.RemoveImages(listOf(image.id))),"remove-resource") as StickerReply.Prepared
        assertTrue(plan.value.requiresConfirmation);assertTrue(original.exists())
        assertTrue(host.approve(plan.value.token) is StickerReply.Applied)
        assertFalse(original.exists());assertTrue(repo.images.value.isEmpty());assertTrue(repo.items.value.isEmpty())
    }
    @Test fun finalCatalogByteLimitRejectsBeforePublishingAndPreservesRestartablePreviousData() {
        val bounded=StickerRepository(app,paths,media,catalogByteLimit=4096)
        val first=PolaroidCard(title="原名片");bounded.apply(listOf(StickerEdit.PutCard(first)));val before=bounded.snapshot.value
        val rejected=runCatching {bounded.apply(listOf(StickerEdit.PutCard(first.copy(description="正文".repeat(4096)))))}.exceptionOrNull()
        assertEquals(StickerError.TOO_LARGE,(rejected as StickerFault).code);assertEquals(before,bounded.snapshot.value)
        val restored=StickerRepository(app,paths,media,catalogByteLimit=4096)
        assertEquals(before,restored.snapshot.value);assertEquals(first,restored.cards.value.single())
    }
    @Test fun exportedPolaroidUsesFullPaperCoordinatesAndCallerReceivesPngCopy()=runBlocking {
        val image=image();val card=PolaroidCard(title="拍立得",frameStyleId="classic",frameColor=0xFFFFFFFF,layers=listOf(CardLayer(kind=CardLayerKind.IMAGE,imageId=image.id,x=.8,y=.8,width=.2)))
        repo.apply(listOf(StickerEdit.PutCard(card)))
        val output=broker.request(StickerCommand.ExportCard(card.id,"exports/card.png")) as StickerReply.Resource
        val bitmap=decoded(paths.storage("test.stickers").file(output.source.path))
        assertEquals(1200,bitmap.width);assertEquals(1500,bitmap.height);assertEquals(Color.GREEN,bitmap.getPixel(960,1200));assertEquals(Color.WHITE,bitmap.getPixel(20,20));bitmap.recycle()
    }
    @Test fun photoPosePersistsAndDraftExportUsesPanZoomWhileInvalidPoseCannotSave()=runBlocking {
        val image=image();val original=PolaroidCard(title="构图",imageId=image.id,frameStyleId="classic")
        repo.apply(listOf(StickerEdit.PutCard(original)));val revision=repo.snapshot.value.revision
        val centered=decoded(repo.exportCard(original))
        assertEquals(Color.GREEN,centered.getPixel(1000,600));assertEquals(0xFFB0B0B0.toInt(),centered.getPixel(900,100));centered.recycle()
        val panned=decoded(repo.exportCard(original.copy(photoX=.1)))
        assertEquals(0xFFB0B0B0.toInt(),panned.getPixel(1000,600));panned.recycle()
        val zoomed=decoded(repo.exportCard(original.copy(photoZoom=2.0)))
        assertEquals(Color.GREEN,zoomed.getPixel(900,100));zoomed.recycle();assertEquals(revision,repo.snapshot.value.revision)
        val pose=original.copy(photoX=.3,photoY=.7,photoZoom=2.5,photoBackdrop=PhotoBackdropMode.BLUR)
        repo.apply(listOf(StickerEdit.PutCard(pose)));assertEquals(pose,StickerRepository(app,paths,media).cards.value.single())
        val before=repo.snapshot.value;assertTrue(prepare(listOf(StickerEdit.PutCard(pose.copy(photoZoom=Double.NaN))),"bad-pose") is StickerReply.Failure)
        assertEquals(before,repo.snapshot.value)
    }
    @Test fun blurredLetterboxFillsWindowWithSourceContentInsteadOfPaperOrGray()=runBlocking {
        val image=image();val card=PolaroidCard(title="背景",imageId=image.id,frameStyleId="classic",frameColor=0xFFFFFFFF,photoBackdrop=PhotoBackdropMode.BLUR)
        val output=decoded(repo.exportCard(card))
        assertEquals(Color.GREEN,output.getPixel(200,100));assertEquals(Color.WHITE,output.getPixel(20,20));output.recycle()
    }
    @Test fun imageMirrorsUseLocalAxesAndRotationInFullPaperSpace() {
        val file=File(app.cacheDir,"quadrants.png");val pixels=IntArray(24*24) {i->when {i%24<12&&i/24<12->Color.RED;i%24>=12&&i/24<12->Color.GREEN;i%24<12->Color.BLUE;else->Color.YELLOW}}
        val bitmap=Bitmap.createBitmap(pixels,24,24,Bitmap.Config.ARGB_8888);file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val image=repo.importResource(file);val base=PolaroidCard(title="镜像",frameStyleId="classic",layers=listOf(CardLayer(kind=CardLayerKind.IMAGE,imageId=image.id,width=.2)))
        fun sample(layer:CardLayer):Int {val output=decoded(repo.exportCard(base.copy(layers=listOf(layer))))
            return try {output.getPixel(540,690)} finally {output.recycle()}}
        val layer=base.layers.single();assertEquals(Color.RED,sample(layer));assertEquals(Color.GREEN,sample(layer.copy(flipX=true)))
        assertEquals(Color.BLUE,sample(layer.copy(flipY=true)));assertEquals(Color.YELLOW,sample(layer.copy(flipX=true,flipY=true)))
        assertEquals(Color.YELLOW,sample(layer.copy(flipX=true,rotation=90.0)))
    }
    @Test fun originalBuiltInFrameColorsAndMissingStyleFallbackMatchPngAndPreserveRequestedId() {
        val night=PolaroidCard(title="深色",frameStyleId="night");repo.apply(listOf(StickerEdit.PutCard(night)))
        val output=decoded(repo.exportCard(night));assertEquals(0xFF24282A.toInt(),output.getPixel(20,20));output.recycle()
        val missing=night.copy(frameStyleId="brand.unavailable",frameColor=0xFF123456)
        repo.apply(listOf(StickerEdit.PutCard(missing)));assertEquals("brand.unavailable",StickerRepository(app,paths,media).cards.value.single().frameStyleId)
        val fallback=decoded(repo.exportCard(missing));assertEquals(0xFF123456.toInt(),fallback.getPixel(20,20));fallback.recycle()
    }
    @Test fun brandedFrameResourceRegistrationOwnershipVersionAndRemovalShareNativeTransactions()=runBlocking {
        val image=image();val style=PolaroidFrameStyle("test.stickers.brand","自定义边框",overlayImageId=image.id,color=0xFFFFFFFF)
        val card=PolaroidCard(title="品牌名片",frameStyleId=style.id)
        val plan=prepare(listOf(StickerEdit.PutFrameStyle(style),StickerEdit.PutCard(card)),"frame-create") as StickerReply.Prepared
        assertTrue(broker.request(StickerCommand.Commit(plan.value.token)) is StickerReply.Applied)
        val registered=(broker.request(StickerCommand.QueryFrames()) as StickerReply.Frames).items.first {it.id==style.id}
        assertEquals("test.stickers",registered.owner);assertEquals(1L,registered.version)
        val output=decoded(repo.exportCard(card));assertEquals(Color.GREEN,output.getPixel(20,20));output.recycle()
        assertTrue(prepare(listOf(StickerEdit.RemoveImages(listOf(image.id))),"remove-used-overlay") is StickerReply.Failure)
        val other=host.forPlugin("test.other",capabilities)
        assertEquals(StickerError.PERMISSION_DENIED,(other.request(StickerCommand.Prepare("foreign",repo.snapshot.value.revision,listOf(StickerEdit.PutFrameStyle(registered.copy(name="侵入"))))) as StickerReply.Failure).code)
        val update=prepare(listOf(StickerEdit.PutFrameStyle(registered.copy(name="更新边框"))),"frame-update") as StickerReply.Prepared
        broker.request(StickerCommand.Commit(update.value.token));assertEquals(2L,repo.frameStyles.value.first {it.id==style.id}.version)
        val remove=prepare(listOf(StickerEdit.RemoveFrameStyles(listOf(style.id)),StickerEdit.RemoveImages(listOf(image.id))),"frame-remove") as StickerReply.Prepared
        assertTrue(remove.value.requiresConfirmation);assertTrue(host.approve(remove.value.token) is StickerReply.Applied)
        assertEquals("classic",repo.frameStyle(card).id);assertEquals(style.id,repo.cards.value.single().frameStyleId);assertNull(repo.imageFile(image.id))
    }
    @Test fun replacingOutdatedBuiltInFrameDefinitionsDoesNotDiscardUserCardsOrCustomFrames()=runBlocking {
        val image=image();val custom=PolaroidFrameStyle("test.stickers.saved","旧自定义边框",owner="test.stickers",overlayImageId=image.id)
        val card=PolaroidCard(title="不得丢失",imageId=image.id,frameStyleId=custom.id)
        repo.apply(listOf(StickerEdit.PutFrameStyle(custom),StickerEdit.PutCard(card)))
        val file=File(repo.root,"catalog.json");val json=org.json.JSONObject(file.readText())
        val oldStyles=repo.frameStyles.value.map {if(it.id=="night")it.copy(name="旧名称",color=0xFF010203) else it}
        json.put("frames",PluginJson.encodeToString(oldStyles));file.writeText(json.toString())
        val restored=StickerRepository(app,paths,media)
        assertEquals(card,restored.cards.value.single());assertTrue(restored.imageFile(image.id)!!.exists())
        assertEquals("旧自定义边框",restored.frameStyles.value.first {it.id==custom.id}.name)
        assertEquals(0xFF24282AL,restored.frameStyles.value.first {it.id=="night"}.color)
    }
    @Test fun allNativePaperFormatsRenderTheirSharedPhotoWindowRatioAndNormalizedLayers()=runBlocking {
        val image=image()
        val widths=mapOf(PolaroidFormat.MINI to 942,PolaroidFormat.SQUARE to 1256,PolaroidFormat.WIDE to 1884)
        for(format in listOf(PolaroidFormat.MINI,PolaroidFormat.SQUARE,PolaroidFormat.WIDE)) {
            val style="instax.${format.name.lowercase()}.classic";val geometry=format.geometry
            val card=PolaroidCard(title="相纸",imageId=image.id,frameStyleId=style,layers=listOf(CardLayer(kind=CardLayerKind.IMAGE,imageId=image.id,x=.8,y=.85,width=.1)))
            val bitmap=decoded(repo.exportCard(card));val scale=bitmap.height/geometry.paperHeight
            assertEquals(widths.getValue(format),bitmap.width)
            assertEquals(1500,bitmap.height)
            val left=(geometry.photoLeft*scale).toInt();val top=(geometry.photoTop*scale).toInt()
            assertEquals(0xFFB0B0B0.toInt(),bitmap.getPixel(left+10,top+10));assertEquals(Color.WHITE,bitmap.getPixel(10,10))
            assertEquals(Color.GREEN,bitmap.getPixel((bitmap.width*.8).toInt(),(bitmap.height*.85).toInt()));bitmap.recycle()
        }
    }
    @Test fun oldCatalogMissingFrameIdStaysLegacyWhileNewPolaroidsDefaultSquare() {
        val legacy=PolaroidCard(title="旧数据",frameStyleId="classic");repo.apply(listOf(StickerEdit.PutCard(legacy)))
        val file=File(repo.root,"catalog.json");val root=org.json.JSONObject(file.readText());val snapshot=org.json.JSONObject(root.getString("snapshot"))
        snapshot.getJSONArray("cards").getJSONObject(0).remove("frameStyleId");root.put("snapshot",snapshot.toString());file.writeText(root.toString())
        val restored=StickerRepository(app,paths,media)
        assertEquals(PolaroidFormat.LEGACY,restored.frameStyle(restored.cards.value.single()).format)
        assertEquals("instax.square.classic",PolaroidCard(title="新拍立得").frameStyleId)
    }
    @Test fun originalFullResolutionPhotoBytesArePreservedAndReferencedOriginalCannotBeRemoved()=runBlocking {
        val source=paths.storage("test.stickers").file("imports/full-photo.jpg").apply {parentFile!!.mkdirs()}
        val full=Bitmap.createBitmap(2304,1280,Bitmap.Config.ARGB_8888);full.eraseColor(Color.GREEN)
        source.outputStream().use {assertTrue(full.compress(Bitmap.CompressFormat.JPEG,95,it))};full.recycle()
        val image=(broker.request(StickerCommand.PublishImage(PluginResource("imports/full-photo.jpg"))) as StickerReply.Image).value
        assertEquals(2304,image.width);assertEquals(1280,image.height)
        assertArrayEquals(source.readBytes(),repo.imageFile(image.id)!!.readBytes())
        val card=PolaroidCard(title="保存原图",originalImageId=image.id)
        repo.apply(listOf(StickerEdit.PutCard(card)))
        assertTrue(prepare(listOf(StickerEdit.RemoveImages(listOf(image.id))),"remove-original") is StickerReply.Failure)
        assertEquals(image.id,StickerRepository(app,paths,media).cards.value.single().originalImageId)
    }
    @Test fun cameraJpegExifOrientationMatchesUprightPhotoInExport() {
        val file=File(app.cacheDir,"camera-orientation.jpg")
        val pixels=IntArray(24*12) {if(it%24<12)Color.RED else Color.GREEN}
        val raw=Bitmap.createBitmap(pixels,24,12,Bitmap.Config.ARGB_8888);file.outputStream().use {raw.compress(Bitmap.CompressFormat.JPEG,100,it)};raw.recycle()
        val exif=androidx.exifinterface.media.ExifInterface(file)
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString());exif.saveAttributes()
        val image=repo.importResource(file);val card=PolaroidCard(title="方向",imageId=image.id,frameStyleId="classic")
        val output=decoded(repo.exportCard(card))
        val upper=output.getPixel(600,300);val lower=output.getPixel(600,1000)
        assertTrue(Color.red(upper)>240&&Color.green(upper)<20);assertTrue(Color.green(lower)>240&&Color.red(lower)<20);output.recycle()
    }
    @Test fun qrCreationUsesWritePermissionAndUnicodeImagePayloadSurvivesAtomicSaveAndRestart()=runBlocking {
        val payload="  你好，KIGTTS 😀\nhttps://example.test/?text=文字  "
        val readonly=host.forPlugin("test.other",setOf("stickers-read"))
        assertEquals(StickerError.PERMISSION_DENIED,(readonly.request(StickerCommand.CreateQrCode(payload)) as StickerReply.Failure).code)
        assertTrue(repo.images.value.isEmpty())
        val image=(broker.request(StickerCommand.CreateQrCode(payload)) as StickerReply.Image).value
        val layer=CardLayer(kind=CardLayerKind.IMAGE,imageId=image.id,text=payload)
        val card=PolaroidCard(title="二维码",layers=listOf(layer))
        val plan=prepare(listOf(StickerEdit.PutCard(card)),"save-unicode-qr") as StickerReply.Prepared
        assertTrue(broker.request(StickerCommand.Commit(plan.value.token)) is StickerReply.Applied)
        val restored=StickerRepository(app,paths,media)
        assertEquals(payload,restored.cards.value.single().layers.single().text)
        assertTrue(restored.imageFile(image.id)!!.exists())
        val before=repo.snapshot.value
        val excessive=card.copy(layers=listOf(layer.copy(text="文".repeat(683))))
        assertTrue(prepare(listOf(StickerEdit.PutCard(excessive)),"excessive-metadata") is StickerReply.Failure)
        assertEquals(before,repo.snapshot.value)
    }
    @Test fun classicAndSlimPaperExportAsPureWhiteWhileCustomColorsRemainExact() {
        val styles=listOf("classic","slim")+listOf("mini","square","wide").flatMap {format->listOf("instax.$format.classic","instax.$format.slim")}
        for(style in styles) {
            val card=PolaroidCard(title="白纸",frameStyleId=style,frameColor=0xFFF7F2E8)
            val paper=decoded(repo.exportCard(card))
            try {assertEquals(style,Color.WHITE,paper.getPixel(paper.width/2,20))} finally {paper.recycle()}
        }
        val custom=PolaroidCard(title="自定义",frameStyleId="classic",frameColor=0xFF123456)
        val customPaper=decoded(repo.exportCard(custom))
        try {assertEquals(0xFF123456.toInt(),customPaper.getPixel(20,20))} finally {customPaper.recycle()}
        val brand=PolaroidFrameStyle("test.stickers.cream","品牌纸色",owner="test.stickers",color=0xFFF7F2E8)
        repo.apply(listOf(StickerEdit.PutFrameStyle(brand)))
        val branded=decoded(repo.exportCard(PolaroidCard(title="品牌",frameStyleId=brand.id)))
        try {assertEquals(0xFFF7F2E8.toInt(),branded.getPixel(20,20))} finally {branded.recycle()}
    }
    @Test fun loadingLegacyCreamDefaultMigratesOnlyClassicPaperAndPersistsOnNextSave() {
        val brand=PolaroidFrameStyle("test.stickers.paper","品牌",owner="test.stickers")
        val legacy=PolaroidCard(title="旧默认",frameStyleId="classic",frameColor=0xFFF7F2E8)
        val explicit=PolaroidCard(title="自定义色",frameStyleId="instax.square.classic",frameColor=0xFF123456)
        val branded=PolaroidCard(title="品牌色",frameStyleId=brand.id,frameColor=0xFFF7F2E8)
        repo.apply(listOf(StickerEdit.PutFrameStyle(brand),StickerEdit.PutCard(legacy),StickerEdit.PutCard(explicit),StickerEdit.PutCard(branded)))
        val file=File(repo.root,"catalog.json");val root=org.json.JSONObject(file.readText());val snapshot=org.json.JSONObject(root.getString("snapshot"))
        snapshot.getJSONArray("cards").getJSONObject(0).remove("frameStyleId");root.put("snapshot",snapshot.toString());file.writeText(root.toString())
        val restored=StickerRepository(app,paths,media)
        val saved=restored.cards.value.associateBy {it.id}
        assertEquals(0xFFFFFFFFL,saved.getValue(legacy.id).frameColor)
        assertEquals(PolaroidFormat.LEGACY,restored.frameStyle(saved.getValue(legacy.id)).format)
        assertEquals(explicit,saved.getValue(explicit.id));assertEquals(branded,saved.getValue(branded.id))
        val paper=decoded(restored.exportCard(legacy.id))
        try {assertEquals(Color.WHITE,paper.getPixel(20,20))} finally {paper.recycle()}
        restored.apply(listOf(StickerEdit.OrderCards(restored.cards.value.map {it.id}.reversed())))
        val persisted=PluginJson.decodeFromString<StickerSnapshot>(org.json.JSONObject(file.readText()).getString("snapshot"))
        assertEquals(0xFFFFFFFFL,persisted.cards.first {it.id==legacy.id}.frameColor)
        assertEquals(explicit,persisted.cards.first {it.id==explicit.id});assertEquals(branded,persisted.cards.first {it.id==branded.id})
    }
}
