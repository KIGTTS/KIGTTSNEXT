package com.kigtts.plugin.runtime

/** Separate services and providers share this bounded worker pool. Model loading remains provider-owned. */
internal val workerServices = listOf(PluginWorker0::class.java, PluginWorker1::class.java, PluginWorker2::class.java, PluginWorker3::class.java,
    PluginWorker4::class.java, PluginWorker5::class.java, PluginWorker6::class.java, PluginWorker7::class.java)
