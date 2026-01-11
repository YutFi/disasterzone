package com.example.disasterzone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
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
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.util.ArrayList;
import java.util.List;

public class FeedActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FeedAdapter feedAdapter;
    private List<Post> postList;
    private DatabaseReference postsRef;
    private FusedLocationProviderClient fusedLocationClient;

    // For periodic location updates
    private Handler locationUpdateHandler;
    private Runnable locationUpdateRunnable;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedData = result.getContents();
                    if (scannedData.startsWith("DISASTER_ZONE|")) {
                        String[] parts = scannedData.split("\\|");
                        if (parts.length > 1) {
                            Intent intent = new Intent(FeedActivity.this, PostDetailActivity.class);
                            intent.putExtra("POST_ID", parts[1]);
                            startActivity(intent);
                        }
                    } else {
                        Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        postsRef = FirebaseDatabase.getInstance().getReference("posts");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize location update handler
        locationUpdateHandler = new Handler();

        // --- START NOTIFICATION SERVICE ---
        // This service listens for new posts and notifications
        Intent serviceIntent = new Intent(this, NotificationService.class);
        startService(serviceIntent);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        postList = new ArrayList<>();
        feedAdapter = new FeedAdapter(this, postList);
        recyclerView.setAdapter(feedAdapter);

        loadPosts();

        // Fixed: Using View instead of ImageView for FrameLayout compatibility
        setupButton(R.id.btnNavNotif, NotificationActivity.class);
        setupButton(R.id.btnNavMap, MapActivity.class);
        setupButton(R.id.btnNavCamera, CameraActivity.class);
        setupButton(R.id.btnNavProfile, ProfileActivity.class);
        setupButton(R.id.btnNavInfo, WebViewActivity.class);

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> loadPosts());

        ImageView btnScan = findViewById(R.id.btnNavScan);
        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                ScanOptions options = new ScanOptions();
                options.setPrompt("Scan QR Code");
                options.setBeepEnabled(true);
                options.setOrientationLocked(true);
                options.setCaptureActivity(CaptureActivity.class);
                barcodeLauncher.launch(options);
            });
        }

        // Start location updates (immediate and periodic)
        updateUserLocation();
        startPeriodicLocationUpdates();
    }

    private void setupButton(int id, Class<?> destination) {
        // Changed from ImageView to View to handle both ImageView and FrameLayout
        View btn = findViewById(id);
        if (btn != null) {
            btn.setOnClickListener(v -> startActivity(new Intent(this, destination)));
        }
    }

    private void updateUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                // 1. Save to Firebase
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                FirebaseDatabase.getInstance().getReference("users")
                        .child(uid)
                        .child("latitude")
                        .setValue(latitude);
                FirebaseDatabase.getInstance().getReference("users")
                        .child(uid)
                        .child("longitude")
                        .setValue(longitude);

                // 2. SAVE TO SHARED PREFS (Critical for NotificationService distance calculation)
                SharedPreferences prefs = getSharedPreferences("DisasterPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong("latitude", Double.doubleToRawLongBits(latitude));
                editor.putLong("longitude", Double.doubleToRawLongBits(longitude));
                editor.apply();

                // Also save timestamp
                editor.putLong("lastLocationUpdate", System.currentTimeMillis());
                editor.apply();
            }
        });
    }

    private void startPeriodicLocationUpdates() {
        locationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUserLocation();
                // Update every 2 minutes (120000 ms)
                locationUpdateHandler.postDelayed(this, 120000);
            }
        };
        // Start first update after 30 seconds
        locationUpdateHandler.postDelayed(locationUpdateRunnable, 30000);
    }

    private void loadPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    try {
                        Post post = ds.getValue(Post.class);
                        if (post != null) {
                            postList.add(post);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                feedAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateUserLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update location when app comes to foreground
        updateUserLocation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop periodic location updates
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }
}