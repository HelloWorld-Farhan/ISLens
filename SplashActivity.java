package com.islvision.islens;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SplashActivity extends AppCompatActivity {

    // ── Theme constants ───────────────────────────────────────────
    public static final int THEME_SYSTEM = 0;  // Follow phone system setting (DEFAULT)
    public static final int THEME_DARK   = 1;  // Always dark — ignore system
    public static final int THEME_LIGHT  = 2;  // Always light — ignore system

    private static final long SPLASH_DURATION  = 2000L;
    public  static final String PREFS_NAME     = "islens_prefs";
    public  static final String KEY_TERMS      = "terms_accepted";
    public  static final String KEY_DARK       = "dark_mode";         // legacy bool
    public  static final String KEY_THEME_MODE = "theme_mode";        // int: 0/1/2

    /**
     * KEY_THEME_SET_BY_USER = false  →  user never changed theme → use THEME_SYSTEM
     * KEY_THEME_SET_BY_USER = true   →  user picked Light or Dark  → honour their choice
     *
     * When user picks "Default" in Settings, this is set back to FALSE so the
     * system setting takes effect again.
     */
    public static final String KEY_THEME_SET_BY_USER = "theme_set_by_user";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ── Apply correct theme BEFORE setContentView (no flash) ──
        applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View ring1       = findViewById(R.id.ring1);
        View ring2       = findViewById(R.id.ring2);
        View ring3       = findViewById(R.id.ring3);
        View logoCard    = findViewById(R.id.splashLogoCard);
        View handEmoji   = findViewById(R.id.handEmoji);
        View titleRow    = findViewById(R.id.titleRow);
        View accentBar   = findViewById(R.id.accentBar);
        View tagline     = findViewById(R.id.tagline);
        View aiChip      = findViewById(R.id.aiChip);
        View aiDot       = findViewById(R.id.aiDot);
        View liveBadge   = findViewById(R.id.liveBadge);
        View liveDot     = findViewById(R.id.liveDot);
        View statsStrip  = findViewById(R.id.statsStrip);
        View loadingBg   = findViewById(R.id.loadingBarBg);
        View loadingFill = findViewById(R.id.loadingBarFill);

        // Reset states
        logoCard.setAlpha(0f); logoCard.setScaleX(0.3f); logoCard.setScaleY(0.3f); logoCard.setRotation(-10f);
        titleRow.setAlpha(0f); titleRow.setTranslationY(24f);
        accentBar.setAlpha(0f); tagline.setAlpha(0f); aiChip.setAlpha(0f);
        statsStrip.setAlpha(0f); statsStrip.setTranslationY(16f);
        loadingBg.setAlpha(0f); liveBadge.setAlpha(0f);

        startPulseRing(ring1, 0);
        startPulseRing(ring2, 400);
        startPulseRing(ring3, 800);

        logoCard.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(420).setStartDelay(80)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> startHandWave(handEmoji), 600);

        titleRow.animate().alpha(1f).translationY(0f)
                .setDuration(350).setStartDelay(350)
                .setInterpolator(new DecelerateInterpolator()).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            accentBar.setAlpha(1f);
            titleRow.post(() -> {
                int targetWidth = titleRow.getWidth();
                ViewGroup.LayoutParams lp = accentBar.getLayoutParams();
                ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
                anim.setDuration(400);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(a -> { lp.width = (int) a.getAnimatedValue(); accentBar.setLayoutParams(lp); });
                anim.start();
            });
        }, 550);

        tagline.animate().alpha(1f).setDuration(300).setStartDelay(650).start();
        aiChip.animate().alpha(1f).setDuration(300).setStartDelay(720).start();
        startAiDotPulse(aiDot);

        statsStrip.animate().alpha(1f).translationY(0f)
                .setDuration(300).setStartDelay(800)
                .setInterpolator(new DecelerateInterpolator()).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            loadingBg.setAlpha(1f);
            loadingBg.post(() -> {
                int maxWidth = dpToPx(160);
                ValueAnimator barAnim = ValueAnimator.ofInt(0, maxWidth);
                barAnim.setDuration(1050);
                barAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                barAnim.addUpdateListener(a -> {
                    ViewGroup.LayoutParams lp = loadingFill.getLayoutParams();
                    lp.width = (int) a.getAnimatedValue();
                    loadingFill.setLayoutParams(lp);
                });
                barAnim.start();
            });
        }, 900);

        liveBadge.animate().alpha(1f).setDuration(250).setStartDelay(950).start();
        startLiveBlink(liveDot);

        // ── Navigate after splash ─────────────────────────────────
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean termsAccepted = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getBoolean(KEY_TERMS, false);
            Class<?> destination = termsAccepted ? MainActivity.class : TermsActivity.class;
            startActivity(new Intent(this, destination));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DURATION);
    }

    // ── Theme resolution ──────────────────────────────────────────

    /**
     * Called inside onCreate BEFORE setContentView so there is no
     * light-flash when the app opens in dark mode (or vice versa).
     *
     * Logic:
     *   • User never changed theme → THEME_SYSTEM (follows phone)
     *   • User explicitly picked Light or Dark → honour their choice
     */
    private void applyTheme() {
        applyThemeMode(getThemeMode(this));
    }

    /**
     * Central theme apply — called from every Activity before setContentView.
     *
     * THEME_SYSTEM → MODE_NIGHT_FOLLOW_SYSTEM  (phone dark/light changes app too)
     * THEME_DARK   → MODE_NIGHT_YES            (always dark, immune to phone toggle)
     * THEME_LIGHT  → MODE_NIGHT_NO             (always light, immune to phone toggle)
     */
    public static void applyThemeMode(int mode) {
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Save theme from SettingsActivity when the user picks one of the three options.
     *
     * @param mode  THEME_SYSTEM, THEME_DARK, or THEME_LIGHT
     *
     * KEY BEHAVIOUR:
     *   • THEME_SYSTEM  → KEY_THEME_SET_BY_USER = false  (reverts to follow-phone)
     *   • THEME_LIGHT / THEME_DARK → KEY_THEME_SET_BY_USER = true (locks to choice)
     */
    public static void saveUserTheme(Context ctx, int mode) {
        boolean setByUser = (mode != THEME_SYSTEM);
        boolean isDark    = (mode == THEME_DARK);

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, mode)
                .putBoolean(KEY_THEME_SET_BY_USER, setByUser)
                .putBoolean(KEY_DARK, isDark)   // keep legacy key in sync
                .apply();
    }

    /**
     * Legacy overload kept so SettingsActivity's old call site still compiles.
     * Maps boolean → THEME_DARK / THEME_LIGHT.
     * NOTE: prefer the int-mode version above for new code.
     */
    public static void saveUserTheme(Context ctx, boolean darkMode) {
        saveUserTheme(ctx, darkMode ? THEME_DARK : THEME_LIGHT);
    }

    /**
     * Read the current theme mode int from prefs.
     *
     * Returns:
     *   THEME_SYSTEM  if user has never explicitly set a preference (first install)
     *   THEME_DARK    if user chose dark
     *   THEME_LIGHT   if user chose light
     */
    public static int getThemeMode(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean setByUser = prefs.getBoolean(KEY_THEME_SET_BY_USER, false);
        if (!setByUser) return THEME_SYSTEM;          // fresh install → follow phone
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    // ── Animations ────────────────────────────────────────────────
    private void startPulseRing(View ring, long delay) {
        ring.setScaleX(0.5f); ring.setScaleY(0.5f); ring.setAlpha(0f);
        Runnable pulse = new Runnable() {
            @Override public void run() {
                ring.setScaleX(0.5f); ring.setScaleY(0.5f); ring.setAlpha(0f);
                ring.animate().scaleX(1.2f).scaleY(1.2f).alpha(0f)
                        .setDuration(1800).setInterpolator(new DecelerateInterpolator())
                        .withStartAction(() -> ring.animate().alpha(0.6f).setDuration(300).start())
                        .start();
                ring.postDelayed(this, 1800);
            }
        };
        ring.postDelayed(pulse, delay);
    }

    private void startHandWave(View hand) {
        hand.animate().rotation(-12f).setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> hand.animate().rotation(10f).setDuration(300)
                        .withEndAction(() -> hand.animate().rotation(0f).setDuration(250).start())
                        .start()).start();
    }

    private void startAiDotPulse(View dot) {
        dot.animate().alpha(0.2f).setDuration(600)
                .withEndAction(() -> dot.animate().alpha(1f).setDuration(600)
                        .withEndAction(() -> startAiDotPulse(dot)).start()).start();
    }

    private void startLiveBlink(View dot) {
        dot.animate().alpha(0.2f).setDuration(500)
                .withEndAction(() -> dot.animate().alpha(1f).setDuration(500)
                        .withEndAction(() -> startLiveBlink(dot)).start()).start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() { /* Disabled during splash */ }
}