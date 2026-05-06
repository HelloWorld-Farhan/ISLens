package com.islvision.islens;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * SettingsActivity — THREE-OPTION THEME (Default / Light / Dark)
 * ───────────────────────────────────────────────────────────────
 * THEME OPTIONS:
 *  • Default  → follows the phone's system dark/light setting (dynamic)
 *  • Light    → always light, ignores system setting
 *  • Dark     → always dark, ignores system setting
 *
 * BUG FIX: On first install the Settings screen now correctly reads the
 * saved theme mode from SharedPreferences and highlights the right button,
 * instead of always defaulting to showing Dark as selected.
 *
 * First-install default is THEME_SYSTEM (Default / follow phone).
 * Since most phones default to light system theme, app opens in light mode
 * and Settings correctly shows "Default" button as selected.
 */
public class SettingsActivity extends AppCompatActivity {

    // ── Shared prefs keys ─────────────────────────────────────────
    public static final String PREFS_NAME    = "islens_prefs";
    public static final String KEY_DARK_MODE = "dark_mode";       // legacy bool, kept for compile compat
    public static final String KEY_VIBRATION = "vibration_on";
    public static final String KEY_SOUND     = "sound_on";
    public static final String KEY_SOUND_URI = "sound_uri";
    public static final String KEY_TEXT_SIZE = "text_size_large"; // legacy, kept for compile

    private static final int REQUEST_RINGTONE = 201;

    private SharedPreferences prefs;
    private Vibrator          vibrator;
    private MediaPlayer       clickPlayer;

    // Theme buttons
    private MaterialButton themeBtnDefault;
    private MaterialButton themeBtnLight;
    private MaterialButton themeBtnDark;
    private TextView       themeSelectedLabel;

    // Sound / vibration
    private SwitchMaterial soundSwitch;
    private SwitchMaterial vibrationSwitch;
    private MaterialButton pickSoundBtn;
    private TextView       currentSoundLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Apply saved theme before setContentView so there's no flash
        SplashActivity.applyThemeMode(SplashActivity.getThemeMode(this));

        super.onCreate(savedInstanceState);
        // Disable edge-to-edge forced by targetSdk 35
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_settings);

        vibrator    = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        clickPlayer = safeCreatePlayer();

        bindViews();
        loadCurrentSettings();
        setupListeners();

        // Apply status/nav bar colors based on actual current theme
        applyThemeColors(isCurrentlyDark());
    }

    // ── Bind views ────────────────────────────────────────────────
    private void bindViews() {
        // Theme selector buttons (3-button layout)
        themeBtnDefault    = findViewById(R.id.themeBtnDefault);
        themeBtnLight      = findViewById(R.id.themeBtnLight);
        themeBtnDark       = findViewById(R.id.themeBtnDark);
        themeSelectedLabel = findViewById(R.id.themeSelectedLabel);

        // Sound & vibration
        soundSwitch       = findViewById(R.id.soundSwitch);
        vibrationSwitch   = findViewById(R.id.vibrationSwitch);
        pickSoundBtn      = findViewById(R.id.pickSoundBtn);
        currentSoundLabel = findViewById(R.id.currentSoundLabel);

        findViewById(R.id.settingsBackButton).setOnClickListener(v -> {
            vibrate(); playSound(); finish();
        });
    }

    // ── Load + reflect saved settings ────────────────────────────
    private void loadCurrentSettings() {

        // ── Theme ─────────────────────────────────────────────────
        // KEY FIX: always read the actual saved mode from prefs.
        // On first install KEY_THEME_SET_BY_USER is false → getThemeMode()
        // returns THEME_SYSTEM, so "Default" button is highlighted correctly.
        int savedMode = SplashActivity.getThemeMode(this);
        updateThemeButtonUI(savedMode);

        // ── Sound & Vibration ─────────────────────────────────────
        boolean soundOn = prefs.getBoolean(KEY_SOUND, true);
        boolean vibOn   = prefs.getBoolean(KEY_VIBRATION, true);

        if (soundSwitch     != null) soundSwitch.setChecked(soundOn);
        if (vibrationSwitch != null) vibrationSwitch.setChecked(vibOn);

        updateSoundLabel();
        if (pickSoundBtn != null) pickSoundBtn.setEnabled(soundOn);
    }

    // ── Listeners ─────────────────────────────────────────────────
    private void setupListeners() {

        // ── Theme buttons ─────────────────────────────────────────
        if (themeBtnDefault != null) {
            themeBtnDefault.setOnClickListener(v -> {
                vibrate(); playSound();
                applyAndSaveTheme(SplashActivity.THEME_SYSTEM);
            });
        }
        if (themeBtnLight != null) {
            themeBtnLight.setOnClickListener(v -> {
                vibrate(); playSound();
                applyAndSaveTheme(SplashActivity.THEME_LIGHT);
            });
        }
        if (themeBtnDark != null) {
            themeBtnDark.setOnClickListener(v -> {
                vibrate(); playSound();
                applyAndSaveTheme(SplashActivity.THEME_DARK);
            });
        }

        // ── Sound ─────────────────────────────────────────────────
        if (soundSwitch != null) {
            soundSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                vibrate();
                prefs.edit().putBoolean(KEY_SOUND, isChecked).apply();
                if (pickSoundBtn != null) pickSoundBtn.setEnabled(isChecked);
                if (isChecked) playSound();
            });
        }

        // ── Vibration ─────────────────────────────────────────────
        if (vibrationSwitch != null) {
            vibrationSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_VIBRATION, isChecked).apply();
                if (isChecked) vibrate();
            });
        }

        // ── Sound picker ──────────────────────────────────────────
        if (pickSoundBtn != null) {
            pickSoundBtn.setOnClickListener(v -> {
                vibrate();
                openNotificationSoundPicker();
            });
        }
    }

    // ── Apply + save a theme choice ───────────────────────────────
    private void applyAndSaveTheme(int mode) {
        // Persist choice:
        //   THEME_SYSTEM  → KEY_THEME_SET_BY_USER = false  (reverts to follow-phone)
        //   THEME_LIGHT/DARK → KEY_THEME_SET_BY_USER = true  (locks to user choice)
        boolean isDark     = (mode == SplashActivity.THEME_DARK);
        boolean setByUser  = (mode != SplashActivity.THEME_SYSTEM);

        prefs.edit()
                .putInt(SplashActivity.KEY_THEME_MODE, mode)
                .putBoolean(SplashActivity.KEY_THEME_SET_BY_USER, setByUser)
                .putBoolean(KEY_DARK_MODE, isDark) // keep legacy key in sync
                .apply();

        // Apply immediately via AppCompatDelegate
        SplashActivity.applyThemeMode(mode);

        // Update button highlights and status label
        updateThemeButtonUI(mode);

        // Update status/nav bar tint
        applyThemeColors(isCurrentlyDark());
    }

    // ── Highlight the active theme button + update status label ───
    private void updateThemeButtonUI(int activeMode) {
        // Reset all to unselected first
        setThemeBtnSelected(themeBtnDefault, false);
        setThemeBtnSelected(themeBtnLight,   false);
        setThemeBtnSelected(themeBtnDark,    false);

        String label;
        switch (activeMode) {
            case SplashActivity.THEME_DARK:
                setThemeBtnSelected(themeBtnDark, true);
                label = "🌙 Dark mode";
                break;
            case SplashActivity.THEME_LIGHT:
                setThemeBtnSelected(themeBtnLight, true);
                label = "☀️ Light mode";
                break;
            case SplashActivity.THEME_SYSTEM:
            default:
                // FIX: Default is the correct initial state — THEME_SYSTEM (0)
                // is what getThemeMode() returns on a fresh install, so this
                // branch runs on first open and correctly highlights Default.
                setThemeBtnSelected(themeBtnDefault, true);
                label = "📱 Default (follows phone)";
                break;
        }

        if (themeSelectedLabel != null) themeSelectedLabel.setText(label);
    }

    /**
     * Visually distinguishes the selected theme button.
     * Selected: full opacity + thick cyan stroke.
     * Unselected: dimmed + thin grey stroke.
     */
    private void setThemeBtnSelected(MaterialButton btn, boolean selected) {
        if (btn == null) return;
        if (selected) {
            btn.setAlpha(1.0f);
            btn.setStrokeWidth(4);
            btn.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0xFF00BCD4));
            // Slightly tinted background for selected state
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0x1A00BCD4));
        } else {
            btn.setAlpha(0.55f);
            btn.setStrokeWidth(1);
            btn.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(0x40888888));
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        }
    }

    // ── Helper: is the UI actually rendering dark right now? ──────
    /**
     * Returns true if the app is ACTUALLY rendering in dark mode right now.
     * Uses the real Configuration rather than the saved pref, so it works
     * correctly when THEME_SYSTEM is active and the phone is in light mode
     * (avoids the old bug where the UI always reported "dark").
     */
    private boolean isCurrentlyDark() {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ── Sound picker ──────────────────────────────────────────────
    private void openNotificationSoundPicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose click sound");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        String savedUri = prefs.getString(KEY_SOUND_URI, null);
        if (savedUri != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(savedUri));
        } else {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }
        startActivityForResult(intent, REQUEST_RINGTONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RINGTONE
                && resultCode == Activity.RESULT_OK
                && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                prefs.edit().putString(KEY_SOUND_URI, uri.toString()).apply();
                updateSoundLabel();
                playSoundPreview(uri);
            }
        }
    }

    private void updateSoundLabel() {
        if (currentSoundLabel == null) return;
        String savedUri = prefs.getString(KEY_SOUND_URI, null);
        if (savedUri == null) {
            currentSoundLabel.setText("Default notification sound");
        } else {
            try {
                Ringtone ringtone = RingtoneManager.getRingtone(this, Uri.parse(savedUri));
                String name = (ringtone != null) ? ringtone.getTitle(this) : "Custom sound";
                currentSoundLabel.setText(name);
            } catch (Exception e) {
                currentSoundLabel.setText("Custom sound");
            }
        }
    }

    private void playSoundPreview(Uri uri) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void applyThemeColors(boolean darkMode) {
        int bgScreen = darkMode ? 0xFF0A0A0F : 0xFFF0F4F8;
        View root = findViewById(R.id.settingsRoot);
        if (root != null) root.setBackgroundColor(bgScreen);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(bgScreen);
            getWindow().setNavigationBarColor(bgScreen);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STATIC HELPERS — called from ALL activities
    // ══════════════════════════════════════════════════════════════

    public static void vibrateIfEnabled(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_VIBRATION, true)) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) return;
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        else
            v.vibrate(50);
    }

    public static void playSoundIfEnabled(Context ctx, MediaPlayer mp) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_SOUND, true)) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;

        String savedUri = p.getString(KEY_SOUND_URI, null);
        if (savedUri != null) {
            try {
                Ringtone ringtone = RingtoneManager.getRingtone(ctx, Uri.parse(savedUri));
                if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
                return;
            } catch (Exception ignored) {}
        }
        try {
            if (mp != null) {
                if (mp.isPlaying()) mp.seekTo(0);
                mp.start();
            }
        } catch (Exception ignored) {}
    }

    // Legacy text-size helpers — kept so existing code compiles
    public static float getWordTextSize(Context ctx)     { return 20f; }
    public static float getSentenceTextSize(Context ctx) { return 15f; }

    private void vibrate()   { vibrateIfEnabled(this); }
    private void playSound() { playSoundIfEnabled(this, clickPlayer); }

    private MediaPlayer safeCreatePlayer() {
        try { return MediaPlayer.create(this, R.raw.click_sound); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clickPlayer != null) { clickPlayer.release(); clickPlayer = null; }
    }
}