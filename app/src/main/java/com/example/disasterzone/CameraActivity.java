package com.example.disasterzone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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
    private static final int GALLERY_REQUEST_CODE = 200;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int PERMISSION_REQUEST_CODE = 102;
    private static final int NOTIFICATION_PERMISSION_CODE = 103;

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
        btnGallery = findViewById(R.id.btnGallery);
        btnUploadReport = findViewById(R.id.btnUploadReport);
        progressBar = findViewById(R.id.progressBar);

        // Create notification channel
        createNotificationChannel();

        // Check for notification permission (Android 13+)
        checkNotificationPermission();

        checkPermissions();

        // Button Listeners
        btnCapture.setOnClickListener(v -> askCameraPermission());
        btnGallery.setOnClickListener(v -> openGallery());
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

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
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

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (requestCode == CAMERA_PERMISSION_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }
            if (requestCode == PERMISSION_REQUEST_CODE) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation();
                }
            }
            if (requestCode == NOTIFICATION_PERMISSION_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Notification permission granted
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle Camera Result
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            capturedBitmap = (Bitmap) data.getExtras().get("data");
            showImageInPreview(capturedBitmap);
        }

        // Handle Gallery Result
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
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

    private void showImageInPreview(Bitmap bitmap) {
        imgPreview.setImageBitmap(bitmap);
        imgPreview.setPadding(0, 0, 0, 0);
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

        // Resize bitmap
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

                newPost.isActive = true;
                newPost.endedTimestamp = 0;

                if (postId != null) {
                    mDatabase.child("posts").child(postId).setValue(newPost).addOnCompleteListener(task -> {
                        progressBar.setVisibility(View.GONE);
                        btnUploadReport.setEnabled(true);

                        if (task.isSuccessful()) {
                            // Show thank you notification
                            showThankYouNotification();

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

    private void showThankYouNotification() {
        // Create the notification builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "THANK_YOU_CHANNEL")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("DisasterZone")
                .setContentText("Thank you for submitting the report!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // Add sound
        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.basic);
        builder.setSound(soundUri);

        // Create intent to open the app when notification is tapped
        Intent intent = new Intent(this, FeedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        try {
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(999, builder.build());
                }
            } else {
                notificationManager.notify(999, builder.build());
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            // Log or handle the case where notification permission is not granted
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Thank You Notifications";
            String description = "Notifications for report submissions";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel("THANK_YOU_CHANNEL", name, importance);
            channel.setDescription(description);

            // Set sound for the channel
            Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.basic);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getCurrentLocation();
    }
}