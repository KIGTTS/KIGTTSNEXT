package com.kigtts.plugin.runtime;
import android.os.ParcelFileDescriptor;
interface IHostSpeechBroker {
    String providers();
    String command(String pluginId, String command);
    ParcelFileDescriptor synthesize(String pluginId, String request, String token);
    void cancel(String token);
}
