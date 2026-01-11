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
import com.example.disasterzone.model.Post;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private Context context;
    private List<Post> postList;

    public PostAdapter(Context context, List<Post> postList) {
        this.context = context;
        this.postList = postList;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout item_post.xml
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);

        // 1. Set Username (Display Name)
        if (post.username != null && !post.username.isEmpty()) {
            holder.tvUser.setText(post.username);
        } else {
            holder.tvUser.setText("Anonymous Reporter");
        }

        // 2. Set Time
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
        String date = sdf.format(new Date(post.timestamp));
        holder.tvTime.setText(date);

        // 3. Set Description
        holder.tvDesc.setText(post.description);

        // 4. Set Image (Decode Base64 to Bitmap)
        if (post.imageBase64 != null && !post.imageBase64.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(post.imageBase64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgPost.setImageBitmap(decodedByte);
            } catch (Exception e) {
                holder.imgPost.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        } else {
            // Hide image view if no image exists (optional optimization)
            holder.imgPost.setImageResource(android.R.drawable.ic_menu_camera);
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    // --- VIEW HOLDER ---
    public static class PostViewHolder extends RecyclerView.ViewHolder {

        TextView tvUser, tvTime, tvDesc;
        ImageView imgPost;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            // FIX: Match the IDs exactly as they appear in item_post.xml

            // This was the error. We changed 'tvPostUser' to 'tvPostAuthor'
            tvUser = itemView.findViewById(R.id.tvPostAuthor);

            tvTime = itemView.findViewById(R.id.tvPostTime);
            tvDesc = itemView.findViewById(R.id.tvPostDesc);
            imgPost = itemView.findViewById(R.id.imgPostImage);
        }
    }
}