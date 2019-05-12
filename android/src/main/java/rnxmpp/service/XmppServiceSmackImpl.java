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
    private String HOST = "nextwhatsapp.raveendran.me";

    XMPPTCPConnection connection;
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

    @Override
    public void connect(String jid, String password, String authMethod, String hostname, Integer port) {
        final String[] jidParts = jid.split("@");
        String[] serviceNameParts = jidParts[1].split("/");
        String serviceName = serviceNameParts[0];

        XMPPTCPConnectionConfiguration.Builder confBuilder = XMPPTCPConnectionConfiguration.builder()
                .setServiceName(serviceName)
                .setUsernameAndPassword(jidParts[0], password)
                .setConnectTimeout(3000)
                //.setDebuggerEnabled(true)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.required);

        if (serviceNameParts.length>1){
            confBuilder.setResource(serviceNameParts[1]);
        } else {
            confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
        }
        if (hostname != null){
            confBuilder.setHost(hostname);
        }
        if (port != null){
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))){
            confBuilder.setCustomSSLContext(UnsafeSSLContext.INSTANCE.getContext());
        }
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        connection = new XMPPTCPConnection(connectionConfiguration);

        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | IOException e) {
                    Log.e(TAG, "Could not login for user " + jidParts[0], e);
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

    private XMPPTCPConnectionConfiguration buildConfiguration() throws XmppStringprepException {
      XMPPTCPConnectionConfiguration.Builder builder =
            XMPPTCPConnectionConfiguration.builder();


      builder.setHost(HOST);
      //builder.setPort(PORT);
      builder.setCompressionEnabled(false);
      builder.setDebuggerEnabled(true);
      builder.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
      builder.setSendPresence(true);

      if (Build.VERSION.SDK_INT >= 14) {
          builder.setKeystoreType("AndroidCAStore");
          // config.setTruststorePassword(null);
          builder.setKeystorePath(null);
      } else {
          builder.setKeystoreType("BKS");
          String str = System.getProperty("javax.net.ssl.trustStore");
          if (str == null) {
              str = System.getProperty("java.home") + File.separator + "etc" + File.separator + "security"
                      + File.separator + "cacerts.bks";
          }
          builder.setKeystorePath(str);
      }
      //DomainBareJid serviceName = JidCreate.domainBareFrom(HOST);
      builder.setServiceName(HOST);


      return builder.build();
  }

    private XMPPTCPConnection getConnection() throws XMPPException, SmackException, IOException, InterruptedException {
        Log.d(TAG, "Getting XMPP Connect");
        if (isConnected()) {
            Log.d(TAG, "Returning already existing connection");
            return this.connection;
        }

        long l = System.currentTimeMillis();
        try {
            if(this.connection != null){
                Log.d(TAG, "Connection found, trying to connect");
                this.connection.connect();
            }else{
                Log.d(TAG, "No Connection found, trying to create a new connection");
                XMPPTCPConnectionConfiguration config = buildConfiguration();
                SmackConfiguration.DEBUG = true;
                this.connection = new XMPPTCPConnection(config);
                this.connection.connect();
            }
        } catch (Exception e) {
            Log.e(TAG,"some issue with getting connection :" + e.getMessage());
        }

        Log.d(TAG, "Connection Properties: " + connection.getHost() + " " + connection.getServiceName());
        Log.d(TAG, "Time taken in first time connect: " + (System.currentTimeMillis() - l));
        return this.connection;
    }

    @Override
    public void register(String username, String password, String hostname){
      AccountManager accountManager = AccountManager.getInstance(getConnection());
      try {
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
      } catch (SmackException e) {
          e.printStackTrace();
          Log.e(TAG, "SmackException: " + e.getMessage());
      } catch (IOException e) {
          e.printStackTrace();
          Log.e(TAG, "IOException: " + e.getMessage());
      } catch (InterruptedException e) {
          e.printStackTrace();
          Log.e(TAG, "InterruptedException " + e.getMessage());
      } catch (XMPPException.XMPPErrorException e) {
          e.printStackTrace();
          Log.e(TAG, "XMPPException.XMPPErrorException: " + e.getMessage());
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
