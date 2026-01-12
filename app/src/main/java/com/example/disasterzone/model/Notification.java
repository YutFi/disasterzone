package com.example.disasterzone.model;

public class Notification {
    public String notificationId;
    public String message;
    public String type; // "like" or "comment"
    public String postId; // <--- NEW: NEEDED FOR PREVIEW IMAGE
    public long timestamp;

    public Notification() {}

    public Notification(String notificationId, String message, String type, String postId, long timestamp) {
        this.notificationId = notificationId;
        this.message = message;
        this.type = type;
        this.postId = postId; // <--- Add this
        this.timestamp = timestamp;
    }
}