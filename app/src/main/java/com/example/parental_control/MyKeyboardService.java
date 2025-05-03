package com.example.parental_control;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyKeyboardService extends InputMethodService implements View.OnClickListener {

    private static final String TAG = "MyKeyboardService";
    private final StringBuilder textBuffer = new StringBuilder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Firebase variables
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // API constants
    private static final String TOXIC_MODEL_URL = "https://api-inference.huggingface.co/models/unitary/toxic-bert";
    private static final String EMOTION_MODEL_URL = "https://api-inference.huggingface.co/models/bhadresh-savani/distilbert-base-uncased-emotion";
    private static final String PERSONALITY_MODEL_URL = "https://api-inference.huggingface.co/models/Minej/bert-base-personality";
    // private static final String HUGGING_FACE_TOKEN = "huggingface_token"; // YAHAN SE TOKEN HATAYA GAYA

    // Emotion labels mapping
    private static final String[] EMOTION_LABELS = {
            "Sadness",    // LABEL_0
            "Joy",        // LABEL_1
            "Love",       // LABEL_2
            "Anger",      // LABEL_3
            "Fear",       // LABEL_4
            "Surprise"    // LABEL_5
    };

    // Personality traits labels mapping
    private static final String[] PERSONALITY_LABELS = {
            "Openness",
            "Conscientiousness",
            "Extraversion",
            "Agreeableness",
            "Neuroticism"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Firebase
        try {
            mAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();

            if (mAuth.getCurrentUser() == null) {
                signInAnonymously();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization failed", e);
        }

        // Check if the token is loaded correctly (optional logging)
        if (BuildConfig.HUGGING_FACE_TOKEN == null || BuildConfig.HUGGING_FACE_TOKEN.isEmpty()) {
            Log.e(TAG, "Hugging Face Token is not loaded from BuildConfig!");
            showToast("Error: Hugging Face Token not configured.");
        }
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Anonymous sign-in successful");
                    } else {
                        Log.e(TAG, "Anonymous sign-in failed", task.getException());
                    }
                });
    }

    @Override
    public View onCreateInputView() {
        View keyboardView = getLayoutInflater().inflate(R.layout.keyboard_layout, null);

        int[] buttonIds = {
                R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
                R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btn0,
                R.id.btnQ, R.id.btnW, R.id.btnE, R.id.btnR, R.id.btnT,
                R.id.btnY, R.id.btnU, R.id.btnI, R.id.btnO, R.id.btnP,
                R.id.btnA, R.id.btnS, R.id.btnD, R.id.btnF, R.id.btnG,
                R.id.btnH, R.id.btnJ, R.id.btnK, R.id.btnL,
                R.id.btnZ, R.id.btnX, R.id.btnC, R.id.btnV, R.id.btnB,
                R.id.btnN, R.id.btnM, R.id.btnComma, R.id.btnDot,
                R.id.btnSpace, R.id.btnEnter, R.id.btnBackSpace
        };

        for (int id : buttonIds) {
            Button button = keyboardView.findViewById(id);
            if (button != null) {
                button.setOnClickListener(this);
            }
        }
        return keyboardView;
    }

    @Override
    public void onClick(View v) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.e(TAG, "InputConnection is null!");
            return;
        }

        int viewId = v.getId();
        if (viewId == R.id.btnBackSpace) {
            CharSequence beforeText = ic.getTextBeforeCursor(1, 0);
            if (beforeText != null && beforeText.length() > 0) {
                ic.deleteSurroundingText(1, 0);
                if (textBuffer.length() > 0) {
                    textBuffer.deleteCharAt(textBuffer.length() - 1);
                }
            }
        } else if (viewId == R.id.btnEnter) {
            ic.commitText("\n", 1);
            textBuffer.append("\n");
        } else if (viewId == R.id.btnSpace) {
            ic.commitText(" ", 1);
            textBuffer.append(" ");
        } else if (v instanceof Button) {
            String text = ((Button) v).getText().toString();
            ic.commitText(text, 1);
            textBuffer.append(text);
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        saveAllTypedText();
    }

    private void saveAllTypedText() {
        if (textBuffer.length() == 0) {
            Log.d(TAG, "No text to save.");
            return;
        }

        final String textToSend = textBuffer.toString();
        textBuffer.setLength(0);

        Log.d(TAG, "Sending text to all models: " + textToSend);
        sendToToxicModel(textToSend);
        sendToEmotionModel(textToSend);
        sendToPersonalityModel(textToSend);
    }

    private String getHuggingFaceToken() {
        // BuildConfig.HUGGING_FACE_TOKEN ab yahan se access hoga
        // Iski value local.properties se ayegi (Step 2 & 3 mein bataya gaya hai)
        String token = BuildConfig.HUGGING_FACE_TOKEN;
        if (token == null || token.isEmpty() || token.equals("YOUR_HUGGING_FACE_TOKEN_PLACEHOLDER")) {
            Log.e(TAG, "Hugging Face Token is not set or is a placeholder in BuildConfig.");
            // Aap yahan par ek default behavior ya error handling kar sakte hain
            // Filhal, hum ek error message show karenge aur null return karenge
            // jisse API calls fail ho jayengi agar token sahi se set nahi hai.
            showToast("API Token Not Configured!");
            return null;
        }
        return token;
    }


    private void sendToToxicModel(String inputText) {
        String token = getHuggingFaceToken();
        if (token == null) return; // Agar token nahi hai to call na karein

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        String jsonBody = "{\"inputs\": \"" + inputText.replace("\"", "\\\"") + "\"}"; // Basic JSON escaping
        RequestBody body = RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url(TOXIC_MODEL_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + token) // YAHAN TOKEN BuildConfig SE AYEGA
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Toxic model API call failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Toxic Model API Response: " + responseData);
                    processAndSaveToxicResults(inputText, responseData);
                } else {
                    Log.e(TAG, "Toxic model request failed: " + response.code() + " - " + response.message());
                    showToast("Toxic model request failed: " + response.code());
                }
            }
        });
    }

    private void sendToEmotionModel(String inputText) {
        String token = getHuggingFaceToken();
        if (token == null) return;

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        String jsonBody = "{\"inputs\": \"" + inputText.replace("\"", "\\\"") + "\"}";
        RequestBody body = RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url(EMOTION_MODEL_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + token) // YAHAN TOKEN BuildConfig SE AYEGA
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Emotion model API call failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Emotion Model API Response: " + responseData);
                    processAndSaveEmotionResults(inputText, responseData);
                } else {
                    Log.e(TAG, "Emotion model request failed: " + response.code() + " - " + response.message());
                    showToast("Emotion model request failed: " + response.code());
                }
            }
        });
    }

    private void sendToPersonalityModel(String inputText) {
        String token = getHuggingFaceToken();
        if (token == null) return;

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        String jsonBody = "{\"inputs\": \"" + inputText.replace("\"", "\\\"") + "\"}";
        RequestBody body = RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url(PERSONALITY_MODEL_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + token) // YAHAN TOKEN BuildConfig SE AYEGA
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Personality model API call failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Personality Model API Response: " + responseData);
                    processAndSavePersonalityResults(inputText, responseData);
                } else {
                    Log.e(TAG, "Personality model request failed: " + response.code() + " - " + response.message());
                    showToast("Personality model request failed: " + response.code());
                }
            }
        });
    }

    private void processAndSaveToxicResults(String originalText, String apiResponse) {
        try {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user for toxic results");
                return;
            }

            String userId = currentUser.getUid();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            JSONArray jsonArray = new JSONArray(apiResponse);
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Toxic API response is empty array.");
                return;
            }
            JSONArray labelsArray = jsonArray.getJSONArray(0);

            Map<String, Object> mistakeData = new HashMap<>();
            mistakeData.put("original_text", originalText);
            mistakeData.put("timestamp", timestamp);

            Map<String, Double> labelsMap = new HashMap<>();

            for (int i = 0; i < labelsArray.length(); i++) {
                JSONObject labelObj = labelsArray.getJSONObject(i);
                String label = labelObj.getString("label");
                double score = labelObj.getDouble("score");
                labelsMap.put(label, score);
            }

            mistakeData.put("analysis", labelsMap);

            DatabaseReference mistakesRef = mDatabase.child("users")
                    .child(userId)
                    .child("mistakes")
                    .push();

            mistakesRef.setValue(mistakeData)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Toxic data saved to Firebase successfully"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to save toxic data to Firebase", e));

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing toxic API response: " + apiResponse, e);
            showToast("Error processing toxic response");
        } catch (Exception e) {
            Log.e(TAG, "General error in toxic processing", e);
            showToast("Error occurred in toxic processing");
        }
    }

    private void processAndSaveEmotionResults(String originalText, String apiResponse) {
        try {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user for emotion results");
                return;
            }

            String userId = currentUser.getUid();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            JSONArray jsonArray = new JSONArray(apiResponse);
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Emotion API response is empty array.");
                return;
            }
            JSONArray labelsArray = jsonArray.getJSONArray(0);

            Map<String, Object> sentimentData = new HashMap<>();
            sentimentData.put("original_text", originalText);
            sentimentData.put("timestamp", timestamp);

            Map<String, Double> labelsMap = new HashMap<>();

            for (int i = 0; i < labelsArray.length(); i++) {
                JSONObject labelObj = labelsArray.getJSONObject(i);
                String labelNumber = labelObj.getString("label");
                double score = labelObj.getDouble("score");

                try {
                    int labelIndex = Integer.parseInt(labelNumber.replace("LABEL_", ""));
                    if (labelIndex >= 0 && labelIndex < EMOTION_LABELS.length) {
                        String emotionName = EMOTION_LABELS[labelIndex];
                        labelsMap.put(emotionName, score);
                    } else {
                        labelsMap.put(labelNumber, score);
                    }
                } catch (NumberFormatException e) {
                    labelsMap.put(labelNumber, score);
                }
            }

            sentimentData.put("analysis", labelsMap);

            DatabaseReference sentimentsRef = mDatabase.child("users")
                    .child(userId)
                    .child("sentiments")
                    .push();

            sentimentsRef.setValue(sentimentData)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Emotion data saved to Firebase successfully"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to save emotion data to Firebase", e));

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing emotion API response: " + apiResponse, e);
            showToast("Error processing emotion response");
        } catch (Exception e) {
            Log.e(TAG, "General error in emotion processing", e);
            showToast("Error occurred in emotion processing");
        }
    }

    private void processAndSavePersonalityResults(String originalText, String apiResponse) {
        try {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user for personality results");
                return;
            }

            String userId = currentUser.getUid();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            JSONArray jsonArray = new JSONArray(apiResponse);
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Personality API response is empty array.");
                return;
            }
            JSONArray labelsArray = jsonArray.getJSONArray(0);

            Map<String, Object> personalityData = new HashMap<>();
            personalityData.put("original_text", originalText);
            personalityData.put("timestamp", timestamp);

            Map<String, Double> labelsMap = new HashMap<>();

            for (int i = 0; i < labelsArray.length(); i++) {
                JSONObject labelObj = labelsArray.getJSONObject(i);
                String labelNumber = labelObj.getString("label");
                double score = labelObj.getDouble("score");

                try {
                    int labelIndex = Integer.parseInt(labelNumber.replace("LABEL_", ""));
                    if (labelIndex >= 0 && labelIndex < PERSONALITY_LABELS.length) {
                        String traitName = PERSONALITY_LABELS[labelIndex];
                        labelsMap.put(traitName, score);
                    } else {
                        labelsMap.put(labelNumber, score);
                    }
                } catch (NumberFormatException e) {
                    labelsMap.put(labelNumber, score);
                }
            }

            personalityData.put("analysis", labelsMap);

            DatabaseReference personalityRef = mDatabase.child("users")
                    .child(userId)
                    .child("personality_traits")
                    .push();

            personalityRef.setValue(personalityData)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Personality data saved to Firebase successfully"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to save personality data to Firebase", e));

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing personality API response: " + apiResponse, e);
            showToast("Error processing personality response");
        } catch (Exception e) {
            Log.e(TAG, "General error in personality processing", e);
            showToast("Error occurred in personality processing");
        }
    }


    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(MyKeyboardService.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroy() {
        saveAllTypedText(); // Consider if you really need to save on destroy
        super.onDestroy();
    }
}