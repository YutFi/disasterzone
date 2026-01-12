package com.example.disasterzone;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.disasterzone.model.Post;
import com.example.disasterzone.model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class NotificationService extends Service {

    private DatabaseReference postsRef;
    private DatabaseReference myNotifRef;
    private ChildEventListener postListener;
    private ChildEventListener notifListener;
    private FirebaseAuth mAuth;
    private long serviceStartTime;

    // IMPORTANT: Changed ID to "V200" to force your phone to reset sound settings
    private static final String CHANNEL_ALERT = "DISASTER_ALERT_V200";
    private static final String CHANNEL_INTERACTION = "INTERACTION_V200";

    @Override
    public void onCreate() {
        super.onCreate();
        mAuth = FirebaseAuth.getInstance();
        serviceStartTime = System.currentTimeMillis(); // Only notify for events happening AFTER app starts

        if (mAuth.getCurrentUser() != null) {
            String myUid = mAuth.getCurrentUser().getUid();

            // 1. Listen for ALL New Posts (Disaster Alerts)
            postsRef = FirebaseDatabase.getInstance().getReference("posts");
            startPostListener(myUid);

            // 2. Listen for Notifications addressed to ME (Likes/Comments)
            myNotifRef = FirebaseDatabase.getInstance().getReference("notifications").child(myUid);
            startNotificationListener();
        }
    }

    private void startPostListener(String myUid) {
        postListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Post post = snapshot.getValue(Post.class);

                // CHECK: Post exists + Created NOW (not old) + Not posted by me
                if (post != null && post.timestamp > serviceStartTime && !post.userId.equals(myUid)) {

                    showNotification(
                            "⚠️ NEW ALERT: " + post.username,
                            post.description,
                            CHANNEL_ALERT,
                            R.raw.alert, // SOUND: alert.mp3
                            post.postId.hashCode()
                    );
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
                Notification notif = snapshot.getValue(Notification.class);

                // CHECK: Notification created NOW (not old)
                if (notif != null && notif.timestamp > serviceStartTime) {

                    showNotification(
                            "DisasterZone",
                            notif.message,
                            CHANNEL_INTERACTION,
                            R.raw.basic, // SOUND: basic.mp3
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

    private void showNotification(String title, String message, String channelId, int soundResId, int notificationId) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. Prepare Sound URI
        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + soundResId);

        // 2. Setup Channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(channelId);

            // Always create/re-create to ensure sound is set
            if (channel == null) {
                int importance = channelId.equals(CHANNEL_ALERT) ?
                        NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT;

                channel = new NotificationChannel(channelId, "Disaster Alerts", importance);

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();

                channel.setSound(soundUri, audioAttributes);
                channel.enableVibration(true);

                manager.createNotificationChannel(channel);
            }
        }

        // 3. Build Notification
        Intent intent = new Intent(this, FeedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists in drawable
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // Expands text if long
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        manager.notify(notificationId, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keeps service running
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