package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class pairing_parent extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing_parent);

        TextView pairingTextView = findViewById(R.id.textViewPairing); // Add ID to the TextView in XML
        Button nextButton = findViewById(R.id.button);

        // Retrieve the name from the intent
        Intent intent = getIntent();
        String userName = intent.getStringExtra("USER_NAME");

        if (userName != null && !userName.isEmpty()) {
            // Display the name in the TextView
            String pairingText = "To pair the device (" + userName + ") to your account you need it here in your hand. Have you got it?";
            pairingTextView.setText(pairingText);
        }

        // Set click listener for the button to navigate to the next activity
        nextButton.setOnClickListener(v -> {
            Intent nextIntent = new Intent(pairing_parent.this, signup.class);
            nextIntent.putExtra("USER_NAME", userName); // Pass USER_NAME to the next activity
            startActivity(nextIntent);
        });
    }
}
