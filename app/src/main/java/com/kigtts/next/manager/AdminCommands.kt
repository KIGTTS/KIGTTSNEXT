package com.kigtts.next.manager

import android.content.Context
import com.kigtts.plugin.api.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import org.json.*
import java.io.File

/** Every change uses the host's existing verifier, storage and worker lifecycle. */
class AdminCommands(private val context: Context, private val host: HostPluginRuntime, private val journal: AdminJournal) {
    private val manager get()=host.manager
    suspend fun run(command:String,args:JSONObject,file:File?):JSONObject = when(command) {
        "navigate","back" -> adminNavigate(requireNotNull(host.ui()) { "请先打开主程序" },args,command=="back")
        "present" -> host.mutate {
            val model=requireNotNull(host.ui()) { "请先打开主程序" }
            val entry=model.mediaRepository.import(android.net.Uri.fromFile(requireNotNull(file)),args.getString("mime"))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.content.show(entry);model.navigate(com.kigtts.next.state.Destination.Home) }
            JSONObject().put("mediaId",entry.id)
        }
        "select-media" -> {
            val model=requireNotNull(host.ui()) { "请先打开主程序" };val entry=requireNotNull(model.mediaRepository.get(args.getString("id")))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.content.show(entry) };JSONObject()
        }
        "remove-media" -> { val model=requireNotNull(host.ui());model.content.removeAsync(args.getString("id"));JSONObject() }
        "snapshot" -> adminSnapshot(context,host,journal)
        "inspect" -> {
            val preview=manager.packages.inspect(requireNotNull(file));val previous=manager.packages.installed()
            JSONObject().put("id",preview.manifest.id).put("name",preview.manifest.name).put("version",preview.manifest.version)
                .put("api",preview.manifest.apiVersion).put("publisher",preview.publisherSha256)
                .put("knownPublisher",previous.any { it.publisherSha256==preview.publisherSha256 })
                .put("installedVersion",previous.firstOrNull { it.manifest.id==preview.manifest.id }?.manifest?.version.orEmpty())
                .put("capabilities",JSONArray(preview.manifest.capabilities))
        }
        "install" -> host.mutate {
            val source=requireNotNull(file);val preview=manager.packages.inspect(source)
            manager.install(source,args.getString("publisher"));JSONObject().put("id",preview.manifest.id).put("version",preview.manifest.version)
        }
        "enable" -> host.mutate { manager.enable(args.getString("id"),args.getBoolean("enabled"));JSONObject() }
        "restart" -> host.mutate { manager.restart(args.getString("id"));JSONObject() }
        "uninstall" -> host.mutate { require(args.optBoolean("confirmed")) { "删除插件和资源需要确认" };manager.uninstall(args.getString("id"));JSONObject() }
        "refresh" -> host.mutate { manager.reload();adminSnapshot(context,host,journal) }
        "resource" -> host.mutate {
            val id=args.getString("id");require(manager.packages.installed().any { it.manifest.id==id }) { "插件未安装" }
            val path=manager.importResource(id,args.getString("directory"),args.getString("name"),args.optBoolean("zip")) { requireNotNull(file).inputStream() }
            JSONObject().put("path",path)
        }
        "execute" -> host.mutate {
            val id=args.getString("id");val plugin=requireNotNull(manager.registry.plugin(id)) { "插件未加载" }
            val inputs=args.optJSONObject("inputs") ?: JSONObject();require(inputs.length()<=32)
            val values=inputs.keys().asSequence().associateWith { key -> require(key.length<=128);inputs.getString(key).also { require(it.length<=4096) } }
            val outcome=withTimeout(30_000) { plugin.execute(args.getString("action"),host.context().copy(inputs=values)) }
            manager.refreshContext(host.context());JSONObject().put("outcome",JSONObject(PluginJson.encodeToString<PluginOutcome>(outcome)))
        }
        "tts-test" -> host.mutate {
            val plugin=requireNotNull(manager.registry.plugin(args.getString("id")));val engine=requireNotNull(plugin.tts)
            val text=args.optString("text","你好，这是插件测试。");require(text.length in 1..1024)
            var bytes=0L;var finished=false
            withTimeout(30_000) { engine.synthesize(TtsRequest(text)).collect { event ->
                if(event is SynthesisEvent.Audio)bytes+=event.audio.bytes.size
                if(event is SynthesisEvent.Finished)finished=true
            } }
            check(finished&&bytes>0) { "合成未产生有效音频" };JSONObject().put("pcmBytes",bytes).put("finished",finished)
        }
        else -> error("未知管理命令：$command")
    }
}
