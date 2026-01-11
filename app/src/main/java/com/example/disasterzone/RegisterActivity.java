package com.example.disasterzone;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

// Make sure you have created the User.java class as discussed before!
import com.example.disasterzone.model.User;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvGoToLogin; // Added this to handle the "Back to Login" text
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // --- BINDING VIEWS (Fixed to match your XML IDs) ---
        etUsername = findViewById(R.id.etUsername);

        // Your XML called this "etRegEmail", so we must use that ID here
        etEmail = findViewById(R.id.etRegEmail);

        // Your XML called this "etRegPassword", so we must use that ID here
        etPassword = findViewById(R.id.etRegPassword);

        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        // --- LISTENERS ---

        // Register Button Click
        btnRegister.setOnClickListener(v -> registerUser());

        // Back to Login Click
        tvGoToLogin.setOnClickListener(v -> {
            finish(); // Closes Register and goes back to Login
        });
    }

    private void registerUser() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 1. Validation
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Create User in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        // 3. Auth Success -> Save extra details to Realtime Database
                        String userId = mAuth.getCurrentUser().getUid();

                        // Create the User object (using the class we created earlier)
                        User user = new User(username, email);

                        FirebaseDatabase.getInstance().getReference("users")
                                .child(userId)
                                .setValue(user)
                                .addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful()) {
                                        // 4. Database Success -> Go to Feed
                                        Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(RegisterActivity.this, FeedActivity.class);
                                        // Clear history so they can't go back to register
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        Toast.makeText(RegisterActivity.this, "Database Error: " + dbTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });

                    } else {
                        // Registration Failed (e.g., email already exists)
                        String error = task.getException().getMessage();
                        Toast.makeText(RegisterActivity.this, "Reg Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }
}