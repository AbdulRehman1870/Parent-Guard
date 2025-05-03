package com.example.parental_control;

// Aapke purane imports waise hi rahenge, plus Pie Chart ke liye naye
import android.graphics.Color; // Pie chart colors ke liye
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
// import android.widget.ImageView; // REMOVED, as updateUsedAppsSection is removed
// import android.widget.LinearLayout; // REMOVED, as usedAppsLayout is removed
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.animation.Easing; // Animations ke liye
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart; // PieChart import
import com.github.mikephil.charting.components.Legend; // Legend ke liye
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData; // PieData import
import com.github.mikephil.charting.data.PieDataSet; // PieDataSet import
import com.github.mikephil.charting.data.PieEntry; // PieEntry import
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter; // Percentages ke liye
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.*;

public class Dasboard_Fragment extends Fragment {

    private static final String TAG = "Dasboard_Fragment";

    private TextView screenTimeTextView;
    // private LinearLayout usedAppsLayout; // REMOVED
    private BarChart usageBarChart; // Renamed from usageChart for clarity
    private PieChart categoryPieChart; // NEW
    private DatabaseReference userAppsDatabaseReference; // Renamed from mDatabase

    // App categorization keywords
    private final List<String> socialKeywords = Arrays.asList("whatsapp", "facebook", "instagram", "twitter", "snapchat", "tiktok", "linkedin", "telegram", "discord", "messenger");
    private final List<String> educationalKeywords = Arrays.asList("teams", "meet", "WPS Office", "camscanner", "notes", "google classroom", "canva", "calculator", "chatgpt", "learn", "study", "education", "skillshare", "solo learn", "programming hub");
    private final List<String> gamesKeywords = Arrays.asList("candy crush", "pubg", "free fire", "magic tiles 3", "subway surfer", "temple run", "clash of clans", "ludo");


    public Dasboard_Fragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_dasboard_, container, false); // XML file ka naam check karein

        screenTimeTextView = rootView.findViewById(R.id.screenTimeTextView);
        // usedAppsLayout = rootView.findViewById(R.id.usedAppsLayout); // REMOVED
        usageBarChart = rootView.findViewById(R.id.usageChart); // Ensure ID is usageChart in XML
        categoryPieChart = rootView.findViewById(R.id.CategoryPieChart); // Ensure ID is categoryPieChart in XML
        // LinearLayout locationSection = rootView.findViewById(R.id.location_section); // REMOVED

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            // Path "users/{userId}/installedApps" use karenge, jaisa aapke purane code mein tha
            userAppsDatabaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId).child("installedApps");
            Log.d(TAG, "Fetching data for userId: " + userId + " from path: " + userAppsDatabaseReference.toString());
            fetchUsageData(); // Renamed for clarity
        } else {
            Log.e(TAG, "User not logged in.");
            showError("User not logged in.");
            if (screenTimeTextView != null) screenTimeTextView.setText("N/A");
            if (usageBarChart != null) {
                usageBarChart.setNoDataText("Please login to see app usage.");
                usageBarChart.invalidate();
            }
            if (categoryPieChart != null) {
                categoryPieChart.setNoDataText("Please login to see app usage categories.");
                categoryPieChart.invalidate();
            }
        }
        // locationSection.setOnClickListener(v -> openLocationActivity()); // REMOVED
        return rootView;
    }

    // private void openLocationActivity() { // REMOVED
    //     ...
    // }

    // AppUsage class aapke purane code jaisi hi rahegi
    private static class AppUsage implements Comparable<AppUsage> {
        String appName;
        long usageMinutes;

        AppUsage(String appName, long usageMinutes) {
            this.appName = appName;
            this.usageMinutes = usageMinutes;
        }

        @Override
        public int compareTo(AppUsage other) {
            return Long.compare(other.usageMinutes, this.usageMinutes); // Descending
        }
    }

    private void fetchUsageData() { // Renamed from fetchTopUsedApps
        if (userAppsDatabaseReference == null) return;

        userAppsDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: Data received. Children count: " + dataSnapshot.getChildrenCount());
                List<AppUsage> appUsages = new ArrayList<>();
                long totalScreenTimeMinutes = 0;

                for (DataSnapshot appSnapshot : dataSnapshot.getChildren()) {
                    String appName = appSnapshot.getKey();
                    String usageString = appSnapshot.getValue(String.class);

                    if (appName == null || usageString == null) {
                        Log.w(TAG, "Skipping app with null name or usage: " + appName);
                        continue;
                    }
                    // Aapka purana, working convertUsageTimeToMinutes method use karenge
                    long usageMinutes = convertUsageTimeToMinutes(usageString);
                    Log.d(TAG, "App: " + appName + ", UsageString: '" + usageString + "', ParsedMinutes: " + usageMinutes);


                    if (usageMinutes > 0) {
                        appUsages.add(new AppUsage(appName, usageMinutes));
                        totalScreenTimeMinutes += usageMinutes;
                    }
                }
                Log.d(TAG, "Processed apps with usage > 0: " + appUsages.size() + ", Total Time: " + totalScreenTimeMinutes);


                Collections.sort(appUsages); // Descending order

                // Data for Bar Chart
                List<String> topAppNamesForBarChart = new ArrayList<>();
                List<Long> topAppTimesForBarChart = new ArrayList<>();
                int topLimit = Math.min(appUsages.size(), 6); // show top 6
                for (int i = 0; i < topLimit; i++) {
                    topAppNamesForBarChart.add(appUsages.get(i).appName);
                    topAppTimesForBarChart.add(appUsages.get(i).usageMinutes);
                }

                // Data for Pie Chart
                Map<String, Long> categorizedUsage = categorizeAppUsage(appUsages);

                updateUI(totalScreenTimeMinutes, topAppNamesForBarChart, topAppTimesForBarChart, categorizedUsage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase data fetch cancelled: " + databaseError.getMessage());
                showError("Failed to load app data: " + databaseError.getMessage());
            }
        });
    }

    // updateUI ab categorizedUsage bhi lega
    private void updateUI(long totalTime, List<String> barChartAppNames, List<Long> barChartUsageTimes, Map<String, Long> categorizedUsage) {
        if (screenTimeTextView != null) {
            screenTimeTextView.setText(formatTime(totalTime));
        }
        setupUsageBarChart(barChartAppNames, barChartUsageTimes); // Aapka purana Bar Chart setup
        setupCategoryPieChart(categorizedUsage); // Naya Pie Chart setup
        // updateUsedAppsSection(barChartAppNames); // REMOVED
    }

    // Aapka purana setupUsageChart method
    private void setupUsageBarChart(List<String> appNames, List<Long> usageTimes) {
        if (getContext() == null || usageBarChart == null) return;
        if (appNames.isEmpty()) {
            usageBarChart.clear();
            usageBarChart.setNoDataText("No app usage data for Bar Chart.");
            usageBarChart.invalidate();
            Log.d(TAG, "No data for Bar Chart, cleared.");
            return;
        }
        Log.d(TAG, "Setting up Bar Chart with " + appNames.size() + " apps.");

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < appNames.size(); i++) {
            entries.add(new BarEntry(i, usageTimes.get(i)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Usage Time (Top Apps)"); // Title updated
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f); // Slightly smaller text
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + "m";
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        usageBarChart.setData(barData);
        usageBarChart.setFitBars(true);
        usageBarChart.getDescription().setEnabled(false);
        usageBarChart.animateY(1000);
        usageBarChart.setDrawGridBackground(false);
        usageBarChart.getLegend().setEnabled(false);
        usageBarChart.setExtraBottomOffset(30f);


        XAxis xAxis = usageBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(appNames));
        xAxis.setLabelRotationAngle(-10); // Rotated for better visibility
        xAxis.setTextSize(8f);
        xAxis.setDrawGridLines(false);


        YAxis leftAxis = usageBarChart.getAxisRight();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(10f); // Granularity based on data or fixed
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + "m";
            }
        });
        leftAxis.setTextSize(20f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setGridColor(Color.parseColor("#e0e0e0"));


        usageBarChart.getAxisRight().setEnabled(false);
        usageBarChart.invalidate();
    }

    // private void updateUsedAppsSection(List<String> appNames) { // REMOVED
    //     ...
    // }

    // private int getAppIconResource(String appName) { // REMOVED
    //    ...
    // }

    // Aapka purana, working convertUsageTimeToMinutes method
    private long convertUsageTimeToMinutes(String usage) {
        if (usage == null) {
            Log.w(TAG, "convertUsageTimeToMinutes: Input usage string is null.");
            return 0;
        }
        // Log.d(TAG, "convertUsageTimeToMinutes input: " + usage);
        String cleanedUsage = usage.toLowerCase().replaceAll("[^0-9hm]", "");
        // Log.d(TAG, "convertUsageTimeToMinutes cleaned: " + cleanedUsage);


        try {
            if (cleanedUsage.contains("h")) {
                String[] parts = cleanedUsage.split("h");
                int h = Integer.parseInt(parts[0]);
                int m = 0;
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    m = Integer.parseInt(parts[1].replace("m", ""));
                }
                return h * 60L + m;
            } else if (cleanedUsage.contains("m")) {
                return Long.parseLong(cleanedUsage.replace("m", ""));
            } else if (!cleanedUsage.isEmpty()){ // Assume minutes if no unit and not empty
                return Long.parseLong(cleanedUsage);
            } else {
                return 0; // Empty string after cleaning
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing usage time: '" + usage + "' (Cleaned: '" + cleanedUsage + "'). Error: " + e.getMessage());
            return 0;
        }
    }

    // Aapka purana formatTime method
    private String formatTime(long totalMinutes) {
        if (totalMinutes < 0) totalMinutes = 0;
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        return String.format(Locale.getDefault(), "%dh %02dm", hours, mins);
    }

    private void showError(String message) {
        if (getContext() != null && message != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        } else {
            Log.e(TAG, "showError: Context or message is null. Message: " + message);
        }
    }

    // --- Naya Pie Chart ke liye Methods ---
    private Map<String, Long> categorizeAppUsage(List<AppUsage> appUsages) {
        Map<String, Long> categoryMap = new LinkedHashMap<>();
        categoryMap.put("Social", 0L);
        categoryMap.put("Educational", 0L);
        categoryMap.put("Games", 0L);
        categoryMap.put("Others", 0L);

        for (AppUsage app : appUsages) {
            String appIdentifier = app.appName.toLowerCase(); // Using appName (key from Firebase)
            boolean categorized = false;

            for (String keyword : gamesKeywords) {
                if (appIdentifier.contains(keyword)) {
                    categoryMap.put("Games", categoryMap.get("Games") + app.usageMinutes);
                    categorized = true;
                    break;
                }
            }
            if (categorized) continue;

            for (String keyword : socialKeywords) {
                if (appIdentifier.contains(keyword)) {
                    categoryMap.put("Social", categoryMap.get("Social") + app.usageMinutes);
                    categorized = true;
                    break;
                }
            }
            if (categorized) continue;

            for (String keyword : educationalKeywords) {
                if (appIdentifier.contains(keyword)) {
                    categoryMap.put("Educational", categoryMap.get("Educational") + app.usageMinutes);
                    categorized = true;
                    break;
                }
            }
            if (categorized) continue;

            categoryMap.put("Others", categoryMap.get("Others") + app.usageMinutes);
        }
        Log.d(TAG, "Categorized Usage for Pie Chart: " + categoryMap.toString());
        return categoryMap;
    }

    private void setupCategoryPieChart(Map<String, Long> categorizedUsage) {
        if (getContext() == null || categoryPieChart == null) return;

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        // Custom colors for categories
        int socialColor = Color.parseColor("#FF6384");
        int educationalColor = Color.parseColor("#36A2EB");
        int gamesColor = Color.parseColor("#FFCE56");
        int othersColor = Color.parseColor("#4BC0C0");

        if (categorizedUsage.getOrDefault("Social", 0L) > 0) {
            entries.add(new PieEntry(categorizedUsage.get("Social"), "Social"));
            colors.add(socialColor);
        }
        if (categorizedUsage.getOrDefault("Educational", 0L) > 0) {
            entries.add(new PieEntry(categorizedUsage.get("Educational"), "Educational"));
            colors.add(educationalColor);
        }
        if (categorizedUsage.getOrDefault("Games", 0L) > 0) {
            entries.add(new PieEntry(categorizedUsage.get("Games"), "Games"));
            colors.add(gamesColor);
        }
        if (categorizedUsage.getOrDefault("Others", 0L) > 0) {
            entries.add(new PieEntry(categorizedUsage.get("Others"), "Others"));
            colors.add(othersColor);
        }

        if (entries.isEmpty()) {
            categoryPieChart.clear();
            categoryPieChart.setNoDataText("No categorized usage data.");
            categoryPieChart.invalidate();
            Log.d(TAG, "No data for Pie Chart, cleared.");
            return;
        }
        Log.d(TAG, "Setting up Pie Chart with " + entries.size() + " categories.");


        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new PercentFormatter(categoryPieChart)); // Use PercentFormatter
        dataSet.setSliceSpace(2f);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        PieData pieData = new PieData(dataSet);
        categoryPieChart.setData(pieData);
        categoryPieChart.setUsePercentValues(true);
        categoryPieChart.getDescription().setEnabled(false);
        categoryPieChart.setExtraOffsets(15, 0, 15, 0);
        categoryPieChart.setDrawHoleEnabled(true);
        categoryPieChart.setHoleColor(Color.WHITE);
        categoryPieChart.setHoleRadius(45f);
        categoryPieChart.setTransparentCircleRadius(50f);
        categoryPieChart.setDrawCenterText(true);
        categoryPieChart.setCenterText("Usage\nCategories");
        categoryPieChart.setCenterTextSize(12f);
        categoryPieChart.setCenterTextColor(Color.DKGRAY);
        categoryPieChart.setEntryLabelTextSize(8f); // Text size for labels on slices
        categoryPieChart.setEntryLabelColor(Color.BLACK);

        categoryPieChart.setDrawEntryLabels(false);
        // Text color for labels on slices
        categoryPieChart.animateY(1200, Easing.EaseInOutQuad);

        Legend legend = categoryPieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setWordWrapEnabled(true);
        legend.setTextSize(10f);

        categoryPieChart.invalidate();
    }
}