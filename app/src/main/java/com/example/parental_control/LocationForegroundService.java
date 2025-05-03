package com.example.parental_control;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LocationForegroundService extends Service {
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private DatabaseReference mDatabase;
    private FirebaseAuth firebaseAuth;
    private String generatedCode;
    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeComponents();
        createLocationRequest();
        createLocationCallback();
        fetchGeneratedCode(); // Always fetch code at service creation
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle the intent that started the service
        if (intent != null && intent.hasExtra("generatedCode")) {
            generatedCode = intent.getStringExtra("generatedCode");
            Log.d("LocationService", "Received generatedCode from intent: " + generatedCode);
        }

        createNotificationChannel();
        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startLocationUpdates();
        return START_STICKY;
    }

    private void initializeComponents() {
        firebaseAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);       // 5 seconds
        locationRequest.setFastestInterval(3000); // 3 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        sendLocationToFirebase(location);
                    }
                }
            }
        };
    }

    private void fetchGeneratedCode() {
        String userId = firebaseAuth.getCurrentUser() != null ?
                firebaseAuth.getCurrentUser().getUid() : "anonymous";

        mDatabase.child("users").child(userId).child("generatedCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            generatedCode = snapshot.getValue(String.class);
                            Log.d("LocationService", "Fetched generatedCode from Firebase: " + generatedCode);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("LocationService", "Error fetching code: " + error.getMessage());
                    }
                });
    }

    private void startLocationUpdates() {
        if (generatedCode == null) {
            Log.e("LocationService", "Cannot start updates - generatedCode is null");
            return;
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d("LocationService", "Location updates started");
        } else {
            Log.e("LocationService", "Location permission not granted");
            stopSelf();
        }
    }

    private void sendLocationToFirebase(Location location) {
        if (generatedCode == null) {
            Log.e("LocationService", "Cannot send location - generatedCode is null");
            return;
        }

        String userId = firebaseAuth.getCurrentUser() != null ?
                firebaseAuth.getCurrentUser().getUid() : "anonymous";

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", ServerValue.TIMESTAMP);

        mDatabase.child("users").child(userId).child("generatedCode")
                .child(generatedCode).child("location").setValue(locationData)
                .addOnSuccessListener(aVoid -> Log.d("LocationService", "Location updated"))
                .addOnFailureListener(e -> Log.e("LocationService", "Update failed", e));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Child Location Tracker")
                .setContentText("Tracking location in background")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        Log.d("LocationService", "Service destroyed");
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d("LocationService", "Location updates stopped");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}