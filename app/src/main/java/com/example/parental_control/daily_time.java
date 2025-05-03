package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class daily_time extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_daily_time);

        TextView textViewButton = findViewById(R.id.textViewButton);

        // Set a click listener on the TextView
        textViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(daily_time.this, vulgar_detection.class);
                startActivity(intent);

            }
        });


    }
}