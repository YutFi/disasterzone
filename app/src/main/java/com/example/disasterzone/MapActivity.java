package com.example.disasterzone;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.disasterzone.model.Post;
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

import java.util.List;

public class MapActivity extends AppCompatActivity {

    private MapView mapView;
    private DatabaseReference postsRef;
    private MyLocationNewOverlay myLocationOverlay; // 1. Declare the overlay

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Important: Configure OSMDroid
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        // Default center
        GeoPoint startPoint = new GeoPoint(3.1390, 101.6869);
        mapView.getController().setCenter(startPoint);

        postsRef = FirebaseDatabase.getInstance().getReference("posts");

        checkPermissions();
        setupUserLocation(); // 2. Setup the location indicator
        loadDisasterMarkers();
    }

    // --- NEW METHOD TO SHOW BLUE DOT ---
    private void setupUserLocation() {
        // This overlay automatically finds GPS and draws the "Blue Person/Arrow" icon
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation(); // Start receiving location updates
        myLocationOverlay.enableFollowLocation(); // Optional: Keep camera centered on user
        myLocationOverlay.setDrawAccuracyEnabled(true); // Show the blue accuracy circle

        mapView.getOverlays().add(myLocationOverlay); // Add to map
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
            myLocationOverlay.enableMyLocation(); // Re-enable when app comes back
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation(); // Save battery when app is closed
        }
    }

    private void loadDisasterMarkers() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Clear overlays BUT keep the MyLocationOverlay
                mapView.getOverlays().clear();
                if (myLocationOverlay != null) {
                    mapView.getOverlays().add(myLocationOverlay);
                }

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Post post = ds.getValue(Post.class);
                    if (post != null && post.latitude != 0 && post.longitude != 0) {
                        GeoPoint point = new GeoPoint(post.latitude, post.longitude);

                        Marker marker = new Marker(mapView);
                        marker.setPosition(point);
                        marker.setTitle(post.username + ": " + post.description);
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        mapView.getOverlays().add(marker);

                        drawDisasterZone(point);
                    }
                }
                mapView.invalidate();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void drawDisasterZone(GeoPoint centerPoint) {
        Polygon circle = new Polygon();
        circle.setFillColor(0x30D32F2F);
        circle.setStrokeColor(0xFFD32F2F);
        circle.setStrokeWidth(2.0f);
        List<GeoPoint> circlePoints = Polygon.pointsAsCircle(centerPoint, 1000.0);
        circle.setPoints(circlePoints);
        mapView.getOverlays().add(circle);
    }
}