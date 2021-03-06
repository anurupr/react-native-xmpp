package rnxmpp.service;

import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppService {

    @ReactMethod
    public void trustHosts(ReadableArray trustedHosts);

    // @ReactMethod
    // void connect(String jid, String password, String authMethod, String hostname, Integer port);

    @ReactMethod
    void connect(String jid, String password, String authMethod, String hostname, Integer port, Callback errorCallback, Callback successCallback);

    @ReactMethod
    //void message(String text, String to, String thread);
    void message(String text, String to, String thread, String itemJSON, Callback errorCallback, Callback successCallback);

    @ReactMethod
    void presence(String to, String type);

    @ReactMethod
    void removeRoster(String to);

    @ReactMethod
    void disconnect();

    @ReactMethod
    void fetchRoster();

    @ReactMethod
    void sendStanza(String stanza);

    @ReactMethod
    void register(String username, String password, String hostname);
}
