package rnxmpp.service;

import com.google.gson.annotations.SerializedName;

public class MessageModel {

    @SerializedName("key")
    public String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getTime() {
        return time;
    }

    public void setTime(Double time) {
        this.time = time;
    }

    public Boolean getSelf() {
        return self;
    }

    public void setSelf(Boolean self) {
        this.self = self;
    }

    public String getStanzaID() {
        return stanzaID;
    }

    public void setStanzaID(String stanzaID) {
        this.stanzaID = stanzaID;
    }

    @SerializedName("from")
    public String from;

    @SerializedName("to")
    public String to;

    @SerializedName("message")
    public String message;

    @SerializedName("time")
    public Double time;

    @SerializedName("self")
    public Boolean self;

    @SerializedName("stanza_id")
    public String stanzaID;
}
