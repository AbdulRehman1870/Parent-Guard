package com.example.parental_control;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
// import android.widget.TableLayout; // No longer needed
// import android.widget.TableRow;    // No longer needed
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
// import androidx.core.widget.ImageViewCompat; // Not used

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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class output_Activity extends AppCompatActivity {

    private static final String TAG = "OutputActivity";
    private static final String CHANNEL_ID = "toxicity_alerts";
    private static final int NOTIFICATION_ID = 1;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 1001;

    private TextView originalTextTextView, timestampTextView, titleTextView;
    // private TableLayout toxicityTableLayout; // Removed
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private List<Map<String, Object>> allMistakesData = new ArrayList<>();
    private SimpleDateFormat firebaseTimestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat displayTimestampFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

    private final Map<String, String> notificationMessages = new HashMap<String, String>() {{
        put("toxic", "Alert: 🟥 Child may be engaging in/exposed to toxic content.");
        put("identity_hate", "Caution: 🟩 Activity shows signs of hate speech (identity, religion, ethnicity).");
        put("insult", "Warning: 🟦 Child may be using/viewing content with personal insults.");
        put("obscene", "Alert: 🟪 Obscene or inappropriate content detected.");
        put("severe_toxic", "Critical Warning: 🟨 Highly aggressive/harmful content detected.");
        put("threat", "Urgent Alert: 🟥 Child may be involved/exposed to threatening language.");
    }};

    private Spinner thresholdSpinner;
    private LinearLayout analysisBreakdownLayout; // Changed from visualSummaryLayout
    private int currentScoreThreshold = 70;
    private static final String PREFS_NAME = "ParentalControlPrefs";
    private static final String KEY_THRESHOLD = "scoreThreshold";
    private final List<Integer> thresholdValues = Arrays.asList(50, 60, 70, 80, 90);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_output);

        titleTextView = findViewById(R.id.titleTextView);
        originalTextTextView = findViewById(R.id.originalTextTextView);
        timestampTextView = findViewById(R.id.timestampTextView);
        // toxicityTableLayout = findViewById(R.id.toxicityTableLayout); // Removed
        thresholdSpinner = findViewById(R.id.thresholdSpinner);
        analysisBreakdownLayout = findViewById(R.id.analysisBreakdownLayout); // Updated ID

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        loadThreshold();
        setupThresholdSpinner();

        createNotificationChannel();
        checkNotificationPermission();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            signInAnonymously();
        } else {
            setupFirebaseListener(currentUser.getUid());
        }
    }

    private void loadThreshold() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentScoreThreshold = prefs.getInt(KEY_THRESHOLD, 70);
    }

    private void saveThreshold(int threshold) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_THRESHOLD, threshold);
        editor.apply();
        currentScoreThreshold = threshold;
    }

    private void setupThresholdSpinner() {
        List<String> thresholdDisplayValues = new ArrayList<>();
        for (Integer val : thresholdValues) {
            thresholdDisplayValues.add(val + "%");
        }
        ArrayAdapter<String> thresholdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, thresholdDisplayValues);
        thresholdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        thresholdSpinner.setAdapter(thresholdAdapter);

        int currentThresholdIndex = thresholdValues.indexOf(currentScoreThreshold);
        if (currentThresholdIndex != -1) {
            thresholdSpinner.setSelection(currentThresholdIndex);
        } else {
            Log.w(TAG, "Saved threshold " + currentScoreThreshold + "% not in standard list. Defaulting to 70%.");
            currentScoreThreshold = 70;
            saveThreshold(currentScoreThreshold);
            currentThresholdIndex = thresholdValues.indexOf(currentScoreThreshold);
            if (currentThresholdIndex != -1) {
                thresholdSpinner.setSelection(currentThresholdIndex);
            } else if (!thresholdValues.isEmpty()) {
                thresholdSpinner.setSelection(0);
                currentScoreThreshold = thresholdValues.get(0);
                saveThreshold(currentScoreThreshold);
            }
        }

        thresholdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedThreshold = thresholdValues.get(position);
                if (selectedThreshold != currentScoreThreshold) {
                    saveThreshold(selectedThreshold);
                    Log.d(TAG, "Threshold changed to: " + currentScoreThreshold + "%");
                    if (allMistakesData != null && !allMistakesData.isEmpty()) {
                        processLatestMistake(allMistakesData.get(0));
                    } else {
                        displayNoDataMessage();
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        POST_NOTIFICATIONS_REQUEST_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
            } else {
                Toast.makeText(this, "Notification permission is required for toxicity alerts", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Toxicity Alerts";
            String description = "Notifications for high toxicity levels detected";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            setupFirebaseListener(user.getUid());
                        }
                    } else {
                        originalTextTextView.setText("Error connecting to database. Please try again.");
                        Log.e(TAG, "signInAnonymously:failure", task.getException());
                        displayNoDataMessage();
                    }
                });
    }

    private void setupFirebaseListener(String userId) {
        DatabaseReference mistakesRef = mDatabase.child("users")
                .child(userId)
                .child("mistakes");

        mistakesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allMistakesData.clear();
                if (dataSnapshot.exists()) {
                    for (DataSnapshot mistakeSnapshot : dataSnapshot.getChildren()) {
                        try {
                            Map<String, Object> mistakeData = (Map<String, Object>) mistakeSnapshot.getValue();
                            if (mistakeData != null) {
                                allMistakesData.add(mistakeData);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing mistake data: " + e.getMessage());
                        }
                    }

                    Collections.sort(allMistakesData, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                            String ts1Str = (String) o1.get("timestamp");
                            String ts2Str = (String) o2.get("timestamp");
                            if (ts1Str == null || ts2Str == null) return 0;
                            try {
                                Date date1 = firebaseTimestampFormat.parse(ts1Str);
                                Date date2 = firebaseTimestampFormat.parse(ts2Str);
                                if (date1 != null && date2 != null) {
                                    return date2.compareTo(date1); // Descending
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing timestamp for sorting", e);
                            }
                            return 0;
                        }
                    });

                    if (!allMistakesData.isEmpty()) {
                        processLatestMistake(allMistakesData.get(0));
                    } else {
                        displayNoDataMessage();
                    }
                } else {
                    displayNoDataMessage();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                originalTextTextView.setText("Error loading data. Please check your connection.");
                Log.e(TAG, "Firebase data load cancelled: " + databaseError.getMessage());
                displayNoDataMessage();
            }
        });
    }

    private void displayNoDataMessage() {
        originalTextTextView.setText("No analysis data available yet.");
        timestampTextView.setText("N/A");
        updateAnalysisBreakdownDisplay(null); // Use the new method for analysis breakdown
    }

    private void processLatestMistake(Map<String, Object> mistakeData) {
        try {
            String originalText = (String) mistakeData.get("original_text");
            String timestampStr = (String) mistakeData.get("timestamp");
            Map<String, Double> analysis = (Map<String, Double>) mistakeData.get("analysis");

            originalTextTextView.setText(originalText != null ? originalText : "N/A");
            if (timestampStr != null) {
                try {
                    Date date = firebaseTimestampFormat.parse(timestampStr);
                    timestampTextView.setText(date != null ? displayTimestampFormat.format(date) : "N/A");
                } catch (ParseException e) {
                    timestampTextView.setText(timestampStr);
                    Log.e(TAG, "Timestamp parsing error: " + e.getMessage());
                }
            } else {
                timestampTextView.setText("N/A");
            }

            updateAnalysisBreakdownDisplay(analysis); // Update new analysis breakdown

            boolean highScoreFound = false;
            StringBuilder notificationContent = new StringBuilder();

            if (analysis != null) {
                for (Map.Entry<String, Double> entry : analysis.entrySet()) {
                    double score = entry.getValue() * 100;
                    if (score >= currentScoreThreshold) {
                        highScoreFound = true;
                        String categoryName = formatLabelName(entry.getKey());
                        String baseMessage = notificationMessages.get(entry.getKey());
                        if (baseMessage == null) {
                            baseMessage = String.format(Locale.getDefault(),
                                    "High score detected for %s.", categoryName);
                        }
                        notificationContent.append("• ").append(baseMessage)
                                .append(" Score: ").append(String.format(Locale.getDefault(), "%.1f%%", score))
                                .append(" (Alert set at ≥").append(currentScoreThreshold).append("%).\n\n");
                    }
                }
            }

            if (highScoreFound) {
                showNotification(notificationContent.toString().trim());
            }

        } catch (Exception e) {
            originalTextTextView.setText("Error processing data.");
            timestampTextView.setText("N/A");
            Log.e(TAG, "Error in processLatestMistake: " + e.getMessage(), e);
            updateAnalysisBreakdownDisplay(null);
        }
    }

    // Removed addTableHeader() and updateTableDisplay() methods

    private void showLabelDetailsDialog(String labelKey, String formattedLabelName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialog);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_label_details, null);
        builder.setView(dialogView);

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitleTextView);
        TextView dialogCount = dialogView.findViewById(R.id.dialogCountTextView);
        TextView dialogMessages = dialogView.findViewById(R.id.dialogMessagesTextView);

        List<String> highScoringMessages = new ArrayList<>();
        int count = 0;
        int totalMessagesConsidered = allMistakesData.size();

        for (Map<String, Object> mistake : allMistakesData) {
            Map<String, Double> analysis = (Map<String, Double>) mistake.get("analysis");
            if (analysis != null && analysis.containsKey(labelKey)) {
                Object scoreObj = analysis.get(labelKey);
                if (scoreObj instanceof Number) {
                    double score = ((Number) scoreObj).doubleValue() * 100;
                    if (score >= currentScoreThreshold) {
                        count++;
                        String originalText = (String) mistake.get("original_text");
                        String timestampStr = (String) mistake.get("timestamp");
                        String formattedTime = timestampStr;
                        if (timestampStr != null) {
                            try {
                                Date date = firebaseTimestampFormat.parse(timestampStr);
                                if (date != null) formattedTime = displayTimestampFormat.format(date);
                            } catch (ParseException | NullPointerException e) {
                                Log.w(TAG, "Could not parse timestamp in dialog: " + timestampStr, e);
                            }
                        }
                        highScoringMessages.add("• \"" + (originalText != null ? originalText : "N/A") +
                                "\"\n  (Time: " + formattedTime + ", Score: " +
                                String.format(Locale.getDefault(), "%.1f%%", score) + ")");
                    }
                } else {
                    Log.w(TAG, "Score for label " + labelKey + " is not a number: " + scoreObj);
                }
            }
        }

        dialogTitle.setText(String.format(Locale.getDefault(), "Details for '%s'", formattedLabelName));

        String countMsgHtml = String.format(Locale.getDefault(),
                "Found <b>%d</b> messages (out of %d total) with '<b>%s</b>' score ≥ <b>%d%%</b>.",
                count, totalMessagesConsidered, formattedLabelName, currentScoreThreshold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dialogCount.setText(Html.fromHtml(countMsgHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            dialogCount.setText(Html.fromHtml(countMsgHtml));
        }


        if (highScoringMessages.isEmpty()) {
            dialogMessages.setText(String.format(Locale.getDefault(), "No messages found where '%s' score was %d%% or higher.", formattedLabelName, currentScoreThreshold));
        } else {
            StringBuilder sb = new StringBuilder();
            for (String msg : highScoringMessages) {
                sb.append(msg).append("\n\n");
            }
            dialogMessages.setText(sb.toString().trim());
        }

        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showNotification(String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot show notification - permission not granted");
            return;
        }

        try {
            Intent intent = new Intent(this, output_Activity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_warning)
                    .setContentTitle("Toxicity Alert!")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setColor(Color.RED)
                    .setLights(Color.RED, 1000, 1000)
                    .setVibrate(new long[]{0, 500, 200, 500});

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission NOT granted at time of notify. Notification not sent.");
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification", e);
        }
    }

    private void updateAnalysisBreakdownDisplay(Map<String, Double> analysis) { // Renamed from updateVisualSummary
        analysisBreakdownLayout.removeAllViews(); // Use the correct layout ID
        LayoutInflater inflater = LayoutInflater.from(this);

        if (analysis == null || analysis.isEmpty()) {
            TextView noDataTv = new TextView(this);
            noDataTv.setText("No analysis breakdown data available."); // Updated text
            noDataTv.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
            noDataTv.setGravity(Gravity.CENTER);
            analysisBreakdownLayout.addView(noDataTv);
            return;
        }

        List<Map.Entry<String, Double>> sortedAnalysis = new ArrayList<>(analysis.entrySet());
        Collections.sort(sortedAnalysis, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

        for (Map.Entry<String, Double> entry : sortedAnalysis) {
            final String labelKey = entry.getKey(); // For click listener
            final String formattedLabel = formatLabelName(labelKey);
            double scoreValue = entry.getValue() * 100;

            View itemView = inflater.inflate(R.layout.item_score_visual, analysisBreakdownLayout, false);

            TextView categoryNameTv = itemView.findViewById(R.id.categoryNameTextView_visual);
            ProgressBar scoreProgressBar = itemView.findViewById(R.id.scoreProgressBar_visual);
            TextView scoreTv = itemView.findViewById(R.id.scoreTextView_visual);

            categoryNameTv.setText(formattedLabel);
            scoreProgressBar.setProgress((int) scoreValue);
            scoreTv.setText(String.format(Locale.getDefault(), "%.1f%%", scoreValue));

            int progressColor;
            int scoreTextColor;

            if (scoreValue >= currentScoreThreshold) {
                progressColor = Color.RED;
                scoreTextColor = Color.RED;
            } else if (scoreValue >= 50) { // This 50 could also be made dynamic or a fraction of currentScoreThreshold
                progressColor = Color.rgb(255, 165, 0); // Orange
                scoreTextColor = Color.rgb(255, 165, 0);
            } else {
                progressColor = ContextCompat.getColor(this, R.color.colorPositiveScore);
                scoreTextColor = Color.DKGRAY;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                scoreProgressBar.setProgressTintList(ColorStateList.valueOf(progressColor));
            } else {
                scoreProgressBar.getProgressDrawable().setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
            }
            scoreTv.setTextColor(scoreTextColor);
            categoryNameTv.setTextColor(ContextCompat.getColor(this, R.color.textColorClickable)); // Make category name look clickable
            categoryNameTv.setTypeface(null, Typeface.BOLD_ITALIC);


            // Make the whole item clickable to show details
            itemView.setOnClickListener(v -> showLabelDetailsDialog(labelKey, formattedLabel));

            analysisBreakdownLayout.addView(itemView);
        }
    }


    private String formatLabelName(String label) {
        if (label == null) return "Unknown";
        switch (label.toLowerCase()) {
            case "toxic": return "Toxic";
            case "severe_toxic": return "Severely Toxic";
            case "obscene": return "Obscene";
            case "threat": return "Threat";
            case "insult": return "Insult";
            case "identity_hate": return "Identity Hate";
            default:
                String[] parts = label.split("_");
                StringBuilder formatted = new StringBuilder();
                for (String part : parts) {
                    if (part.length() > 0) {
                        formatted.append(Character.toUpperCase(part.charAt(0)))
                                .append(part.substring(1).toLowerCase())
                                .append(" ");
                    }
                }
                String result = formatted.toString().trim();
                return result.isEmpty() ? "Unknown Label" : result;
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}