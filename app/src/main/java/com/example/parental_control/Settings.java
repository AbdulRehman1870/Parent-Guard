package com.example.parental_control;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    Activity context;
    SharedPreferences sharedPref;

    // Constructor remains the same
    Settings(Activity context, SharedPreferences sharedPref) {
        this.context = context;
        this.sharedPref = sharedPref;
    }

    // saveBooleanState remains the same
    void saveBooleanState(String key, Boolean value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    // getBooleanState remains the same
    // It will default to 'false' if the key is not found, which is correct for INTRO_COMPLETED
    Boolean getBooleanState(String key) {
        return sharedPref.getBoolean(key, false);
    }

    // saveStringState remains the same
    void saveStringState(String key, String value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    // getStringState remains the same
    String getStringState(String key) {
        return sharedPref.getString(key,"");
    }

    // Instance and getPreference method remain the same
    private static Settings settingsInstance = null;

    static Settings getPreference(Activity context) {
        if (settingsInstance != null && settingsInstance.context == context) { // Added a check to re-instantiate if context changes, though Activity context for SP is not ideal
            return settingsInstance;
        } else {
            settingsInstance = new Settings(context, context.getSharedPreferences("appSettings", Context.MODE_PRIVATE));
            return settingsInstance;
        }
    }

    // Your existing keys
    static final String onBoarding = "is_on_boarding_completed";
    static final String pairId = "pair_id";

    // New key for intro completion status
    static final String INTRO_COMPLETED = "intro_completed_status";

    // Optional: Method to clear a specific preference (e.g., for testing), if you need it
    public void clearPreference(String key) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(key);
        editor.apply();
    }
}