package com.kigtts.plugin.runtime;
import android.os.ParcelFileDescriptor;
interface IHostSpeechBroker {
    String providers();
    String command(String pluginId, String command);
    ParcelFileDescriptor synthesize(String pluginId, String request, String token);
    void cancel(String token);
    String asrProviders();
    ParcelFileDescriptor recognizeProvider(String pluginId, in ParcelFileDescriptor input, boolean streaming, String config, String token);
    void ttsControl(String pluginId, String command);
    boolean asrControl(String pluginId, String command);
}
