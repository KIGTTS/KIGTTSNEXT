package com.kigtts.manager.protocol;
import android.os.ParcelFileDescriptor;
// Versioned JSON and fd transport deliberately does not depend on the engine SDK's binary ABI.
interface IPluginAdmin {
    String describe();
    String call(String command, String payload, in ParcelFileDescriptor file);
}
