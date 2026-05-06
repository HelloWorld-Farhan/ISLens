package com.islvision.islens;

import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FeedbackActivity — FINAL (Play Store Safe)
 * ─────────────────────────────────────────────
 * CALL 1 → Web3Forms  → notifies YOU (dev) at your email
 * CALL 2 → EmailJS    → sends thank-you TO THE USER (no password in code)
 *
 * NO JavaMail. NO hardcoded Gmail password. NO SMTP. 100% Play Store safe.
 */
public class FeedbackActivity extends AppCompatActivity {

    // ── Web3Forms (notifies YOU) ───────────────────────────────────────────────
    private static final String WEB3FORMS_ACCESS_KEY = "d2fba443-3014-4337-a7d2-88e8016a7024";
    private static final String WEB3FORMS_URL        = "https://api.web3forms.com/submit";

    // ── EmailJS (thank-you email TO THE USER) ─────────────────────────────────
    private static final String EMAILJS_SERVICE_ID  = "service_q7g54pu";
    private static final String EMAILJS_TEMPLATE_ID = "template_cxsxtac";
    private static final String EMAILJS_PUBLIC_KEY  = "fxMHIPxfld30DBwka";
    private static final String EMAILJS_URL         = "https://api.emailjs.com/api/v1.0/email/send";
    // ──────────────────────────────────────────────────────────────────────────

    private RatingBar      ratingBar;
    private EditText       nameInput;
    private EditText       emailInput;
    private EditText       feedbackInput;
    private MaterialButton submitButton;
    private ProgressBar    progressBar;
    private TextView       ratingLabel;

    private MediaPlayer clickPlayer;

    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final Handler         uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Same theme resolution as every other page in the app
        int mode = SplashActivity.getThemeMode(this);
        SplashActivity.applyThemeMode(mode);

        super.onCreate(savedInstanceState);
        // Disable edge-to-edge forced by targetSdk 35
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_feedback);

        clickPlayer = safeCreatePlayer();

        ratingBar     = findViewById(R.id.feedbackRatingBar);
        nameInput     = findViewById(R.id.feedbackNameInput);
        emailInput    = findViewById(R.id.feedbackEmailInput);
        feedbackInput = findViewById(R.id.feedbackDescInput);
        submitButton  = findViewById(R.id.submitFeedbackBtn);
        progressBar   = findViewById(R.id.feedbackProgress);
        ratingLabel   = findViewById(R.id.ratingLabel);

        boolean darkMode = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(SettingsActivity.KEY_DARK_MODE, false);
        applyThemeColors(darkMode);

        findViewById(R.id.feedbackBackButton).setOnClickListener(v -> {
            vibrate(); playSound(); finish();
        });

        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
            if (fromUser) { vibrate(); playSound(); }
            ratingLabel.setText(getRatingLabel((int) rating));
        });

        submitButton.setOnClickListener(v -> {
            vibrate(); playSound();
            handleSubmit();
        });
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private void handleSubmit() {
        String userName  = nameInput.getText().toString().trim();
        String userEmail = emailInput.getText().toString().trim();
        String feedback  = feedbackInput.getText().toString().trim();
        int    rating    = (int) ratingBar.getRating();

        if (rating == 0) {
            Toast.makeText(this, "Please give a star rating.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(userName)) {
            nameInput.setError("Please enter your name");
            nameInput.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(userEmail)
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            emailInput.setError("Please enter a valid email address");
            emailInput.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(feedback)) {
            feedbackInput.setError("Please write your feedback");
            feedbackInput.requestFocus();
            return;
        }

        submitButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        sendFeedback(userName, userEmail, feedback, rating);
    }

    // ── Background: Web3Forms + EmailJS ──────────────────────────────────────
    private void sendFeedback(String userName, String userEmail,
                              String feedbackText, int starRating) {
        executor.execute(() -> {
            boolean success = false;
            try {
                String stars = "★".repeat(starRating) + "☆".repeat(5 - starRating);

                // ── CALL 1: Web3Forms → notifies YOU ─────────────────────────
                JSONObject devPayload = new JSONObject();
                devPayload.put("access_key", WEB3FORMS_ACCESS_KEY);
                devPayload.put("subject",    "ISLens Feedback — " + starRating + "/5 ⭐ from " + userName + " (" + userEmail + ")");
                devPayload.put("from_name",  userName);
                devPayload.put("email",      userEmail);
                devPayload.put("name",       userName);
                devPayload.put("rating",     starRating + "/5  " + stars);
                devPayload.put("message",    feedbackText);
                devPayload.put("replyto",    userEmail);
                devPayload.put("botcheck",   "");
                success = postWeb3Forms(devPayload);

                // ── CALL 2: EmailJS → thank-you TO THE USER ───────────────────
                if (success) {
                    sendThankYouViaEmailJS(userName, userEmail, feedbackText, starRating, stars);
                }

            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }

            final boolean finalSuccess = success;
            uiHandler.post(() -> onSendResult(finalSuccess));
        });
    }

    // ── EmailJS (no password, Play Store safe) ────────────────────────────────
    private void sendThankYouViaEmailJS(String userName, String toEmail,
                                        String feedbackText, int starRating, String stars) {
        try {
            JSONObject templateParams = new JSONObject();
            templateParams.put("to_name",  userName);
            templateParams.put("to_email", toEmail);
            templateParams.put("rating",   starRating + "/5  " + stars);
            templateParams.put("message",  feedbackText);
            templateParams.put("reply_to", "islens.support@gmail.com");

            JSONObject payload = new JSONObject();
            payload.put("service_id",      EMAILJS_SERVICE_ID);
            payload.put("template_id",     EMAILJS_TEMPLATE_ID);
            payload.put("user_id",         EMAILJS_PUBLIC_KEY);
            payload.put("template_params", templateParams);

            URL url = new URL(EMAILJS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("origin", "http://localhost");

            byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(data); }

            conn.getResponseCode(); // trigger the request
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace(); // silent fail — user already got success toast
        }
    }

    // ── Web3Forms POST ────────────────────────────────────────────────────────
    private boolean postWeb3Forms(JSONObject json) {
        HttpURLConnection conn = null;
        try {
            byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(WEB3FORMS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            try (OutputStream os = conn.getOutputStream()) { os.write(data); }

            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code == 200 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            try { return new JSONObject(sb.toString()).optBoolean("success", false); }
            catch (Exception e) { return code == 200; }
        } catch (Exception e) {
            e.printStackTrace(); return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────
    private void onSendResult(boolean success) {
        if (isFinishing() || isDestroyed()) return;
        progressBar.setVisibility(View.GONE);
        submitButton.setEnabled(true);
        if (success) {
            Toast.makeText(this,
                    "✅ Feedback submitted! Check your email for a confirmation.",
                    Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this,
                    "❌ Failed to send. Please check your internet and try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    private void applyThemeColors(boolean darkMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = darkMode ? 0xFF0A0A0F : 0xFFF0F4F8;
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        }

        android.content.res.ColorStateList goldColor =
                android.content.res.ColorStateList.valueOf(0xFFFFD700);
        android.content.res.ColorStateList emptyColor =
                android.content.res.ColorStateList.valueOf(
                        darkMode ? 0x40FFFFFF : 0xFFCCCCCC);

        ratingBar.setProgressTintList(goldColor);
        ratingBar.setSecondaryProgressTintList(goldColor);
        ratingBar.setProgressBackgroundTintList(emptyColor);
    }

    private String getRatingLabel(int stars) {
        switch (stars) {
            case 1: return "😞 Needs improvement";
            case 2: return "😐 Below average";
            case 3: return "🙂 Good";
            case 4: return "😊 Very good";
            case 5: return "🤩 Excellent!";
            default: return "Tap to rate";
        }
    }

    // ── Sound / Vibrate ───────────────────────────────────────────────────────
    private void vibrate()   { SettingsActivity.vibrateIfEnabled(this); }
    private void playSound() { SettingsActivity.playSoundIfEnabled(this, clickPlayer); }

    private MediaPlayer safeCreatePlayer() {
        try { return MediaPlayer.create(this, R.raw.click_sound); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (clickPlayer != null) { clickPlayer.release(); clickPlayer = null; }
    }
}