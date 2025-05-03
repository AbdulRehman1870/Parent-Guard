package com.example.parental_control; // Ensure this matches your package name

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat; // Needed for ContextCompat
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap; // Needed for Bitmap
import android.graphics.Canvas; // Needed for Canvas
import android.graphics.Color;
import android.graphics.drawable.Drawable; // Needed for Drawable
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor; // Needed for BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory; // Needed for BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MapsGeofence extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private static final String TAG = "MapsGeofence";
    private GoogleMap mMap;
    private DatabaseReference mDatabase;
    private DatabaseReference safeZonesRef;
    private String userId;
    private String generatedCode;
    private Marker currentMarker;
    private List<SafeZone> safeZones = new ArrayList<>();
    private HashMap<String, Circle> mapCircles = new HashMap<>(); // To manage circles on map
    private boolean isUserInteracting = false;
    private float currentZoomLevel = 15;
    private HashMap<String, Boolean> zoneStatusMap = new HashMap<>();
    private ValueEventListener locationListener;
    private ChildEventListener safeZonesListener;
    private FirebaseAuth auth;

    private static final String CHANNEL_ID = "geofence_alerts";
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final double DEFAULT_SAFE_RADIUS = 50.0; // Default radius in meters
    private double currentRadiusForNewZones = DEFAULT_SAFE_RADIUS; // User configurable radius

    // UI Elements
    private Button btnSetRadius;

    private static final int[] ZONE_COLORS = {
            Color.argb(70, 255, 0, 0),    // Red
            Color.argb(70, 0, 255, 0),    // Green
            Color.argb(70, 0, 0, 255)     // Blue
    };
    private static final int[] ZONE_STROKE_COLORS = {
            Color.RED,
            Color.GREEN,
            Color.BLUE
    };

    // *** Variable to hold the custom child icon ***
    private BitmapDescriptor childIconDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps_geofence);

        btnSetRadius = findViewById(R.id.btnSetRadius);

        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in. Redirecting...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, login.class)); // Replace login.class
            finish();
            return;
        }
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();
        safeZonesRef = mDatabase.child("users").child(userId).child("safeZones");

        btnSetRadius.setOnClickListener(v -> showSetRadiusDialog());

        // *** Prepare the custom marker icon ***
        // Adjust the width and height (e.g., 80, 80) as needed for your image size
        childIconDescriptor = getBitmapDescriptorFromVector(R.drawable.final_child, 80, 80);
        if (childIconDescriptor == null) {
            Log.e(TAG, "Failed to create custom marker icon!");
            Toast.makeText(this, "Error loading custom icon", Toast.LENGTH_SHORT).show();
            // Optionally fallback: childIconDescriptor = BitmapDescriptorFactory.defaultMarker();
        }

        createNotificationChannel();
        checkAndRequestNotificationPermission();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        Objects.requireNonNull(mapFragment).getMapAsync(this);
    }

    // --- Helper method to create BitmapDescriptor from drawable with specific size ---
    // (Same helper method as before)
    private BitmapDescriptor getBitmapDescriptorFromVector(int resourceId, int width, int height) {
        Drawable vectorDrawable = ContextCompat.getDrawable(this, resourceId);
        if (vectorDrawable == null) {
            Log.e(TAG, "Drawable resource not found: " + resourceId);
            return null; // Return null if drawable not found
        }
        vectorDrawable.setBounds(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        try {
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating BitmapDescriptor: " + e.getMessage());
            return null; // Return null on error
        }
        // Note: Skipping bitmap.recycle() here intentionally to avoid potential issues with the descriptor
    }


    private void showSetRadiusDialog() {
        // --- This function remains exactly the same ---
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Safe Zone Radius");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_set_radius, findViewById(android.R.id.content), false);
        final EditText inputRadius = viewInflated.findViewById(R.id.editTextDialogRadius);
        inputRadius.setText(String.format(Locale.US, "%.0f", currentRadiusForNewZones)); // Show current radius
        inputRadius.setSelection(inputRadius.getText().length()); // Cursor at end

        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            dialog.dismiss();
            String radiusStr = inputRadius.getText().toString();
            if (TextUtils.isEmpty(radiusStr)) {
                Toast.makeText(MapsGeofence.this, "Radius cannot be empty. Using previous: " + currentRadiusForNewZones + "m", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double newRadius = Double.parseDouble(radiusStr);
                if (newRadius <= 0) {
                    Toast.makeText(MapsGeofence.this, "Radius must be positive. Using previous: " + currentRadiusForNewZones + "m", Toast.LENGTH_SHORT).show();
                } else if (newRadius > 20000) { // Max 20km
                    Toast.makeText(MapsGeofence.this, "Radius too large (max 20km). Setting to 20km.", Toast.LENGTH_SHORT).show();
                    currentRadiusForNewZones = 20000.0;
                } else {
                    currentRadiusForNewZones = newRadius;
                    Toast.makeText(MapsGeofence.this, "New safe zones will use radius: " + currentRadiusForNewZones + "m", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(MapsGeofence.this, "Invalid radius format. Using previous: " + currentRadiusForNewZones + "m", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }


    private void checkAndRequestNotificationPermission() {
        // --- This function remains exactly the same ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // --- This function remains mostly the same, just initializes map interactions ---
        mMap = googleMap;
        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isUserInteracting = true;
                Log.d(TAG, "User is interacting with map"); // Log interaction
            } else if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION ||
                    reason == GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
                // Resetting interaction flag on programmatic moves (like location updates)
                isUserInteracting = false;
                Log.d(TAG, "Programmatic camera move"); // Log programmatic move
            }
        });

        mMap.setOnMapLongClickListener(this); // For adding/removing geofences

        LatLng defaultLocation = new LatLng(33.6844, 73.0479); // Islamabad
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, currentZoomLevel));

        fetchGeneratedCode(); // Fetches code, then starts location listener
        loadSafeZonesFromFirebase(); // Load persistent safe zones
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // --- This function remains exactly the same (Geofence add/remove logic) ---
        // Check for removing an existing zone
        for (SafeZone zone : new ArrayList<>(safeZones)) { // Iterate over a copy for safe removal
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    latLng.latitude, latLng.longitude,
                    zone.getLatitude(), zone.getLongitude(), // Use getters from SafeZone POJO
                    results
            );

            if (results[0] <= zone.getRadius()) {
                removeSafeZone(zone.getId()); // Remove from Firebase and map
                Toast.makeText(this, "Safe zone removed", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Add new zone if limit not reached
        if (safeZones.size() >= 3) {
            Toast.makeText(this, "Maximum of 3 safe zones allowed.", Toast.LENGTH_SHORT).show();
            return;
        }

        int zoneIndex = 0;
        boolean[] colorUsed = new boolean[ZONE_COLORS.length];
        for(SafeZone existingZone : safeZones) {
            for(int i=0; i < ZONE_COLORS.length; i++) {
                // Check based on color assigned to the zone
                if(existingZone.getFillColor() == ZONE_COLORS[i]) {
                    colorUsed[i] = true;
                    break;
                }
            }
        }
        // Find the first unused color index
        for(int i=0; i < ZONE_COLORS.length; i++) {
            if(!colorUsed[i]) {
                zoneIndex = i;
                break;
            }
        }


        String newZoneId = safeZonesRef.push().getKey(); // Generate unique ID from Firebase
        if (newZoneId == null) {
            Toast.makeText(this, "Failed to create zone ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        SafeZone newZoneData = new SafeZone(
                newZoneId,
                latLng.latitude,
                latLng.longitude,
                currentRadiusForNewZones,
                ZONE_COLORS[zoneIndex], // Use the determined available index
                ZONE_STROKE_COLORS[zoneIndex] // Use the determined available index
        );

        // Save to Firebase. The ChildEventListener will handle adding to map.
        safeZonesRef.child(newZoneId).setValue(newZoneData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MapsGeofence.this, "Safe zone added with radius " + String.format(Locale.US, "%.0f", currentRadiusForNewZones) + "m", Toast.LENGTH_SHORT).show();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mMap.getCameraPosition().zoom));
                    // Check geofences immediately if child marker exists
                    if (currentMarker != null) {
                        checkAllGeofences(currentMarker.getPosition());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(MapsGeofence.this, "Failed to save zone: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addSafeZoneToMap(SafeZone zoneData) {
        // --- This function remains exactly the same (Draws geofence circles) ---
        if (mMap == null || zoneData == null) return;

        // Remove old circle if it exists (e.g., on update)
        if (mapCircles.containsKey(zoneData.getId())) {
            Objects.requireNonNull(mapCircles.get(zoneData.getId())).remove();
        }

        CircleOptions circleOptions = new CircleOptions()
                .center(new LatLng(zoneData.getLatitude(), zoneData.getLongitude()))
                .radius(zoneData.getRadius())
                .strokeColor(zoneData.getStrokeColor())
                .fillColor(zoneData.getFillColor())
                .strokeWidth(5)
                .clickable(false); // Geofence circles themselves aren't clickable here
        Circle circle = mMap.addCircle(circleOptions);

        mapCircles.put(zoneData.getId(), circle);

        // Update local list (ensure no duplicates by ID)
        boolean found = false;
        for (int i = 0; i < safeZones.size(); i++) {
            if (safeZones.get(i).getId().equals(zoneData.getId())) {
                safeZones.set(i, zoneData); // Update existing
                found = true;
                break;
            }
        }
        if (!found) {
            safeZones.add(zoneData);
        }

        zoneStatusMap.put(zoneData.getId(), false); // Initialize status
        if (currentMarker != null) {
            checkSingleGeofence(zoneData, currentMarker.getPosition()); // Check new zone
        }
    }

    private void removeSafeZone(String zoneId) {
        // --- This function remains exactly the same (Removes zone from Firebase) ---
        safeZonesRef.child(zoneId).removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Zone " + zoneId + " removed from Firebase."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove zone " + zoneId + " from Firebase: " + e.getMessage()));
        // The ChildEventListener's onChildRemoved will handle map and list cleanup
    }

    private void removeSafeZoneFromMap(String zoneId) {
        // --- This function remains exactly the same (Removes circle from map and local lists) ---
        if (mapCircles.containsKey(zoneId)) {
            Objects.requireNonNull(mapCircles.get(zoneId)).remove();
            mapCircles.remove(zoneId);
        }
        safeZones.removeIf(zone -> zone.getId().equals(zoneId));
        zoneStatusMap.remove(zoneId);
    }


    private void loadSafeZonesFromFirebase() {
        // --- This function remains exactly the same (Listens for geofence changes in Firebase) ---
        if (safeZonesListener != null) {
            safeZonesRef.removeEventListener(safeZonesListener); // Remove old listener if any
        }
        safeZonesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                SafeZone zoneData = snapshot.getValue(SafeZone.class);
                if (zoneData != null && snapshot.getKey() != null) {
                    zoneData.setId(snapshot.getKey()); // Ensure ID is set from snapshot key
                    addSafeZoneToMap(zoneData);
                    Log.d(TAG, "Loaded and added zone: " + zoneData.getId());
                } else {
                    Log.w(TAG, "Received null zone data or key onChildAdded: " + snapshot.getKey());
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                SafeZone zoneData = snapshot.getValue(SafeZone.class);
                if (zoneData != null && snapshot.getKey() != null) {
                    zoneData.setId(snapshot.getKey());
                    addSafeZoneToMap(zoneData); // Re-adds/updates the circle and data
                    Log.d(TAG, "Updated zone: " + zoneData.getId());
                } else {
                    Log.w(TAG, "Received null zone data or key onChildChanged: " + snapshot.getKey());
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String zoneId = snapshot.getKey();
                if (zoneId != null) {
                    removeSafeZoneFromMap(zoneId);
                    Log.d(TAG, "Removed zone from map: " + zoneId);
                } else {
                    Log.w(TAG, "Received null key onChildRemoved");
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {} // Not used

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase safe zones listener cancelled: " + error.getMessage());
                Toast.makeText(MapsGeofence.this, "Error loading safe zones.", Toast.LENGTH_SHORT).show();
            }
        };
        safeZonesRef.addChildEventListener(safeZonesListener);
    }


    private void fetchGeneratedCode() {
        // --- This function remains exactly the same ---
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot fetch code.");
            return; // Prevent crash if userId somehow becomes null
        }
        mDatabase.child("users").child(userId).child("generatedCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            boolean codeFound = false;
                            for (DataSnapshot codeSnapshot : snapshot.getChildren()) {
                                generatedCode = codeSnapshot.getKey();
                                if (generatedCode != null && !generatedCode.isEmpty()) {
                                    Log.d(TAG, "Fetched Generated Code: " + generatedCode);
                                    startListeningForLocationUpdates();
                                    codeFound = true;
                                    return; // Exit after finding the first valid code
                                }
                            }
                            if (!codeFound) {
                                Log.e(TAG, "No valid child key found under generatedCode for user " + userId);
                                Toast.makeText(MapsGeofence.this, "No valid pairing code found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Generated code path not found for user: " + userId);
                            Toast.makeText(MapsGeofence.this, "No pairing code found for this user", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error fetching generated code: " + error.getMessage());
                        Toast.makeText(MapsGeofence.this, "Database error fetching code", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startListeningForLocationUpdates() {
        // --- This function remains exactly the same (Starts listening for child location) ---
        if (generatedCode == null) {
            Log.e(TAG, "Generated code is null. Cannot listen for location updates.");
            Toast.makeText(this, "Pairing code missing, cannot track location.", Toast.LENGTH_LONG).show();
            return;
        }
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot construct location path.");
            return;
        }

        // *** IMPORTANT: Verify this path matches where the child app writes location ***
        DatabaseReference locationRef = mDatabase.child("users").child(userId)
                .child("generatedCode").child(generatedCode).child("location");

        Log.d(TAG, "Starting location listener at: " + locationRef.toString());

        // Remove previous listener if any
        if (locationListener != null) {
            try {
                locationRef.removeEventListener(locationListener);
                Log.d(TAG, "Removed previous location listener.");
            } catch (Exception e) {
                Log.w(TAG, "Could not remove previous location listener: " + e.getMessage());
            }
        }

        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Location data received: " + dataSnapshot.getValue());
                if (dataSnapshot.exists()) {
                    Double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = dataSnapshot.child("longitude").getValue(Double.class);

                    if (latitude != null && longitude != null) {
                        // Basic validation
                        if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                            LatLng location = new LatLng(latitude, longitude);
                            updateMarkerPosition(location); // Update the child marker
                            checkAllGeofences(location); // Check if child entered/exited zones
                        } else {
                            Log.w(TAG, "Received invalid Lat/Lng values: " + latitude + ", " + longitude);
                        }
                    } else {
                        Log.w(TAG, "Latitude or Longitude is null in Firebase snapshot.");
                    }
                } else {
                    Log.w(TAG, "Location data does not exist at path: " + locationRef.toString());
                    // Optionally notify user that location is not available
                    // Toast.makeText(MapsGeofence.this, "Waiting for child's location...", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase location listener cancelled: " + databaseError.getMessage());
                Toast.makeText(MapsGeofence.this, "Location tracking stopped: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        locationRef.addValueEventListener(locationListener);
        Log.d(TAG, "Location listener attached.");
    }

    private void updateMarkerPosition(LatLng location) {
        // --- This function is MODIFIED to use the custom icon ---
        if (mMap == null) {
            Log.e(TAG, "Map not ready, cannot update marker");
            return;
        }
        if (childIconDescriptor == null) {
            Log.e(TAG, "childIconDescriptor is null, cannot update marker with custom icon.");
            // Maybe add default marker as fallback here if needed
            // MarkerOptions fallbackOptions = new MarkerOptions().position(location).title("Child's Location");
            // if (currentMarker == null) currentMarker = mMap.addMarker(fallbackOptions);
            // else currentMarker.setPosition(location);
            Toast.makeText(this, "Child icon not loaded", Toast.LENGTH_SHORT).show();
            return;
        }


        if (currentMarker == null) {
            // *** Create marker WITH CUSTOM ICON ***
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(location)
                    .title("Child's Location")
                    .icon(childIconDescriptor) // Use the custom icon
                    .anchor(0.5f, 0.5f); // Center the icon

            currentMarker = mMap.addMarker(markerOptions);
            Log.d(TAG, "Created marker with custom icon at: " + location);

            // Initial camera movement (logic remains same)
            if (!isUserInteracting) {
                Log.d(TAG, "Animating camera to initial marker position");
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, currentZoomLevel), 1000, null);
                // We might not need to set isUserInteracting = false here,
                // as the onCameraMoveStarted listener handles programmatic moves.
            }
        } else {
            // *** Update existing marker position ***
            currentMarker.setPosition(location);
            Log.d(TAG, "Updated marker position to: " + location);

            // Camera movement for updates (logic remains same)
            if (!isUserInteracting) {
                // Maintain current zoom if user hasn't interacted, just move center
                // currentZoomLevel = mMap.getCameraPosition().zoom; // Update zoom level based on map? Or keep fixed? Let's keep original logic.
                Log.d(TAG, "Animating camera to updated marker position");
                mMap.animateCamera(CameraUpdateFactory.newLatLng(location), 1000, null); // Only move LatLng
            } else {
                Log.d(TAG, "User interacting, marker updated, camera not moved.");
            }
        }
    }

    private void checkAllGeofences(LatLng currentLocation) {
        // --- This function remains exactly the same (Iterates through zones) ---
        if (safeZones.isEmpty()) {
            // Log.d(TAG, "No safe zones defined to check against.");
            return;
        }
        // Log.d(TAG, "Checking location " + currentLocation + " against " + safeZones.size() + " zones.");
        for (SafeZone zone : new ArrayList<>(safeZones)) { // Iterate copy in case list changes
            checkSingleGeofence(zone, currentLocation);
        }
    }

    private void checkSingleGeofence(SafeZone zone, LatLng currentLocation) {
        // --- This function remains exactly the same (Checks distance and triggers notification) ---
        if(zone == null || zone.getId() == null) {
            Log.w(TAG,"Skipping check for null zone or zone with null ID.");
            return;
        }

        float[] results = new float[1];
        try {
            android.location.Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    zone.getLatitude(), zone.getLongitude(),
                    results
            );
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid Lat/Lng for distance calculation: " +
                    "Current: " + currentLocation + ", Zone(" + zone.getId() + "): " + zone.getLatitude() + "," + zone.getLongitude());
            return; // Cannot calculate distance
        }


        float distance = results[0];
        boolean nowInside = distance <= zone.getRadius();
        // Default to false if zone ID somehow not in map yet (shouldn't happen with init logic)
        boolean wasInside = zoneStatusMap.getOrDefault(zone.getId(), false);

        // Log state for debugging
        // Log.d(TAG, "Zone " + zone.getId() + ": Dist=" + distance + "m, Radius=" + zone.getRadius() + "m, NowInside=" + nowInside + ", WasInside=" + wasInside);


        if (nowInside != wasInside) {
            zoneStatusMap.put(zone.getId(), nowInside); // Update the status map

            // Try to find a display index/name
            int zoneDisplayIndex = -1;
            List<SafeZone> currentZoneList = new ArrayList<>(safeZones); // Use consistent list for indexing
            for(int i=0; i< currentZoneList.size(); i++){
                if(currentZoneList.get(i).getId().equals(zone.getId())){
                    zoneDisplayIndex = i + 1; // 1-based index for display
                    break;
                }
            }
            // Use the zone's ID or a generic name if index fails
            String zoneName = (zoneDisplayIndex != -1) ? "Safe Zone " + zoneDisplayIndex : "Safe Zone (" + zone.getId().substring(0, Math.min(5, zone.getId().length())) + ")";


            String message = nowInside ?
                    "Child has entered " + zoneName :
                    "Child has exited " + zoneName;

            Log.i(TAG, "Geofence Alert: " + message); // Log the alert
            showNotification(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void createNotificationChannel() {
        // --- This function remains exactly the same ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Geofence Alerts";
            String description = "Notifications for child entering/exiting safe zones";
            int importance = NotificationManager.IMPORTANCE_HIGH; // High importance for alerts
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setLightColor(Color.CYAN);
            channel.enableLights(true);
            channel.enableVibration(true); // Enable vibration
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC); // Show on lock screen

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
            }
        }
    }

    private void showNotification(String message) {
        // --- This function remains exactly the same ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted. Cannot show notification: " + message);
            // Maybe prompt user again or show an in-app message?
            Toast.makeText(this, "Notification Permission Needed: " + message, Toast.LENGTH_LONG).show(); // Inform user via Toast
            return;
        }

        int notificationId = (int) System.currentTimeMillis(); // Unique ID for each notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this drawable exists in res/drawable
                .setContentTitle("Parental Control Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure high priority
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Category for alerts
                .setAutoCancel(true) // Dismiss notification when tapped
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC); // Show on lock screen
        // Optional: Add intent to open the app when notification is tapped
        // Intent intent = new Intent(this, MapsGeofence.class);
        // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        // builder.setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Notification shown: " + message);
        } catch (SecurityException e) {
            // This might happen if POST_NOTIFICATIONS permission is revoked after check
            Log.e(TAG, "SecurityException showing notification (Permission likely revoked): " + e.getMessage());
            Toast.makeText(this, "Could not show notification: " + message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // --- This function remains exactly the same ---
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Notification permission granted by user.");
            } else {
                Toast.makeText(this, "Notification permission denied. Alerts may not be visible.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Notification permission denied by user.");
                // Optionally show a dialog explaining why the permission is needed
            }
        }
    }

    @Override
    protected void onDestroy() {
        // --- This function remains mostly the same, just ensures listener removal ---
        super.onDestroy();
        Log.d(TAG, "onDestroy: Cleaning up listeners and map.");
        // Remove Firebase listeners
        if (locationListener != null && auth != null && auth.getCurrentUser() != null && generatedCode != null && mDatabase != null) {
            try {
                String currentUserId = auth.getCurrentUser().getUid(); // Get fresh UID just in case
                DatabaseReference locationRef = mDatabase.child("users").child(currentUserId)
                        .child("generatedCode").child(generatedCode)
                        .child("location");
                locationRef.removeEventListener(locationListener);
                Log.i(TAG,"Removed location listener.");
            } catch (Exception e) { // Catch potential errors during removal
                Log.e(TAG, "Error removing location listener in onDestroy: " + e.getMessage());
            }
        } else {
            Log.w(TAG,"Skipped removing location listener - some components were null.");
        }

        if (safeZonesListener != null && safeZonesRef != null) {
            try {
                safeZonesRef.removeEventListener(safeZonesListener);
                Log.i(TAG,"Removed safe zones listener.");
            } catch (Exception e) {
                Log.e(TAG, "Error removing safe zones listener in onDestroy: " + e.getMessage());
            }
        } else {
            Log.w(TAG,"Skipped removing safe zones listener - listener or ref was null.");
        }

        // Clear map resources
        if (mMap != null) {
            mMap.clear(); // Clears markers, circles, etc.
            mMap = null; // Help garbage collection
        }
        mapCircles.clear();
        safeZones.clear();
        zoneStatusMap.clear(); // Clear status map as well
        Log.d(TAG, "Map cleared and local lists emptied.");
    }

    // --- POJO class for Safe Zones (Remains exactly the same) ---
    public static class SafeZone {
        private String id;
        private double latitude;
        private double longitude;
        private double radius;
        private int fillColor;
        private int strokeColor;

        // Default constructor required for calls to DataSnapshot.getValue(SafeZone.class)
        public SafeZone() {
        }

        public SafeZone(String id, double latitude, double longitude, double radius, int fillColor, int strokeColor) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public double getRadius() { return radius; }
        public void setRadius(double radius) { this.radius = radius; }
        public int getFillColor() { return fillColor; }
        public void setFillColor(int fillColor) { this.fillColor = fillColor; }
        public int getStrokeColor() { return strokeColor; }
        public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SafeZone safeZone = (SafeZone) o;
            return Objects.equals(id, safeZone.id); // ID is the primary identifier
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @NonNull
        @Override
        public String toString() {
            return "SafeZone{" +
                    "id='" + id + '\'' +
                    ", lat=" + latitude +
                    ", lon=" + longitude +
                    ", radius=" + radius +
                    '}';
        }
    }
}