package com.example.disasterzone.model;

public class Notification {
    public String notificationId;
    public String message;
    public String type;
    public long timestamp;

    // Empty constructor required for Firebase
    public Notification() {
    }

    public Notification(String notificationId, String message, String type, long timestamp) {
        this.notificationId = notificationId;
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
    }
}