package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class block_access extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_access);

        TextView textViewButton = findViewById(R.id.textViewButton);

        // Set a click listener on the TextView
        textViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to the next activity
                Intent intent = new Intent(block_access.this, youtube_age.class); // Replace 'NextActivity' with your target activity name
                startActivity(intent);
            }
        });
    }
}
