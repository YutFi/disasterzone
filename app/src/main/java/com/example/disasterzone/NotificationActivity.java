package com.example.disasterzone;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disasterzone.adapter.NotificationAdapter;
import com.example.disasterzone.model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private List<Notification> notificationList;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // Setup Header Back Button
        ImageView btnBack = findViewById(R.id.btnBack); // Ensure this ID exists in XML (or remove if using default)
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // UI Setup
        recyclerView = findViewById(R.id.listNotifications); // Ensure ID matches XML
        tvEmpty = findViewById(R.id.tvEmptyState); // Create this TextView in XML or remove logic

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(this, notificationList);
        recyclerView.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(uid);

        notifRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Notification notif = ds.getValue(Notification.class);
                    if (notif != null) {
                        notificationList.add(notif);
                    }
                }
                // Show newest alerts first
                Collections.reverse(notificationList);
                adapter.notifyDataSetChanged();

                // Show/Hide Empty State
                if (notificationList.isEmpty()) {
                    if(tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    if(tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NotificationActivity.this, "Failed to load alerts", Toast.LENGTH_SHORT).show();
            }
        });
    }
}