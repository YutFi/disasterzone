package com.example.disasterzone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.disasterzone.model.Post;
import com.example.disasterzone.model.User;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 200; // New Code for Gallery
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int PERMISSION_REQUEST_CODE = 102;

    private ImageView imgPreview;
    private EditText etReportDesc;
    private Button btnCapture, btnGallery, btnUploadReport;
    private ProgressBar progressBar;

    private Bitmap capturedBitmap;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        imgPreview = findViewById(R.id.imgPreview);
        etReportDesc = findViewById(R.id.etReportDesc);
        btnCapture = findViewById(R.id.btnCapture);
        btnGallery = findViewById(R.id.btnGallery); // Init new button
        btnUploadReport = findViewById(R.id.btnUploadReport);
        progressBar = findViewById(R.id.progressBar);

        checkPermissions();

        // Button Listeners
        btnCapture.setOnClickListener(v -> askCameraPermission());
        btnGallery.setOnClickListener(v -> openGallery()); // Gallery Listener
        btnUploadReport.setOnClickListener(v -> uploadPost());
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();
                saveLocationToSharedPreferences();
            }
        });
    }

    private void saveLocationToSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences("DisasterPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("latitude", Double.doubleToRawLongBits(currentLatitude));
        editor.putLong("longitude", Double.doubleToRawLongBits(currentLongitude));
        editor.apply();

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(uid)
                    .child("latitude")
                    .setValue(currentLatitude);
            FirebaseDatabase.getInstance().getReference("users")
                    .child(uid)
                    .child("longitude")
                    .setValue(currentLongitude);
        }
    }

    // --- CAMERA LOGIC ---
    private void askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        }
    }

    // --- GALLERY LOGIC ---
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }

    // --- HANDLING RESULTS ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 1. Handle Camera Result
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            capturedBitmap = (Bitmap) data.getExtras().get("data");
            showImageInPreview(capturedBitmap);
        }

        // 2. Handle Gallery Result
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    // Convert URI to Bitmap
                    InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                    capturedBitmap = BitmapFactory.decodeStream(imageStream);
                    showImageInPreview(capturedBitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // Helper to fix the Preview Glitch
    private void showImageInPreview(Bitmap bitmap) {
        imgPreview.setImageBitmap(bitmap);
        imgPreview.setPadding(0, 0, 0, 0);
        // CRITICAL FIX: Remove the gray tint so the photo shows clearly
        imgPreview.setImageTintList(null);
    }

    private void uploadPost() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "Please take a photo or select from gallery", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLatitude == 0.0 || currentLongitude == 0.0) {
            getCurrentLocation();
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();
            return;
        }

        String description = etReportDesc.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUploadReport.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        // Resize bitmap to avoid "Base64 too long" crashes if from Gallery
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(capturedBitmap, 800, 800, true);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream);
        String imageBase64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = "Anonymous";
                if (snapshot.exists()) {
                    User userProfile = snapshot.getValue(User.class);
                    if (userProfile != null) username = userProfile.username;
                }

                String postId = mDatabase.child("posts").push().getKey();
                Post newPost = new Post(postId, userId, username, description, imageBase64, currentLatitude, currentLongitude, System.currentTimeMillis());

                if (postId != null) {
                    mDatabase.child("posts").child(postId).setValue(newPost).addOnCompleteListener(task -> {
                        progressBar.setVisibility(View.GONE);
                        btnUploadReport.setEnabled(true);

                        if (task.isSuccessful()) {
                            Toast.makeText(CameraActivity.this, "Posted Successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(CameraActivity.this, "Upload Failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                btnUploadReport.setEnabled(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getCurrentLocation();
    }
}