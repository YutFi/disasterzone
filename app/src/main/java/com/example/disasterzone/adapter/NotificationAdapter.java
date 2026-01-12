package com.example.disasterzone.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disasterzone.R;
import com.example.disasterzone.model.Notification;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private Context context;
    private List<Notification> notificationList;

    public NotificationAdapter(Context context, List<Notification> notificationList) {
        this.context = context;
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notif = notificationList.get(position);

        holder.tvMessage.setText(notif.message);
        holder.tvDate.setText(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", notif.timestamp));

        // 1. SET TYPE ICON (Heart vs Comment)
        if ("like".equals(notif.type)) {
            holder.imgIcon.setImageResource(R.drawable.ic_heart_filled); // Make sure you have this drawable
        } else if ("comment".equals(notif.type)) {
            holder.imgIcon.setImageResource(R.drawable.ic_comment); // Make sure you have a comment icon
        } else {
            holder.imgIcon.setImageResource(R.drawable.ic_notification);
        }

        // 2. LOAD POST PREVIEW (Fetch image using postId)
        if (notif.postId != null) {
            DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(notif.postId);
            postRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.child("imageUrl").getValue() != null) {
                        String base64Image = snapshot.child("imageUrl").getValue(String.class);
                        try {
                            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            holder.imgPreview.setImageBitmap(decodedByte);
                            holder.imgPreview.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            holder.imgPreview.setVisibility(View.GONE);
                        }
                    } else {
                        // If post has no image or is deleted
                        holder.imgPreview.setVisibility(View.GONE);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        } else {
            holder.imgPreview.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imgIcon, imgPreview;
        public TextView tvMessage, tvDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgNotifIcon);
            imgPreview = itemView.findViewById(R.id.imgPostPreview);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvDate = itemView.findViewById(R.id.tvNotifDate);
        }
    }
}