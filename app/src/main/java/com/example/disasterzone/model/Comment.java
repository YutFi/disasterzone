package com.example.disasterzone.model;

public class Comment {
    public String commentId;
    public String userId;
    public String username;
    public String text;
    public long timestamp;

    // Empty constructor required for Firebase
    public Comment() {}

    public Comment(String commentId, String userId, String username, String text, long timestamp) {
        this.commentId = commentId;
        this.userId = userId;
        this.username = username;
        this.text = text;
        this.timestamp = timestamp;
    }
}