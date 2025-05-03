package com.example.parental_control;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
// import java.util.Map; // Yeh import ab istemal nahi ho raha, hata sakte hain

public class DisplayInstalledApps extends AppCompatActivity {

    private RecyclerView recyclerViewApps;
    private AppsAdapter appsAdapter;
    private DatabaseReference mDatabase;
    private String userId;
    private FirebaseAuth auth;
    // Optional: Add a TextView for when the list is empty
    private TextView textViewEmptyList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_installed_apps);

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        if (auth.getCurrentUser() != null) {
            userId = auth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "User not authenticated. Please log in again.", Toast.LENGTH_LONG).show();
            // Aap yahan login screen pe redirect bhi kar sakte hain
            // finish(); // Close this activity if user is not authenticated
            return; // Stop further execution if user is not authenticated
        }

        // Initialize RecyclerView
        recyclerViewApps = findViewById(R.id.recycler_view_apps);
        textViewEmptyList = findViewById(R.id.textViewEmptyList); // Make sure to add this ID in your main layout if you want this feature

        recyclerViewApps.setLayoutManager(new LinearLayoutManager(this));
        appsAdapter = new AppsAdapter();
        recyclerViewApps.setAdapter(appsAdapter);

        // Fetch apps data from Firebase
        fetchInstalledAppsFromFirebase();
    }

    private void fetchInstalledAppsFromFirebase() {
        if (userId == null) { // Double check userId
            Log.e("Firebase", "User ID is null, cannot fetch data.");
            Toast.makeText(this, "Authentication error. Cannot fetch data.", Toast.LENGTH_SHORT).show();
            if (textViewEmptyList != null) {
                textViewEmptyList.setText("Authentication error. Cannot load app usage.");
                textViewEmptyList.setVisibility(View.VISIBLE);
            }
            recyclerViewApps.setVisibility(View.GONE);
            return;
        }

        mDatabase.child("users").child(userId).child("installedApps")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<AppData> appList = new ArrayList<>();
                        if (snapshot.exists()) {
                            for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                                String appName = appSnapshot.getKey();
                                String usageTimeValue = appSnapshot.getValue(String.class); // Assuming time is stored as a string
                                appList.add(new AppData(appName, usageTimeValue));
                            }
                        }

                        if (appList.isEmpty()) {
                            if (textViewEmptyList != null) {
                                textViewEmptyList.setText("No app usage data found for the child.");
                                textViewEmptyList.setVisibility(View.VISIBLE);
                            }
                            recyclerViewApps.setVisibility(View.GONE);
                        } else {
                            if (textViewEmptyList != null) {
                                textViewEmptyList.setVisibility(View.GONE);
                            }
                            recyclerViewApps.setVisibility(View.VISIBLE);
                            appsAdapter.setApps(appList);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(DisplayInstalledApps.this, "Failed to fetch data: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("Firebase", "Error fetching data", error.toException());
                        if (textViewEmptyList != null) {
                            textViewEmptyList.setText("Failed to load app usage data.");
                            textViewEmptyList.setVisibility(View.VISIBLE);
                        }
                        recyclerViewApps.setVisibility(View.GONE);
                    }
                });
    }

    // AppData class (data model)
    private static class AppData {
        String appName;
        String usageTime; // This could be raw milliseconds string or pre-formatted

        public AppData(String appName, String usageTime) {
            this.appName = appName;
            this.usageTime = usageTime;
        }

        public String getAppName() {
            return appName;
        }

        public String getUsageTime() {
            return usageTime;
        }
    }

    // AppsAdapter class (RecyclerView Adapter)
    private static class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppsViewHolder> {

        private List<AppData> apps = new ArrayList<>();

        public void setApps(List<AppData> apps) {
            this.apps.clear();
            this.apps.addAll(apps);
            notifyDataSetChanged(); // For simplicity. For better performance, use DiffUtil.
        }

        @NonNull
        @Override
        public AppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Inflate the custom layout (item_app_usage.xml)
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false);
            return new AppsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppsViewHolder holder, int position) {
            AppData appData = apps.get(position);
            holder.appName.setText(appData.getAppName());

            // Format usage time if it's stored in milliseconds
            // Agar aapka usageTime pehle se "1h 20m" jaisa formatted hai, to formatUsageTime ki zaroorat nahi.
            // Agar woh milliseconds (e.g., "3600000") hai, to yeh method kaam karega.
            holder.usageTime.setText(formatUsageTimeDisplay(appData.getUsageTime()));
        }

        // Helper method to format usage time from String (e.g., milliseconds string) to "Xh Ym Zs"
        private String formatUsageTimeDisplay(String usageTimeStr) {
            if (usageTimeStr == null || usageTimeStr.isEmpty()) {
                return "N/A";
            }
            try {
                long timeInMillis = Long.parseLong(usageTimeStr);
                if (timeInMillis < 0) return "N/A";

                long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % 60;

                StringBuilder sb = new StringBuilder();
                if (hours > 0) {
                    sb.append(hours).append("h ");
                }
                if (minutes > 0 || hours > 0) { // Show minutes if hours are present or minutes > 0
                    sb.append(String.format(Locale.getDefault(), "%02dm", minutes)).append(" ");
                }
                sb.append(String.format(Locale.getDefault(), "%02ds", seconds));

                return sb.toString().trim();

            } catch (NumberFormatException e) {
                // Agar 'usageTimeStr' pehle se "1 hr 20 min" jaisa formatted hai, to waisa hi return karein
                Log.w("AppsAdapter", "Usage time is not in millis, returning as is: " + usageTimeStr);
                return usageTimeStr;
            }
        }


        @Override
        public int getItemCount() {
            return apps.size();
        }

        // ViewHolder class
        static class AppsViewHolder extends RecyclerView.ViewHolder {
            TextView appName, usageTime;

            public AppsViewHolder(@NonNull View itemView) {
                super(itemView);
                // Get references to the TextViews from item_app_usage.xml
                appName = itemView.findViewById(R.id.textViewAppName);
                usageTime = itemView.findViewById(R.id.textViewUsageTime);
            }
        }
    }
}