package rnxmpp.service;

import com.google.gson.annotations.SerializedName;

public class MessageModel {

    @SerializedName("key")
    public String key;

    @SerializedName("from")
    public String from;

    @SerializedName("to")
    public String to;

    @SerializedName("message")
    public String message;

    @SerializedName("time")
    public Double double;

    @SerializedName("self")
    public Boolean self;

    @SerializedName("stanza_id")
    public String stanzaID;
}
