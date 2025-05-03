package com.example.parental_control;

import androidx.annotation.ColorInt;

public class TraitDisplayInfo {
    String traitDisplayName; // User-friendly name e.g., "Creative Spark"
    String internalTraitKey; // e.g., "Openness"
    String emoji;
    String scoreCategoryText; // "Low", "Medium-Low", "Medium", "Medium-High", "High"
    double scoreValue; // 0.0 to 1.0
    String positiveFeedback;
    String parentTip;
    @ColorInt int progressColor;

    public TraitDisplayInfo(String internalTraitKey, String traitDisplayName, double scoreValue) {
        this.internalTraitKey = internalTraitKey;
        this.traitDisplayName = traitDisplayName;
        this.scoreValue = scoreValue;
    }
}