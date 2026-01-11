package com.example.disasterzone.model;

public class Post {
    public String id;
    public String uid;       // CHANGED: matched to FeedAdapter (was userId)
    public String username;  // CHANGED: matched to FeedAdapter (was userName)
    public String description;
    public String imageBase64;
    public double latitude;
    public double longitude;
    public long timestamp;

    // We don't store 'likes' integer here because we count them live from Firebase

    public Post() {} // Required for Firebase

    public Post(String id, String uid, String username, String description, String imageBase64, double latitude, double longitude, long timestamp) {
        this.id = id;
        this.uid = uid;
        this.username = username;
        this.description = description;
        this.imageBase64 = imageBase64;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}