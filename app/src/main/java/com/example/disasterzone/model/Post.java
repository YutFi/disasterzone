package com.example.disasterzone.model;

public class Post {
    public String postId;
    public String userId;
    public String username;
    public String description;
    public String imageUrl; // <--- This was missing!
    public double latitude;
    public double longitude;
    public long timestamp;

    // Default constructor required for Firebase
    public Post() {
    }

    public Post(String postId, String userId, String username, String description, String imageUrl, double latitude, double longitude, long timestamp) {
        this.postId = postId;
        this.userId = userId;
        this.username = username;
        this.description = description;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}