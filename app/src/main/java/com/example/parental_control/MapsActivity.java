package com.example.parental_control;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat; // Needed for ContextCompat
import androidx.fragment.app.FragmentActivity;

import android.graphics.Bitmap; // Needed for Bitmap
import android.graphics.Canvas; // Needed for Canvas
import android.graphics.drawable.Drawable; // Needed for Drawable
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor; // Needed for BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory; // Needed for BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.parental_control.databinding.ActivityMapsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnCameraMoveStartedListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private DatabaseReference mDatabase;
    private String generatedCode;
    private FirebaseAuth auth;
    private ValueEventListener locationListener;
    private Marker currentMarker;
    private boolean isUserInteracting = false;
    private float currentZoomLevel = 12; // Default zoom level

    // Variable to hold your custom icon
    private BitmapDescriptor childIconDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // *** Prepare the custom marker icon ***
        // Adjust the width and height (e.g., 80, 80) as needed for your image size
        childIconDescriptor = getBitmapDescriptorFromVector(R.drawable.final_child, 80, 80);
        if (childIconDescriptor == null) {
            Log.e("GoogleMapTAG", "Failed to create custom marker icon!");
            // Optionally fallback to default marker if custom fails
            // childIconDescriptor = BitmapDescriptorFactory.defaultMarker();
            Toast.makeText(this, "Error loading custom icon", Toast.LENGTH_SHORT).show();
        }


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        Objects.requireNonNull(mapFragment).getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Set up map listeners
        mMap.setOnCameraMoveStartedListener(this);

        // Initial position
        LatLng defaultLocation = new LatLng(33.6844, 73.0479); // Islamabad coordinates
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, currentZoomLevel));

        // Fetch the generated code and start listening for location updates
        fetchGeneratedCode();

        // Regarding "Location Permission Denied":
        // This error usually comes from the *device sending the location* (the child's device)
        // or if this app (the parent's app) tries to access its *own* location without permission.
        // Make sure:
        // 1. The CHILD APP has requested and been granted location permissions (ACCESS_FINE_LOCATION).
        // 2. The CHILD APP's location service is running and sending data to Firebase correctly.
        // 3. This PARENT APP has internet permission in its AndroidManifest.xml.
        // This code *reads* location from Firebase, it doesn't request device location itself.
    }

    @Override
    public void onCameraMoveStarted(int reason) {
        // Detect if the camera movement was caused by user gesture
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            isUserInteracting = true;
            Log.d("GoogleMapTAG", "User is interacting"); // Log interaction
        } else if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION ||
                reason == GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
            // Reset flag if movement is programmatic (location update, etc.)
            // Keep this simple as per original code intent
            isUserInteracting = false;
            Log.d("GoogleMapTAG", "Programmatic camera move"); // Log programmatic move
        }
        // Note: The original code only handled GESTURE and API_ANIMATION.
        // Adding DEVELOPER_ANIMATION covers more cases but keeps the core logic.
    }

    private void fetchGeneratedCode() {
        // --- This function remains exactly the same as your original code ---
        if (auth.getCurrentUser() == null) {
            Log.e("GoogleMapTAG", "User not logged in, cannot fetch code.");
            Toast.makeText(MapsActivity.this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = auth.getCurrentUser().getUid();

        mDatabase.child("users").child(userId).child("generatedCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            boolean codeFound = false;
                            for (DataSnapshot codeSnapshot : snapshot.getChildren()) {
                                generatedCode = codeSnapshot.getKey();
                                if (generatedCode != null && !generatedCode.isEmpty()) { // Basic check
                                    Log.d("GoogleMapTAG", "Fetched Generated Code: " + generatedCode);
                                    startListeningForLocationUpdates();
                                    codeFound = true;
                                    break; // Use the first valid code found
                                }
                            }
                            if (!codeFound) {
                                Log.e("GoogleMapTAG", "No valid child key found under generatedCode for user " + userId);
                                Toast.makeText(MapsActivity.this, "No valid pairing code found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e("GoogleMapTAG", "Path users/" + userId + "/generatedCode not found.");
                            Toast.makeText(MapsActivity.this, "No pairing code found for this user", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GoogleMapTAG", "Database error fetching code: " + error.getMessage());
                        Toast.makeText(MapsActivity.this, "Database error fetching code", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startListeningForLocationUpdates() {
        // --- This function remains exactly the same as your original code ---
        // --- EXCEPT for added null checks and logging ---
        if (generatedCode == null) {
            Log.e("GoogleMapTAG", "Generated code is null. Cannot listen for updates.");
            Toast.makeText(this, "Pairing code missing", Toast.LENGTH_SHORT).show();
            return;
        }
        if (auth.getCurrentUser() == null) {
            Log.e("GoogleMapTAG", "User is null. Cannot listen for updates.");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        // *** IMPORTANT: Double-check this Firebase path matches where the CHILD APP writes location data ***
        DatabaseReference locationRef = mDatabase.child("users").child(userId)
                .child("generatedCode").child(generatedCode).child("location");

        Log.d("GoogleMapTAG", "Setting up listener at path: " + locationRef.toString());


        // Remove previous listener if exists (using the exact same reference logic)
        if (locationListener != null) {
            try {
                // Attempt to remove listener from the same path it would be added to
                locationRef.removeEventListener(locationListener);
                Log.d("GoogleMapTAG", "Removed existing listener.");
            } catch (Exception e) {
                // May happen if listener was on a different ref somehow, log it
                Log.w("GoogleMapTAG", "Could not remove previous listener: " + e.getMessage());
            }
        }

        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("GoogleMapTAG", "Data received at listener path: " + dataSnapshot.toString()); // Log received data
                if (dataSnapshot.exists()) {
                    try {
                        Double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                        Double longitude = dataSnapshot.child("longitude").getValue(Double.class);

                        if (latitude != null && longitude != null) {
                            // Basic validation for coordinates
                            if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                                LatLng location = new LatLng(latitude, longitude);
                                updateMarkerPosition(location);

                                // Keep the toast as in original code
                                Toast.makeText(MapsActivity.this,
                                        "Location updated: " + String.format("%.4f", latitude) + ", " + String.format("%.4f", longitude),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e("GoogleMapTAG", "Invalid Lat/Lng values received: " + latitude + ", " + longitude);
                            }
                        } else {
                            Log.e("GoogleMapTAG", "Latitude or Longitude is null in snapshot: " + dataSnapshot.getValue());
                        }
                    } catch (Exception e) {
                        Log.e("GoogleMapTAG", "Error processing location snapshot: " + e.getMessage(), e);
                    }
                } else {
                    Log.w("GoogleMapTAG", "Location node does not exist at listened path.");
                    // Optional: Toast.makeText(MapsActivity.this, "Waiting for location data...", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("GoogleMapTAG", "Firebase listener cancelled: " + databaseError.getMessage());
                Toast.makeText(MapsActivity.this, "Location listener error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show(); // Show longer toast for errors
            }
        };

        // Add the listener to get real-time updates
        locationRef.addValueEventListener(locationListener);
        Log.d("GoogleMapTAG", "Location listener attached successfully.");
    }

    private void updateMarkerPosition(LatLng location) {
        if (mMap == null) {
            Log.e("GoogleMapTAG", "mMap is null, cannot update marker.");
            return; // Exit if map isn't ready
        }
        if (childIconDescriptor == null) {
            Log.e("GoogleMapTAG", "childIconDescriptor is null, cannot create/update marker with custom icon.");
            // Consider using default marker as fallback if icon failed to load
            // return; // Or just return without updating marker
            Toast.makeText(this, "Marker icon not ready", Toast.LENGTH_SHORT).show();
            return;
        }


        try {
            if (currentMarker == null) {
                // --- Create the marker WITH THE CUSTOM ICON ---
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(location)
                        .title("Child's Location") // Updated title slightly
                        .icon(childIconDescriptor) // Use the prepared custom icon
                        .anchor(0.5f, 0.5f); // Center the icon on the LatLng

                currentMarker = mMap.addMarker(markerOptions);
                Log.d("GoogleMapTAG", "Created new marker with custom icon at: " + location);

                // Initial camera position (logic remains same as original)
                if (!isUserInteracting) {
                    Log.d("GoogleMapTAG", "Animating camera to initial marker position (Zoom: " + currentZoomLevel + ")");
                    // Using animateCamera for smoother transition
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, currentZoomLevel), 1000, null);
                }
            } else {
                // --- Update the existing marker's position ---
                currentMarker.setPosition(location);
                Log.d("GoogleMapTAG", "Updated existing marker position to: " + location);


                // Only move camera if user isn't interacting (logic remains same as original)
                if (!isUserInteracting) {
                    // Get current zoom level before moving (logic remains same)
                    currentZoomLevel = mMap.getCameraPosition().zoom;
                    Log.d("GoogleMapTAG", "Animating camera to updated marker position (Keeping zoom: " + currentZoomLevel + ")");
                    // Using animateCamera for smoother transition, only changing LatLng
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(location), 1000, null);
                } else {
                    Log.d("GoogleMapTAG", "User is interacting, marker position updated but camera not moved.");
                }
            }

        } catch (Exception e) {
            Log.e("GoogleMapTAG", "Error adding or updating marker: " + e.getMessage(), e);
            Toast.makeText(this, "Error displaying location on map", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Helper method to create BitmapDescriptor from drawable with specific size ---
    private BitmapDescriptor getBitmapDescriptorFromVector(int resourceId, int width, int height) {
        Drawable vectorDrawable = ContextCompat.getDrawable(this, resourceId);
        if (vectorDrawable == null) {
            Log.e("MapHelper", "Drawable resource not found: " + resourceId);
            return null; // Return null if drawable not found
        }
        vectorDrawable.setBounds(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        try {
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e("MapHelper", "Error creating BitmapDescriptor: " + e.getMessage());
            return null; // Return null on error
        } finally {
            // It's good practice to recycle the bitmap if you are done with it,
            // but BitmapDescriptorFactory might hold a reference. Let's skip recycling here
            // to avoid potential "trying to use a recycled bitmap" errors.
            // if (bitmap != null && !bitmap.isRecycled()) {
            //     bitmap.recycle();
            // }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("GoogleMapTAG", "onDestroy: Cleaning up listener.");
        // --- Cleanup logic remains exactly the same as your original code ---
        // --- EXCEPT for added null checks and logging ---
        try {
            if (locationListener != null && auth != null && auth.getCurrentUser() != null && generatedCode != null && mDatabase != null) {
                // Reconstruct the exact path used for adding the listener
                String userId = auth.getCurrentUser().getUid();
                DatabaseReference locationRef = mDatabase.child("users").child(userId)
                        .child("generatedCode").child(generatedCode).child("location");

                locationRef.removeEventListener(locationListener);
                Log.i("GoogleMapTAG", "Successfully removed location listener for code: " + generatedCode);
            } else {
                Log.w("GoogleMapTAG", "Skipped removing listener in onDestroy - some required info was null.");
            }
        } catch (Exception e) { // Catch potential exceptions during removal
            Log.e("GoogleMapTAG", "Error removing listener in onDestroy: " + e.getMessage(), e);
        }
        binding = null; // Release view binding
    }
}