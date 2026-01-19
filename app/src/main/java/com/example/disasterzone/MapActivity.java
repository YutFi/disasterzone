package com.example.disasterzone;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends AppCompatActivity {

    private MapView mapView;
    private DatabaseReference postsRef;
    private MyLocationNewOverlay myLocationOverlay;
    private Button btnEndDisaster;
    private Spinner spinnerDisasters;
    private TextView tvDistanceInfo;

    // Store disaster data
    private Map<String, Post> disasterMap = new HashMap<>();
    private List<String> disasterList = new ArrayList<>();
    private ArrayAdapter<String> disasterAdapter;

    // Store overlays
    private Map<String, Marker> markerMap = new HashMap<>();
    private Map<String, Polygon> circleMap = new HashMap<>();

    // Current user location
    private GeoPoint userLocation = null;
    private String currentUserId;

    // Handler for automatic deletion
    private Handler deletionHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        btnEndDisaster = findViewById(R.id.btnEndDisaster);
        spinnerDisasters = findViewById(R.id.spinnerDisasters);
        tvDistanceInfo = findViewById(R.id.tvDistanceInfo);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        GeoPoint startPoint = new GeoPoint(3.1390, 101.6869);
        mapView.getController().setCenter(startPoint);

        postsRef = FirebaseDatabase.getInstance().getReference("posts");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupDisasterSpinner();
        setupEndDisasterButton();

        checkPermissions();
        setupUserLocation();
        loadDisasterMarkers();
        startAutoDeletionCheck();
    }

    private void setupDisasterSpinner() {
        disasterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                disasterList);
        disasterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisasters.setAdapter(disasterAdapter);

        spinnerDisasters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String selectedDisasterKey = disasterList.get(position);
                    Post selectedPost = disasterMap.get(selectedDisasterKey);

                    if (selectedPost != null) {
                        // Center map on selected disaster
                        GeoPoint point = new GeoPoint(selectedPost.latitude, selectedPost.longitude);
                        mapView.getController().animateTo(point);
                        mapView.getController().setZoom(15.0);

                        // Update distance info
                        updateDistanceInfo(selectedPost);

                        // Show/hide end button based on distance and status
                        checkUserProximity(selectedPost);
                    }
                } else {
                    tvDistanceInfo.setVisibility(View.GONE);
                    btnEndDisaster.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                tvDistanceInfo.setVisibility(View.GONE);
                btnEndDisaster.setVisibility(View.GONE);
            }
        });
    }

    private void setupEndDisasterButton() {
        btnEndDisaster.setOnClickListener(v -> {
            int selectedPosition = spinnerDisasters.getSelectedItemPosition();

            if (selectedPosition == 0) {
                Toast.makeText(this, "Please select a disaster first", Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedDisasterKey = disasterList.get(selectedPosition);
            Post selectedPost = disasterMap.get(selectedDisasterKey);

            if (selectedPost != null) {
                // Check if already inactive
                if (!selectedPost.isActive) {
                    Toast.makeText(this, "This disaster is already marked as inactive", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Double-check proximity before allowing
                if (!isUserWithin1Km(selectedPost)) {
                    Toast.makeText(this, "You must be within 1km to mark disaster as ended", Toast.LENGTH_LONG).show();
                    return;
                }

                // Confirm action
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Confirm End Disaster")
                        .setMessage("Are you sure this disaster has ended? It will turn grey and be removed in 24 hours.")
                        .setPositiveButton("Yes", (dialog, which) -> markDisasterAsInactive(selectedPost, selectedDisasterKey))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void markDisasterAsInactive(Post post, String disasterKey) {
        // Mark disaster as inactive
        post.isActive = false;
        post.endedTimestamp = System.currentTimeMillis();

        // Update in Firebase
        postsRef.child(post.postId).setValue(post)
                .addOnSuccessListener(aVoid -> {
                    // Update map visualization
                    updateDisasterVisualization(post);

                    // Update spinner text to show it's inactive
                    updateSpinnerItem(disasterKey, post);

                    Toast.makeText(MapActivity.this,
                            "Disaster marked as inactive. It will be removed from the map in 24 hours.",
                            Toast.LENGTH_LONG).show();

                    // Hide the button since disaster is now inactive
                    btnEndDisaster.setVisibility(View.GONE);
                    tvDistanceInfo.setVisibility(View.GONE);

                    // Reset spinner to "Select Disaster"
                    spinnerDisasters.setSelection(0);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MapActivity.this,
                            "Failed to update disaster: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateDistanceInfo(Post post) {
        if (userLocation == null) {
            tvDistanceInfo.setText("Waiting for your location...");
            tvDistanceInfo.setVisibility(View.VISIBLE);
            return;
        }

        GeoPoint disasterPoint = new GeoPoint(post.latitude, post.longitude);
        double distance = calculateDistance(userLocation, disasterPoint);

        String status = post.isActive ? "Active" : "Inactive";
        String color = post.isActive ? "#D32F2F" : "#757575";

        String htmlText = String.format(
                "<font color='%s'>%s</font> - Distance: <b>%.1f km</b>",
                color, status, distance
        );

        tvDistanceInfo.setText(android.text.Html.fromHtml(htmlText));
        tvDistanceInfo.setVisibility(View.VISIBLE);
    }

    private void checkUserProximity(Post post) {
        if (!post.isActive) {
            // Don't show button for inactive disasters
            btnEndDisaster.setVisibility(View.GONE);
            tvDistanceInfo.setText("This disaster is already marked as inactive");
            return;
        }

        if (userLocation == null) {
            btnEndDisaster.setVisibility(View.VISIBLE);
            btnEndDisaster.setText("Waiting for location...");
            btnEndDisaster.setEnabled(false);
            return;
        }

        boolean isWithin1Km = isUserWithin1Km(post);

        if (isWithin1Km) {
            btnEndDisaster.setVisibility(View.VISIBLE);
            btnEndDisaster.setText("The Disaster Has Ended");
            btnEndDisaster.setEnabled(true);
        } else {
            btnEndDisaster.setVisibility(View.GONE);
            String currentText = tvDistanceInfo.getText().toString();
            tvDistanceInfo.setText(currentText + "\n(Too far to mark as ended)");
        }
    }

    private boolean isUserWithin1Km(Post post) {
        if (userLocation == null) return false;

        GeoPoint disasterPoint = new GeoPoint(post.latitude, post.longitude);
        double distance = calculateDistance(userLocation, disasterPoint);

        return distance <= 1.0; // 1 km
    }

    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        // Haversine formula for distance calculation
        double lat1 = Math.toRadians(point1.getLatitude());
        double lon1 = Math.toRadians(point1.getLongitude());
        double lat2 = Math.toRadians(point2.getLatitude());
        double lon2 = Math.toRadians(point2.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double radius = 6371; // Earth's radius in kilometers

        return radius * c;
    }

    private void updateDisasterVisualization(Post post) {
        Marker marker = markerMap.get(post.postId);
        Polygon circle = circleMap.get(post.postId);

        if (marker != null) {
            // Change marker appearance for inactive disasters
            // You can customize this further
            marker.setAlpha(0.5f); // Make it semi-transparent
        }

        if (circle != null) {
            // Change circle to grey
            circle.setFillColor(0x30606060); // Semi-transparent grey
            circle.setStrokeColor(0xFF606060); // Solid grey border
            mapView.invalidate();
        }
    }

    private void updateSpinnerItem(String key, Post post) {
        int position = disasterList.indexOf(key);
        if (position > 0) {
            String newText = "[INACTIVE] " + post.username + " - " +
                    (post.description.length() > 30 ?
                            post.description.substring(0, 30) + "..." :
                            post.description);
            disasterList.set(position, newText);
            disasterAdapter.notifyDataSetChanged();
        }
    }

    private void setupUserLocation() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);

        // Listen for location updates
        myLocationOverlay.runOnFirstFix(() -> {
            runOnUiThread(() -> {
                userLocation = myLocationOverlay.getMyLocation();
                if (userLocation != null) {
                    // Update distance info if a disaster is selected
                    int selectedPosition = spinnerDisasters.getSelectedItemPosition();
                    if (selectedPosition > 0) {
                        String selectedKey = disasterList.get(selectedPosition);
                        Post selectedPost = disasterMap.get(selectedKey);
                        if (selectedPost != null) {
                            updateDistanceInfo(selectedPost);
                            checkUserProximity(selectedPost);
                        }
                    }
                }
            });
        });

        mapView.getOverlays().add(myLocationOverlay);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }

    private void loadDisasterMarkers() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Clear previous data but keep active overlays
                disasterMap.clear();
                disasterList.clear();
                markerMap.clear();
                circleMap.clear();

                // Clear overlays but keep the MyLocationOverlay
                mapView.getOverlays().clear();
                if (myLocationOverlay != null) {
                    mapView.getOverlays().add(myLocationOverlay);
                }

                // Add "Select Disaster" as first item
                disasterList.add("Select Disaster");

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Post post = ds.getValue(Post.class);
                    if (post != null && post.latitude != 0 && post.longitude != 0) {
                        // Skip if inactive for more than 24 hours
                        if (!post.isActive &&
                                System.currentTimeMillis() - post.endedTimestamp > 24 * 60 * 60 * 1000) {
                            continue; // Skip loading expired inactive disasters
                        }

                        GeoPoint point = new GeoPoint(post.latitude, post.longitude);

                        // Create and store marker
                        Marker marker = createMarker(point, post);
                        markerMap.put(post.postId, marker);
                        mapView.getOverlays().add(marker);

                        // Create and store circle
                        Polygon circle = createDisasterZone(point, post.isActive);
                        circleMap.put(post.postId, circle);
                        mapView.getOverlays().add(circle);

                        // Store for spinner
                        String prefix = post.isActive ? "" : "[INACTIVE] ";
                        String displayText = prefix + post.username + " - " +
                                (post.description.length() > 30 ?
                                        post.description.substring(0, 30) + "..." :
                                        post.description);
                        disasterList.add(displayText);
                        disasterMap.put(displayText, post);
                    }
                }

                // Update UI
                if (disasterList.size() > 1) {
                    spinnerDisasters.setVisibility(View.VISIBLE);
                    disasterAdapter.notifyDataSetChanged();

                    // Show/hide distance info and button based on selection
                    int selectedPosition = spinnerDisasters.getSelectedItemPosition();
                    if (selectedPosition > 0) {
                        String selectedKey = disasterList.get(selectedPosition);
                        Post selectedPost = disasterMap.get(selectedKey);
                        if (selectedPost != null) {
                            updateDistanceInfo(selectedPost);
                            checkUserProximity(selectedPost);
                        }
                    }
                } else {
                    spinnerDisasters.setVisibility(View.GONE);
                    btnEndDisaster.setVisibility(View.GONE);
                    tvDistanceInfo.setVisibility(View.GONE);
                }

                mapView.invalidate();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapActivity.this, "Failed to load disasters: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Marker createMarker(GeoPoint point, Post post) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);

        // Set title with status
        String status = post.isActive ? "Active" : "Inactive";
        marker.setTitle(post.username + ": " + post.description + " (" + status + ")");
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Visual difference for inactive markers
        if (!post.isActive) {
            marker.setAlpha(0.5f); // Semi-transparent
        }

        return marker;
    }

    private Polygon createDisasterZone(GeoPoint centerPoint, boolean isActive) {
        Polygon circle = new Polygon();

        if (isActive) {
            circle.setFillColor(0x30D32F2F); // Semi-transparent red
            circle.setStrokeColor(0xFFD32F2F); // Solid red border
        } else {
            circle.setFillColor(0x30606060); // Semi-transparent grey
            circle.setStrokeColor(0xFF606060); // Solid grey border
        }

        circle.setStrokeWidth(2.0f);
        List<GeoPoint> circlePoints = Polygon.pointsAsCircle(centerPoint, 1000.0);
        circle.setPoints(circlePoints);

        return circle;
    }

    private void startAutoDeletionCheck() {
        // Check every hour for inactive disasters older than 24 hours
        deletionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndDeleteOldDisasters();
                deletionHandler.postDelayed(this, 60 * 60 * 1000); // Check every hour
            }
        }, 60 * 60 * 1000); // Start after 1 hour
    }

    private void checkAndDeleteOldDisasters() {
        postsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Post post = ds.getValue(Post.class);
                    if (post != null && !post.isActive) {
                        long inactiveDuration = System.currentTimeMillis() - post.endedTimestamp;
                        long oneDayInMillis = 24 * 60 * 60 * 1000;

                        if (inactiveDuration > oneDayInMillis) {
                            // Delete from Firebase (only from map, not from feed)
                            postsRef.child(post.postId).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        // Remove from local maps
                                        markerMap.remove(post.postId);
                                        circleMap.remove(post.postId);
                                    });
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove handler callbacks
        deletionHandler.removeCallbacksAndMessages(null);
    }
}