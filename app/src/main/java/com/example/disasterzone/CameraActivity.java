package com.example.disasterzone;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.core.content.FileProvider;
import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int PERMISSION_CODE = 101;

    private ImageView imgPreview;
    private EditText etDesc;
    private Button btnCapture, btnUpload;
    private ProgressBar progressBar;

    private Bitmap capturedBitmap;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Bind Views
        imgPreview = findViewById(R.id.imgPreview);
        etDesc = findViewById(R.id.etReportDesc);
        btnCapture = findViewById(R.id.btnCapture);
        btnUpload = findViewById(R.id.btnUploadReport);
        progressBar = findViewById(R.id.progressBar);

        // Set Listeners
        btnCapture.setOnClickListener(v -> checkPermissionsAndOpenCam());
        btnUpload.setOnClickListener(v -> uploadPost());
    }

    private void checkPermissionsAndOpenCam() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Request Permissions
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_CODE);
        } else {
            dispatchTakePictureIntent();
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
        }

        if (photoFile != null) {
            try {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.disasterzone.fileprovider", // Must match Manifest
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // Force open camera (Fixed: Removed resolveActivity check)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Load the full-size image we saved to the file
            capturedBitmap = BitmapFactory.decodeFile(currentPhotoPath);
            imgPreview.setImageBitmap(capturedBitmap);
        }
    }

    private void uploadPost() {
        String desc = etDesc.getText().toString().trim();
        if (desc.isEmpty()) { etDesc.setError("Required"); return; }

        Location loc = getLastKnownLocation();
        if (loc == null) { Toast.makeText(this, "Enable GPS!", Toast.LENGTH_SHORT).show(); return; }

        if (capturedBitmap == null) { Toast.makeText(this, "Take a photo first!", Toast.LENGTH_SHORT).show(); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Compress Image
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        capturedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos); // Reduced quality for speed
        String base64Img = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

        FirebaseDatabase.getInstance().getReference("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = snapshot.hasChild("username") ? snapshot.child("username").getValue(String.class) : "Anonymous";

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("posts");
                String postId = ref.push().getKey();

                Post post = new Post(postId, uid, username, desc, base64Img, loc.getLatitude(), loc.getLongitude(), System.currentTimeMillis());

                ref.child(postId).setValue(post).addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(CameraActivity.this, "Uploaded!", Toast.LENGTH_SHORT).show();
                        notifyNearbyUsers(loc.getLatitude(), loc.getLongitude());
                        finish();
                    } else {
                        btnUpload.setEnabled(true);
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { progressBar.setVisibility(View.GONE); }
        });
    }

    private void notifyNearbyUsers(double disasterLat, double disasterLng) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String targetUid = ds.getKey();
                    if (targetUid.equals(myUid)) continue;

                    if (ds.hasChild("latitude") && ds.hasChild("longitude")) {
                        double userLat = ds.child("latitude").getValue(Double.class);
                        double userLng = ds.child("longitude").getValue(Double.class);

                        float[] results = new float[1];
                        Location.distanceBetween(disasterLat, disasterLng, userLat, userLng, results);

                        if (results[0] <= 1000) { // 1KM Radius
                            sendAlertToUser(targetUid, results[0]);
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendAlertToUser(String targetUid, float distance) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(targetUid);
        HashMap<String, Object> map = new HashMap<>();
        map.put("message", "🚨 DANGER: Incident reported " + String.format("%.0fm", distance) + " away!");
        map.put("timestamp", System.currentTimeMillis());
        notifRef.push().setValue(map);
    }

    private Location getLastKnownLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        return loc;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}