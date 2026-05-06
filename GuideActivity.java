package com.islvision.islens;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * GuideActivity — UPDATED
 * ─────────────────────────────────────────────────────────────────
 * • Uses SettingsActivity.vibrateIfEnabled() and playSoundIfEnabled()
 *   so the user's chosen notification sound plays on every button press
 *   on this page — consistent with all other pages.
 * • Theme resolved via SplashActivity.getThemeMode() + applyThemeMode().
 */
public class GuideActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 201;

    private MediaPlayer clickPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply correct theme before setContentView
        int mode = SplashActivity.getThemeMode(this);
        SplashActivity.applyThemeMode(mode);

        super.onCreate(savedInstanceState);
        // Disable edge-to-edge forced by targetSdk 35
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_guide);

        // Create fallback MediaPlayer (used by SettingsActivity.playSoundIfEnabled)
        clickPlayer = safeCreatePlayer();

        // ── Back button ───────────────────────────────────────────
        findViewById(R.id.backButton).setOnClickListener(v -> {
            vibrate(); playSound(); finish();
        });

        // ── Back-to-home button ───────────────────────────────────
        MaterialButton backToHomeBtn = findViewById(R.id.backToHomeButton);
        if (backToHomeBtn != null) {
            backToHomeBtn.setOnClickListener(v -> {
                vibrate(); playSound(); finish();
            });
        }

        // ── Download ISL chart button ─────────────────────────────
        MaterialButton downloadBtn = findViewById(R.id.downloadAlphabetButton);
        if (downloadBtn != null) {
            downloadBtn.setOnClickListener(v -> {
                vibrate(); playSound(); handleDownload();
            });
        }

        // ── Terms & Conditions button ─────────────────────────────
        MaterialButton termsBtn = findViewById(R.id.termsButton);
        if (termsBtn != null) {
            termsBtn.setOnClickListener(v -> {
                vibrate(); playSound();
                Object tag = termsBtn.getTag();
                if (tag != null && !tag.toString().contains("YOUR_TERMS_URL_HERE")) {
                    openUrl(tag.toString());
                } else {
                    Toast.makeText(this,
                            "Terms page coming soon — check back after the next update.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ── Privacy Policy button ─────────────────────────────────
        MaterialButton privacyBtn = findViewById(R.id.privacyButton);
        if (privacyBtn != null) {
            privacyBtn.setOnClickListener(v -> {
                vibrate(); playSound();
                Object tag = privacyBtn.getTag();
                if (tag != null && !tag.toString().contains("YOUR_PRIVACY_POLICY_URL_HERE")) {
                    openUrl(tag.toString());
                } else {
                    Toast.makeText(this,
                            "Privacy policy page coming soon — check back after the next update.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // ── URL helper ────────────────────────────────────────────────
    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link — no browser found.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── Sound / Vibrate via SettingsActivity static helpers ───────
    private void vibrate() {
        SettingsActivity.vibrateIfEnabled(this);
    }

    private void playSound() {
        SettingsActivity.playSoundIfEnabled(this, clickPlayer);
    }

    private MediaPlayer safeCreatePlayer() {
        try { return MediaPlayer.create(this, R.raw.click_sound); }
        catch (Exception e) { return null; }
    }

    // ── Download ISL chart to gallery ────────────────────────────
    private void handleDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return;
        }
        saveImageToGallery();
    }

    private void saveImageToGallery() {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.isl_alphabet);
            if (bitmap == null) { showToast("❌ Image not found"); return; }

            final String fileName   = "ISL_Alphabet_Chart.jpg";
            final String folderName = "ISL Vision";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + folderName);
                Uri uri = getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                    }
                    showToast("✅ Saved to Gallery › Pictures › ISL Vision");
                } else {
                    showToast("❌ Could not save — storage issue");
                }
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        folderName);
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                }
                showToast("✅ Saved to Gallery › Pictures › ISL Vision");
            }
            bitmap.recycle();
        } catch (Exception e) { showToast("❌ Failed: " + e.getMessage()); }
    }

    @Override
    public void onRequestPermissionsResult(int rc,
                                           @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        if (rc == STORAGE_PERMISSION_CODE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                saveImageToGallery();
            else
                showToast("⚠️ Storage permission denied.");  // FIX: removed duplicate line
        }
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clickPlayer != null) { clickPlayer.release(); clickPlayer = null; }
    }
}