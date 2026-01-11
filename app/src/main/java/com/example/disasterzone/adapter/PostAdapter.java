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
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disasterzone.CommentActivity; // We will create this next
import com.example.disasterzone.R;
import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private Context context;
    private List<Post> postList;
    private String myUid;

    public PostAdapter(Context context, List<Post> postList) {
        this.context = context;
        this.postList = postList;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user != null) myUid = user.getUid();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.tvUser.setText(post.username);
        holder.tvDesc.setText(post.description);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(post.timestamp)));

        // Load Image
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(post.imageUrl, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgPost.setImageBitmap(decodedByte);
            } catch (Exception e) {
                holder.imgPost.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.imgPost.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // --- LIKE FUNCTIONALITY ---
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId).child("likes");

        // 1. Check if I liked it & Count total likes
        likesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild(myUid)) {
                    holder.imgLike.setImageResource(R.drawable.ic_heart_filled); // Red Heart
                    holder.tvLikeCount.setText(snapshot.getChildrenCount() + " Liked");
                } else {
                    holder.imgLike.setImageResource(R.drawable.ic_heart_outline); // Grey Heart
                    holder.tvLikeCount.setText(snapshot.getChildrenCount() + " Likes");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 2. Handle Like Click
        holder.btnLike.setOnClickListener(v -> {
            likesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.hasChild(myUid)) {
                        likesRef.child(myUid).removeValue(); // Unlike
                    } else {
                        likesRef.child(myUid).setValue(true); // Like
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        });

        // --- COMMENT FUNCTIONALITY ---
        holder.btnComment.setOnClickListener(v -> {
            Intent intent = new Intent(context, CommentActivity.class);
            intent.putExtra("POST_ID", post.postId); // Pass ID to next screen
            context.startActivity(intent);
        });

        // --- QR CODE ---
        holder.btnQr.setOnClickListener(v -> {
            try {
                String content = "DISASTER_ZONE|" + post.postId;
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
                showQrDialog(bitmap);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void showQrDialog(Bitmap bitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        ImageView imageView = new ImageView(context);
        imageView.setImageBitmap(bitmap);
        imageView.setPadding(40, 40, 40, 40);
        builder.setTitle("Share Report QR").setView(imageView).setPositiveButton("Close", null).show();
    }

    @Override
    public int getItemCount() { return postList.size(); }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvUser, tvDate, tvDesc, tvLikeCount;
        ImageView imgPost, btnQr, imgLike;
        LinearLayout btnLike, btnComment;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUser = itemView.findViewById(R.id.tvUsername);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            imgPost = itemView.findViewById(R.id.imgPost);
            btnQr = itemView.findViewById(R.id.btnQr);
            imgLike = itemView.findViewById(R.id.imgLike);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
        }
    }
}