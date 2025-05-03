package com.example.parental_control;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Display_Sentiments extends AppCompatActivity implements OnChartValueSelectedListener {

    private static final String TAG = "DisplaySentiments";
    private static final String CHANNEL_ID = "sentiment_alerts";
    private static final int NOTIFICATION_ID = 1;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 1001;

    // UI Elements
    private TextView dominantEmotionTextView;
    private TextView dominantEmotionEmojiTextView;
    private TextView dominantEmotionMessageTextView;
    private PieChart emotionsPieChart;
    private TextView originalTextTextView;
    private TextView timestampTextView;
    private CardView dominantEmotionCardView;
    private CardView analyzedTextCardView; // Added for visibility control
    private TextView statusTextView;
    private Spinner alertThresholdSpinner;
    private TextView viewOldSentimentsLink;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private List<Map<String, Object>> allSentimentsData = new ArrayList<>();
    private SimpleDateFormat firebaseTimestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat displayTimestampFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());


    private final Map<String, String> emotionFriendlyMessages = new HashMap<String, String>() {{
        put("sadness", "😢 Your child seems to be feeling sad. A good time to offer comfort and listen.");
        put("joy", "😊 Looks like your child is happy and content! Share in their joy.");
        put("love", "🥰 Feelings of love and warmth are strong. A wonderful moment for connection.");
        put("anger", "😠 Your child might be feeling angry or frustrated. Approach calmly and try to understand.");
        put("fear", "😨 It seems your child is feeling scared or anxious. Reassurance and safety are key.");
        put("surprise", "😮 Something might have surprised your child. Check in to see what's new!");
    }};

    private final Map<String, String> emotionEmojis = new HashMap<String, String>() {{
        put("sadness", "😢");
        put("joy", "😊");
        put("love", "🥰");
        put("anger", "😠");
        put("fear", "😨");
        put("surprise", "😮");
        put("default", "🤔");
    }};

    private final Map<String, Integer> emotionColors = new HashMap<>();

    // Threshold settings
    private int currentScoreThreshold = 70; // Default
    private static final String PREFS_NAME = "SentimentPrefs";
    private static final String KEY_THRESHOLD = "sentimentThreshold";
    private final List<Integer> thresholdValues = Arrays.asList(50, 60, 70, 80, 90);
    private Map<String, Object> latestSentimentForMainDisplay = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_sentiments);

        // Initialize UI Elements
        dominantEmotionTextView = findViewById(R.id.dominantEmotionTextView);
        dominantEmotionEmojiTextView = findViewById(R.id.dominantEmotionEmojiTextView);
        dominantEmotionMessageTextView = findViewById(R.id.dominantEmotionMessageTextView);
        emotionsPieChart = findViewById(R.id.emotionsPieChart);
        originalTextTextView = findViewById(R.id.originalTextTextView);
        timestampTextView = findViewById(R.id.timestampTextView);
        dominantEmotionCardView = findViewById(R.id.dominantEmotionCardView);
        analyzedTextCardView = findViewById(R.id.analyzedTextCardView);
        statusTextView = findViewById(R.id.statusTextView);
        alertThresholdSpinner = findViewById(R.id.alertThresholdSpinner);
        viewOldSentimentsLink = findViewById(R.id.viewOldSentimentsLink);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        loadThreshold();
        setupThresholdSpinner();
        populateEmotionColors();
        createNotificationChannel();
        checkNotificationPermission();
        setupPieChartStyle();

        viewOldSentimentsLink.setOnClickListener(v -> showOldSentimentsDialog());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showInitialLoadingState("Connecting to database...");
            signInAnonymously();
        } else {
            showInitialLoadingState("Loading sentiment data...");
            setupFirebaseListener(currentUser.getUid());
        }
    }

    private void showInitialLoadingState(String message) {
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
        dominantEmotionCardView.setVisibility(View.GONE);
        analyzedTextCardView.setVisibility(View.GONE);
        emotionsPieChart.setVisibility(View.GONE);
        viewOldSentimentsLink.setVisibility(View.GONE);
    }


    private void loadThreshold() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentScoreThreshold = prefs.getInt(KEY_THRESHOLD, 70); // Default to 70
    }

    private void saveThreshold(int threshold) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_THRESHOLD, threshold);
        editor.apply();
        currentScoreThreshold = threshold;
        Log.d(TAG, "Sentiment threshold saved: " + currentScoreThreshold + "%");
        // Re-process latest data for notifications if needed, or re-filter dialog if open
        if (latestSentimentForMainDisplay != null) {
            processLatestSentimentForMainDisplay(latestSentimentForMainDisplay);
        }
    }

    private void setupThresholdSpinner() {
        List<String> thresholdDisplayValues = new ArrayList<>();
        for (Integer val : thresholdValues) {
            thresholdDisplayValues.add(val + "%");
        }
        ArrayAdapter<String> thresholdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, thresholdDisplayValues);
        thresholdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        alertThresholdSpinner.setAdapter(thresholdAdapter);

        int currentThresholdIndex = thresholdValues.indexOf(currentScoreThreshold);
        if (currentThresholdIndex != -1) {
            alertThresholdSpinner.setSelection(currentThresholdIndex);
        } else { // Should not happen if default is in the list
            alertThresholdSpinner.setSelection(thresholdValues.indexOf(70));
            saveThreshold(70); // Save default if somehow not set
        }

        alertThresholdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedThreshold = thresholdValues.get(position);
                if (selectedThreshold != currentScoreThreshold) {
                    saveThreshold(selectedThreshold);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }


    private void populateEmotionColors() {
        emotionColors.put("sadness", ContextCompat.getColor(this, R.color.emotion_sadness));
        emotionColors.put("joy", ContextCompat.getColor(this, R.color.emotion_joy));
        emotionColors.put("love", ContextCompat.getColor(this, R.color.emotion_love));
        emotionColors.put("anger", ContextCompat.getColor(this, R.color.emotion_anger));
        emotionColors.put("fear", ContextCompat.getColor(this, R.color.emotion_fear));
        emotionColors.put("surprise", ContextCompat.getColor(this, R.color.emotion_surprise));
        emotionColors.put("default", ContextCompat.getColor(this, R.color.emotion_default));
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, POST_NOTIFICATIONS_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Notification permission is needed for alerts.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Sentiment Alerts";
            String description = "Notifications for strong child emotions detected";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) setupFirebaseListener(user.getUid());
            } else {
                showErrorState("Error connecting. Please check internet and try again.");
                Log.w(TAG, "signInAnonymously:failure", task.getException());
            }
        });
    }

    private String formatFirebaseKeyToDisplayLabel(String key) {
        if (key == null || key.isEmpty()) return "Unknown";
        return key.substring(0, 1).toUpperCase() + key.substring(1).toLowerCase();
    }

    private void setupFirebaseListener(String userId) {
        DatabaseReference sentimentsRef = mDatabase.child("users").child(userId).child("sentiments");

        // Listener for all sentiment data (for historical dialog)
        sentimentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allSentimentsData.clear();
                if (dataSnapshot.exists()) {
                    for (DataSnapshot sentimentSnapshot : dataSnapshot.getChildren()) {
                        try {
                            Map<String, Object> sentimentData = (Map<String, Object>) sentimentSnapshot.getValue();
                            if (sentimentData != null) {
                                // Add snapshot key if you need it for unique identification later
                                sentimentData.put("_key", sentimentSnapshot.getKey());
                                allSentimentsData.add(sentimentData);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing historical sentiment data", e);
                        }
                    }
                    // Sort by timestamp descending (newest first)
                    Collections.sort(allSentimentsData, (o1, o2) -> {
                        String ts1Str = (String) o1.get("timestamp");
                        String ts2Str = (String) o2.get("timestamp");
                        if (ts1Str == null || ts2Str == null) return 0;
                        try {
                            Date date1 = firebaseTimestampFormat.parse(ts1Str);
                            Date date2 = firebaseTimestampFormat.parse(ts2Str);
                            if (date1 != null && date2 != null) return date2.compareTo(date1);
                        } catch (ParseException e) { Log.e(TAG, "Timestamp parsing error for sorting", e); }
                        return 0;
                    });

                    if (!allSentimentsData.isEmpty()) {
                        latestSentimentForMainDisplay = allSentimentsData.get(0); // Newest is first
                        processLatestSentimentForMainDisplay(latestSentimentForMainDisplay);
                        viewOldSentimentsLink.setVisibility(View.VISIBLE);
                    } else {
                        showNoDataState("No sentiment data available yet. Monitoring activity...");
                    }
                } else {
                    showNoDataState("No sentiment data available yet. Monitoring activity...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showErrorState("Error loading data: " + databaseError.getMessage());
                Log.e(TAG, "Firebase data load cancelled: " + databaseError.getMessage());
            }
        });
    }

    private void processLatestSentimentForMainDisplay(Map<String, Object> sentimentData) {
        if (sentimentData == null) {
            showNoDataState("Received null sentiment data for main display.");
            return;
        }
        try {
            String originalText = (String) sentimentData.get("original_text");
            String timestamp = (String) sentimentData.get("timestamp");
            Map<String, Double> analysis = (Map<String, Double>) sentimentData.get("analysis");

            if (analysis == null || analysis.isEmpty()) {
                showNoDataState("No analysis data in the latest entry for main display.");
                return;
            }

            statusTextView.setVisibility(View.GONE);
            dominantEmotionCardView.setVisibility(View.VISIBLE);
            analyzedTextCardView.setVisibility(View.VISIBLE);
            emotionsPieChart.setVisibility(View.VISIBLE);

            originalTextTextView.setText(originalText != null ? "Text: " + originalText : "Text: N/A");
            if (timestamp != null) {
                try {
                    Date date = firebaseTimestampFormat.parse(timestamp);
                    timestampTextView.setText(date != null ? "Analyzed: " + displayTimestampFormat.format(date) : "Timestamp: N/A");
                } catch (ParseException e) {
                    timestampTextView.setText("Analyzed: " + timestamp); // Fallback to raw timestamp
                }
            } else {
                timestampTextView.setText("Timestamp: N/A");
            }

            updateEmotionDisplay(analysis); // For main card and pie chart
            prepareAndSendNotifications(analysis, originalText); // Pass original text for context

        } catch (Exception e) {
            Log.e(TAG, "Error processing latest sentiment data", e);
            showErrorState("Error processing latest data: " + e.getMessage());
        }
    }


    private void updateEmotionDisplay(Map<String, Double> analysis) {
        // ... (This method updates dominant emotion card and PieChart data, remains largely the same)
        // Crucially, for PieChart interactivity, ensure values are not drawn by default:
        if (analysis == null || analysis.isEmpty()) {
            // Handle appropriately, maybe clear chart or show "no data" on chart
            emotionsPieChart.clear();
            emotionsPieChart.setNoDataText("No emotion analysis available.");
            emotionsPieChart.invalidate();
            return;
        }

        List<PieEntry> pieEntries = new ArrayList<>();
        List<Integer> sliceColors = new ArrayList<>();
        String dominantEmotionKey = "";
        double maxScore = -1.0;

        // Sort for consistent dominant emotion and pie chart order (optional but good practice)
        List<Map.Entry<String, Double>> sortedAnalysis = new ArrayList<>(analysis.entrySet());
        Collections.sort(sortedAnalysis, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        for (Map.Entry<String, Double> entry : sortedAnalysis) {
            String labelKey = entry.getKey();
            double score = entry.getValue();

            if (score > 0.01) { // Threshold to display a slice
                pieEntries.add(new PieEntry((float) score, formatFirebaseKeyToDisplayLabel(labelKey)));
                sliceColors.add(emotionColors.getOrDefault(labelKey, emotionColors.get("default")));
            }
            if (score > maxScore) {
                maxScore = score;
                dominantEmotionKey = labelKey;
            }
        }

        // Update Dominant Emotion Card
        if (!dominantEmotionKey.isEmpty()) {
            String formattedDominantLabel = formatFirebaseKeyToDisplayLabel(dominantEmotionKey);
            dominantEmotionTextView.setText(String.format(Locale.getDefault(), "%s - %.0f%%", formattedDominantLabel, maxScore * 100));
            dominantEmotionEmojiTextView.setText(emotionEmojis.getOrDefault(dominantEmotionKey, emotionEmojis.get("default")));
            dominantEmotionMessageTextView.setText(emotionFriendlyMessages.getOrDefault(dominantEmotionKey, "Emotion detected."));
        } else {
            dominantEmotionTextView.setText("No dominant emotion");
            dominantEmotionEmojiTextView.setText(emotionEmojis.get("default"));
            dominantEmotionMessageTextView.setText("Detailed analysis below.");
        }

        // Update Pie Chart
        if (!pieEntries.isEmpty()) {
            PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
            pieDataSet.setColors(sliceColors);
            pieDataSet.setDrawValues(false); // IMPORTANT: Do not draw values by default
            pieDataSet.setSliceSpace(3f);
            pieDataSet.setSelectionShift(5f); // How much slice pops out on selection

            PieData pieData = new PieData(pieDataSet);
            // pieData.setValueFormatter(new PercentFormatter(emotionsPieChart)); // Not needed if values not drawn
            // pieData.setValueTextSize(12f);
            // pieData.setValueTextColor(Color.BLACK);

            emotionsPieChart.setData(pieData);
            emotionsPieChart.setUsePercentValues(true);
            emotionsPieChart.highlightValues(null); // Clear any previous highlights
            emotionsPieChart.setCenterText("Emotions"); // Reset center text
            emotionsPieChart.invalidate();
        } else {
            emotionsPieChart.clear();
            emotionsPieChart.setNoDataText("No significant emotions to display.");
            emotionsPieChart.invalidate();
        }
    }

    private void prepareAndSendNotifications(Map<String, Double> analysis, String originalText) {
        StringBuilder notificationContent = new StringBuilder();
        boolean highScoreFound = false;
        String dominantNotifEmotion = "";
        double dominantNotifScore = 0;

        if (analysis == null) return;

        for (Map.Entry<String, Double> entry : analysis.entrySet()) {
            String labelKey = entry.getKey();
            double score = entry.getValue() * 100;

            if (score >= currentScoreThreshold) { // Use dynamic threshold
                highScoreFound = true;
                if (score > dominantNotifScore) {
                    dominantNotifScore = score;
                    dominantNotifEmotion = labelKey;
                }
            }
        }

        if (highScoreFound && !dominantNotifEmotion.isEmpty()) {
            String message = emotionFriendlyMessages.get(dominantNotifEmotion);
            if (message != null) {
                notificationContent.append(message)
                        .append(" (Score: ").append(String.format(Locale.getDefault(), "%.0f%%", dominantNotifScore)).append(")");
                if (originalText != null && !originalText.isEmpty()) {
                    notificationContent.append("\nText: \"").append(originalText.substring(0, Math.min(originalText.length(), 50))).append(originalText.length() > 50 ? "..." : "").append("\"");
                }
                showNotification(notificationContent.toString().trim());
            }
        }
    }

    private void setupPieChartStyle() {
        emotionsPieChart.setUsePercentValues(true);
        emotionsPieChart.getDescription().setEnabled(false);
        emotionsPieChart.setExtraOffsets(5, 10, 5, 5);
        emotionsPieChart.setDragDecelerationFrictionCoef(0.95f);

        emotionsPieChart.setDrawHoleEnabled(true);
        emotionsPieChart.setHoleColor(Color.WHITE);
        emotionsPieChart.setTransparentCircleColor(Color.WHITE);
        emotionsPieChart.setTransparentCircleAlpha(110);
        emotionsPieChart.setHoleRadius(45f);
        emotionsPieChart.setTransparentCircleRadius(50f);

        emotionsPieChart.setDrawCenterText(true);
        emotionsPieChart.setCenterText("Emotions");
        emotionsPieChart.setCenterTextSize(16f);
        emotionsPieChart.setCenterTextColor(Color.DKGRAY);

// THIS IS THE KEY CHANGE: Disable drawing entry labels on slices
        emotionsPieChart.setDrawEntryLabels(false); // <--- ADDED THIS LINE

        emotionsPieChart.setRotationAngle(0);
        emotionsPieChart.setRotationEnabled(true);
        emotionsPieChart.setHighlightPerTapEnabled(true);
        emotionsPieChart.setOnChartValueSelectedListener(this); // Set listener

        Legend legend = emotionsPieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setWordWrapEnabled(true);
        legend.setTextSize(10f);
        legend.setFormSize(10f);
        legend.setXEntrySpace(7f);
        legend.setYEntrySpace(5f);
        legend.setEnabled(true);
    }

    // --- OnChartValueSelectedListener methods for PieChart ---
    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e instanceof PieEntry) {
            PieEntry pe = (PieEntry) e;
            emotionsPieChart.setCenterText(pe.getLabel() + "\n" + String.format(Locale.getDefault(), "%.0f%%", pe.getValue() * 100));
            // Make center text a bit more prominent when a value is selected
            emotionsPieChart.setCenterTextSize(18f);
            emotionsPieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    @Override
    public void onNothingSelected() {
        emotionsPieChart.setCenterText("Emotions");
        emotionsPieChart.setCenterTextSize(16f); // Reset size
        emotionsPieChart.setCenterTextTypeface(Typeface.DEFAULT); // Reset typeface
    }
    // --- End OnChartValueSelectedListener methods ---


    private void showNotification(String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted at showNotification.");
            return; // Don't Toast here, already handled in onRequestPermissionsResult or initial check
        }

        try {
            Intent intent = new Intent(this, Display_Sentiments.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification) // Ensure this drawable exists
                    .setContentTitle("Child Emotion Alert!")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setColor(ContextCompat.getColor(this, R.color.purple_500)) // Use a theme color
                    .setLights(ContextCompat.getColor(this, R.color.purple_500), 1000, 1000)
                    .setVibrate(new long[]{0, 500, 200, 500});

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            // Double check permission right before notifying on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission NOT granted at time of notify. Notification not sent.");
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) { // Catch broader exceptions for safety
            Log.e(TAG, "Failed to show notification", e);
        }
    }

    private void showOldSentimentsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_old_sentiments, null);
        builder.setView(dialogView);

        LinearLayout oldSentimentsContainer = dialogView.findViewById(R.id.oldSentimentsContainer);
        TextView dialogThresholdInfoTextView = dialogView.findViewById(R.id.dialogThresholdInfoTextView);
        TextView noOldSentimentsTextView = dialogView.findViewById(R.id.noOldSentimentsTextView);

        dialogThresholdInfoTextView.setText(String.format(Locale.getDefault(), "Showing entries where any emotion score was ≥ %d%%", currentScoreThreshold));
        oldSentimentsContainer.removeAllViews();
        boolean foundRelevant = false;

        for (Map<String, Object> sentimentData : allSentimentsData) {
            Map<String, Double> analysis = (Map<String, Double>) sentimentData.get("analysis");
            String originalText = (String) sentimentData.get("original_text");
            String timestampStr = (String) sentimentData.get("timestamp");

            boolean relevantEntry = false;
            if (analysis != null) {
                for (double score : analysis.values()) {
                    if (score * 100 >= currentScoreThreshold) {
                        relevantEntry = true;
                        break;
                    }
                }
            }

            if (relevantEntry) {
                foundRelevant = true;
                View entryView = inflater.inflate(R.layout.item_old_sentiment, oldSentimentsContainer, false); // Need to create this layout
                TextView oldText = entryView.findViewById(R.id.oldSentimentText);
                TextView oldTime = entryView.findViewById(R.id.oldSentimentTimestamp);
                TextView oldAnalysis = entryView.findViewById(R.id.oldSentimentAnalysis);

                oldText.setText(Html.fromHtml("<b>Text:</b> " + (originalText != null ? originalText : "N/A")));
                String formattedTime = "N/A";
                if (timestampStr != null) {
                    try {
                        Date date = firebaseTimestampFormat.parse(timestampStr);
                        if (date != null) formattedTime = displayTimestampFormat.format(date);
                    } catch (ParseException e) { formattedTime = timestampStr; }
                }
                oldTime.setText(Html.fromHtml("<b>Time:</b> " + formattedTime));

                StringBuilder analysisStr = new StringBuilder("<b>Analysis:</b><br/>");
                if (analysis != null) {
                    for (Map.Entry<String, Double> anEntry : analysis.entrySet()) {
                        analysisStr.append(String.format(Locale.getDefault(), "    • %s: %.0f%%<br/>",
                                formatFirebaseKeyToDisplayLabel(anEntry.getKey()),
                                anEntry.getValue() * 100));
                    }
                } else {
                    analysisStr.append("N/A");
                }
                oldAnalysis.setText(Html.fromHtml(analysisStr.toString()));
                oldSentimentsContainer.addView(entryView);

                // Add a divider
                View divider = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        2 // height of 2px
                );
                params.setMargins(0, 16, 0, 16); // 16dp margin top/bottom
                divider.setLayoutParams(params);
                divider.setBackgroundColor(Color.LTGRAY);
                oldSentimentsContainer.addView(divider);

            }
        }

        if (foundRelevant) {
            noOldSentimentsTextView.setVisibility(View.GONE);
            // Remove the last divider if it was added
            if (oldSentimentsContainer.getChildCount() > 0 &&
                    oldSentimentsContainer.getChildAt(oldSentimentsContainer.getChildCount() - 1) instanceof View &&
                    !(oldSentimentsContainer.getChildAt(oldSentimentsContainer.getChildCount() - 1) instanceof LinearLayout)) {
                oldSentimentsContainer.removeViewAt(oldSentimentsContainer.getChildCount() - 1);
            }
        } else {
            noOldSentimentsTextView.setVisibility(View.VISIBLE);
        }

        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void showNoDataState(String message) {
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
        dominantEmotionCardView.setVisibility(View.GONE);
        analyzedTextCardView.setVisibility(View.GONE);
        emotionsPieChart.setVisibility(View.GONE);
        viewOldSentimentsLink.setVisibility(View.GONE); // Hide link if no data at all
        if (emotionsPieChart.getData() != null) emotionsPieChart.clear();
        emotionsPieChart.setNoDataText(message);
        emotionsPieChart.invalidate();
    }

    private void showErrorState(String errorMessage) {
        statusTextView.setText(errorMessage);
        statusTextView.setVisibility(View.VISIBLE);
        dominantEmotionCardView.setVisibility(View.GONE);
        analyzedTextCardView.setVisibility(View.GONE);
        emotionsPieChart.setVisibility(View.GONE);
        viewOldSentimentsLink.setVisibility(View.GONE);
        if (emotionsPieChart.getData() != null) emotionsPieChart.clear();
        emotionsPieChart.setNoDataText("Error loading data.");
        emotionsPieChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If you have listeners that should be removed, do it here.
        // Firebase ValueEventListeners are typically removed automatically if tied to activity lifecycle
        // but explicit removal can be done if needed.
    }
}