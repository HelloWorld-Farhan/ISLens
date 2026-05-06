package com.islvision.islens;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.media.MediaPlayer;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.EditText;
import android.widget.ListView;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ISL_Main";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private static final float CONFIDENCE_THRESHOLD = 0.85f;
    private static final int   REQUIRED_FRAMES      = 7;
    private static final int   COOLDOWN_MS          = 2000;

    private PreviewView        previewView;
    private TextView           resultText, confidenceText, wordText, sentenceText;
    private FloatingActionButton captureButton, backspaceButton, spaceButton, clearButton, referenceButton;
    private ImageButton        flashButton, speakWordButton, speakSentenceButton;
    private Button             targetLanguageButton;
    private ImageButton        swapLanguageButton;
    private ImageButton        historyButton;
    private ImageButton        saveHistoryBtn;

    private SignLanguageClassifier classifier;
    private ExecutorService        cameraExecutor;
    private Handler                mainHandler;
    private Camera                 camera;
    private MediaPlayer            clickPlayer;
    private Vibrator               vibrator;
    private TextToSpeech           textToSpeech;

    private boolean isFlashOn      = false;
    private StringBuilder currentWord     = new StringBuilder();
    private StringBuilder currentSentence = new StringBuilder();
    private boolean isCapturing    = false;
    private boolean canCapture     = true;
    private String  lastPredictedLetter = "";
    private int     consecutiveCount    = 0;

    private String currentTargetLangName = "English";
    private String currentSourceLangCode = "en";
    private String currentTargetLangCode = "en";

    // Language map: Display Name -> Language Code
    private final LinkedHashMap<String, String> languageMap = new LinkedHashMap<String, String>() {{
        put("Afrikaans",      "af");
        put("Arabic",         "ar");
        put("Belarusian",     "be");
        put("Bengali",        "bn");
        put("Bulgarian",      "bg");
        put("Catalan",        "ca");
        put("Chinese",        "zh");
        put("Croatian",       "hr");
        put("Czech",          "cs");
        put("Danish",         "da");
        put("Dutch",          "nl");
        put("English",        "en");
        put("Estonian",       "et");
        put("Finnish",        "fi");
        put("French",         "fr");
        put("Galician",       "gl");
        put("Georgian",       "ka");
        put("German",         "de");
        put("Greek",          "el");
        put("Gujarati",       "gu");
        put("Hebrew",         "he");
        put("Hindi",          "hi");
        put("Hungarian",      "hu");
        put("Icelandic",      "is");
        put("Indonesian",     "id");
        put("Italian",        "it");
        put("Japanese",       "ja");
        put("Kannada",        "kn");
        put("Korean",         "ko");
        put("Latvian",        "lv");
        put("Lithuanian",     "lt");
        put("Macedonian",     "mk");
        put("Malay",          "ms");
        put("Maltese",        "mt");
        put("Marathi",        "mr");
        put("Norwegian",      "no");
        put("Persian",        "fa");
        put("Polish",         "pl");
        put("Portuguese",     "pt");
        put("Romanian",       "ro");
        put("Russian",        "ru");
        put("Slovak",         "sk");
        put("Slovenian",      "sl");
        put("Spanish",        "es");
        put("Swahili",        "sw");
        put("Swedish",        "sv");
        put("Tagalog",        "tl");
        put("Tamil",          "ta");
        put("Telugu",         "te");
        put("Thai",           "th");
        put("Turkish",        "tr");
        put("Ukrainian",      "uk");
        put("Urdu",           "ur");
        put("Vietnamese",     "vi");
        put("Welsh",          "cy");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme already applied by SplashActivity before this Activity was started.
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_main);

        mainHandler    = new Handler(Looper.getMainLooper());
        cameraExecutor = Executors.newSingleThreadExecutor();

        initializeViews();
        initializeHardware();
        initializeButtons();

        // FIX: Initialise the classifier on a background thread so it cannot
        //      block the main thread and cause an ANR / crash on slower devices.
        cameraExecutor.execute(() -> {
            try {
                classifier = new SignLanguageClassifier(this);
                mainHandler.post(() -> {
                    if (checkPermissions()) {
                        startCamera();
                    } else {
                        requestPermissions();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Classifier init failed", e);
                mainHandler.post(() ->
                        Toast.makeText(this,
                                "Failed to load AI model. Please reinstall the app.",
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── View binding ──────────────────────────────────────────────
    private void initializeViews() {
        previewView          = findViewById(R.id.previewView);
        resultText           = findViewById(R.id.resultText);
        confidenceText       = findViewById(R.id.confidenceText);
        wordText             = findViewById(R.id.wordText);
        sentenceText         = findViewById(R.id.sentenceText);
        captureButton        = findViewById(R.id.captureButton);
        backspaceButton      = findViewById(R.id.backspaceButton);
        spaceButton          = findViewById(R.id.spaceButton);
        clearButton          = findViewById(R.id.clearButton);
        referenceButton      = findViewById(R.id.referenceButton);
        flashButton          = findViewById(R.id.flashButton);
        speakWordButton      = findViewById(R.id.speakWordButton);
        speakSentenceButton  = findViewById(R.id.speakSentenceButton);
        saveHistoryBtn       = findViewById(R.id.saveHistoryBtn);
        targetLanguageButton = findViewById(R.id.targetLanguageButton);
        swapLanguageButton   = findViewById(R.id.swapLanguageButton);
        historyButton        = findViewById(R.id.historyButton);

        if (resultText     != null) resultText.setText("--");
        if (confidenceText != null) confidenceText.setText("Ready");

        startLiveBlink();
    }

    private void startLiveBlink() {
        View liveBadge = findViewById(R.id.liveBadge);
        if (liveBadge == null) return;
        liveBadge.animate()
                .alpha(0.15f)
                .setDuration(700)
                .withEndAction(() ->
                        liveBadge.animate()
                                .alpha(1f)
                                .setDuration(700)
                                .withEndAction(this::startLiveBlink)
                                .start())
                .start();
    }

    // ── Hardware init ─────────────────────────────────────────────
    private void initializeHardware() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        try {
            clickPlayer = MediaPlayer.create(this, R.raw.click_sound);
            if (clickPlayer != null) clickPlayer.setVolume(1.0f, 1.0f);
        } catch (Exception e) {
            Log.e(TAG, "Click sound failed", e);
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void vibrate()   { SettingsActivity.vibrateIfEnabled(this); }
    private void playSound() { SettingsActivity.playSoundIfEnabled(this, clickPlayer); }

    // ── Button wiring ─────────────────────────────────────────────
    private void initializeButtons() {
        setupTranslationSpinners();

        // Capture
        if (captureButton != null) {
            captureButton.setOnClickListener(v -> {
                if (!isCapturing && canCapture) {
                    isCapturing = true;
                    lastPredictedLetter = "";
                    consecutiveCount = 0;
                    captureButton.setBackgroundTintList(
                            ColorStateList.valueOf(0xFFFF9800));
                    if (confidenceText != null) confidenceText.setText("Hold steady...");
                    vibrate();
                    playSound();
                }
            });
        }

        // Save history
        if (saveHistoryBtn != null) {
            saveHistoryBtn.setOnClickListener(v -> {
                vibrate();
                playSound();
                String sentence = sentenceText != null
                        ? sentenceText.getText().toString().trim() : "";
                if (!sentence.isEmpty()) {
                    saveToHistory(sentence);
                    Toast.makeText(this, "✅ Sentence saved to history!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠ No sentence to save", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Backspace
        if (backspaceButton != null) {
            backspaceButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                if (currentWord.length() > 0) {
                    currentWord.deleteCharAt(currentWord.length() - 1);
                    if (wordText != null) wordText.setText(currentWord.toString());
                }
            });
        }

        // Space
        if (spaceButton != null) {
            spaceButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                if (currentWord.length() > 0) {
                    if (currentSentence.length() > 0) currentSentence.append(" ");
                    currentSentence.append(currentWord.toString());
                    if (sentenceText != null) sentenceText.setText(currentSentence.toString());
                    translateAndUpdateSentence(currentSentence.toString());
                    currentWord = new StringBuilder();
                    if (wordText != null) wordText.setText("");
                }
            });
        }

        // Clear
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                currentWord = new StringBuilder();
                currentSentence = new StringBuilder();
                if (wordText     != null) wordText.setText("");
                if (sentenceText != null) sentenceText.setText("");
                if (resultText   != null) resultText.setText("--");
                if (confidenceText != null) confidenceText.setText("Cleared");
            });
        }

        // Reference / Guide
        if (referenceButton != null) {
            referenceButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            });
        }

        // Flash
        if (flashButton != null) {
            flashButton.setOnClickListener(v -> {
                if (camera != null) {
                    try {
                        isFlashOn = !isFlashOn;
                        camera.getCameraControl().enableTorch(isFlashOn);
                        flashButton.setImageResource(
                                isFlashOn ? R.drawable.flash : R.drawable.flash_off);
                        vibrate();
                        playSound();
                    } catch (Exception e) {
                        Log.e(TAG, "Flash failed", e);
                    }
                }
            });
        }

        // Speak word
        if (speakWordButton != null) {
            speakWordButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                String word = wordText != null ? wordText.getText().toString() : "";
                if (!word.isEmpty() && textToSpeech != null) {
                    speakWordButton.setImageResource(R.drawable.volume_on);
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) {
                            runOnUiThread(() -> speakWordButton.setImageResource(R.drawable.volume_off));
                        }
                        @Override public void onError(String id) {
                            runOnUiThread(() -> speakWordButton.setImageResource(R.drawable.volume_off));
                        }
                    });
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "word");
                    setTtsLocaleForLanguage(currentTargetLangCode);
                    textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, params, "word");
                }
            });
        }

        // Speak sentence
        if (speakSentenceButton != null) {
            speakSentenceButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                String sentence = sentenceText != null ? sentenceText.getText().toString() : "";
                if (!sentence.isEmpty() && textToSpeech != null) {
                    speakSentenceButton.setImageResource(R.drawable.volume_on);
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) {
                            runOnUiThread(() -> speakSentenceButton.setImageResource(R.drawable.volume_off));
                        }
                        @Override public void onError(String id) {
                            runOnUiThread(() -> speakSentenceButton.setImageResource(R.drawable.volume_off));
                        }
                    });
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence");
                    setTtsLocaleForLanguage(currentTargetLangCode);
                    textToSpeech.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "sentence");
                }
            });
        }

        // History
        if (historyButton != null) {
            historyButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            });
        }

        // Settings
        ImageButton settingsButton = findViewById(R.id.mainSettingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            });
        }

        // Feedback
        ImageButton feedbackButton = findViewById(R.id.feedbackButton);
        if (feedbackButton != null) {
            feedbackButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                startActivity(new Intent(MainActivity.this, FeedbackActivity.class));
            });
        }
    }

    // ── Translation setup ─────────────────────────────────────────
    private void setupTranslationSpinners() {
        currentSourceLangCode = "en";
        currentTargetLangCode = "en";
        currentTargetLangName = "English";
        if (targetLanguageButton != null) targetLanguageButton.setText("English ▼");

        if (targetLanguageButton != null) {
            targetLanguageButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                showLanguagePickerDialog();
            });
        }

        if (swapLanguageButton != null) {
            swapLanguageButton.setOnClickListener(v -> {
                vibrate();
                playSound();
                currentTargetLangCode = "en";
                currentTargetLangName = "English";
                if (targetLanguageButton != null) targetLanguageButton.setText("English ▼");
                if (currentWord.length() > 0)     translateAndUpdateWord(currentWord.toString());
                if (currentSentence.length() > 0) translateAndUpdateSentence(currentSentence.toString());
            });
        }
    }

    private void showLanguagePickerDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_language_picker);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.75));
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        }

        EditText  searchBox = dialog.findViewById(R.id.searchLanguage);
        ListView  listView  = dialog.findViewById(R.id.languageList);

        if (searchBox == null || listView == null) return;

        List<String>       allNames = new ArrayList<>(languageMap.keySet());
        final List<String> filtered = new ArrayList<>(allNames);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, filtered) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View     v  = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(15);
                tv.setPadding(48, 24, 48, 24);
                if (filtered.get(position).equals(currentTargetLangName)) {
                    tv.setTextColor(0xFF00E5FF);
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                v.setBackgroundColor(0xFF1A1A2E);
                return v;
            }
        };
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filtered.clear();
                String query = s.toString().toLowerCase();
                for (String name : allNames) {
                    if (name.toLowerCase().contains(query)) filtered.add(name);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = filtered.get(position);
            String selectedCode = languageMap.get(selectedName);
            if (selectedCode != null) {
                vibrate();
                playSound();
                dialog.dismiss();
                applyLanguageSelection(selectedName, selectedCode);
            }
        });

        dialog.show();
    }

    private void applyLanguageSelection(String langName, String langCode) {
        currentTargetLangCode = langCode;
        currentTargetLangName = langName;
        if (targetLanguageButton != null) targetLanguageButton.setText(langName + " ▼");
        DownloadHelper.show(this, langName, langCode,
                (code, name) -> {
                    if (currentWord.length() > 0)     translateAndUpdateWord(currentWord.toString());
                    if (currentSentence.length() > 0) translateAndUpdateSentence(currentSentence.toString());
                },
                code -> Toast.makeText(this,
                        "Could not apply language — check your connection.",
                        Toast.LENGTH_SHORT).show());
    }

    private void translateAndUpdateWord(String originalWord) {
        if ("en".equals(currentTargetLangCode) || wordText == null) {
            if (wordText != null) wordText.setText(originalWord);
            return;
        }
        MyMemoryTranslator.translateFromEnglish(originalWord, currentTargetLangCode,
                result -> { if (wordText != null) wordText.setText(result != null ? result : originalWord); });
    }

    private void translateAndUpdateSentence(String originalSentence) {
        if ("en".equals(currentTargetLangCode) || sentenceText == null) {
            if (sentenceText != null) sentenceText.setText(originalSentence);
            return;
        }
        MyMemoryTranslator.translateFromEnglish(originalSentence, currentTargetLangCode,
                result -> { if (sentenceText != null) sentenceText.setText(result != null ? result : originalSentence); });
    }

    // ── onResume — apply theme colours ────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        applyThemeColors(dark);
    }

    private void applyThemeColors(boolean darkMode) {
        int bgScreen      = darkMode ? 0xFF0A0A0F : 0xFFF0F4F8;
        int bgCard        = darkMode ? 0xFF1A1A2E : 0xFFFFFFFF;
        int textPrimary   = darkMode ? 0xFFFFFFFF : 0xFF0D1B2A;
        int textSecondary = darkMode ? 0xFFC0C8FF : 0xFF2C3E50;
        int textLabel     = darkMode ? 0xFF6060A0 : 0xFF6B7C93;
        int textHint      = darkMode ? 0x40FFFFFF : 0x80000000;
        int divider       = darkMode ? 0x20FFFFFF : 0x20000000;
        int banner        = darkMode ? 0x0D00E5FF : 0x0D0097A7;
        int accent        = darkMode ? 0xFF00E5FF : 0xFF0097A7;
        int iconBtnBg     = darkMode ? 0x1AFFFFFF : 0x1A0097A7;
        int statusBar     = darkMode ? 0xFF0A0A0F : 0xFFE2EAF4;

        View root = previewView != null ? previewView.getRootView() : null;
        if (root != null) root.setBackgroundColor(bgScreen);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(statusBar);
            getWindow().setNavigationBarColor(statusBar);
        }

        setCardColor(R.id.detectedSignCard, bgCard);
        setCardColor(R.id.wordSentenceCard, bgCard);

        if (resultText   != null) resultText.setTextColor(textPrimary);
        if (wordText     != null) { wordText.setTextColor(textPrimary);     wordText.setHintTextColor(textHint); }
        if (sentenceText != null) { sentenceText.setTextColor(textSecondary); sentenceText.setHintTextColor(textHint); }
        if (confidenceText != null) confidenceText.setTextColor(accent);

        setLabelColor(R.id.wordLabel,       textLabel);
        setLabelColor(R.id.sentenceLabel,   textLabel);
        setLabelColor(R.id.historyNoteText, textLabel);

        setDividerColor(R.id.divider,     divider);
        setDividerColor(R.id.langDivider, divider);

        View bannerView = findViewById(R.id.rightHandBanner);
        if (bannerView != null) bannerView.setBackgroundColor(banner);

        if (swapLanguageButton != null)
            swapLanguageButton.setBackgroundTintList(
                    ColorStateList.valueOf(iconBtnBg));

        if (targetLanguageButton != null) targetLanguageButton.setTextColor(accent);
    }

    private void setCardColor(int viewId, int color) {
        View v = findViewById(viewId);
        if (v instanceof androidx.cardview.widget.CardView)
            ((androidx.cardview.widget.CardView) v).setCardBackgroundColor(color);
    }
    private void setLabelColor(int viewId, int color) {
        View v = findViewById(viewId);
        if (v instanceof TextView) ((TextView) v).setTextColor(color);
    }
    private void setDividerColor(int viewId, int color) {
        View v = findViewById(viewId);
        if (v != null) v.setBackgroundColor(color);
    }

    // ── Camera ────────────────────────────────────────────────────
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bindCameraUseCases(provider);
            } catch (Exception e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        if (previewView != null) preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> analyzeFrame(imageProxy));

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(this, selector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Bind failed", e);
        }
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        if (!isCapturing || classifier == null) {
            imageProxy.close();
            return;
        }
        try {
            Bitmap bitmap = convertImageProxyToBitmap(imageProxy);
            if (bitmap != null) {
                Bitmap cropped = cropToCenter(bitmap);
                SignLanguageClassifier.Result result = classifier.classify(cropped);
                runOnUiThread(() -> updateUIWithResult(result));
                if (cropped != bitmap) cropped.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Analysis error", e);
        } finally {
            imageProxy.close();
        }
    }

    private void updateUIWithResult(SignLanguageClassifier.Result result) {
        if (resultText == null || confidenceText == null) return;
        String label = result.getLabel();
        float  conf  = result.getConfidence();

        if (conf >= CONFIDENCE_THRESHOLD) {
            resultText.setText(label);
        } else {
            resultText.setText("--");
        }

        if (conf >= CONFIDENCE_THRESHOLD) {
            confidenceText.setTextColor(0xFF4CAF50);
            if (label.equals(lastPredictedLetter)) {
                consecutiveCount++;
                int remaining = REQUIRED_FRAMES - consecutiveCount;
                if (remaining > 0) {
                    confidenceText.setText(String.format("%s - %d more", label, remaining));
                } else {
                    confidenceText.setText(String.format("%.0f%%", conf * 100));
                }
            } else {
                lastPredictedLetter = label;
                consecutiveCount    = 1;
                confidenceText.setText(String.format("%.0f%%", conf * 100));
            }

            if (consecutiveCount >= REQUIRED_FRAMES) {
                addLetterToWord(label);
                resetCapture();
            }
        } else {
            confidenceText.setTextColor(0xFFFF5722);
            confidenceText.setText("Ready");
        }
    }

    private void addLetterToWord(String letter) {
        currentWord.append(letter);
        if (wordText != null) wordText.setText(currentWord.toString());
        translateAndUpdateWord(currentWord.toString());
        if (captureButton != null)
            captureButton.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        Toast.makeText(this, "✓ " + letter, Toast.LENGTH_SHORT).show();
        vibrate();
        playSound();

        canCapture = false;
        mainHandler.postDelayed(() -> {
            canCapture = true;
            if (captureButton  != null)
                captureButton.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
            if (resultText     != null) resultText.setText("--");
            if (confidenceText != null) confidenceText.setText("Ready");
        }, COOLDOWN_MS);
    }

    private void resetCapture() {
        isCapturing         = false;
        lastPredictedLetter = "";
        consecutiveCount    = 0;
    }

    // ── TTS locale ────────────────────────────────────────────────
    private void setTtsLocaleForLanguage(String langCode) {
        if (textToSpeech == null) return;
        Locale locale;
        switch (langCode) {
            case "hi": locale = new Locale("hi", "IN"); break;
            case "zh": locale = Locale.CHINESE;         break;
            case "ja": locale = Locale.JAPANESE;        break;
            case "ko": locale = Locale.KOREAN;          break;
            case "fr": locale = Locale.FRENCH;          break;
            case "de": locale = Locale.GERMAN;          break;
            case "es": locale = new Locale("es", "ES"); break;
            case "ar": locale = new Locale("ar");       break;
            case "ru": locale = new Locale("ru");       break;
            case "pt": locale = new Locale("pt");       break;
            case "it": locale = Locale.ITALIAN;         break;
            default:   locale = Locale.US;              break;
        }
        textToSpeech.setLanguage(locale);
    }

    // ── Image conversion ─────────────────────────────────────────
    private Bitmap convertImageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes  = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);

            byte[]  jpegBytes = out.toByteArray();
            Bitmap  bitmap    = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap cropToCenter(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int x    = (src.getWidth()  - size) / 2;
        int y    = (src.getHeight() - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, size, size);
        if (cropped != src) src.recycle();
        return cropped;
    }

    // ── History helpers ───────────────────────────────────────────
    private void saveToHistory(String sentence) {
        try {
            String timestamp = new SimpleDateFormat(
                    "dd MMM yyyy  HH:mm", Locale.getDefault()).format(new Date());
            String entry = timestamp + "||" + sentence + "\n";
            java.io.FileOutputStream fos =
                    openFileOutput("islens_history.txt", Context.MODE_APPEND);
            fos.write(entry.getBytes());
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "History save failed", e);
        }
    }

    // ── Reference dialog ──────────────────────────────────────────
    @SuppressWarnings("unused")
    private void showReferenceDialog() {
        try {
            AlertDialog.Builder builder   = new AlertDialog.Builder(this);
            View         dialogView       = LayoutInflater.from(this).inflate(R.layout.dialog_reference, null);
            ImageView    referenceImage   = dialogView.findViewById(R.id.referenceImage);
            if (referenceImage != null) referenceImage.setImageResource(R.drawable.isl_alphabet);

            AlertDialog dialog = builder.setView(dialogView)
                    .setTitle("ISL Reference")
                    .setPositiveButton("Download", (d, w) -> saveReferenceToGallery())
                    .setNegativeButton("Close", null)
                    .create();
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Dialog error", e);
        }
    }

    private void saveReferenceToGallery() {
        try {
            Bitmap bitmap   = BitmapFactory.decodeResource(getResources(), R.drawable.isl_alphabet);
            String fileName = "ISL_Reference_" + System.currentTimeMillis() + ".jpg";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/ISL Vision");
                Uri uri = getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os); os.close(); }
                    Toast.makeText(this, "✅ Saved!", Toast.LENGTH_SHORT).show();
                }
            } else {
                File directory = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "ISL Vision");
                if (!directory.exists()) directory.mkdirs();
                File file = new File(directory, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                Toast.makeText(this, "✅ Saved!", Toast.LENGTH_SHORT).show();
            }
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
        }
    }

    // ── Permission handling ───────────────────────────────────────
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            finish();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier    != null) classifier.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (textToSpeech  != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (clickPlayer   != null) { clickPlayer.release(); clickPlayer = null; }
    }
}
