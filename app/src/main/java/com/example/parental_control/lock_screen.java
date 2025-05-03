package com.example.parental_control;

import android.app.ActivityManager;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.widget.*;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class lock_screen extends AppCompatActivity {

    private EditText passwordInput;
    private Button unlockButton;
    private DatabaseReference mDatabase;
    private FirebaseAuth firebaseAuth;
    private View decorView;
    private Handler uiHandler = new Handler();
    private ConstraintLayout rootLayout;
    private LinearLayout navigationButtons;
    private int initialBottomMargin;

    String correctPassword = "";
    String userId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Window configuration
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.activity_lock_screen);

        // View initialization
        decorView = getWindow().getDecorView();
        rootLayout = findViewById(R.id.lockScreenLayout);
        navigationButtons = findViewById(R.id.navigationButtons);
        initialBottomMargin = ((ConstraintLayout.LayoutParams) navigationButtons.getLayoutParams()).bottomMargin;

        // Keyboard height detection
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect rect = new Rect();
            decorView.getWindowVisibleDisplayFrame(rect);
            int screenHeight = decorView.getHeight();
            int keyboardHeight = screenHeight - rect.bottom;

            adjustForKeyboard(keyboardHeight);
            enforceImmersiveMode();
        });

        // Kiosk mode activation
        activateKioskMode();

        // UI setup
        initializeUIComponents();
        setupFirebase();
        setupNavigationButtons();
    }

    private void adjustForKeyboard(int keyboardHeight) {
        runOnUiThread(() -> {
            ConstraintLayout.LayoutParams params =
                    (ConstraintLayout.LayoutParams) navigationButtons.getLayoutParams();

            if (keyboardHeight > 100) { // Keyboard visible
                params.bottomMargin = keyboardHeight + initialBottomMargin;
            } else {
                params.bottomMargin = initialBottomMargin;
            }
            navigationButtons.setLayoutParams(params);
        });
    }

    private void activateKioskMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            }
        }
    }

    private void initializeUIComponents() {
        passwordInput = findViewById(R.id.passwordInput);
        unlockButton = findViewById(R.id.unlockButton);
        unlockButton.setEnabled(false);

        passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) uiHandler.post(this::enforceImmersiveMode);
        });

        unlockButton.setOnClickListener(v -> attemptUnlock());

        // Back button handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showSystemUIWarning();
            }
        });
    }

    private void setupFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = firebaseAuth.getCurrentUser() != null ?
                firebaseAuth.getCurrentUser().getUid() : "anonymous";

        mDatabase.child("users").child(userId).child("screenTime").child("password").get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        correctPassword = snapshot.getValue(String.class);
                        unlockButton.setEnabled(true);
                    } else {
                        showToast("Password not set by parent");
                    }
                })
                .addOnFailureListener(e -> showToast("Error fetching password"));
    }

    private void setupNavigationButtons() {
        int[] buttonIds = {R.id.custom_back, R.id.custom_home, R.id.custom_recent};
        for (int id : buttonIds) {
            findViewById(id).setOnClickListener(v -> showSystemUIWarning());
        }
    }

    private void attemptUnlock() {
        String entered = passwordInput.getText().toString();
        if (entered.equals(correctPassword)) {
            showToast("Unlocked!");
            uiHandler.postDelayed(this::exitKioskMode, 1500);
        } else {
            showToast("Incorrect Password");
            passwordInput.setText("");
        }
    }

    private void exitKioskMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask();
        }
        startActivity(new Intent(this, paired_successfull.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void enforceImmersiveMode() {
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(
                    WindowInsets.Type.navigationBars() |
                            WindowInsets.Type.statusBars()
            );
        }
    }

    private void showSystemUIWarning() {
        showToast("Mobile is still in Lock Mode");
        showCustomOverlayToast();
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    // Hardware key handling
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            showSystemUIWarning();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showCustomOverlayToast() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    Settings.canDrawOverlays(this)) {

                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    TextView overlay = createOverlayView();
                    WindowManager.LayoutParams params = createOverlayParams();

                    wm.addView(overlay, params);
                    uiHandler.postDelayed(() -> removeOverlay(wm, overlay), 2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private TextView createOverlayView() {
        TextView overlay = new TextView(this);
        overlay.setText("Mobile is still in Lock Mode");
        overlay.setTextColor(Color.WHITE);
        overlay.setBackgroundColor(Color.parseColor("#CC000000"));
        overlay.setPadding(100, 200, 100, 200);
        overlay.setTextSize(18);
        overlay.setGravity(Gravity.CENTER);
        return overlay;
    }

    private WindowManager.LayoutParams createOverlayParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
        );

        // Explicitly set gravity in Java
        params.gravity = Gravity.TOP;
        return params;
    }

    private void removeOverlay(WindowManager wm, View overlay) {
        try {
            if (wm != null && overlay != null && overlay.isShown()) {
                wm.removeView(overlay);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enforceImmersiveMode();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        showCustomOverlayToast();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }
}