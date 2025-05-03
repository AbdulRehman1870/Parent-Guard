package com.example.parental_control;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.parental_control.models.ChildLocationModel;
import com.example.parental_control.models.ChildModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Random;

public class signup extends AppCompatActivity {

    private EditText nameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button signupButton;
    private FirebaseAuth auth;
    private TextView generatedCodeTextView;
    private String generatedCode;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Find Views
        nameEditText = findViewById(R.id.name);
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.pass);
        confirmPasswordEditText = findViewById(R.id.cpass);
        signupButton = findViewById(R.id.signup_button);
        generatedCodeTextView = findViewById(R.id.generatedCode);

        // Generate and display the 4-digit code
        generatedCode = generateCode();
        generatedCodeTextView.setText(generatedCode);

        // Navigate to Login Screen
        TextView loginTextView = findViewById(R.id.textViewButton);
        loginTextView.setOnClickListener(v -> {
            Intent intent = new Intent(signup.this, login.class);
            startActivity(intent);
        });

        // Handle Signup
        signupButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            // Input Validation
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(signup.this, "Please enter your name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(signup.this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(signup.this, "Please enter your password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(confirmPassword)) {
                Toast.makeText(signup.this, "Please confirm your password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(signup.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // Register User in Firebase
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(signup.this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(signup.this, "User registered successfully!", Toast.LENGTH_SHORT).show();
                            clearFields();

                            // Get current user ID from Firebase Auth
                            String userId = auth.getCurrentUser().getUid();

                            // Store the generated code in the Firebase Realtime Database
                            databaseReference.child("users").child(userId).child("generatedCode").setValue(generatedCode)
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            Intent intent = new Intent(signup.this, child_setname.class);
                                            intent.putExtra("generatedCode", generatedCode);  // Pass the code to the next activity
                                            startActivity(intent);
                                        } else {
                                            Toast.makeText(signup.this, "Failed to save code to database", Toast.LENGTH_SHORT).show();
                                        }
                                    });

                        } else {
                            Toast.makeText(signup.this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private String generateCode() {
        Random random = new Random();
        int code = 1000 + random.nextInt(9000); // Generates a number between 1000 and 9999
        return String.valueOf(code);
    }

    private void clearFields() {
        nameEditText.setText("");
        emailEditText.setText("");
        passwordEditText.setText("");
        confirmPasswordEditText.setText("");
    }
}
