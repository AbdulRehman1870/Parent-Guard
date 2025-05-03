package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class child_setname extends AppCompatActivity {

    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_setname);

        settings = Settings.getPreference(this);

        EditText nameEditText = findViewById(R.id.editTextText); // Name input field
        EditText ageEditText = findViewById(R.id.editTextText2); // Age input field
        Button nextButton = findViewById(R.id.button);

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = nameEditText.getText().toString().trim();
                String ageInput = ageEditText.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(child_setname.this, "Please enter the name", Toast.LENGTH_SHORT).show();
                } else if (ageInput.isEmpty()) {
                    Toast.makeText(child_setname.this, "Please enter the age", Toast.LENGTH_SHORT).show();
                } else if (!ageInput.matches("\\d+")) { // Check if age is numeric
                    Toast.makeText(child_setname.this, "Age must be a number", Toast.LENGTH_SHORT).show();
                } else {
                    int age = Integer.parseInt(ageInput);
                    if (age < 5 || age > 18) {
                        Toast.makeText(child_setname.this, "Age must be between 5 and 18", Toast.LENGTH_SHORT).show();
                    } else {

                        settings.saveStringState(Settings.pairId, Settings.onBoarding);
                        // Pass the name to the next activity
                        Intent intent = new Intent(child_setname.this, dashboard.class);
                        intent.putExtra("USER_NAME", name); // Add the name to the intent
                        startActivity(intent);
                    }
                }
            }
        });
    }
}
