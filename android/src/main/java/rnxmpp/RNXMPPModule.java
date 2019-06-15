package rnxmpp;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;

import java.util.logging.Logger;

import rnxmpp.service.RNXMPPCommunicationBridge;
import rnxmpp.service.XmppServiceSmackImpl;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */
public class RNXMPPModule extends ReactContextBaseJavaModule implements rnxmpp.service.XmppService {

    public static final String MODULE_NAME = "RNXMPP";
    Logger logger = Logger.getLogger(RNXMPPModule.class.getName());
    XmppServiceSmackImpl xmppService;

    public RNXMPPModule(ReactApplicationContext reactContext) {
        super(reactContext);
        xmppService = new XmppServiceSmackImpl(new RNXMPPCommunicationBridge(reactContext));
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    @ReactMethod
    public void trustHosts(ReadableArray trustedHosts) {
        this.xmppService.trustHosts(trustedHosts);
    }

    @Override
    @ReactMethod
    /*public void connect(String jid, String password, String authMethod, String hostname, Integer port){
        this.xmppService.connect(jid, password, authMethod, hostname, port);

    } */
    public void connect(String jid, String password, String authMethod, String hostname, Integer port, Callback errorCallback, Callback successCallback){
        this.xmppService.connect(jid, password, authMethod, hostname, port, errorCallback, successCallback);

    }

    @Override
    @ReactMethod
    public void message(String text, String to, String thread, String itemJSON, Callback errorCallback, Callback successCallback){
        this.xmppService.message(text, to, thread, itemJSON, errorCallback, successCallback);
    }

    @Override
    @ReactMethod
    public void presence(String to, String type) {
        this.xmppService.presence(to, type);
    }

    @Override
    @ReactMethod
    public void removeRoster(String to) {
        this.xmppService.removeRoster(to);
    }

    @Override
    @ReactMethod
    public void disconnect() {
        this.xmppService.disconnect();
    }

    @Override
    @ReactMethod
    public void fetchRoster() {
        this.xmppService.fetchRoster();
    }

    @Override
    @ReactMethod
    public void sendStanza(String stanza) {
        this.xmppService.sendStanza(stanza);
    }

    @Override
    @ReactMethod
    public void register(String username, String password, String hostname){
        this.xmppService.register(username, password, hostname);
    }
}
