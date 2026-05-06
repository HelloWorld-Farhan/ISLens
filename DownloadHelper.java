package com.islvision.islens;

import android.content.Context;

/**
 * DownloadHelper — REWRITTEN (No Download Version)
 * ─────────────────────────────────────────────────
 * ML Kit model downloads REMOVED. Language selection is now INSTANT.
 * Translation done live via MyMemory REST API — no files, no waiting.
 *
 * All old call sites (show, isDownloaded, markDownloaded) still compile
 * unchanged — zero edits needed in MainActivity.
 */
public class DownloadHelper {

    // Legacy fields — kept so MainActivity compiles without any changes
    public static boolean isSystemBusyDownloading      = false;
    public static String  currentlyDownloadingLangName = "";

    public interface OnSuccess { void run(String langCode, String langName); }
    public interface OnFailure { void run(String langCode); }

    /**
     * Previously: showed download progress dialog + foreground service.
     * Now: saves selection, fires onSuccess IMMEDIATELY. No dialog. No wait.
     */
    public static void show(Context context,
                            String langName,
                            String langCode,
                            OnSuccess onSuccess,
                            OnFailure onFailure) {

        // Persist the selected language
        LanguageManager.saveSelectedLanguage(context, langCode, langName);

        // Fire success instantly — MyMemory API translates live, no model needed
        if (onSuccess != null) {
            onSuccess.run(langCode, langName);
        }
    }

    /** Always true — no download ever needed. Kept for compile compatibility. */
    public static boolean isDownloaded(Context ctx, String code) {
        return true;
    }

    /** No-op — no model files to mark. Kept for compile compatibility. */
    public static void markDownloaded(Context ctx, String code) { }
}
