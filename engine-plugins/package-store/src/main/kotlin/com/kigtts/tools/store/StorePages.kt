package com.kigtts.tools.store

import com.kigtts.plugin.api.*

data class StoreView(var query: String = "", var filter: String = "all", var source: String? = null, var offset: Int = 0,
    var selected: CatalogEntry? = null, var selectedVersion: Int? = null)
internal fun storePage(id: String, sources: SourceStore, jobs: StoreJobs, view: StoreView, device: StoreDevice): PluginPage? = when (id) {
    "store" -> {
        val matching = sources.entries().filter { entry ->
            (view.source == null || entry.source.key == view.source) &&
                (view.query.isBlank() || (entry.item.name + entry.item.summary + entry.item.tags.joinToString()).contains(view.query, ignoreCase = true)) &&
                when (view.filter) { "installed" -> entry.item.id in jobs.installed
                    "updates" -> entry.latest(device)?.let { latest -> jobs.installed[entry.item.id]?.let { old ->
                        old.apiVersion != device.api || SemVersion.parse(latest.version) > SemVersion.parse(old.version) ||
                            (latest.format == PackageFormat.NATIVE && latest.versionCode > old.versionCode)
                    } } == true
                    else -> true }
        }
        val list = matching.drop(view.offset).take(40)
        PluginPage(id, "插件商店", commonStatus(jobs) + listOf(
            PageBlock.Group("浏览", listOf(PageBlock.Input("搜索", view.query, "search"),
                PageBlock.Choice("显示", listOf(PageAction("filter/all", "全部"), PageAction("filter/installed", "已安装"), PageAction("filter/updates", "可更新")), "filter/${view.filter}"),
                PageBlock.Actions(listOf(PageAction("sources", "源管理", PluginIcon.SETTINGS), PageAction("refresh", "检查更新", PluginIcon.EXPORT),
                    PageAction("source-select", if (view.source == null) "全部源" else sources.source(view.source!!).name, PluginIcon.MENU))))),
            PageBlock.Actions(buildList { if (view.offset > 0) add(PageAction("previous", "上一页")); if (view.offset + 40 < matching.size) add(PageAction("next", "下一页")) })),
            management = ManagementList("packages", list.map { entry ->
                val latest = entry.latest(device); val installed = jobs.installed[entry.item.id]
                ManagementItem("${entry.source.key}/${entry.item.id}", entry.item.name,
                    subtitle = listOfNotNull(latest?.version?.let { "最新 $it" } ?: "暂不兼容", installed?.version?.let { "已安装 $it" }, entry.source.name).joinToString(" · "),
                    icon = PluginIcon.MENU, primaryAction = "package/${entry.source.key}/${entry.item.id}")
            }, emptyText = if (sources.records().isEmpty()) "添加一个源，开始浏览插件" else "没有匹配的包"))
    }
    "sources" -> PluginPage(id, "源管理", commonStatus(jobs) + listOf(
        PageBlock.Actions(listOf(PageAction("add-source", "添加源", PluginIcon.ADD), PageAction("refresh", "更新全部", PluginIcon.EXPORT),
            PageAction("clear-cache", "清除下载缓存", PluginIcon.CLOSE)))),
        management = ManagementList("sources", sources.records().map { source ->
            ManagementItem(source.key, source.name, source.error.ifBlank { sources.catalog(source.key)?.packages?.size?.let { "$it 个包" } ?: "尚未更新" },
                icon = PluginIcon.MENU, primaryAction = "browse-source/${source.key}",
                actions = listOf(PageAction("refresh/${source.key}", "更新", PluginIcon.EXPORT), PageAction("source-remove/${source.key}", "移除", PluginIcon.CLOSE)),
                toggle = ManagementToggle(source.enabled, "source-toggle/${source.key}"))
        }, reorderAction = "sources-reorder", emptyText = "支持添加多个 HTTPS 包源"))
    "source-review" -> sources.pending()?.let { pending -> PluginPage(id, "添加源", listOf(
        PageBlock.Group("确认源地址", listOf(PageBlock.Text(pending.url), PageBlock.Notice("确认后会读取目录。添加源不会安装包或信任发布者。"),
            PageBlock.Actions(listOf(PageAction("source-confirm/${pending.token}", "添加", PluginIcon.ADD), PageAction("source-cancel", "取消", PluginIcon.CLOSE))))))) }
    "package" -> view.selected?.let { entry ->
        val release = entry.item.releases.firstOrNull { it.versionCode == view.selectedVersion } ?: entry.latest(device)
        PluginPage(id, entry.item.name, commonStatus(jobs) + listOf(PageBlock.Group("插件信息", listOf(
            PageBlock.Text(entry.item.summary), PageBlock.Text("${entry.source.name} · ${entry.item.license}"),
            PageBlock.Actions(listOf(PageAction("versions", release?.version ?: "选择版本", PluginIcon.MENU)))))) +
            (release?.let { listOf(PageBlock.Group("版本", buildList {
                add(PageBlock.Text(it.notes.ifBlank { "版本 ${it.version}" }))
                if (it.dependencies.isNotEmpty()) add(PageBlock.Text("前置依赖：" + it.dependencies.entries.joinToString { dependency -> "${dependency.key} ${dependency.value}" }))
                add(PageBlock.Actions(listOf(PageAction("prepare", if (jobs.installed[entry.item.id] == null) "安装此版本" else "切换到此版本", PluginIcon.ADD))))
            })) } ?: listOf(PageBlock.Notice("此源没有兼容当前设备的版本"))))
    }
    "install-plan" -> jobs.prepared?.let { ready -> PluginPage(id, "检查安装计划", commonStatus(jobs) + listOf(
        PageBlock.Group("本次安装", ready.plan.ordered.map { selected -> PageBlock.Text("${selected.entry.item.name} · ${selected.release.version}") } +
            listOfNotNull(ready.plan.downgrades.takeIf { it.isNotEmpty() }?.let { PageBlock.Notice("包含较早版本：" + it.joinToString()) }) +
            listOf(PageBlock.Notice("继续后仍会检查发布者和权限，并由你确认安装。"), PageBlock.Actions(listOf(
                PageAction("install-plan", "继续安装", PluginIcon.ADD), PageAction("install-cancel", "取消", PluginIcon.CLOSE))))))) }
        ?: PluginPage(id, "检查安装计划", commonStatus(jobs) + listOf(PageBlock.Notice(if (jobs.busy) "正在准备所需的包" else "请先选择一个版本")))
    "await-install" -> PluginPage(id, "等待确认", listOf(PageBlock.Notice("请在安装页面完成确认。商店会以主程序的已安装状态为准。"),
        PageBlock.Actions(listOf(PageAction("continue-install", "检查并继续", PluginIcon.EXPORT), PageAction("install-cancel", "返回商店", PluginIcon.CLOSE)))))
    else -> null
}
private fun commonStatus(jobs: StoreJobs): List<PageBlock> = buildList {
    if (jobs.busy && jobs.status.isNotEmpty()) {
        add(PageBlock.Notice(jobs.progress?.let { "${jobs.status} · ${(it.bytes * 100 / it.total).coerceIn(0, 100)}%" } ?: jobs.status))
        add(PageBlock.Actions(listOf(PageAction("cancel-job", "取消", PluginIcon.CLOSE))))
    }
    if (jobs.error.isNotEmpty()) add(PageBlock.Notice(jobs.error))
    if (jobs.prepared != null) add(PageBlock.Actions(listOf(PageAction("show-plan", "查看安装计划", PluginIcon.ADD))))
}
