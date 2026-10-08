package com.kigtts.plugin.runtime;
import android.os.ParcelFileDescriptor;

interface IPluginWorker {
    String initialize(String pluginId);
    String snapshot(String context);
    String execute(String actionId, String context);
    ParcelFileDescriptor synthesize(String request, String token);
    ParcelFileDescriptor recognize(in ParcelFileDescriptor input, boolean streaming, String config, String token);
    void cancel(String token);
    oneway void shutdown();
}
