package com.kigtts.plugin.runtime;
import com.kigtts.plugin.runtime.IStickerObserver;
interface IHostStickerBroker {
    String request(String command);
    void subscribe(String token, long afterRevision, IStickerObserver observer);
    void unsubscribe(String token);
}
