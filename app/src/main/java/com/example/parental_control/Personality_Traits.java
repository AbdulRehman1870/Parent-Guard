package com.example.parental_control;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Personality_Traits extends AppCompatActivity {

    private static final String TAG = "PersonalityTraits";
    private static final int MIN_ENTRIES_FOR_ANALYSIS_BATCH = 15;

    // --- Views ---
    private TextView mainTitleTextView;
    private TextView personalitySummaryTextView;
    private LinearLayout traitsContainerLayout;
    private TextView noDataTextView;
    private TextView batchProgressTextView; // New TextView for batch progress
    private TextView finalMotivationalTextView;
    private TextView graphTriggerTextView;
    // --- END: Views ---

    // --- Firebase ---
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private ValueEventListener traitsListener;
    private DatabaseReference traitsRef;
    // --- END: Firebase ---

    private List<String> traitOrder = new ArrayList<>();

    // State variables for batched averaging
    private Map<String, Double> lastDisplayedOverallAverageScores = new HashMap<>();
    private long entriesCountUsedInLastOverallAverage = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personality_traits);

        mainTitleTextView = findViewById(R.id.mainTitleTextView);
        personalitySummaryTextView = findViewById(R.id.personalitySummaryTextView);
        traitsContainerLayout = findViewById(R.id.traitsContainerLayout);
        noDataTextView = findViewById(R.id.noDataTextView);
        batchProgressTextView = findViewById(R.id.batchProgressTextView); // Initialize
        finalMotivationalTextView = findViewById(R.id.finalMotivationalTextView);
        graphTriggerTextView = findViewById(R.id.graphTriggerTextView);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        traitOrder.add("Openness");
        traitOrder.add("Conscientiousness");
        traitOrder.add("Extraversion");
        traitOrder.add("Agreeableness");
        traitOrder.add("Neuroticism");

        showUiForInitialAssessment(0); // Initial UI state

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            signInAnonymously();
        } else {
            setupFirebaseListener(currentUser.getUid());
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
                        showErrorState("Error connecting to database. Please try again.");
                        Toast.makeText(Personality_Traits.this, "Anonymous sign-in failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupFirebaseListener(String userId) {
        traitsRef = mDatabase.child("users").child(userId).child("personality_traits");

        if (traitsListener != null) {
            traitsRef.removeEventListener(traitsListener);
        }

        traitsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long totalFirebaseEntries = dataSnapshot.getChildrenCount();
                List<DataSnapshot> allEntriesList = new ArrayList<>();
                for (DataSnapshot entrySnapshot : dataSnapshot.getChildren()) {
                    allEntriesList.add(entrySnapshot);
                }
                Collections.sort(allEntriesList, Comparator.comparing(DataSnapshot::getKey));

                if (totalFirebaseEntries < MIN_ENTRIES_FOR_ANALYSIS_BATCH) {
                    showUiForInitialAssessment(totalFirebaseEntries);
                    lastDisplayedOverallAverageScores.clear();
                    entriesCountUsedInLastOverallAverage = 0;
                    return;
                }

                long numTotalPossibleBatches = totalFirebaseEntries / MIN_ENTRIES_FOR_ANALYSIS_BATCH;
                long numBatchesInLastAvg = entriesCountUsedInLastOverallAverage / MIN_ENTRIES_FOR_ANALYSIS_BATCH;

                if (numTotalPossibleBatches > numBatchesInLastAvg) {
                    // A new batch (or more) has completed. Process the LATEST completed batch.
                    int endIdxInListForLatestBatch = (int) (numTotalPossibleBatches * MIN_ENTRIES_FOR_ANALYSIS_BATCH);
                    int startIdxInListForLatestBatch = endIdxInListForLatestBatch - MIN_ENTRIES_FOR_ANALYSIS_BATCH;

                    if (startIdxInListForLatestBatch < 0 || endIdxInListForLatestBatch > allEntriesList.size()) {
                        Log.e(TAG, "Index out of bounds for latest batch. Start: " + startIdxInListForLatestBatch + ", End: " + endIdxInListForLatestBatch + ", Size: " + allEntriesList.size());
                        if (!lastDisplayedOverallAverageScores.isEmpty()) {
                            showUiWithFullInsights(entriesCountUsedInLastOverallAverage);
                            updateBatchProgressText(totalFirebaseEntries, entriesCountUsedInLastOverallAverage);
                        } else {
                            showErrorState("Error preparing data for analysis.");
                        }
                        return;
                    }

                    List<DataSnapshot> latestBatchData = allEntriesList.subList(startIdxInListForLatestBatch, endIdxInListForLatestBatch);
                    Map<String, Double> avgOfLatestBatch = calculateAveragesForSpecificEntries(latestBatchData);

                    if (avgOfLatestBatch.isEmpty()) {
                        Log.w(TAG, "Latest batch processing yielded no scores.");
                        if (!lastDisplayedOverallAverageScores.isEmpty()) {
                            showUiWithFullInsights(entriesCountUsedInLastOverallAverage);
                            updateBatchProgressText(totalFirebaseEntries, entriesCountUsedInLastOverallAverage);
                        } else {
                            showErrorState("Could not get insights from recent interactions.");
                        }
                        return;
                    }

                    if (entriesCountUsedInLastOverallAverage == 0) {
                        lastDisplayedOverallAverageScores = new HashMap<>(avgOfLatestBatch);
                    } else {
                        lastDisplayedOverallAverageScores = averageTwoScoreMaps(lastDisplayedOverallAverageScores, avgOfLatestBatch);
                    }
                    entriesCountUsedInLastOverallAverage = numTotalPossibleBatches * MIN_ENTRIES_FOR_ANALYSIS_BATCH;

                    showUiWithFullInsights(entriesCountUsedInLastOverallAverage);
                    updateBatchProgressText(totalFirebaseEntries, entriesCountUsedInLastOverallAverage);
                } else {
                    // No new full batch completed, but we are past the initial 15.
                    // Show existing insights and update progress for the current pending batch.
                    showUiWithFullInsights(entriesCountUsedInLastOverallAverage);
                    updateBatchProgressText(totalFirebaseEntries, entriesCountUsedInLastOverallAverage);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showErrorState("Error loading data. Please check your connection.");
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                // Potentially clear scores or revert to a safe state
                lastDisplayedOverallAverageScores.clear();
                entriesCountUsedInLastOverallAverage = 0;
                Toast.makeText(Personality_Traits.this, "Database error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        traitsRef.orderByKey().addValueEventListener(traitsListener);
    }

    private Map<String, Double> calculateAveragesForSpecificEntries(List<DataSnapshot> entriesToAverage) {
        Map<String, List<Double>> batchTraitScores = new HashMap<>();
        for (DataSnapshot traitEntrySnapshot : entriesToAverage) {
            try {
                Map<String, Object> traitData = (Map<String, Object>) traitEntrySnapshot.getValue();
                if (traitData == null) continue;

                Object analysisObj = traitData.get("analysis");
                if (!(analysisObj instanceof Map)) {
                    Log.w(TAG, "Analysis data is not a Map for entry: " + traitEntrySnapshot.getKey());
                    continue;
                }
                Map<String, Object> currentAnalysisRaw = (Map<String, Object>) analysisObj;
                Map<String, Double> currentAnalysis = new HashMap<>();

                for(Map.Entry<String, Object> entry : currentAnalysisRaw.entrySet()){
                    if(entry.getValue() instanceof Number){
                        currentAnalysis.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    } else {
                        Log.w(TAG, "Non-numeric score for trait '"+entry.getKey()+"': " + entry.getValue());
                    }
                }
                if (currentAnalysis.isEmpty()) continue;

                for (Map.Entry<String, Double> traitScoreEntry : currentAnalysis.entrySet()) {
                    String traitName = formatTraitNameForKey(traitScoreEntry.getKey());
                    if (traitOrder.contains(traitName)) {
                        batchTraitScores.computeIfAbsent(traitName, k -> new ArrayList<>()).add(traitScoreEntry.getValue());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing a trait entry during batch calc for key: " + traitEntrySnapshot.getKey(), e);
            }
        }

        Map<String, Double> averagedScores = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : batchTraitScores.entrySet()) {
            List<Double> scores = entry.getValue();
            if (scores != null && !scores.isEmpty()) {
                double sum = 0;
                for (double score : scores) {
                    sum += score;
                }
                averagedScores.put(entry.getKey(), sum / scores.size());
            }
        }
        return averagedScores;
    }

    private Map<String, Double> averageTwoScoreMaps(Map<String, Double> overallMap, Map<String, Double> newBatchMap) {
        Map<String, Double> resultMap = new HashMap<>();
        if (overallMap == null || overallMap.isEmpty()) {
            return new HashMap<>(newBatchMap); // Should only happen if called incorrectly
        }
        if (newBatchMap == null || newBatchMap.isEmpty()) {
            Log.w(TAG, "New batch map was empty during averaging. Returning old overall map.");
            return new HashMap<>(overallMap);
        }

        for (String trait : traitOrder) {
            double overallScore = overallMap.getOrDefault(trait, 0.0);
            double newBatchScore = newBatchMap.getOrDefault(trait, 0.0);
            resultMap.put(trait, (overallScore + newBatchScore) / 2.0);
        }
        return resultMap;
    }

    private void showUiForInitialAssessment(long currentTotalEntries) {
        mainTitleTextView.setText("Child's Personality Insights");
        personalitySummaryTextView.setVisibility(View.GONE);
        traitsContainerLayout.setVisibility(View.GONE);
        graphTriggerTextView.setVisibility(View.GONE);
        batchProgressTextView.setVisibility(View.GONE);

        noDataTextView.setText(String.format(Locale.getDefault(),
                "Assessing initial personality profile. Insights will appear after %d interactions.\n(%d/%d available)",
                MIN_ENTRIES_FOR_ANALYSIS_BATCH, currentTotalEntries, MIN_ENTRIES_FOR_ANALYSIS_BATCH));
        noDataTextView.setVisibility(View.VISIBLE);
    }

    private void showUiWithFullInsights(long totalEntriesConsideredInAvg) {
        mainTitleTextView.setText(String.format(Locale.getDefault(),
                "Child's Personality (Based on %d Interactions)", totalEntriesConsideredInAvg));
        noDataTextView.setVisibility(View.GONE); // Hide initial assessment/error message

        if (lastDisplayedOverallAverageScores.isEmpty() && totalEntriesConsideredInAvg > 0) {
            // This case might occur if the first batch processing failed but we still try to show UI.
            showErrorState("Insufficient data to display detailed insights currently.");
            batchProgressTextView.setVisibility(View.GONE);
            return;
        } else if (lastDisplayedOverallAverageScores.isEmpty() && totalEntriesConsideredInAvg == 0) {
            // Still in initial assessment, this method shouldn't be called ideally, but as a safeguard:
            showUiForInitialAssessment(0);
            return;
        }


        personalitySummaryTextView.setVisibility(View.VISIBLE);
        traitsContainerLayout.setVisibility(View.VISIBLE);
        graphTriggerTextView.setVisibility(View.VISIBLE);
        // batchProgressTextView visibility is handled by updateBatchProgressText

        personalitySummaryTextView.setText(generateSummaryReport(lastDisplayedOverallAverageScores, totalEntriesConsideredInAvg));
        traitsContainerLayout.removeAllViews();

        for (String internalTraitKey : traitOrder) {
            double score = lastDisplayedOverallAverageScores.getOrDefault(internalTraitKey, -1.0);
            if (score < 0.0 || score > 1.0) {
                Log.w(TAG, "Invalid/missing overall average score for trait: " + internalTraitKey + " Score: " + score);
                // Optionally add a placeholder card or skip
                View errorTraitView = LayoutInflater.from(this).inflate(R.layout.item_personality_trait, traitsContainerLayout, false);
                TextView errorName = errorTraitView.findViewById(R.id.traitNameTextVie);
                errorName.setText(internalTraitKey + " (Data pending)");
                errorTraitView.findViewById(R.id.traitScoreTextVie).setVisibility(View.GONE);
                errorTraitView.findViewById(R.id.traitScoreProgressBa).setVisibility(View.GONE);
                TextView errorFeedbackTextView = errorTraitView.findViewById(R.id.traitFeedbackTextVie);
                if (errorFeedbackTextView != null) {
                    errorFeedbackTextView.setText("Detailed insights for this trait are being processed.");
                }

                TextView errorParentTipTextView = errorTraitView.findViewById(R.id.traitParentTipTextVie);
                if (errorParentTipTextView != null) {
                    errorParentTipTextView.setText("Please check back after more interactions.");
                }
                traitsContainerLayout.addView(errorTraitView);
                continue;
            }

            TraitDisplayInfo displayInfo = getTraitUIDetails(internalTraitKey, score);
            View traitView = LayoutInflater.from(this).inflate(R.layout.item_personality_trait, traitsContainerLayout, false);

            TextView traitEmojiTextView = traitView.findViewById(R.id.traitEmojiTextVie);
            TextView traitNameTextView = traitView.findViewById(R.id.traitNameTextVie);
            TextView traitScoreTextView = traitView.findViewById(R.id.traitScoreTextVie);
            ProgressBar traitScoreProgressBar = traitView.findViewById(R.id.traitScoreProgressBa);
            TextView traitFeedbackTextView = traitView.findViewById(R.id.traitFeedbackTextVie);
            TextView traitParentTipTextView = traitView.findViewById(R.id.traitParentTipTextVie);

            traitEmojiTextView.setText(displayInfo.emoji);
            traitNameTextView.setText(displayInfo.traitDisplayName);
            traitScoreTextView.setText(String.format(Locale.getDefault(), "Overall Avg: %.2f (%s)", displayInfo.scoreValue, displayInfo.scoreCategoryText));

            traitScoreProgressBar.setProgress((int) (displayInfo.scoreValue * 100));
            LayerDrawable progressBarDrawable = (LayerDrawable) traitScoreProgressBar.getProgressDrawable();
            if (progressBarDrawable != null) {
                progressBarDrawable.findDrawableByLayerId(android.R.id.progress).setColorFilter(displayInfo.progressColor, PorterDuff.Mode.SRC_IN);
            }

            traitFeedbackTextView.setText(displayInfo.positiveFeedback);
            traitParentTipTextView.setText(displayInfo.parentTip);
            traitsContainerLayout.addView(traitView);
        }
        graphTriggerTextView.setOnClickListener(v -> showPersonalityGraphDialog(lastDisplayedOverallAverageScores, totalEntriesConsideredInAvg));
    }

    private void updateBatchProgressText(long currentTotalFirebaseEntries, long entriesInLastCalcForProgress) {
        if (entriesInLastCalcForProgress == 0) { // Still in initial assessment phase
            batchProgressTextView.setVisibility(View.GONE);
            return;
        }

        long entriesIntoCurrentPendingBatch = currentTotalFirebaseEntries - entriesInLastCalcForProgress;
        // Ensure it's not negative if currentTotalFirebaseEntries somehow becomes less than entriesInLastCalcForProgress (e.g. data deletion)
        entriesIntoCurrentPendingBatch = Math.max(0, entriesIntoCurrentPendingBatch);

        long progressInCurrentBatchCycle = entriesIntoCurrentPendingBatch % MIN_ENTRIES_FOR_ANALYSIS_BATCH;

        batchProgressTextView.setText(String.format(Locale.getDefault(),
                "Current insights based on %d interactions. Next update progress: (%d/%d interactions)",
                entriesInLastCalcForProgress,
                progressInCurrentBatchCycle,
                MIN_ENTRIES_FOR_ANALYSIS_BATCH));
        batchProgressTextView.setVisibility(View.VISIBLE);
    }

    private void showErrorState(String message) {
        mainTitleTextView.setText("Child's Personality Insights");
        personalitySummaryTextView.setVisibility(View.GONE);
        traitsContainerLayout.setVisibility(View.GONE);
        graphTriggerTextView.setVisibility(View.GONE);
        batchProgressTextView.setVisibility(View.GONE);
        noDataTextView.setText(message);
        noDataTextView.setVisibility(View.VISIBLE);
    }

    private String formatTraitNameForKey(String rawLabel) {
        if (rawLabel == null || rawLabel.isEmpty()) return "Unknown";
        String lower = rawLabel.toLowerCase();
        if (lower.contains("openness")) return "Openness";
        if (lower.contains("conscientiousness")) return "Conscientiousness";
        if (lower.contains("extraversion") || lower.contains("extroversion")) return "Extraversion";
        if (lower.contains("agreeableness")) return "Agreeableness";
        if (lower.contains("neuroticism") || lower.contains("emotional stability")) return "Neuroticism";
        String formatted = lower.replace("_", " ");
        if (formatted.length() > 0) {
            return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        }
        return "Unknown";
    }

    private String generateSummaryReport(Map<String, Double> averagedScores, long totalEntriesConsidered) {
        if (averagedScores == null || averagedScores.isEmpty()) {
            return "Awaiting enough data to generate a personality summary.";
        }

        StringBuilder summary = new StringBuilder("Based on an overall analysis from the latest " + totalEntriesConsidered + " interactions (updated in batches of " + MIN_ENTRIES_FOR_ANALYSIS_BATCH + "), your child's personality shows:\n\n");
        boolean hasHighlights = false;
        double highThreshold = 0.67;
        double lowThreshold = 0.33;

        if (averagedScores.getOrDefault("Agreeableness", 0.0) > highThreshold) {
            summary.append("🌟 **Naturally Cooperative:** They often show a strong ability to get along well with others and are considerate of feelings.\n");
            hasHighlights = true;
        }
        if (averagedScores.getOrDefault("Conscientiousness", 0.0) > highThreshold) {
            summary.append("📚 **Organized & Dutiful:** There's a good chance they are responsible, tidy, and prefer to follow through on tasks.\n");
            hasHighlights = true;
        }
        if (averagedScores.getOrDefault("Openness", 0.0) > highThreshold) {
            summary.append("💡 **Curious Explorer:** They might be very imaginative, enjoy new experiences, and think in creative ways.\n");
            hasHighlights = true;
        }
        if (averagedScores.getOrDefault("Extraversion", 0.0) > highThreshold) {
            summary.append("💬 **Social Energizer:** They likely enjoy being around people and gain energy from social interactions.\n");
            hasHighlights = true;
        }
        if (averagedScores.getOrDefault("Neuroticism", 1.0) < lowThreshold) {
            summary.append("😊 **Emotionally Resilient:** They tend to be calm and handle stress well, bouncing back from setbacks.\n");
            hasHighlights = true;
        } else if (averagedScores.getOrDefault("Neuroticism", 0.0) > highThreshold) {
            summary.append("💭 **Deeply Perceptive:** They may feel emotions intensely and be very empathetic, noticing subtle cues others might miss.\n");
            hasHighlights = true;
        }

        if (!hasHighlights) {
            summary.append("Every child develops at their own pace. Continue observing and supporting their journey. Your nurturing presence is key!");
        } else {
            summary.append("\nRemember, these are general tendencies. Every child is a unique and wonderful mix of qualities!");
        }
        return summary.toString();
    }

    private TraitDisplayInfo getTraitUIDetails(String internalTraitKey, double score) {
        TraitDisplayInfo info = new TraitDisplayInfo(internalTraitKey, internalTraitKey, score);
        double normalizedScore = Math.max(0.0, Math.min(1.0, score));

        if (normalizedScore < 0.34) {
            info.scoreCategoryText = "Low";
            info.progressColor = ContextCompat.getColor(this, R.color.score_low);
        } else if (normalizedScore < 0.67) {
            info.scoreCategoryText = "Medium";
            info.progressColor = ContextCompat.getColor(this, R.color.score_medium);
        } else {
            info.scoreCategoryText = "High";
            info.progressColor = ContextCompat.getColor(this, R.color.score_high);
        }

        // Switch case for trait details (same as your provided code)
        switch (internalTraitKey) {
            case "Openness":
                info.traitDisplayName = "Creative Spark (Openness)";
                info.emoji = "💡";
                if (info.scoreCategoryText.equals("High")) {
                    info.positiveFeedback = "Your child shows high imagination and curiosity, loving new ideas. They might be a natural innovator!";
                    info.parentTip = "Nurture their curiosity with diverse books, creative projects, and new places. Explore 'why' questions together.";
                } else if (info.scoreCategoryText.equals("Medium")) {
                    info.positiveFeedback = "They balance appreciating new ideas with valuing familiar routines. Curious yet practical.";
                    info.parentTip = "Introduce new activities at a comfortable pace. Discuss different perspectives and value their unique thoughts.";
                } else { // Low
                    info.positiveFeedback = "Your child may prefer familiar routines and concrete ideas, finding comfort in predictability.";
                    info.parentTip = "Respect their need for consistency. Introduce changes gradually with clear explanations to help them adjust.";
                }
                break;
            case "Conscientiousness":
                info.traitDisplayName = "Reliable Achiever (Conscientiousness)";
                info.emoji = "✅";
                if (info.scoreCategoryText.equals("High")) {
                    info.positiveFeedback = "They are likely very organized, responsible, and dependable, taking commitments seriously.";
                    info.parentTip = "Acknowledge their hard work. Help them understand it's okay to make mistakes and perfection isn't always needed.";
                } else if (info.scoreCategoryText.equals("Medium")) {
                    info.positiveFeedback = "Generally organized and reliable, balancing responsibility with flexibility. Manages tasks effectively.";
                    info.parentTip = "Encourage good habits with gentle reminders and positive reinforcement. Celebrate their efforts.";
                } else { // Low
                    info.positiveFeedback = "More spontaneous and flexible, less focused on strict schedules and more adaptable to change.";
                    info.parentTip = "Help develop organizational skills with visual aids like checklists. Break down tasks into smaller steps.";
                }
                break;
            case "Extraversion":
                info.traitDisplayName = "Social Energy (Extraversion)";
                info.emoji = "😊";
                if (info.scoreCategoryText.equals("High")) {
                    info.positiveFeedback = "Outgoing, energetic, and loves being around people! Thrives in social settings.";
                    info.parentTip = "Provide safe social opportunities. Guide them in active listening and understanding personal space.";
                } else if (info.scoreCategoryText.equals("Medium")) {
                    info.positiveFeedback = "Enjoys social time but also values quiet, personal time. A healthy mix of social engagement.";
                    info.parentTip = "Support social activities and respect their need for solitude or one-on-one time.";
                } else { // Low (Introversion)
                    info.traitDisplayName = "Thoughtful Observer (Introversion)";
                    info.positiveFeedback = "Prefers quieter environments. Often thoughtful, observant, recharging alone or with few close friends.";
                    info.parentTip = "Value their reflective nature. Encourage interactions in small, familiar groups. Let them engage at their own pace.";
                }
                break;
            case "Agreeableness":
                info.traitDisplayName = "Kind Collaborator (Agreeableness)";
                info.emoji = "🤝";
                if (info.scoreCategoryText.equals("High")) {
                    info.positiveFeedback = "Very cooperative, kind, empathetic, and considerate. A natural team player valuing harmony.";
                    info.parentTip = "Praise their empathy. Gently teach them it's okay to assert their own needs respectfully.";
                } else if (info.scoreCategoryText.equals("Medium")) {
                    info.positiveFeedback = "Generally cooperative and kind, while also able to express their own views when needed.";
                    info.parentTip = "Encourage continued empathy. Discuss different social scenarios and problem-solving in relationships.";
                } else { // Low
                    info.positiveFeedback = "More competitive, direct, and assertive. Voices opinions and may challenge others. Values honesty.";
                    info.parentTip = "Help them understand others' perspectives and the importance of tact. Channel assertiveness constructively.";
                }
                break;
            case "Neuroticism":
                info.emoji = "🧠";
                if (info.scoreCategoryText.equals("High")) { // High Neuroticism
                    info.traitDisplayName = "Emotional Sensitivity (Neuroticism)";
                    info.positiveFeedback = "Feels emotions intensely, may be more sensitive to stress. This can also mean high empathy.";
                    info.parentTip = "Create a calm, supportive environment. Teach healthy ways to manage strong feelings. Reassure them often.";
                } else if (info.scoreCategoryText.equals("Medium")) { // Medium Neuroticism
                    info.traitDisplayName = "Emotional Balance (Neuroticism)";
                    info.positiveFeedback = "Experiences a normal range of emotions and generally copes well with everyday ups and downs.";
                    info.parentTip = "Continue to encourage open communication about feelings. Validate their emotions.";
                } else { // Low Neuroticism (High Emotional Stability)
                    info.traitDisplayName = "Emotional Resilience (Low Neuroticism)";
                    info.positiveFeedback = "Generally calm, composed, and resilient under pressure. Handles stress well.";
                    info.parentTip = "Acknowledge their calm nature. Ensure they still feel comfortable sharing any worries, as everyone has them.";
                }
                break;
            default:
                info.traitDisplayName = internalTraitKey;
                info.emoji = "❓";
                info.positiveFeedback = "Information for this trait is being prepared based on the latest data.";
                info.parentTip = "Continue to observe and support your child's unique way of interacting with the world.";
                break;
        }
        return info;
    }

    private void showPersonalityGraphDialog(Map<String, Double> scoresToGraph, long totalEntriesConsidered) {
        if (scoresToGraph == null || scoresToGraph.isEmpty()) {
            Toast.makeText(this, "Not enough data to display graph.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_personality_graph, null);
        builder.setView(dialogView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        BarChart barChart = dialogView.findViewById(R.id.personalityBarChart);
        TextView graphDialogTitleTextView = dialogView.findViewById(R.id.graphDialogTitleTextView);
        if (graphDialogTitleTextView != null) {
            graphDialogTitleTextView.setText(String.format(Locale.getDefault(),
                    "💖 Personality (Overall Avg. from %d Interactions)", totalEntriesConsidered));
        }
        setupBarChart(barChart, scoresToGraph); // Pass the overall scores

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void setupBarChart(BarChart barChart, Map<String, Double> averagedScores) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        Map<String, String> simpleLabels = new HashMap<>();
        simpleLabels.put("Openness", "Open.");
        simpleLabels.put("Conscientiousness", "Cons.");
        simpleLabels.put("Extraversion", "Extra.");
        simpleLabels.put("Agreeableness", "Agree.");
        simpleLabels.put("Neuroticism", "Neuro.");

        int i = 0;
        for (String traitKey : traitOrder) {
            if (averagedScores.containsKey(traitKey)) {
                double score = averagedScores.get(traitKey);
                entries.add(new BarEntry(i, (float) score * 100));
                labels.add(simpleLabels.getOrDefault(traitKey, traitKey.substring(0, Math.min(traitKey.length(), 5))));
                i++;
            }
        }

        if (entries.isEmpty()) {
            barChart.setNoDataText("No data available for graph.");
            barChart.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Overall Avg. Trait Scores (0-100)"); // Updated label
        ArrayList<Integer> colors = new ArrayList<>();
        for (int c : ColorTemplate.MATERIAL_COLORS) colors.add(c);
        for (int c : ColorTemplate.JOYFUL_COLORS) colors.add(c);
        dataSet.setColors(colors);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.DKGRAY);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);

        barChart.setData(barData);
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setFitBars(true);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setTextSize(10f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(6, true);

        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(true);
        barChart.getLegend().setTextSize(12f);

        barChart.animateY(1000);
        barChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (traitsRef != null && traitsListener != null) {
            traitsRef.removeEventListener(traitsListener);
        }
    }

    private static class TraitDisplayInfo {
        String internalKey;
        String traitDisplayName;
        String emoji;
        double scoreValue;
        String scoreCategoryText;
        int progressColor;
        String positiveFeedback;
        String parentTip;

        TraitDisplayInfo(String internalKey, String displayName, double score) {
            this.internalKey = internalKey;
            this.traitDisplayName = displayName;
            this.scoreValue = score;
        }
    }
}