package com.example.parental_control;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 9000; // 1 second (Reduced for faster testing, you can set it back to 9000)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Your splash screen layout

        // For testing, you might want to reset the intro and onboarding flags:
        // Settings.getPreference(this).saveBooleanState(Settings.INTRO_COMPLETED, false);
        // Settings.getPreference(this).saveBooleanState(Settings.onBoarding, false); // Also reset onboarding if needed for full flow testing

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if intro has been completed
            boolean introCompleted = Settings.getPreference(this).getBooleanState(Settings.INTRO_COMPLETED);

            if (!introCompleted) {
                // Intro not completed, show IntroActivity
                Log.i("MainActivity", "Intro not completed. Starting IntroActivity.");
                Intent intent = new Intent(MainActivity.this, IntroActivity.class);
                startActivity(intent);
            } else {
                // Intro completed.
                // Now, regardless of the old onBoardingPref for block_access,
                // we will directly go to parentorchild.
                // The onBoardingPref will be handled within parentorchild or subsequent activities.
                Log.i("MainActivity", "Intro completed. Starting parentorchild activity directly.");
                Intent intent = new Intent(MainActivity.this, parentorchild.class);
                startActivity(intent);
            }
            finish(); // Close the splash activity
        }, SPLASH_DELAY);
    }
}