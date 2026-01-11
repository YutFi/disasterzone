package com.example.disasterzone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashSet;
import java.util.Set;

public class NotificationService extends Service {

    private DatabaseReference postsRef;
    private DatabaseReference myNotifRef;
    private ChildEventListener postListener;
    private ChildEventListener notifListener;
    private FirebaseAuth mAuth;

    // Prevent duplicates
    private Set<String> processedPostIds = new HashSet<>();
    private Set<String> processedNotifIds = new HashSet<>();

    // Channel IDs
    private static final String CHANNEL_ALERT = "DISASTER_ALERT_CHANNEL";
    private static final String CHANNEL_INTERACTION = "INTERACTION_CHANNEL";

    @Override
    public void onCreate() {
        super.onCreate();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            String myUid = mAuth.getCurrentUser().getUid();

            // Listen to new posts
            postsRef = FirebaseDatabase.getInstance().getReference("posts");
            startPostListener(myUid);

            // Listen to personal notifications
            myNotifRef = FirebaseDatabase.getInstance().getReference("notifications").child(myUid);
            startNotificationListener();
        }
    }

    private void startPostListener(String myUid) {
        postListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Post post = snapshot.getValue(Post.class);
                if (post != null && !post.userId.equals(myUid)) {
                    // Check if already processed
                    if (processedPostIds.contains(post.postId)) {
                        return;
                    }
                    processedPostIds.add(post.postId);

                    // Check distance
                    if (isWithin1KM(post.latitude, post.longitude)) {
                        // Trigger alert notification
                        showNotification(
                                "⚠️ DANGER NEARBY",
                                "New disaster report: " + post.description,
                                CHANNEL_ALERT,
                                R.raw.alert,  // Make sure alert.mp3 is in res/raw/
                                post.postId.hashCode()
                        );
                    }
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        postsRef.addChildEventListener(postListener);
    }

    private void startNotificationListener() {
        notifListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                com.example.disasterzone.model.Notification notif = snapshot.getValue(com.example.disasterzone.model.Notification.class);
                if (notif != null) {
                    // Check if already processed
                    if (processedNotifIds.contains(notif.notificationId)) {
                        return;
                    }
                    processedNotifIds.add(notif.notificationId);

                    // Trigger interaction notification
                    showNotification(
                            "DisasterZone Update",
                            notif.message,
                            CHANNEL_INTERACTION,
                            R.raw.basic,  // Make sure basic.mp3 is in res/raw/
                            notif.notificationId.hashCode()
                    );
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        myNotifRef.addChildEventListener(notifListener);
    }

    private boolean isWithin1KM(double targetLat, double targetLng) {
        SharedPreferences prefs = getSharedPreferences("DisasterPrefs", MODE_PRIVATE);
        double myLat = Double.longBitsToDouble(prefs.getLong("latitude", 0));
        double myLng = Double.longBitsToDouble(prefs.getLong("longitude", 0));

        if (myLat == 0 && myLng == 0) return false;

        // Haversine formula
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(targetLat - myLat);
        double dLng = Math.toRadians(targetLng - myLng);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(myLat)) * Math.cos(Math.toRadians(targetLat)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = R * c;

        return distance <= 1000; // 1 KM
    }

    private void showNotification(String title, String message, String channelId, int soundResId, int notificationId) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(channelId);
            if (channel == null) {
                int importance = channelId.equals(CHANNEL_ALERT) ?
                        NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT;

                channel = new NotificationChannel(channelId, channelId, importance);

                // Set sound
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();

                Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + soundResId);
                channel.setSound(soundUri, audioAttributes);

                // Set vibration for alert
                if (channelId.equals(CHANNEL_ALERT)) {
                    channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
                    channel.enableVibration(true);
                }

                manager.createNotificationChannel(channel);
            }
        }

        // Create notification
        Intent intent = new Intent(this, FeedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(channelId.equals(CHANNEL_ALERT) ?
                        NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);

        // For Android < 8, set sound directly
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + soundResId);
            builder.setSound(soundUri);
        }

        manager.notify(notificationId, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (postsRef != null && postListener != null) postsRef.removeEventListener(postListener);
        if (myNotifRef != null && notifListener != null) myNotifRef.removeEventListener(notifListener);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}