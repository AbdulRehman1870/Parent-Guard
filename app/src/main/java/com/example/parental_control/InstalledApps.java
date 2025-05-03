package com.example.parental_control;

import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InstalledApps extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_USAGE_STATS = 100;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_installed_apps);

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();

        // Check usage stats permission
        if (isUsageStatsPermissionGranted()) {
            uploadInstalledAppsWithUsage();
        } else {
            requestUsageStatsPermission();
        }
    }

    private boolean isUsageStatsPermissionGranted() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, currentTime - 1000 * 60, currentTime
        );
        return stats != null && !stats.isEmpty();
    }

    private void requestUsageStatsPermission() {
        Toast.makeText(this, "Permission required to access app usage stats.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivityForResult(intent, REQUEST_PERMISSION_USAGE_STATS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION_USAGE_STATS) {
            if (isUsageStatsPermissionGranted()) {
                uploadInstalledAppsWithUsage();
            } else {
                Toast.makeText(this, "Permission not granted. Cannot fetch installed apps.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void uploadInstalledAppsWithUsage() {
        Map<String, String> appsWithUsageTime = getInstalledAppsWithUsageTime();
        saveAppsToFirebase(appsWithUsageTime);
    }

    @SuppressLint("QueryPermissionsNeeded")
    private Map<String, String> getInstalledAppsWithUsageTime() {
        Map<String, String> appUsageMap = new HashMap<>();
        PackageManager packageManager = getPackageManager();
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - (24 * 60 * 60 * 1000); // Last 24 hours

        // Add all installed apps with default "0 mins" usage
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : installedApps) {
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                appUsageMap.put(appName, "0 mins");
            }
        }

        // Get usage stats for the last 24 hours
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, currentTime
        );

        if (usageStatsList != null) {
            for (UsageStats usageStats : usageStatsList) {
                String packageName = usageStats.getPackageName();
                long totalTimeInForeground = usageStats.getTotalTimeInForeground(); // Time in milliseconds
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        String appName = packageManager.getApplicationLabel(appInfo).toString();
                        long usageTimeInMinutes = totalTimeInForeground / (1000 * 60); // Convert to minutes
                        String usageTimeFormatted = usageTimeInMinutes > 0
                                ? usageTimeInMinutes + " mins"
                                : "0 mins";
                        appUsageMap.put(appName, usageTimeFormatted);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("InstalledApps", "App not found: " + packageName, e);
                }
            }
        }

        return appUsageMap;
    }

    private void saveAppsToFirebase(Map<String, String> appsWithUsageTime) {
        mDatabase.child("users").child(userId).child("installedApps").setValue(appsWithUsageTime)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Apps list with usage time uploaded successfully.", Toast.LENGTH_SHORT).show();
                        Log.d("Firebase", "Apps and usage time uploaded successfully.");
                    } else {
                        Toast.makeText(this, "Failed to upload apps list.", Toast.LENGTH_SHORT).show();
                        Log.e("Firebase", "Failed to upload apps list.", task.getException());
                    }
                });
    }
}
