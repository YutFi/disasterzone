package com.example.disasterzone.model;

public class Notification {
    public String message;
    public String postId;
    public String senderId;
    public long timestamp;

    public Notification() {
        // Empty constructor for Firebase
    }

    public Notification(String message, String postId, String senderId, long timestamp) {
        this.message = message;
        this.postId = postId;
        this.senderId = senderId;
        this.timestamp = timestamp;
    }
}