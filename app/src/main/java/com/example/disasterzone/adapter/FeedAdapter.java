package com.example.disasterzone.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disasterzone.R;
import com.example.disasterzone.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.FeedViewHolder> {

    private Context context;
    private List<Post> postList;
    private String myUid;

    public FeedAdapter(Context context, List<Post> postList) {
        this.context = context;
        this.postList = postList;
        this.myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new FeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        Post post = postList.get(position);

        // Uses 'username' and 'uid' from the updated Post model
        holder.tvAuthor.setText(post.username != null ? post.username : "Anonymous");
        holder.tvDesc.setText(post.description);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
        holder.tvTime.setText(sdf.format(new Date(post.timestamp)));

        if (post.imageBase64 != null && !post.imageBase64.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(post.imageBase64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgPost.setImageBitmap(decodedByte);
            } catch (Exception e) {
                holder.imgPost.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        }

        // DELETE Logic
        if (post.uid.equals(myUid)) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> deletePost(post.id));
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }

        // LIKE Logic
        isLiked(post.id, holder.imgLikeIcon, holder.tvLikeCount);
        holder.btnLike.setOnClickListener(v -> {
            if (post.uid.equals(myUid)) {
                Toast.makeText(context, "Cannot like own post", Toast.LENGTH_SHORT).show();
            } else {
                toggleLike(post);
            }
        });

        // COMMENT Logic
        holder.btnComment.setOnClickListener(v -> showCommentDialog(post));
    }

    private void deletePost(String postId) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Report")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseDatabase.getInstance().getReference("posts").child(postId).removeValue();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleLike(Post post) {
        final DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("likes");
        likesRef.child(post.id).child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    likesRef.child(post.id).child(myUid).removeValue();
                } else {
                    likesRef.child(post.id).child(myUid).setValue("liked");
                    sendNotification(post.uid, post.id, "Someone liked your report!");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void isLiked(String postId, ImageView imageView, TextView textView) {
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("likes").child(postId);
        likesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                textView.setText(snapshot.getChildrenCount() + " Likes");
                if (snapshot.child(myUid).exists()) {
                    imageView.setColorFilter(context.getResources().getColor(R.color.alert_red));
                } else {
                    imageView.setColorFilter(context.getResources().getColor(R.color.text_secondary_light));
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showCommentDialog(Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Add Comment");
        final EditText input = new EditText(context);
        input.setHint("Type comment...");
        builder.setView(input);

        builder.setPositiveButton("Post", (dialog, which) -> {
            String comment = input.getText().toString().trim();
            if (!comment.isEmpty()) {
                sendNotification(post.uid, post.id, "Comment: " + comment);
                Toast.makeText(context, "Comment sent", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendNotification(String targetUid, String postId, String message) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(targetUid);
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("message", message);
        hashMap.put("postId", postId);
        hashMap.put("senderId", myUid);
        hashMap.put("timestamp", System.currentTimeMillis());
        notifRef.push().setValue(hashMap);
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class FeedViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvTime, tvDesc, tvLikeCount;
        ImageView imgPost, imgLikeIcon;
        ImageButton btnDelete;
        LinearLayout btnLike, btnComment;

        public FeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthor = itemView.findViewById(R.id.tvPostAuthor);
            tvTime = itemView.findViewById(R.id.tvPostTime);
            tvDesc = itemView.findViewById(R.id.tvPostDesc);
            imgPost = itemView.findViewById(R.id.imgPostImage);
            btnDelete = itemView.findViewById(R.id.btnDeletePost);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
            imgLikeIcon = itemView.findViewById(R.id.imgLikeIcon);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
        }
    }
}