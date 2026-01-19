package com.example.disasterzone.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disasterzone.CommentActivity;
import com.example.disasterzone.R;
import com.example.disasterzone.model.Notification;
import com.example.disasterzone.model.Post;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        boolean isActive = post.isActive;

        // Set username
        holder.tvUsername.setText(post.username != null ? post.username : "Anonymous");

        // Set description
        holder.tvDesc.setText(post.description != null ? post.description : "");

        // Format and set date
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
        String dateText = sdf.format(new Date(post.timestamp));
        if (!isActive && post.endedTimestamp > 0) {
            dateText += " • Ended: " + sdf.format(new Date(post.endedTimestamp));
        }
        holder.tvDate.setText(dateText);

        // Set status badge
        if (isActive) {
            holder.tvStatusBadge.setText("ACTIVE");
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_active);
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatusBadge.setText("INACTIVE");
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_inactive);
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
        }

        // Apply visual styling based on activity status
        applyPostStyling(holder, isActive);

        // --- IMAGE DECODING ---
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(post.imageUrl, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgPost.setImageBitmap(decodedByte);

                // Apply grayscale filter for inactive posts
                if (!isActive) {
                    applyGrayscaleFilter(holder.imgPost);
                }

                holder.imgPost.setVisibility(View.VISIBLE);
                holder.imgPost.setAlpha(isActive ? 1.0f : 0.7f);
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
                    if (!isActive) {
                        holder.imgLike.setColorFilter(ContextCompat.getColor(context, R.color.inactive_color));
                    }
                } else {
                    holder.imgLike.setImageResource(R.drawable.ic_heart_outline);
                }
                holder.tvLikeCount.setText(snapshot.getChildrenCount() + " likes");
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 2. Handle Like Click (disabled for inactive posts)
        holder.btnLike.setOnClickListener(v -> {
            if (!isActive) {
                Toast.makeText(context, "Cannot interact with inactive disasters", Toast.LENGTH_SHORT).show();
                return;
            }

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

                        // Send notification
                        sendLikeNotification(post.userId, post.description, post.postId);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        });

        // --- COMMENT CLICK ---
        holder.btnComment.setOnClickListener(v -> {
            if (!isActive) {
                Toast.makeText(context, "Cannot comment on inactive disasters", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(context, CommentActivity.class);
            intent.putExtra("postId", post.postId);
            intent.putExtra("authorId", post.userId);
            context.startActivity(intent);
        });

        // --- QR GENERATION ---
        holder.btnQr.setOnClickListener(v -> {
            if (!isActive) {
                Toast.makeText(context, "Cannot generate QR for inactive disasters", Toast.LENGTH_SHORT).show();
                return;
            }
            generateQR("DISASTER_ZONE|" + post.postId);
        });

        // Disable/enable interactions based on post status
        holder.btnQr.setEnabled(isActive);
        holder.btnQr.setAlpha(isActive ? 1.0f : 0.5f);
    }

    private void applyPostStyling(ViewHolder holder, boolean isActive) {
        if (isActive) {
            // Active post styling
            holder.postCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white));
            holder.tvUsername.setTextColor(ContextCompat.getColor(context, R.color.active_text));
            holder.tvDesc.setTextColor(ContextCompat.getColor(context, R.color.active_text));
            holder.tvDate.setTextColor(ContextCompat.getColor(context, R.color.active_date));
            holder.tvLikeCount.setTextColor(ContextCompat.getColor(context, R.color.active_date));
            holder.imgLike.clearColorFilter();
        } else {
            // Inactive post styling
            holder.postCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.inactive_background));
            holder.tvUsername.setTextColor(ContextCompat.getColor(context, R.color.inactive_text));
            holder.tvDesc.setTextColor(ContextCompat.getColor(context, R.color.inactive_text));
            holder.tvDate.setTextColor(ContextCompat.getColor(context, R.color.inactive_date));
            holder.tvLikeCount.setTextColor(ContextCompat.getColor(context, R.color.inactive_date));
            holder.imgLike.setColorFilter(ContextCompat.getColor(context, R.color.inactive_color));
        }
    }

    private void applyGrayscaleFilter(ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        imageView.setColorFilter(filter);
    }

    // --- HELPER: SEND NOTIFICATION ---
    private void sendLikeNotification(String postOwnerId, String postDescription, String postId) {
        if (postOwnerId.equals(currentUserId)) return; // Don't notify myself

        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(postOwnerId);
        String notifId = notifRef.push().getKey();

        String shortDesc = postDescription.length() > 20 ? postDescription.substring(0, 20) + "..." : postDescription;
        String message = "Someone liked your report: " + shortDesc;

        if (notifId != null) {
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
        public MaterialCardView postCard;
        public TextView tvStatusBadge, tvUsername, tvDesc, tvDate, tvLikeCount;
        public ImageView imgAvatar, imgPost, imgLike, btnQr;
        public LinearLayout btnLike, btnComment;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            postCard = itemView.findViewById(R.id.postCard);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnQr = itemView.findViewById(R.id.btnQr);
            imgPost = itemView.findViewById(R.id.imgPost);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            imgLike = itemView.findViewById(R.id.imgLike);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
        }
    }
}