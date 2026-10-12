package com.kigtts.plugin.runtime;
import com.kigtts.plugin.runtime.IQuickTextObserver;
interface IHostQuickTextBroker {
    String request(String command);
    void subscribe(String token, long afterRevision, IQuickTextObserver observer);
    void unsubscribe(String token);
}
