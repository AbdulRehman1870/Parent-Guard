package com.example.parental_control;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class location extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_location);

        Button button= findViewById(R.id.button_start);

        // Set a click listener on the TextView
        Activity context = this;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.getPreference(context).saveBooleanState(Settings.onBoarding, true);

                Intent intent = new Intent(location.this, parentorchild.class);
                startActivity(intent);

            }
        });


    }
}