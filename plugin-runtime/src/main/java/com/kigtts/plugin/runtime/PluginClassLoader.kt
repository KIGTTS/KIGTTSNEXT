package com.kigtts.plugin.runtime

import dalvik.system.DexClassLoader

/** Keep a plugin's complete ONNX binding paired with its native libraries. */
internal class PluginClassLoader(dex: String, optimized: String, libraries: String?, parent: ClassLoader,
    private val ownsOnnxRuntime: Boolean) : DexClassLoader(dex, optimized, libraries, parent) {
    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!ownsOnnxRuntime || !name.startsWith("ai.onnxruntime.")) return super.loadClass(name, resolve)
        // The host's camera uses an ORT-only build with the same Java class names.
        val loaded = findLoadedClass(name) ?: findClass(name)
        if (resolve) resolveClass(loaded)
        return loaded
    }
}
