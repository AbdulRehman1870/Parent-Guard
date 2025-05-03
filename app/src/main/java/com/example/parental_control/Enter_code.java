package com.example.parental_control;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Enter_code extends AppCompatActivity {

    private EditText box1, box2, box3, box4;
    private Button submitButton;
    private String generatedCode; // Variable to store the fetched code from Firebase
    private DatabaseReference databaseReference; // Firebase Database reference
    private FirebaseAuth auth; // Firebase Authentication
    private Settings settings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_code);

        settings = Settings.getPreference(this);
        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Find views by ID
        box1 = findViewById(R.id.box1);
        box2 = findViewById(R.id.box2);
        box3 = findViewById(R.id.box3);
        box4 = findViewById(R.id.box4);
        submitButton = findViewById(R.id.submitButton);

        // Fetch the generated code from Firebase
        fetchGeneratedCode();

        // Setup auto-shift for input fields
        setupAutoShift(box1, box2);
        setupAutoShift(box2, box3);
        setupAutoShift(box3, box4);
        setupBackShift(box2, box1);
        setupBackShift(box3, box2);
        setupBackShift(box4, box3);

        submitButton.setOnClickListener(v -> {
            // Concatenate input fields into a single code
            String enteredCode = box1.getText().toString().trim()
                    + box2.getText().toString().trim()
                    + box3.getText().toString().trim()
                    + box4.getText().toString().trim();

            // Validate the entered code
            if (validateCode(enteredCode)) {

                settings.saveStringState(Settings.pairId, enteredCode);

                Toast.makeText(this, "Pairing Successful!", Toast.LENGTH_SHORT).show();
                // Navigate to PairingSuccessful activity
                Intent intent = new Intent(Enter_code.this, paired_successfull.class);
                startActivity(intent);
                finish(); // Optional: Close the current activity
            } else {
                Toast.makeText(this, "Invalid Code. Please Try Again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Method to fetch the generated code from Firebase
    private void fetchGeneratedCode() {
        // Get the current user's ID
        String userId = auth.getCurrentUser().getUid();

        // Retrieve the generated code from Firebase Realtime Database
        databaseReference.child("users").child(userId).child("generatedCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Store the fetched code
                            generatedCode = snapshot.getValue(String.class);
                        } else {
                            Toast.makeText(Enter_code.this, "Failed to fetch code. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Enter_code.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Validate the entered code
    private boolean validateCode(String enteredCode) {
        // Check if the entered code matches the fetched code
        return enteredCode.equals(generatedCode);
    }

    // Auto-shift logic for next input field
    private void setupAutoShift(EditText current, EditText next) {
        current.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) { // Move to next field when input is complete
                    next.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // Back-shift logic for previous input field
    private void setupBackShift(EditText current, EditText previous) {
        current.setOnKeyListener((v, keyCode, event) -> {
            if (current.getText().toString().isEmpty() && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                previous.requestFocus(); // Move to previous field on backspace
            }
            return false;
        });
    }
}
