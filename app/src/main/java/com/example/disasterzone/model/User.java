package com.example.disasterzone.model;

public class User {
    public String username;
    public String email;
    public double latitude;
    public double longitude;

    // Default constructor required for calls to DataSnapshot.getValue(User.class)
    public User() {
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }
}