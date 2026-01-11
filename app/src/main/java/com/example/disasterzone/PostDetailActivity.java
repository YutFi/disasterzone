package com.example.disasterzone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.disasterzone.model.Post;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PostDetailActivity extends AppCompatActivity {

    private ImageView imgPost;
    private TextView tvUsername, tvDesc, tvDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        imgPost = findViewById(R.id.detailImgPost);
        tvUsername = findViewById(R.id.detailTvUsername);
        tvDesc = findViewById(R.id.detailTvDesc);
        tvDate = findViewById(R.id.detailTvDate);

        String postId = getIntent().getStringExtra("POST_ID");

        if (postId != null) {
            loadPostData(postId);
        } else {
            Toast.makeText(this, "Error loading post", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPostData(String postId) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("posts").child(postId);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Post post = snapshot.getValue(Post.class);
                if (post != null) {
                    tvUsername.setText(post.username);
                    tvDesc.setText(post.description);
                    tvDate.setText(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", post.timestamp));

                    if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
                        try {
                            byte[] decodedString = Base64.decode(post.imageUrl, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            imgPost.setImageBitmap(decodedByte);
                        } catch (Exception e) {
                            imgPost.setImageResource(R.drawable.ic_launcher_background);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}