package com.example.disasterzone;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disasterzone.adapter.FeedAdapter;
import com.example.disasterzone.model.Post;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.content.Intent;

public class FeedActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FeedAdapter feedAdapter;
    private List<Post> postList;
    private DatabaseReference postsRef;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        postsRef = FirebaseDatabase.getInstance().getReference("posts");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 🔥 START THE NOTIFICATION SERVICE
        Intent serviceIntent = new Intent(this, NotificationService.class);
        startService(serviceIntent);

        // UI Setup
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        postList = new ArrayList<>();
        feedAdapter = new FeedAdapter(this, postList);
        recyclerView.setAdapter(feedAdapter);

        // Navigation Buttons
        ImageView btnNotif = findViewById(R.id.btnNavNotif);
        ImageView btnHome = findViewById(R.id.btnHome);
        ImageView btnMap = findViewById(R.id.btnNavMap);
        ImageView btnCamera = findViewById(R.id.btnNavCamera);
        ImageView btnProfile = findViewById(R.id.btnNavProfile);
        ImageView btnInfo = findViewById(R.id.btnNavInfo);

        btnNotif.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
        btnHome.setOnClickListener(v -> recyclerView.smoothScrollToPosition(0));
        btnMap.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        btnCamera.setOnClickListener(v -> startActivity(new Intent(this, CameraActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnInfo.setOnClickListener(v -> startActivity(new Intent(FeedActivity.this, WebViewActivity.class)));

        loadPosts();

        // 🔥 CRITICAL: Update Location so others can alert you
        updateUserLocation();
    }

    private void updateUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

                userRef.child("latitude").setValue(location.getLatitude());
                userRef.child("longitude").setValue(location.getLongitude());
            }
        });
    }

    private void loadPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Post post = ds.getValue(Post.class);
                    if (post != null) {
                        postList.add(post);
                    }
                }
                Collections.reverse(postList);
                feedAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FeedActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}