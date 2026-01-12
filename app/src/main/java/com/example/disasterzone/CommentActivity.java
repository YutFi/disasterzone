package com.example.disasterzone;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disasterzone.adapter.CommentAdapter;
import com.example.disasterzone.model.Comment;
import com.example.disasterzone.model.Notification;
import com.example.disasterzone.model.Post;
import com.example.disasterzone.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CommentActivity extends AppCompatActivity {

    private RecyclerView recyclerComments;
    private CommentAdapter commentAdapter;
    private List<Comment> commentList;
    private EditText etComment;
    private Button btnPostComment;
    private ImageView btnBack;

    private String postId;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        postId = getIntent().getStringExtra("postId");
        if (postId == null) {
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        recyclerComments = findViewById(R.id.recyclerComments);
        recyclerComments.setLayoutManager(new LinearLayoutManager(this));

        etComment = findViewById(R.id.etComment);
        btnPostComment = findViewById(R.id.btnPostComment);
        btnBack = findViewById(R.id.btnBack);

        commentList = new ArrayList<>();
        commentAdapter = new CommentAdapter(commentList);
        recyclerComments.setAdapter(commentAdapter);

        loadComments();

        btnPostComment.setOnClickListener(v -> postComment());
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void postComment() {
        String commentText = etComment.getText().toString().trim();
        if (TextUtils.isEmpty(commentText)) return;

        String userId = mAuth.getCurrentUser().getUid();

        // 1. Fetch Current User's Name
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = "Anonymous";
                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) username = user.username;
                }

                // 2. Save the Comment
                saveCommentToDb(userId, username, commentText);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void saveCommentToDb(String userId, String username, String text) {
        String commentId = mDatabase.child("comments").child(postId).push().getKey();
        Comment comment = new Comment(commentId, userId, username, text, System.currentTimeMillis());

        if (commentId != null) {
            mDatabase.child("comments").child(postId).child(commentId).setValue(comment);
            etComment.setText("");

            // 3. Trigger the Notification with Details
            triggerInteractionNotification(username, text);
        }
    }

    private void triggerInteractionNotification(String commenterName, String commentText) {
        // Fetch Post details to get the Description/Title
        mDatabase.child("posts").child(postId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Post post = snapshot.getValue(Post.class);

                if (post != null && !post.userId.equals(mAuth.getCurrentUser().getUid())) {

                    String postTitle = post.description;
                    if (postTitle.length() > 20) postTitle = postTitle.substring(0, 20) + "...";

                    String message = commenterName + " commented: \"" + commentText + "\" on: " + postTitle;

                    DatabaseReference notifRef = mDatabase.child("notifications").child(post.userId);
                    String notifId = notifRef.push().getKey();
                    if (notifId != null) {
                        // Updated Constructor with postId
                        Notification notif = new Notification(notifId, message, "comment", postId, System.currentTimeMillis());
                        notifRef.child(notifId).setValue(notif);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadComments() {
        mDatabase.child("comments").child(postId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                commentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Comment c = ds.getValue(Comment.class);
                    if (c != null) commentList.add(c);
                }
                commentAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}