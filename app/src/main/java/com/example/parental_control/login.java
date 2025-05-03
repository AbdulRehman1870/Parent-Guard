package com.example.parental_control;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;

import java.util.List;

public class login extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private TextView signUpTextView;

    private FirebaseAuth auth;
    private boolean keyboardSetupPending = false;

    private final String CUSTOM_KEYBOARD_ID = "com.example.parental_control/.MyKeyboardService";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.pass);
        loginButton = findViewById(R.id.signup_button);
        signUpTextView = findViewById(R.id.textViewButton);

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                Toast.makeText(getApplicationContext(), "Please enter email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                Toast.makeText(getApplicationContext(), "Please enter password", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(login.this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                Toast.makeText(getApplicationContext(), "Login successful!", Toast.LENGTH_SHORT).show();

                                // Start keyboard permission process
                                handleKeyboardSetup();

                            } else {
                                Toast.makeText(getApplicationContext(), "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        });

        signUpTextView.setOnClickListener(v -> {
            Intent intent = new Intent(login.this, signup.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (keyboardSetupPending) {
            // Only check after user returns from settings or picker
            if (isKeyboardEnabled() && isCustomKeyboardSelected()) {
                goToNextScreen();
            } else if (isKeyboardEnabled()) {
                showInputMethodPicker();
            }
        }
    }

    private void handleKeyboardSetup() {
        if (!isKeyboardEnabled()) {
            keyboardSetupPending = true;
            Intent enableIntent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(enableIntent);
        } else if (!isCustomKeyboardSelected()) {
            keyboardSetupPending = true;
            showInputMethodPicker();
        } else {
            goToNextScreen();
        }
    }

    private void showInputMethodPicker() {
        InputMethodManager imeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imeManager != null) {
            imeManager.showInputMethodPicker();
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> enabledInputs = imm.getEnabledInputMethodList();
            for (InputMethodInfo inputMethod : enabledInputs) {
                if (inputMethod.getId().equals(CUSTOM_KEYBOARD_ID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCustomKeyboardSelected() {
        String currentKeyboard = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return currentKeyboard != null && currentKeyboard.equals(CUSTOM_KEYBOARD_ID);
    }

    private void goToNextScreen() {
        keyboardSetupPending = false;
        Intent intent = new Intent(login.this, Enter_code.class);
        startActivity(intent);
        finish();
    }
}

