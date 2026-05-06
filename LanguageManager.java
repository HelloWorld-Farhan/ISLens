package com.islvision.islens;

import android.content.Context;

import java.util.LinkedHashMap;

/**
 * LanguageManager
 * ───────────────
 * Central store for all supported languages + user's selected language.
 * No downloads. No ML Kit. Just a map and SharedPreferences.
 *
 * Language codes are ISO 639-1, same as ML Kit used — so your
 * existing languageMap in MainActivity still works unchanged.
 */
public class LanguageManager {

    private static final String PREFS_NAME    = "islens_prefs";
    private static final String KEY_LANG_CODE = "selected_lang_code";
    private static final String KEY_LANG_NAME = "selected_lang_name";

    /** All 60+ languages supported by MyMemory API */
    public static final LinkedHashMap<String, String> LANGUAGES =
            new LinkedHashMap<String, String>() {{
        put("English",       "en");
        put("Hindi",         "hi");
        put("Bengali",       "bn");
        put("Telugu",        "te");
        put("Marathi",       "mr");
        put("Tamil",         "ta");
        put("Gujarati",      "gu");
        put("Kannada",       "kn");
        put("Malayalam",     "ml");
        put("Punjabi",       "pa");
        put("Urdu",          "ur");
        put("Afrikaans",     "af");
        put("Arabic",        "ar");
        put("Belarusian",    "be");
        put("Bulgarian",     "bg");
        put("Catalan",       "ca");
        put("Chinese",       "zh");
        put("Croatian",      "hr");
        put("Czech",         "cs");
        put("Danish",        "da");
        put("Dutch",         "nl");
        put("Estonian",      "et");
        put("Finnish",       "fi");
        put("French",        "fr");
        put("Galician",      "gl");
        put("Georgian",      "ka");
        put("German",        "de");
        put("Greek",         "el");
        put("Hebrew",        "he");
        put("Hungarian",     "hu");
        put("Icelandic",     "is");
        put("Indonesian",    "id");
        put("Italian",       "it");
        put("Japanese",      "ja");
        put("Korean",        "ko");
        put("Latvian",       "lv");
        put("Lithuanian",    "lt");
        put("Macedonian",    "mk");
        put("Malay",         "ms");
        put("Maltese",       "mt");
        put("Norwegian",     "no");
        put("Persian",       "fa");
        put("Polish",        "pl");
        put("Portuguese",    "pt");
        put("Romanian",      "ro");
        put("Russian",       "ru");
        put("Serbian",       "sr");
        put("Slovak",        "sk");
        put("Slovenian",     "sl");
        put("Spanish",       "es");
        put("Swahili",       "sw");
        put("Swedish",       "sv");
        put("Tagalog",       "tl");
        put("Thai",          "th");
        put("Turkish",       "tr");
        put("Ukrainian",     "uk");
        put("Vietnamese",    "vi");
        put("Welsh",         "cy");
    }};

    /** Save the user's selected language to prefs */
    public static void saveSelectedLanguage(Context ctx, String code, String name) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANG_CODE, code)
                .putString(KEY_LANG_NAME, name)
                .apply();
    }

    /** Get saved language code (default: English) */
    public static String getSavedLangCode(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANG_CODE, "en");
    }

    /** Get saved language name (default: English) */
    public static String getSavedLangName(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANG_NAME, "English");
    }
}
