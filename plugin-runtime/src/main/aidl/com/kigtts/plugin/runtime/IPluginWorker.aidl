package com.kigtts.plugin.runtime;
import android.os.ParcelFileDescriptor;
import com.kigtts.plugin.runtime.IHostSpeechBroker;
import com.kigtts.plugin.runtime.IHostQuickTextBroker;
import com.kigtts.plugin.runtime.IHostAppBroker;
import com.kigtts.plugin.runtime.IHostStickerBroker;
import com.kigtts.plugin.runtime.IPluginUiObserver;

interface IPluginWorker {
    String initialize(String pluginId, IHostSpeechBroker broker);
    String voiceCommand(String command);
    String snapshot(String context);
    String execute(String actionId, String context);
    ParcelFileDescriptor synthesize(String request, String token);
    ParcelFileDescriptor recognize(in ParcelFileDescriptor input, boolean streaming, String config, String token);
    void cancel(String token);
    oneway void shutdown();
    void attachQuickTextBroker(IHostQuickTextBroker broker);
    void attachAppBroker(IHostAppBroker broker);
    void observeUi(IPluginUiObserver observer);
    void ttsControl(String command);
    void attachStickerBroker(IHostStickerBroker broker);
}
