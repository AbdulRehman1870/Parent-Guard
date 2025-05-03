package com.example.parental_control;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;
import java.util.Objects;

public class set_time extends AppCompatActivity {

    EditText fromTimeEditText, toTimeEditText, passwordEditText, confirmPasswordEditText;
    Button saveButton;
    int fromHour, fromMinute, toHour, toMinute;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_time);

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();

        fromTimeEditText = findViewById(R.id.editTextFromTime);
        toTimeEditText = findViewById(R.id.editTextToTime);
        passwordEditText = findViewById(R.id.editTextPassword);
        confirmPasswordEditText = findViewById(R.id.editTextConfirmPassword);
        saveButton = findViewById(R.id.buttonSave);

        fromTimeEditText.setOnClickListener(v -> showTimePicker(fromTimeEditText, true));
        toTimeEditText.setOnClickListener(v -> showTimePicker(toTimeEditText, false));

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void showTimePicker(EditText editText, boolean isFromTime) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog dialog = new TimePickerDialog(this, (TimePicker view, int hourOfDay, int minute1) -> {
            String time = String.format("%02d:%02d", hourOfDay, minute1);
            editText.setText(time);
            if (isFromTime) {
                fromHour = hourOfDay;
                fromMinute = minute1;
            } else {
                toHour = hourOfDay;
                toMinute = minute1;
            }
        }, hour, minute, true);
        dialog.show();
    }

    private void saveSettings() {
        String fromTime = fromTimeEditText.getText().toString();
        String toTime = toTimeEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();

        if (TextUtils.isEmpty(fromTime) || TextUtils.isEmpty(toTime) ||
                TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Store data under user's ID
        mDatabase.child("users").child(userId).child("screenTime")
                .child("fromTime").setValue(fromTime)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mDatabase.child("users").child(userId).child("screenTime")
                                .child("toTime").setValue(toTime)
                                .addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        mDatabase.child("users").child(userId).child("screenTime")
                                                .child("password").setValue(password)
                                                .addOnCompleteListener(task2 -> {
                                                    if (task2.isSuccessful()) {
                                                        Toast.makeText(set_time.this, "Settings Saved Successfully", Toast.LENGTH_SHORT).show();
                                                        finish();
                                                    } else {
                                                        Toast.makeText(set_time.this, "Failed to save password", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    } else {
                                        Toast.makeText(set_time.this, "Failed to save end time", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(set_time.this, "Failed to save start time", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}