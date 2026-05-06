package com.islvision.islens;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class TermsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "islens_prefs";
    private static final String KEY_TERMS  = "terms_accepted";

    // ── Your real hosted URLs ─────────────────────────────────────────
    private static final String PRIVACY_POLICY_URL =
            "https://sites.google.com/view/islens-privacy-policy/home";
    private static final String TERMS_URL =
            "https://sites.google.com/view/islens-terms-and-condition/home";
    // ─────────────────────────────────────────────────────────────────

    private static final String[][] TERMS_SECTIONS = {
            {
                    "TERMS AND CONDITIONS",
                    "ISLens — Real-Time Indian Sign Language Recognition\nLast Updated: April 2026"
            },
            {
                    "1. Acceptance of Terms",
                    "By downloading, installing, or using ISLens (\"the App\"), you agree to be bound " +
                            "by these Terms and Conditions (\"Terms\"). If you do not agree to these Terms, " +
                            "please do not use the App. These Terms apply to all users of the App."
            },
            {
                    "2. Description of the App",
                    "ISLens is a mobile application that uses on-device Artificial Intelligence (AI) " +
                            "and Machine Learning to recognise all 26 letters of the Indian Sign Language (ISL) " +
                            "alphabet in real time using the device camera. The App also provides:\n\n" +
                            "  • Real-time sign-to-text translation\n" +
                            "  • Text-to-speech output in 58+ languages\n" +
                            "  • Sentence building and history saving\n" +
                            "  • An ISL alphabet reference chart"
            },
            {
                    "3. Permitted Use",
                    "ISLens is provided for personal, non-commercial, educational, and accessibility " +
                            "purposes only. You may use the App to:\n\n" +
                            "  • Learn and practise Indian Sign Language letters\n" +
                            "  • Assist communication for the hearing-impaired community\n" +
                            "  • Explore AI-powered sign recognition technology\n\n" +
                            "You may NOT use the App to:\n\n" +
                            "  • Redistribute, sell, sublicense, or commercially exploit the App or any part of it\n" +
                            "  • Reverse-engineer, decompile, or disassemble the App or its AI models\n" +
                            "  • Copy, modify, or create derivative works based on the App\n" +
                            "  • Use the App for any unlawful, harmful, or deceptive purpose\n" +
                            "  • Attempt to extract or replicate the TFLite model files included in the App"
            },
            {
                    "4. Accuracy Disclaimer",
                    "ISLens uses a custom-trained TensorFlow Lite model for sign detection. While we " +
                            "have worked to maximise accuracy, we do not guarantee 100% detection accuracy in " +
                            "all conditions. Accuracy may vary based on:\n\n" +
                            "  • Lighting conditions (low or very bright light reduces accuracy)\n" +
                            "  • Hand position and orientation (right hand only is supported)\n" +
                            "  • Background complexity\n" +
                            "  • Device camera quality\n" +
                            "  • Distance from the camera\n\n" +
                            "ISLens is NOT a medical device, communication aid for critical situations, or a " +
                            "certified accessibility tool. Do not rely solely on ISLens for safety-critical communication."
            },
            {
                    "5. Camera & Microphone Permission",
                    "The App requires camera access to perform sign language detection. This permission " +
                            "is used exclusively for real-time on-device inference. No camera feed, image, or " +
                            "video is transmitted to any external server."
            },
            {
                    "6. Internet Usage",
                    "The App uses the internet solely for the following:\n\n" +
                            "  • Translation via the MyMemory REST API (https://mymemory.translated.net) — " +
                            "free and anonymous, subject to a daily quota of approximately 5,000 words\n" +
                            "  • Sending feedback via Web3Forms (if the user submits a feedback form)\n" +
                            "  • Sending a thank-you email via EmailJS (if the user submits feedback and " +
                            "provides their email address)\n\n" +
                            "No camera data, detected signs, or user-specific data is sent over the internet."
            },
            {
                    "7. Sentence History",
                    "Sentences saved via the \"Save\" button in the App are stored locally on your " +
                            "device only, in the App's internal storage. This data is:\n\n" +
                            "  • Not backed up to any cloud service by the App\n" +
                            "  • Permanently deleted when you uninstall the App\n" +
                            "  • Accessible only by ISLens on your device"
            },
            {
                    "8. Intellectual Property",
                    "All content within the App — including but not limited to the TFLite model " +
                            "(isl_ultimate.tflite), the ISL alphabet chart, source code, UI design, graphics, " +
                            "and branding — is the intellectual property of the ISLens team.\n\n" +
                            "© 2026 ISLens. All Rights Reserved.\n\n" +
                            "Unauthorised copying, distribution, or modification of any part of this App is " +
                            "strictly prohibited and may constitute a violation of copyright law."
            },
            {
                    "9. Third-Party Services",
                    "The App integrates the following third-party services. Each service is subject " +
                            "to its own terms and privacy policy:\n\n" +
                            "  • Google MediaPipe — on-device hand landmark detection\n" +
                            "  • TensorFlow Lite — on-device AI inference (Google LLC)\n" +
                            "  • MyMemory Translation API — free REST translation\n" +
                            "  • Web3Forms — feedback form processing\n" +
                            "  • EmailJS — thank-you email delivery (no password stored)\n" +
                            "  • Android Text-to-Speech — on-device speech synthesis (Google LLC)\n\n" +
                            "We are not responsible for the availability, accuracy, or conduct of these " +
                            "third-party services."
            },
            {
                    "10. Limitation of Liability",
                    "To the maximum extent permitted by applicable law, the ISLens team shall not " +
                            "be liable for:\n\n" +
                            "  • Any direct, indirect, incidental, or consequential damages arising from " +
                            "the use or inability to use the App\n" +
                            "  • Any loss of data, including saved sentence history\n" +
                            "  • Errors or inaccuracies in sign detection or translation\n" +
                            "  • Any issues arising from third-party service outages or changes"
            },
            {
                    "11. Changes to the App and These Terms",
                    "We reserve the right to:\n\n" +
                            "  • Update, modify, or discontinue the App at any time without notice\n" +
                            "  • Update these Terms at any time\n\n" +
                            "Continued use of the App after any changes to these Terms constitutes your " +
                            "acceptance of the new Terms. The \"Last Updated\" date at the top of this " +
                            "document will reflect any changes."
            },
            {
                    "12. Children's Privacy",
                    "ISLens does not knowingly collect personal information from children under the " +
                            "age of 13. The App does not require user registration, login, or the provision " +
                            "of any personal data. The feedback feature is optional and requires the user " +
                            "to voluntarily provide their name and email address."
            },
            {
                    "13. Governing Law",
                    "These Terms shall be governed by and construed in accordance with the laws of " +
                            "India. Any disputes arising under these Terms shall be subject to the exclusive " +
                            "jurisdiction of the courts located in India."
            },
            {
                    "14. Contact Us",
                    "If you have any questions about these Terms, please contact us at:\n\n" +
                            "  Email: islens.support@gmail.com\n" +
                            "  App:   ISLens — Indian Sign Language Recognition\n" +
                            "  Team:  ISLens Development Team\n\n" +
                            "© 2026 ISLens · All Rights Reserved"
            },

            // ── PRIVACY POLICY ────────────────────────────────────────────────
            {
                    "PRIVACY POLICY",
                    "ISLens — Real-Time Indian Sign Language Recognition\nLast Updated: April 2026\n\n" +
                            "ISLens is committed to protecting your privacy. This Privacy Policy explains what " +
                            "data the App accesses, how it is used, and your rights. By using ISLens, you agree " +
                            "to the practices described in this Privacy Policy."
            },
            {
                    "1. Data We Do NOT Collect",
                    "ISLens does NOT collect, store, or transmit:\n\n" +
                            "  ✗ Camera images or video recordings\n" +
                            "  ✗ Detected sign language letters or words\n" +
                            "  ✗ Device identifiers (IMEI, advertising ID, etc.)\n" +
                            "  ✗ Location data\n" +
                            "  ✗ Usage analytics or crash logs sent to our servers\n" +
                            "  ✗ Personal profile information\n" +
                            "  ✗ Any data without your explicit action"
            },
            {
                    "2. Camera Access",
                    "ISLens requires camera permission to function. The camera feed is processed " +
                            "entirely on your device using the TensorFlow Lite model and Google MediaPipe " +
                            "Hand Landmarker. No frame, image, or video from your camera is ever uploaded, " +
                            "stored externally, or shared with any third party.\n\n" +
                            "The camera is active only while the App is open and the \"Detect\" function is in use."
            },
            {
                    "3. Local Data Storage",
                    "The App stores the following data locally on your device only:\n\n" +
                            "  • Sentence History: Sentences you manually save using the \"Save\" button are " +
                            "stored in the App's private internal storage (islens_history.txt). This file is " +
                            "only accessible by ISLens and is permanently deleted when you uninstall the App.\n\n" +
                            "  • App Preferences: Settings such as dark/light mode, sound preferences, " +
                            "vibration on/off, and selected language are stored in SharedPreferences on your " +
                            "device. These are also deleted on uninstall.\n\n" +
                            "No data is backed up to any cloud service by the App itself."
            },
            {
                    "4. Internet and Network Usage",
                    "The App connects to the internet only for the following specific purposes:\n\n" +
                            "  4a. TRANSLATION\n" +
                            "  When you select a non-English output language, the detected text is sent to the " +
                            "MyMemory REST API for translation. No personally identifiable information is included.\n\n" +
                            "  4b. FEEDBACK FORM (OPTIONAL)\n" +
                            "  If you choose to submit feedback, the following data you voluntarily provide is " +
                            "sent to Web3Forms and forwarded to the developer email:\n" +
                            "    - Your name\n" +
                            "    - Your email address\n" +
                            "    - Your rating (1–5 stars)\n" +
                            "    - Your feedback message\n\n" +
                            "  4c. THANK-YOU EMAIL (OPTIONAL)\n" +
                            "  If you provide your email address in the feedback form, a thank-you confirmation " +
                            "email is sent to that address via EmailJS. No password is stored in the App. " +
                            "Your email address is not stored anywhere beyond this single transaction.\n\n" +
                            "The App does NOT use the internet for:\n" +
                            "  ✗ Sign detection (100% on-device)\n" +
                            "  ✗ Analytics or telemetry\n" +
                            "  ✗ Advertising\n" +
                            "  ✗ User tracking"
            },
            {
                    "5. Permissions Used",
                    "  • android.permission.CAMERA\n" +
                            "    Required for real-time sign language detection via the device camera.\n\n" +
                            "  • android.permission.INTERNET\n" +
                            "    Required for translation (MyMemory API) and optional feedback submission.\n\n" +
                            "  • android.permission.VIBRATE\n" +
                            "    Required for vibration feedback on button presses (can be disabled in Settings).\n\n" +
                            "  • android.permission.WRITE_EXTERNAL_STORAGE (Android < 10 only)\n" +
                            "    Required to save the ISL alphabet chart image to the device gallery."
            },
            {
                    "6. Third-Party Services",
                    "ISLens uses the following third-party libraries and services:\n\n" +
                            "  • Google MediaPipe (on-device, no data sent)\n" +
                            "  • TensorFlow Lite by Google (on-device, no data sent)\n" +
                            "  • MyMemory Translation API — text to translate + target language code only\n" +
                            "  • Web3Forms — only if feedback is submitted\n" +
                            "  • EmailJS — thank-you email delivery (Play Store safe, no password in code)\n" +
                            "  • Android TextToSpeech (on-device Google TTS engine)"
            },
            {
                    "7. Children's Privacy",
                    "ISLens does not knowingly collect personal information from children under 13. " +
                            "The App does not require account creation, registration, or login. If you are a " +
                            "parent or guardian and believe your child has submitted personal data via the " +
                            "feedback form, please contact us at islens.support@gmail.com and we " +
                            "will delete it promptly."
            },
            {
                    "8. Data Security",
                    "  • All AI inference runs on-device — your camera feed never leaves your phone.\n" +
                            "  • Sentence history is stored in the App's private internal storage, " +
                            "inaccessible to other apps.\n" +
                            "  • Network requests use HTTPS encryption.\n" +
                            "  • We do not operate servers that store user data."
            },
            {
                    "9. Data Retention and Deletion",
                    "  • Sentence history: Stored locally until you manually delete it in the History " +
                            "screen, or until you uninstall the App.\n" +
                            "  • App preferences: Stored locally and deleted on uninstall.\n" +
                            "  • Feedback data: Retained in the developer's email inbox for support purposes. " +
                            "You may request deletion by contacting us.\n\n" +
                            "To delete all your local App data immediately: uninstall ISLens from your device. " +
                            "Android will remove all associated App data automatically."
            },
            {
                    "10. Your Rights",
                    "Since ISLens does not collect personal data in its normal operation, most data " +
                            "rights are exercised by simply uninstalling the App. For feedback data you may " +
                            "have submitted, you have the right to:\n\n" +
                            "  • Request access to the data you submitted\n" +
                            "  • Request deletion of your feedback data\n" +
                            "  • Withdraw your feedback at any time\n\n" +
                            "To exercise these rights, email: islens.support@gmail.com"
            },
            {
                    "11. Changes to This Privacy Policy",
                    "We may update this Privacy Policy from time to time. Any changes will be " +
                            "reflected in the \"Last Updated\" date at the top of this document and, where " +
                            "significant, announced through an App update description on the Play Store. " +
                            "Continued use of the App after changes constitutes acceptance of the updated " +
                            "Privacy Policy."
            },
            {
                    "12. Contact Us",
                    "If you have any questions or concerns about this Privacy Policy or your data, " +
                            "please contact us:\n\n" +
                            "  Email: islens.support@gmail.com\n" +
                            "  App:   ISLens — Indian Sign Language Recognition\n" +
                            "  Team:  ISLens Development Team\n\n" +
                            "© 2026 ISLens · All Rights Reserved"
            }
    };

    private boolean scrolledToBottom = false;
    private CheckBox acceptCheckBox;
    private MaterialButton acceptButton;
    private Vibrator vibrator;
    private MediaPlayer clickPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int mode = SplashActivity.getThemeMode(this);
        SplashActivity.applyThemeMode(mode);

        super.onCreate(savedInstanceState);
        // Disable edge-to-edge forced by targetSdk 35
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_terms);

        vibrator    = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        clickPlayer = safeCreatePlayer();

        LinearLayout container = findViewById(R.id.termsContentContainer);
        populateTerms(container);

        ScrollView scrollView = findViewById(R.id.termsScrollView);
        acceptCheckBox        = findViewById(R.id.acceptCheckBox);
        acceptButton          = findViewById(R.id.acceptButton);

        acceptButton.setEnabled(false);
        acceptButton.setAlpha(0.45f);

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) -> {
            if (scrolledToBottom) return;
            ScrollView sv     = (ScrollView) v;
            int contentHeight = sv.getChildAt(0).getHeight();
            int visibleHeight = sv.getHeight();
            int maxScroll     = contentHeight - visibleHeight;
            if (scrollY >= maxScroll - dp(40)) {
                scrolledToBottom = true;
                acceptCheckBox.setEnabled(true);
                acceptCheckBox.setAlpha(1f);
                showScrollReachedHint();
            }
        });

        acceptCheckBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!scrolledToBottom) {
                btn.setChecked(false);
                Toast.makeText(this,
                        "Please scroll and read the full terms first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            acceptButton.setEnabled(isChecked);
            acceptButton.setAlpha(isChecked ? 1f : 0.45f);
        });

        acceptButton.setOnClickListener(v -> {
            vibrate();
            playSound();
            if (!acceptCheckBox.isChecked()) {
                Toast.makeText(this,
                        "Please read and accept the terms.", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_TERMS, true)
                    .apply();
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        // Privacy Policy link
        TextView privacyLink = container.findViewById(R.id.privacyPolicyLink);
        if (privacyLink != null) {
            privacyLink.setOnClickListener(v -> openUrl(PRIVACY_POLICY_URL));
        }

        // Full Terms link
        TextView termsLink = container.findViewById(R.id.fullTermsLink);
        if (termsLink != null) {
            termsLink.setOnClickListener(v -> openUrl(TERMS_URL));
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateTerms(LinearLayout container) {
        int dp6  = dp(6);
        int dp16 = dp(16);

        for (int i = 0; i < TERMS_SECTIONS.length; i++) {
            String  heading = TERMS_SECTIONS[i][0];
            String  body    = TERMS_SECTIONS[i][1];
            boolean isLast  = (i == TERMS_SECTIONS.length - 1);

            // Section divider before Privacy Policy heading
            if (heading.equals("PRIVACY POLICY")) {
                TextView divider = new TextView(this);
                LinearLayout.LayoutParams dParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                dParams.topMargin    = dp(20);
                dParams.bottomMargin = dp(8);
                divider.setLayoutParams(dParams);
                divider.setText("────────────────────────────────────");
                divider.setTextColor(Color.parseColor("#00E5FF"));
                divider.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                container.addView(divider);
            }

            TextView tvHeading = new TextView(this);
            LinearLayout.LayoutParams hParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hParams.bottomMargin = dp6;
            tvHeading.setLayoutParams(hParams);
            tvHeading.setText(heading);
            tvHeading.setTextColor(Color.parseColor("#00E5FF"));
            // Main section titles slightly larger
            boolean isMainTitle = heading.equals("TERMS AND CONDITIONS") || heading.equals("PRIVACY POLICY");
            tvHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, isMainTitle ? 16 : 14);
            tvHeading.setTypeface(null, Typeface.BOLD);
            container.addView(tvHeading);

            TextView tvBody = new TextView(this);
            LinearLayout.LayoutParams bParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bParams.bottomMargin = isLast ? dp(8) : dp16;
            tvBody.setLayoutParams(bParams);
            tvBody.setText(body);
            tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvBody.setLineSpacing(0f, 1.5f);
            tvBody.setTextColor(resolveAttrColor(R.attr.islTextSecondary, 0xFFAAAAAA));
            container.addView(tvBody);
        }

        // ── "View full Privacy Policy" link ───────────────────────────────
        TextView tvPrivacy = new TextView(this);
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pParams.topMargin = dp(12);
        tvPrivacy.setLayoutParams(pParams);
        tvPrivacy.setId(R.id.privacyPolicyLink);
        tvPrivacy.setText("View full Privacy Policy \u2192");
        tvPrivacy.setTextColor(Color.parseColor("#00E5FF"));
        tvPrivacy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvPrivacy.setTypeface(null, Typeface.BOLD);
        tvPrivacy.setPadding(0, dp(4), 0, dp(4));
        container.addView(tvPrivacy);

        // ── "View full Terms & Conditions" link ───────────────────────────
        TextView tvTerms = new TextView(this);
        LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tParams.topMargin    = dp(6);
        tParams.bottomMargin = dp(16);
        tvTerms.setLayoutParams(tParams);
        tvTerms.setId(R.id.fullTermsLink);
        tvTerms.setText("View full Terms & Conditions \u2192");
        tvTerms.setTextColor(Color.parseColor("#00E5FF"));
        tvTerms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTerms.setTypeface(null, Typeface.BOLD);
        tvTerms.setPadding(0, dp(4), 0, dp(4));
        container.addView(tvTerms);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private int resolveAttrColor(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) return tv.data;
        return fallback;
    }

    private void showScrollReachedHint() {
        Toast.makeText(this,
                "\u2713 You've reached the bottom \u2014 now accept to continue.",
                Toast.LENGTH_SHORT).show();
    }

    private void vibrate() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null || am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) return;
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                vibrator.vibrate(50);
            }
        }
    }

    private void playSound() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
        try {
            if (clickPlayer != null) {
                if (clickPlayer.isPlaying()) clickPlayer.seekTo(0);
                clickPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    private MediaPlayer safeCreatePlayer() {
        try { return MediaPlayer.create(this, R.raw.click_sound); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clickPlayer != null) { clickPlayer.release(); clickPlayer = null; }
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this,
                "Please accept the terms to use ISLens.",
                Toast.LENGTH_SHORT).show();
    }

    public static boolean hasAccepted(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_TERMS, false);
    }
}