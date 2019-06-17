package rnxmpp.service;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;

import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.stringprep.XmppStringprepException;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.OutgoingChatMessageListener;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.FlexibleStanzaTypeFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.filter.FromMatchesFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.ThreadFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.util.PacketParserUtils;

import android.os.Build;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.Gson;

import rnxmpp.ssl.UnsafeSSLContext;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ConnectionListener,OutgoingChatMessageListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
    public static final String TAG = "XMPPServiceImpl";

    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;
    Map<String, String> credentials = new HashMap<String, String>();

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
          try {
            Log.i(TAG, "Service Name: " + JidCreate.domainBareFrom(hostname).toString());
            confBuilder.setServiceName(JidCreate.domainBareFrom(hostname));
            confBuilder.setResource("PostIt");
            confBuilder.setHost(hostname);
          } catch (XmppStringprepException e) {
            Log.e(TAG, "error  : " + e.getMessage());
            e.printStackTrace();
          }
      }

      if (port != null)
          confBuilder.setPort(port);

      if (username != null && password != null)
          confBuilder.setUsernameAndPassword(username, password);

      confBuilder
      .setConnectTimeout(3000)
      //.setDebuggerEnabled(true)
      .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
      //.setCompressionEnabled(true)
      .setSendPresence(true);

      SmackConfiguration.DEBUG = true;

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
    public void connect(String username, String password, String authMethod, String hostname, Integer port, Callback errorCallback, Callback successCallback) {
      if (this.isConnected()) {
        Log.d(TAG, "Already Connected");
        return;
      } else {
        Log.d(TAG, "Not connected, connecting");
      }

      if (username != null && !credentials.containsKey("username"))  {
        credentials.put("username", username);
      }

      if (password != null && !credentials.containsKey("password"))  {
        credentials.put("password", password);
      }

      if (username == null) {
        username = "admin@localhost";
      }

      if (password == null) {
        password = "h@ckf3st";
      }

      XMPPTCPConnectionConfiguration.Builder confBuilder = getConfiguration(hostname, port, username, password);
      XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
      this.connection = new XMPPTCPConnection(connectionConfiguration);
        final String uname = username;
      PacketTypeFilter responseFilter = new PacketTypeFilter(Message.class);
      this.connection.addConnectionListener(this);

      final XMPPTCPConnection con = this.connection;
      final Callback sCB = successCallback;
      final Callback eCB = errorCallback;

        try {
          new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                  try {
                      con.connect().login();
                      if (sCB != null) {
                        sCB.invoke("Successfully logged in");
                      }
                  } catch (XMPPException | SmackException | IOException | InterruptedException | IllegalArgumentException e) {
                        if (uname != null)
                          Log.e(TAG, "Could not login for user " + uname, e);

                          if (eCB != null) {
                            eCB.invoke("Error logging in, " + e.getMessage());
                          }
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
      } catch (Exception e) {
        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
      }
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
              accountManager.createAccount(Localpart.fromUnescaped(username), password);
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
      } catch (XmppStringprepException e) {
          e.printStackTrace();
          Log.e(TAG, "XmppStringprepException: " + e.getMessage());
      } catch (InterruptedException e) {
          e.printStackTrace();
          Log.e(TAG, "InterruptedException: " + e.getMessage());
      }
    }

    @Override
    public void newOutgoingMessage(EntityBareJid to, Message message, Chat chat) {
      Log.i(TAG, "New Outgoing Message , ID " + message.getStanzaId().toString() + ", Thread ID " + message.getThread());
    }

    @Override
    public void message(String text, String to, String thread, String itemJSON, Callback errorCallback, Callback successCallback) {
      String chatIdentifier = (thread == null ? to : thread);
      EntityJid toJID = null;
      MessageModel messageModel = null;
      Gson gson = new Gson();
      try {
          toJID = (EntityJid) JidCreate.from(to);
      } catch (XmppStringprepException e) {
          e.printStackTrace();
          Log.e(TAG, "XmppStringprepException: " + e.getMessage());
      }

      try {
          messageModel = gson.fromJson(itemJSON, MessageModel.class);
      } catch (Exception e) {
          e.printStackTrace();
          Log.e(TAG, "GSON Exception " + e.getMessage());
      }

      final ChatManager cm = ChatManager.getInstanceFor(connection);
      final Chat chat = cm.chatWith(toJID.asEntityBareJid());
      cm.addOutgoingListener(this);

      try {
          Message newMessage = new Message(toJID.asEntityBareJid(), Message.Type.chat);
          newMessage.setThread(chatIdentifier);
          newMessage.setBody(text);
          Log.i(TAG, "New New Outgoing Message , ID " + newMessage.getStanzaId().toString() + ", Thread ID " + newMessage.getThread());
          chat.send(newMessage);
          if (successCallback != null) {
            messageModel.setStanzaID(newMessage.getStanzaId().toString());
            successCallback.invoke(gson.toJson(messageModel));
          }
      } catch (SmackException | InterruptedException e) {
          Log.w(TAG, "Could not send message", e);
          if (errorCallback != null) {
            errorCallback.invoke("Error sending message, " + e.getMessage());
          }
      }
    }

    @Override
    public void presence(String to, String type) {
        try {
            connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            Log.w(TAG, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        Roster roster = Roster.getInstanceFor(connection);
        EntityJid toJID = null;
        try {
          toJID = (EntityJid) JidCreate.from(to);
        } catch (XmppStringprepException e) {
          e.printStackTrace();
          Log.e(TAG, "XmppStringprepException : " + e.getMessage());
        }
        RosterEntry rosterEntry = roster.getEntry(toJID.asEntityBareJid());
        if (rosterEntry != null){
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
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
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException e) {
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
         public CharSequence toXML(String enclosingNamespace) {
           XmlStringBuilder xml = new XmlStringBuilder();
           xml.append(this.xmlString);
           return xml.toString();
         }

         @Override
         public String toString() {
             Log.i(TAG, "toString");
             return "ToString()";
         }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            Log.i(TAG, "send stanza Stanza ID" + packet.getStanzaId());
            connection.sendStanza(packet);
        } catch (SmackException | InterruptedException e) {
            Log.w(TAG, "Could not send stanza", e);
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        this.xmppServiceListener.onConnect();
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        this.xmppServiceListener.onLogin(connection.getUser().toString(), password);
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        Log.i(TAG, "Connection was closed.");
    }

}
