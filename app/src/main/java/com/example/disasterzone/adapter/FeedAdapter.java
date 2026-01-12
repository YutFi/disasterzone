package com.example.disasterzone.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disasterzone.CommentActivity;
import com.example.disasterzone.R;
import com.example.disasterzone.model.Notification;
import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

    private final Context context;
    private final List<Post> postList;
    private String currentUserId;

    public FeedAdapter(Context context, List<Post> postList) {
        this.context = context;
        this.postList = postList;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.tvUsername.setText(post.username != null ? post.username : "Anonymous");
        holder.tvDesc.setText(post.description != null ? post.description : "");
        holder.tvDate.setText(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", post.timestamp));

        // --- IMAGE DECODING ---
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(post.imageUrl, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgPost.setImageBitmap(decodedByte);
                holder.imgPost.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                holder.imgPost.setVisibility(View.GONE);
            }
        } else {
            holder.imgPost.setVisibility(View.GONE);
        }

        // --- LIKE SYSTEM ---
        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("likes").child(post.postId);

        // 1. Check status
        likeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (currentUserId != null && snapshot.child(currentUserId).exists()) {
                    holder.imgLike.setImageResource(R.drawable.ic_heart_filled);
                } else {
                    holder.imgLike.setImageResource(R.drawable.ic_heart_outline);
                }
                holder.tvLikeCount.setText(snapshot.getChildrenCount() + " likes");
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 2. Handle Click
        holder.btnLike.setOnClickListener(v -> {
            if (currentUserId == null) return;

            likeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.child(currentUserId).exists()) {
                        // Unlike
                        likeRef.child(currentUserId).removeValue();
                    } else {
                        // Like
                        likeRef.child(currentUserId).setValue(true);

                        // *** TRIGGER NOTIFICATION (Updated to include postId) ***
                        sendLikeNotification(post.userId, post.description, post.postId);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        });

        // --- COMMENT CLICK ---
        holder.btnComment.setOnClickListener(v -> {
            Intent intent = new Intent(context, CommentActivity.class);
            intent.putExtra("postId", post.postId);
            intent.putExtra("authorId", post.userId);
            context.startActivity(intent);
        });

        // --- QR GENERATION ---
        holder.btnQr.setOnClickListener(v -> generateQR("DISASTER_ZONE|" + post.postId));
    }

    // --- HELPER: SEND NOTIFICATION ---
    private void sendLikeNotification(String postOwnerId, String postDescription, String postId) {
        if (postOwnerId.equals(currentUserId)) return; // Don't notify myself

        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(postOwnerId);
        String notifId = notifRef.push().getKey();

        String shortDesc = postDescription.length() > 20 ? postDescription.substring(0, 20) + "..." : postDescription;
        String message = "Someone liked your report: " + shortDesc;

        if (notifId != null) {
            // Updated Constructor with postId
            Notification notif = new Notification(notifId, message, "like", postId, System.currentTimeMillis());
            notifRef.child(notifId).setValue(notif);
        }
    }

    private void generateQR(String content) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            ImageView imageView = new ImageView(context);
            imageView.setImageBitmap(bitmap);
            imageView.setPadding(30, 30, 30, 30);
            builder.setTitle("Share Incident").setView(imageView).setPositiveButton("Close", null).show();
        } catch (Exception e) {
            Toast.makeText(context, "QR Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imgPost, imgLike, btnQr;
        public TextView tvUsername, tvDesc, tvDate, tvLikeCount;
        public LinearLayout btnLike, btnComment;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPost = itemView.findViewById(R.id.imgPost);
            imgLike = itemView.findViewById(R.id.imgLike);
            btnQr = itemView.findViewById(R.id.btnQr);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
        }
    }
}