package com.example.parental_control;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class paired_successfull extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_LOCATION_ENABLE = 2;
    private static final int REQUEST_IGNORE_BATTERY_OPTIMIZATION = 3;

    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference mDatabase;
    private FirebaseAuth firebaseAuth;
    private String generatedCode;

    private Handler handler = new Handler();
    private Runnable screenTimeChecker;

    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private boolean isRequestingOverlay = false;

    private boolean isScreenLocked = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paired_successfull);

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Fetch the generated code from Firebase
        fetchGeneratedCode();

        findViewById(R.id.paired).setOnClickListener(view -> {
            Intent intent = new Intent(paired_successfull.this, InstalledApps.class);
            startActivity(intent);
        });


        //         permission for apps
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Do nothing here — handle in onResume()
                }
        );

        if (!Settings.canDrawOverlays(this)) {
            isRequestingOverlay = true; // ✅ Set flag
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            startScreenTimeService(); // ✅ Already granted
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1000);
        }

    }


    @Override
    protected void onResume () {
        super.onResume();

        if (isRequestingOverlay) {
            isRequestingOverlay = false; // Reset flag

            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                startScreenTimeService(); // ✅ Start your service here
            } else {
                Toast.makeText(this, "Overlay permission still not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private void startScreenTimeService() {
        Intent serviceIntent = new Intent(this, ScreenTimeService.class);
        startService(serviceIntent);
    }








    private void fetchGeneratedCode() {
        String userId = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : "anonymous";

        mDatabase.child("users").child(userId).child("generatedCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            if (snapshot.exists()) {
                                // Get the raw value first
                                Object value = snapshot.getValue();

                                // Convert to string (handles cases where it might be stored as Long)
                                generatedCode = String.valueOf(value);

                                // Remove any non-digit characters (just in case)
                                generatedCode = generatedCode.replaceAll("[^0-9]", "");

                                // Validate the code is exactly 4 digits
                                if (generatedCode.length() != 4) {
                                    throw new Exception("Code must be exactly 4 digits");
                                }

                                Log.d("PairedActivity", "Successfully fetched Generated Code: " + generatedCode);
                                startScreenTimeTimer();
                                checkLocationPermissionAndEnable();
                            } else {
                                Toast.makeText(paired_successfull.this, "Failed to fetch code. Please try again.", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        } catch (Exception e) {
                            Toast.makeText(paired_successfull.this, "Invalid code format. Please check database.", Toast.LENGTH_SHORT).show();
                            Log.e("PairedActivity", "Error processing generated code: " + e.getMessage());
                            if (snapshot.exists()) {
                                Log.e("PairedActivity", "Actual database value type: " + snapshot.getValue().getClass().getSimpleName());
                                Log.e("PairedActivity", "Actual database value: " + snapshot.getValue());
                            }
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(paired_successfull.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("PairedActivity", "Error fetching code: " + error.getMessage());
                        finish();
                    }
                });
    }

    private void checkLocationPermissionAndEnable() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            checkLocationServicesAndStart();
        }
    }

    private void checkLocationServicesAndStart() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION_ENABLE);
        } else {
            checkBatteryOptimization();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATION);
            } else {
                startLocationService();
            }
        } else {
            startLocationService();
        }
    }

    private void startLocationService() {
        if (generatedCode == null || generatedCode.length() != 4) {
            Toast.makeText(this, "Cannot start service - invalid code", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        serviceIntent.putExtra("generatedCode", generatedCode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Background location tracking started", Toast.LENGTH_SHORT).show();
    }

    // Lock screen if time exceeded
    private void startScreenTimeTimer() {
        screenTimeChecker = new Runnable() {
            @Override
            public void run() {
                fetchScreenTimeDataFromFirebase(); // 👈 Always fetch fresh end time
                handler.postDelayed(this, 5000); // repeat after 1 min
            }
        };
        handler.post(screenTimeChecker); // initial call
    }

    // 🔥 Firebase se toTime le kar comparison
    private void fetchScreenTimeDataFromFirebase() {
        String userId = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : "anonymous";

        mDatabase.child("users").child(userId).child("screenTime")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String fromTime = snapshot.child("fromTime").getValue(String.class);
                            String toTime = snapshot.child("toTime").getValue(String.class);
                            String password = snapshot.child("password").getValue(String.class);

                            if (fromTime != null && toTime != null && password != null) {
                                long endTimeInMillis = convertTimeToMillis(toTime);
                                long startTimeInMillis = convertTimeToMillis(fromTime);

                                long currentTime = System.currentTimeMillis();

                                if (currentTime < endTimeInMillis) {
                                    long remainingMillis = endTimeInMillis - currentTime;
                                    long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
                                    long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60;

                                    String timeLeft = String.format(Locale.getDefault(),
                                            "⏳ Remaining Time: %02d:%02d Seconds", minutes, seconds);
                                    Toast.makeText(paired_successfull.this, timeLeft, Toast.LENGTH_SHORT).show();
                                } else {
                                    // Time is up! Lock screen
                                    if (!isScreenLocked) {
                                        isScreenLocked = true;
                                        Toast.makeText(paired_successfull.this,
                                                "⛔ Time limit exceeded! Locking screen...",
                                                Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(paired_successfull.this, lock_screen.class));
                                        finish();
                                    }
                                }
                            } else {
                                Toast.makeText(paired_successfull.this, "Some values missing", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(paired_successfull.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    // 🧠 Sirf comparison wala method
    private void lockScreenIfTimeExceeded(long endTimeInMillis) {
        long currentTime = System.currentTimeMillis();

        if (currentTime >= endTimeInMillis) {
            if (!isScreenLocked) { // 👉 Check if already locked
                isScreenLocked = true; // ✅ Set flag
                Toast.makeText(this, "Time limit exceeded! Locking screen.", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, lock_screen.class);
                startActivity(intent);
                finish();
            }
        } else {
            Log.d("LockScreen", "Screen is still active. Time not exceeded.");
        }
    }

    // 🕓 Convert "HH:mm" to milliseconds
    private long convertTimeToMillis(String timeString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = sdf.parse(timeString);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);

            // set same day
            Calendar now = Calendar.getInstance();
            calendar.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));

            return calendar.getTimeInMillis();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocationServicesAndStart();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOCATION_ENABLE) {
            checkLocationServicesAndStart();
        } else if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATION) {
            startLocationService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Service will continue running independently
    }
}