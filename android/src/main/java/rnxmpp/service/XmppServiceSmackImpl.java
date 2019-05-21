package rnxmpp.service;

import com.facebook.react.bridge.ReadableArray;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;

import org.jxmpp.stringprep.XmppStringprepException;

import android.os.Build;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import rnxmpp.ssl.UnsafeSSLContext;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
    public static final String TAG = "XMPPServiceImpl";

    XMPPTCPConnection connection, connectionAlt;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;

    public XmppServiceSmackImpl(XmppServiceListener xmppServiceListener) {
      this.xmppServiceListener = xmppServiceListener;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for(int i = 0; i < trustedHosts.size(); i++){
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    private XMPPTCPConnectionConfiguration.Builder getConfiguration(String hostname, Integer port,String username, String password) {
      XMPPTCPConnectionConfiguration.Builder confBuilder = XMPPTCPConnectionConfiguration.builder();

      if (hostname != null) {
          confBuilder.setServiceName(hostname);
          confBuilder.setHost(hostname);
      }

      if (port != null)
          confBuilder.setPort(port);

      if (username != null && password != null)
          confBuilder.setUsernameAndPassword(username, password);

      confBuilder
      .setConnectTimeout(3000)
      //.setDebuggerEnabled(true)
      .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
      .setCompressionEnabled(false)
      .setSendPresence(true);

      if (Build.VERSION.SDK_INT >= 14) {
          confBuilder.setKeystoreType("AndroidCAStore");
          // config.setTruststorePassword(null);
          confBuilder.setKeystorePath(null);
      } else {
          confBuilder.setKeystoreType("BKS");
          String str = System.getProperty("javax.net.ssl.trustStore");
          if (str == null) {
              str = System.getProperty("java.home") + File.separator + "etc" + File.separator + "security"
                      + File.separator + "cacerts.bks";
          }
          confBuilder.setKeystorePath(str);
      }

      return confBuilder;
    }

    @Override
    public void connect(String username, String password, String authMethod, String hostname, Integer port) {
        XMPPTCPConnectionConfiguration.Builder confBuilder = getConfiguration(hostname, port, username, password);
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        connection = new XMPPTCPConnection(connectionConfiguration);
        final String uname = username;
        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | IOException e) {
                      if (uname != null)
                        Log.e(TAG, "Could not login for user " + uname, e);
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();
    }

    public boolean isConnected() {
        return (this.connection != null) && (this.connection.isConnected());
    }

    @Override
    public void register(String username, String password, String hostname){
      try {
          AccountManager accountManager = AccountManager.getInstance(connection);
          if (accountManager.supportsAccountCreation()) {
              accountManager.sensitiveOperationOverInsecureConnection(true);
              accountManager.createAccount(username, password);
          } else {
              Log.e(TAG, "accountManager doesn't support account creation");
          }
      } catch (SmackException.NotConnectedException e) {
          e.printStackTrace();
          Log.e(TAG, "SmackException.NotConnectedException: " + e.getMessage());
      } catch (SmackException.NoResponseException e) {
          e.printStackTrace();
          Log.e(TAG, "SmackException.NoResponseException: " + e.getMessage());
      } catch (XMPPException e) {
          e.printStackTrace();
          Log.e(TAG, "XMPPException: " + e.getMessage());
      }
    }

    @Override
    public void message(String text, String to, String thread) {
        String chatIdentifier = (thread == null ? to : thread);

        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat chat = chatManager.getThreadChat(chatIdentifier);
        if (chat == null) {
            if (thread == null){
                chat = chatManager.createChat(to, this);
            }else{
                chat = chatManager.createChat(to, thread, this);
            }
        }
        try {
            chat.sendMessage(text);
        } catch (SmackException e) {
            Log.w(TAG, "Could not send message", e);
        }
    }

    @Override
    public void presence(String to, String type) {
        try {
            connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
        } catch (SmackException.NotConnectedException e) {
            Log.w(TAG, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        Roster roster = Roster.getInstanceFor(connection);
        RosterEntry rosterEntry = roster.getEntry(to);
        if (rosterEntry != null){
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
                Log.w(TAG, "Could not remove roster entry: " + to);
            }
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        xmppServiceListener.onDisconnect(null);
    }

    @Override
    public void fetchRoster() {
        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException e) {
            Log.w(TAG, "Could not fetch roster", e);
        }
    }

    public class StanzaPacket extends org.jivesoftware.smack.packet.Stanza {
         private String xmlString;

         public StanzaPacket(String xmlString) {
             super();
             this.xmlString = xmlString;
         }

         @Override
         public XmlStringBuilder toXML() {
             XmlStringBuilder xml = new XmlStringBuilder();
             xml.append(this.xmlString);
             return xml;
         }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendPacket(packet);
        } catch (SmackException e) {
            Log.w(TAG, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        if (packet instanceof IQ){
            this.xmppServiceListener.onIQ((IQ) packet);
        }else if (packet instanceof Presence){
            this.xmppServiceListener.onPresence((Presence) packet);
        }else{
            Log.w(TAG, "Got a Stanza, of unknown subclass" +  packet.toXML().toString());
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        this.xmppServiceListener.onConnnect(connection.getUser(), password);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        this.xmppServiceListener.onLogin(connection.getUser(), password);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        this.xmppServiceListener.onMessage(message);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        this.xmppServiceListener.onRosterReceived(roster);
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        Log.i(TAG, "Connection was closed.");
    }

    @Override
    public void reconnectionSuccessful() {
        Log.i(TAG, "Did reconnect");
    }

    @Override
    public void reconnectingIn(int seconds) {
        Log.i(TAG, "Reconnecting in {0} seconds" + seconds);
    }

    @Override
    public void reconnectionFailed(Exception e) {
        Log.w(TAG, "Could not reconnect" + e.getMessage());

    }

}
