package com.islvision.islens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private static final String HISTORY_FILE = "islens_history.txt";
    private static final String SEPARATOR    = "||";

    private RecyclerView            recyclerView;
    private HistoryAdapter          adapter;
    private final List<HistoryItem> items         = new ArrayList<>();
    private final List<HistoryItem> filteredItems = new ArrayList<>();

    private Vibrator     vibrator;
    private MediaPlayer  clickPlayer;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // FIX: apply theme before super.onCreate / setContentView
        int mode = SplashActivity.getThemeMode(this);
        SplashActivity.applyThemeMode(mode);

        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_history);

        vibrator    = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        clickPlayer = createClickPlayer();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        applyStatusBarColor();

        View backBtn = findViewById(R.id.histBackButton);
        if (backBtn != null) backBtn.setOnClickListener(v -> { vibrate(); playSound(); finish(); });

        MaterialButton clearBtn = findViewById(R.id.histClearAllButton);
        if (clearBtn != null) clearBtn.setOnClickListener(v -> { vibrate(); playSound(); clearHistory(); });

        recyclerView = findViewById(R.id.histRecyclerView);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new HistoryAdapter(filteredItems);
            recyclerView.setAdapter(adapter);
        }

        EditText searchBox = findViewById(R.id.histSearchBox);
        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    filterItems(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadHistory();
    }

    private void applyStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // FIX: derive dark/light from the actual current UI mode, not the legacy bool
            int nightMode = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean dark  = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int color = dark ? 0xFF07070D : 0xFFF0F4F8;
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        }
    }

    // ── History I/O ───────────────────────────────────────────────
    private void loadHistory() {
        items.clear();
        // FIX: use getFilesDir() consistently with saveToHistory() in MainActivity
        File file = new File(getFilesDir(), HISTORY_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(SEPARATOR)) {
                        String[] parts = line.split("\\|\\|", 2);
                        if (parts.length == 2)
                            items.add(new HistoryItem(parts[0].trim(), parts[1].trim()));
                    }
                }
            } catch (IOException e) {
                Toast.makeText(this, "Could not load history.", Toast.LENGTH_SHORT).show();
            }
        }

        Collections.reverse(items);

        // Apply globally saved language
        String savedCode = LanguageManager.getSavedLangCode(this);
        String savedName = LanguageManager.getSavedLangName(this);
        for (HistoryItem item : items) {
            item.selectedLangCode = savedCode;
            item.selectedLangName = savedName;
        }

        filteredItems.clear();
        filteredItems.addAll(items);
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();

        // Auto-translate all items if a non-English language is saved
        if (!"en".equals(savedCode)) {
            for (int i = 0; i < filteredItems.size(); i++) {
                final HistoryItem item = filteredItems.get(i);
                final int         pos  = i;
                MyMemoryTranslator.translateFromEnglish(
                        item.originalSentence, savedCode, result -> {
                            item.translatedSentence = result != null ? result : item.originalSentence;
                            item.displaySentence    = item.translatedSentence;
                            if (adapter != null) adapter.notifyItemChanged(pos);
                        });
            }
        }
    }

    private void clearHistory() {
        File file = new File(getFilesDir(), HISTORY_FILE);
        try (FileWriter fw = new FileWriter(file, false)) { fw.write(""); } catch (IOException ignored) {}
        items.clear();
        filteredItems.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
        Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show();
    }

    private void deleteItem(int position) {
        if (position < 0 || position >= filteredItems.size()) return;
        HistoryItem toDelete = filteredItems.get(position);
        items.remove(toDelete);
        filteredItems.remove(position);
        if (adapter != null) adapter.notifyItemRemoved(position);
        rewriteHistoryFile();
        updateEmptyState();
    }

    private void rewriteHistoryFile() {
        List<HistoryItem> reversed = new ArrayList<>(items);
        Collections.reverse(reversed);
        File file = new File(getFilesDir(), HISTORY_FILE);
        try (FileWriter fw = new FileWriter(file, false)) {
            for (HistoryItem item : reversed)
                fw.write(item.timestamp + SEPARATOR + item.originalSentence + "\n");
        } catch (IOException ignored) {}
    }

    private void filterItems(String query) {
        filteredItems.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredItems.addAll(items);
        } else {
            String lower = query.toLowerCase().trim();
            for (HistoryItem item : items) {
                if (item.originalSentence.toLowerCase().contains(lower)
                        || item.timestamp.toLowerCase().contains(lower))
                    filteredItems.add(item);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        View emptyView = findViewById(R.id.histEmptyText);
        if (emptyView    != null) emptyView.setVisibility(filteredItems.isEmpty() ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(filteredItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Per-item language picker ──────────────────────────────────
    private void showLanguagePickerForItem(HistoryItem item, int adapterPosition) {
        List<String> langNames = new ArrayList<>(LanguageManager.LANGUAGES.keySet());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Translate to…");
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_language_picker, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText     searchBox  = dialogView.findViewById(R.id.searchLanguage);
        ListView     listView   = dialogView.findViewById(R.id.languageList);
        if (listView == null) { dialog.show(); return; }

        final List<String> filtered = new ArrayList<>(langNames);

        ArrayAdapter<String> langAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, filtered) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View     v  = super.getView(pos, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(15);
                tv.setPadding(48, 24, 48, 24);
                v.setBackgroundColor(0xFF1A1A2E);
                return v;
            }
        };
        listView.setAdapter(langAdapter);

        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    filtered.clear();
                    String q = s.toString().toLowerCase();
                    for (String name : langNames)
                        if (name.toLowerCase().contains(q)) filtered.add(name);
                    langAdapter.notifyDataSetChanged();
                }
            });
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = filtered.get(position);
            String selectedCode = LanguageManager.LANGUAGES.get(selectedName);
            if (selectedCode != null) {
                vibrate(); playSound();
                dialog.dismiss();
                item.selectedLangName = selectedName;
                item.selectedLangCode = selectedCode;

                if ("en".equals(selectedCode)) {
                    item.translatedSentence = null;
                    item.displaySentence    = item.originalSentence;
                    if (adapter != null) adapter.notifyItemChanged(adapterPosition);
                } else {
                    item.translatedSentence = "Translating…";
                    item.displaySentence    = "Translating…";
                    if (adapter != null) adapter.notifyItemChanged(adapterPosition);
                    MyMemoryTranslator.translateFromEnglish(
                            item.originalSentence, selectedCode, result -> {
                                item.translatedSentence = result != null ? result : item.originalSentence;
                                item.displaySentence    = item.translatedSentence;
                                if (adapter != null) adapter.notifyItemChanged(adapterPosition);
                            });
                }
            }
        });

        dialog.show();
    }

    // ── Sound / Vibrate ───────────────────────────────────────────
    private MediaPlayer createClickPlayer() {
        try { return MediaPlayer.create(this, R.raw.click_sound); }
        catch (Exception e) { return null; }
    }

    private void vibrate() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null || am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) return;
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                else
                    vibrator.vibrate(50);
            }
        } catch (Exception ignored) {}
    }

    private void playSound() { SettingsActivity.playSoundIfEnabled(this, clickPlayer); }

    private void setTtsLocale(String langCode) {
        if (tts == null) return;
        Locale locale;
        switch (langCode) {
            case "hi": locale = new Locale("hi", "IN"); break;
            case "zh": locale = Locale.CHINESE;         break;
            case "ja": locale = Locale.JAPANESE;        break;
            case "ko": locale = Locale.KOREAN;          break;
            case "fr": locale = Locale.FRENCH;          break;
            case "de": locale = Locale.GERMAN;          break;
            case "it": locale = Locale.ITALIAN;         break;
            case "es": locale = new Locale("es", "ES"); break;
            case "ar": locale = new Locale("ar");       break;
            case "ru": locale = new Locale("ru");       break;
            case "pt": locale = new Locale("pt");       break;
            default:   locale = Locale.US;              break;
        }
        tts.setLanguage(locale);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clickPlayer != null) { clickPlayer.release(); clickPlayer = null; }
        if (tts         != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    // ── Data model ────────────────────────────────────────────────
    static class HistoryItem {
        final String timestamp;
        final String originalSentence;
        String displaySentence;
        String translatedSentence;
        String selectedLangCode = "en";
        String selectedLangName = "English";

        HistoryItem(String ts, String sentence) {
            this.timestamp        = ts;
            this.originalSentence = sentence;
            this.displaySentence  = sentence;
        }
    }

    // ── RecyclerView Adapter ──────────────────────────────────────
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        private final List<HistoryItem> data;
        HistoryAdapter(List<HistoryItem> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            HistoryItem item = data.get(position);

            if (holder.sentenceTv  != null) holder.sentenceTv.setText(item.originalSentence);
            if (holder.timestampTv != null) holder.timestampTv.setText(item.timestamp);
            if (holder.langBtn     != null) holder.langBtn.setText(item.selectedLangName + " ▼");

            // Translation row visibility
            if (holder.translationRow != null) {
                boolean hasTranslation = item.translatedSentence != null
                        && !item.selectedLangCode.equals("en");
                holder.translationRow.setVisibility(hasTranslation ? View.VISIBLE : View.GONE);
                if (holder.translatedTv != null) {
                    holder.translatedTv.setText(
                            item.translatedSentence != null ? item.translatedSentence : "");
                }
            }

            // Delete
            if (holder.deleteBtn != null) {
                holder.deleteBtn.setOnClickListener(v -> {
                    vibrate(); playSound();
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_ID) deleteItem(pos);
                });
            }

            // Speak original
            if (holder.speakBtn != null) {
                holder.speakBtn.setImageResource(R.drawable.volume_off);
                holder.speakBtn.setOnClickListener(v -> {
                    vibrate(); playSound();
                    if (tts == null || item.originalSentence.isEmpty()) return;
                    if (tts.isSpeaking()) {
                        tts.stop();
                        holder.speakBtn.setImageResource(R.drawable.volume_off);
                    } else {
                        setTtsLocale("en");
                        tts.speak(item.originalSentence,
                                TextToSpeech.QUEUE_FLUSH, null, "hist_speak_en");
                        holder.speakBtn.setImageResource(R.drawable.volume_on);
                        tts.setOnUtteranceProgressListener(
                                new android.speech.tts.UtteranceProgressListener() {
                                    @Override public void onStart(String id) {}
                                    @Override public void onDone(String id) {
                                        runOnUiThread(() -> holder.speakBtn.setImageResource(R.drawable.volume_off));
                                    }
                                    @Override public void onError(String id) {
                                        runOnUiThread(() -> holder.speakBtn.setImageResource(R.drawable.volume_off));
                                    }
                                });
                    }
                });
            }

            // Speak translated
            if (holder.speakTranslatedBtn != null) {
                holder.speakTranslatedBtn.setImageResource(R.drawable.volume_off);
                holder.speakTranslatedBtn.setOnClickListener(v -> {
                    vibrate(); playSound();
                    String text = item.translatedSentence != null
                            ? item.translatedSentence : item.displaySentence;
                    if (tts == null || text.isEmpty()) return;
                    if (tts.isSpeaking()) {
                        tts.stop();
                        holder.speakTranslatedBtn.setImageResource(R.drawable.volume_off);
                    } else {
                        setTtsLocale(item.selectedLangCode);
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hist_speak_tr");
                        holder.speakTranslatedBtn.setImageResource(R.drawable.volume_on);
                        tts.setOnUtteranceProgressListener(
                                new android.speech.tts.UtteranceProgressListener() {
                                    @Override public void onStart(String id) {}
                                    @Override public void onDone(String id) {
                                        runOnUiThread(() -> holder.speakTranslatedBtn.setImageResource(R.drawable.volume_off));
                                    }
                                    @Override public void onError(String id) {
                                        runOnUiThread(() -> holder.speakTranslatedBtn.setImageResource(R.drawable.volume_off));
                                    }
                                });
                    }
                });
            }

            // Per-item language picker
            if (holder.langBtn != null) {
                holder.langBtn.setOnClickListener(v -> {
                    vibrate(); playSound();
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_ID)
                        showLanguagePickerForItem(item, pos);
                });
            }

            // Long-press → copy original
            holder.itemView.setOnLongClickListener(v -> {
                vibrate(); playSound();
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("ISLens", item.originalSentence));
                    Toast.makeText(HistoryActivity.this,
                            "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView    sentenceTv, timestampTv, translatedTv;
            ImageButton deleteBtn, speakBtn, speakTranslatedBtn;
            android.widget.Button langBtn;
            View        translationRow;

            VH(@NonNull View itemView) {
                super(itemView);
                sentenceTv         = itemView.findViewById(R.id.histItemSentence);
                timestampTv        = itemView.findViewById(R.id.histItemTimestamp);
                translatedTv       = itemView.findViewById(R.id.histItemTranslated);
                deleteBtn          = itemView.findViewById(R.id.histItemDeleteBtn);
                speakBtn           = itemView.findViewById(R.id.histItemSpeakBtn);
                speakTranslatedBtn = itemView.findViewById(R.id.histItemSpeakTranslatedBtn);
                langBtn            = itemView.findViewById(R.id.histItemLangBtn);
                translationRow     = itemView.findViewById(R.id.histTranslationRow);
            }
        }
    }
}
