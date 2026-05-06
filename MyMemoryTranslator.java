package com.islvision.islens;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MyMemoryTranslator
 * ──────────────────
 * FREE translation using MyMemory REST API.
 *
 * WHY THIS REPLACES ML KIT:
 *  ✅ Zero downloads — no model files, no stuck progress bars
 *  ✅ Instant language switching — just change the target code
 *  ✅ Free: 5000 words/day — more than enough for ISL sentences
 *  ✅ No API key needed — works out of the box
 *  ✅ 60+ languages — same as ML Kit
 *  ✅ No notification, no foreground service, no Play Store issues
 *
 * REQUIRES: android.permission.INTERNET (already in your manifest)
 * REQUIRES: org.json — built into Android, no extra dependency
 *
 * Usage:
 *   MyMemoryTranslator.translateFromEnglish("HELLO", "hi", result -> {
 *       textView.setText(result != null ? result : "HELLO");
 *   });
 */
public class MyMemoryTranslator {

    private static final String TAG     = "MyMemoryTranslator";
    private static final String API_URL = "https://api.mymemory.translated.net/get";

    // Single background thread — fast enough, keeps it simple
    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private static final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    public interface TranslationCallback {
        /**
         * Called on the MAIN THREAD.
         * result = translated text, or null if translation failed.
         * On null, show the original English text as fallback.
         */
        void onResult(String result);
    }

    /**
     * Translate from English to any language.
     * This is the main method used throughout the app.
     * Callback always fires on the UI thread — safe to update TextViews directly.
     */
    public static void translateFromEnglish(String text,
                                             String targetLangCode,
                                             TranslationCallback callback) {
        translate(text, "en", targetLangCode, callback);
    }

    /**
     * General translate: any source → any target.
     */
    public static void translate(String text,
                                  String sourceLang,
                                  String targetLang,
                                  TranslationCallback callback) {

        // Same language or empty — return original immediately, no network call
        if (sourceLang.equals(targetLang) || "en".equals(targetLang)) {
            uiHandler.post(() -> callback.onResult(text));
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            uiHandler.post(() -> callback.onResult(text));
            return;
        }

        final String trimmed = text.trim();

        executor.execute(() -> {
            String result = callApi(trimmed, sourceLang, targetLang);
            uiHandler.post(() -> callback.onResult(result));
        });
    }

    // ── Internal API call (runs on background thread) ─────────────
    private static String callApi(String text, String src, String tgt) {
        HttpURLConnection conn = null;
        try {
            String encoded  = URLEncoder.encode(text, "UTF-8");
            String langPair = src + "|" + tgt;
            String urlStr   = API_URL
                    + "?q="        + encoded
                    + "&langpair=" + URLEncoder.encode(langPair, "UTF-8");

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);  // 8 seconds
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ISLens/1.0");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: " + code);
                return null;
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json         = new JSONObject(sb.toString());
            JSONObject responseData = json.getJSONObject("responseData");
            String translated       = responseData.getString("translatedText");

            // Quota exceeded message
            if (translated.toUpperCase().startsWith("MYMEMORY WARNING")) {
                Log.w(TAG, "Daily quota exceeded");
                return null;
            }

            Log.d(TAG, src + "→" + tgt + ": \"" + text + "\" = \"" + translated + "\"");
            return translated;

        } catch (Exception e) {
            Log.e(TAG, "Translation failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
