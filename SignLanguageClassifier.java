package com.islvision.islens;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class SignLanguageClassifier {

    private static final String TAG         = "ISL_Classifier";
    private static final String MODEL_NAME  = "isl_ultimate.tflite";
    private static final String HAND_MODEL  = "hand_landmarker.task";
    private static final String LABELS_FILE = "labels.txt";
    private static final int    IMG_SIZE    = 224;
    private static final int    NUM_CLASSES = 26;
    private static final int    SMOOTHING_FRAMES = 7;

    private Interpreter    tflite;
    private List<String>   labels;
    private HandLandmarker handLandmarker;

    private final Queue<String> recentPredictions = new LinkedList<>();
    private final Queue<Float>  recentConfidences = new LinkedList<>();

    public SignLanguageClassifier(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tflite = new Interpreter(loadModelFile(context, MODEL_NAME), options);
            labels = loadLabels(context);

            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(HAND_MODEL)
                    .build();

            HandLandmarker.HandLandmarkerOptions handOptions =
                    HandLandmarker.HandLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.IMAGE)
                            .setNumHands(1)
                            .setMinHandDetectionConfidence(0.5f)
                            .build();

            handLandmarker = HandLandmarker.createFromOptions(context, handOptions);
            Log.d(TAG, "✅ Classifier initialised");

        } catch (Exception e) {
            Log.e(TAG, "Init failed", e);
            // FIX: throw so MainActivity can catch and show a user-friendly message
            throw new RuntimeException("SignLanguageClassifier init failed: " + e.getMessage(), e);
        }
    }

    // ── Model loading ─────────────────────────────────────────────
    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fd          = context.getAssets().openFd(modelName);
        FileInputStream     inputStream = new FileInputStream(fd.getFileDescriptor());
        FileChannel         channel     = inputStream.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(), fd.getDeclaredLength());
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String>   labelList = new ArrayList<>();
        BufferedReader reader    = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABELS_FILE)));
        String line;
        while ((line = reader.readLine()) != null) labelList.add(line.trim());
        reader.close();
        return labelList;
    }

    // ── Lighting enhancement ──────────────────────────────────────
    private Bitmap enhanceLighting(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            Bitmap enhanced = Bitmap.createBitmap(
                    bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(enhanced);

            int[]  pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                    bitmap.getWidth(), bitmap.getHeight());

            long totalBrightness = 0;
            for (int pixel : pixels) {
                totalBrightness += ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3);
            }

            float avgBrightness   = totalBrightness / (float) pixels.length;
            float brightnessFactor = 1.0f;

            if      (avgBrightness < 80)  brightnessFactor = 1.5f;
            else if (avgBrightness > 200) brightnessFactor = 0.8f;

            ColorMatrix cm = new ColorMatrix();
            cm.setScale(brightnessFactor, brightnessFactor, brightnessFactor, 1);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(bitmap, 0, 0, paint);
            return enhanced;
        } catch (Exception e) {
            Log.e(TAG, "enhanceLighting failed", e);
            return bitmap;
        }
    }

    // ── Background removal via MediaPipe ─────────────────────────
    private Bitmap removeBackground(Bitmap bitmap) {
        if (bitmap == null) return createWhiteBackground(224, 224);
        Bitmap enhanced = null;
        try {
            enhanced = enhanceLighting(bitmap);
            if (enhanced == null) enhanced = bitmap;

            MPImage               mpImage    = new BitmapImageBuilder(enhanced).build();
            HandLandmarkerResult  handResult = handLandmarker.detect(mpImage);

            if (handResult.landmarks().isEmpty()) {
                Log.w(TAG, "⚠ No hand detected");
                if (enhanced != bitmap) enhanced.recycle();
                return createWhiteBackground(bitmap.getWidth(), bitmap.getHeight());
            }

            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks =
                    handResult.landmarks().get(0);

            int   width = enhanced.getWidth();
            int   height = enhanced.getHeight();
            float minX = 1f, maxX = 0f, minY = 1f, maxY = 0f;

            for (com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm : landmarks) {
                minX = Math.min(minX, lm.x());
                maxX = Math.max(maxX, lm.x());
                minY = Math.min(minY, lm.y());
                maxY = Math.max(maxY, lm.y());
            }

            float pad = 0.2f;
            minX = Math.max(0, minX - pad);
            maxX = Math.min(1, maxX + pad);
            minY = Math.max(0, minY - pad);
            maxY = Math.min(1, maxY + pad);

            int x1 = (int) (minX * width);
            int y1 = (int) (minY * height);
            int x2 = (int) (maxX * width);
            int y2 = (int) (maxY * height);

            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            canvas.drawBitmap(enhanced, new Rect(x1, y1, x2, y2), new Rect(x1, y1, x2, y2), paint);

            if (enhanced != bitmap) enhanced.recycle();
            return output;

        } catch (Exception e) {
            Log.e(TAG, "removeBackground failed", e);
            if (enhanced != null && enhanced != bitmap) enhanced.recycle();
            return bitmap; // fall back to original
        }
    }

    private Bitmap createWhiteBackground(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(width, 1), Math.max(height, 1), Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(Color.WHITE);
        return bitmap;
    }

    // ── Main classification ───────────────────────────────────────
    public Result classify(Bitmap bitmap) {
        if (tflite == null || bitmap == null) {
            return new Result("Error", 0.0f, false);
        }

        Bitmap noBg   = null;
        Bitmap resized = null;

        try {
            noBg   = removeBackground(bitmap);
            resized = Bitmap.createScaledBitmap(noBg, IMG_SIZE, IMG_SIZE, true);

            ByteBuffer inputBuffer =
                    ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3);
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] pixels = new int[IMG_SIZE * IMG_SIZE];
            resized.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE);
            for (int pixel : pixels) {
                inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
                inputBuffer.putFloat(((pixel >> 8)  & 0xFF) / 255.0f);
                inputBuffer.putFloat((pixel         & 0xFF) / 255.0f);
            }

            float[][] output = new float[1][NUM_CLASSES];
            tflite.run(inputBuffer, output);
            float[] probs = output[0];

            int   maxIdx    = 0;
            float maxProb   = probs[0];
            int   secondIdx = 1;
            float secondProb = probs.length > 1 ? probs[1] : 0f;

            for (int i = 1; i < NUM_CLASSES; i++) {
                if (probs[i] > maxProb) {
                    secondIdx  = maxIdx;
                    secondProb = maxProb;
                    maxIdx     = i;
                    maxProb    = probs[i];
                } else if (probs[i] > secondProb) {
                    secondIdx  = i;
                    secondProb = probs[i];
                }
            }

            String topLabel      = (labels != null && maxIdx < labels.size())
                    ? labels.get(maxIdx) : String.valueOf(maxIdx);
            float  confidenceGap = maxProb - secondProb;

            recentPredictions.add(topLabel);
            recentConfidences.add(maxProb);
            if (recentPredictions.size() > SMOOTHING_FRAMES) {
                recentPredictions.poll();
                recentConfidences.poll();
            }

            String stableLabel  = getStablePrediction();
            float  avgConfidence = getAverageConfidence();

            boolean isStable = stableLabel != null
                    && confidenceGap  > 0.25f
                    && avgConfidence > 0.85f;

            String finalLabel = isStable ? stableLabel : topLabel;

            Log.d(TAG, String.format("Label: %s  conf: %.2f  gap: %.2f  stable: %s",
                    finalLabel, maxProb, confidenceGap, isStable));

            return new Result(finalLabel, maxProb, isStable);

        } catch (Exception e) {
            Log.e(TAG, "Classification error", e);
            return new Result("Error", 0.0f, false);
        } finally {
            // FIX: safe recycle — never recycle the original caller's bitmap
            if (resized != null && resized != noBg  && resized != bitmap) resized.recycle();
            if (noBg    != null && noBg    != bitmap)                      noBg.recycle();
        }
    }

    // ── Smoothing helpers ─────────────────────────────────────────
    private String getStablePrediction() {
        if (recentPredictions.size() < SMOOTHING_FRAMES) return null;
        Map<String, Integer> counts = new HashMap<>();
        for (String pred : recentPredictions)
            counts.put(pred, counts.getOrDefault(pred, 0) + 1);
        String mostCommon = null;
        int    maxCount   = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > maxCount) { maxCount = e.getValue(); mostCommon = e.getKey(); }
        }
        return maxCount >= 5 ? mostCommon : null;
    }

    private float getAverageConfidence() {
        if (recentConfidences.isEmpty()) return 0f;
        float sum = 0f;
        for (float c : recentConfidences) sum += c;
        return sum / recentConfidences.size();
    }

    public void resetSmoothing() {
        recentPredictions.clear();
        recentConfidences.clear();
    }

    public void close() {
        try { if (tflite        != null) { tflite.close();        tflite        = null; } } catch (Exception ignored) {}
        try { if (handLandmarker != null) { handLandmarker.close(); handLandmarker = null; } } catch (Exception ignored) {}
    }

    // ── Result DTO ────────────────────────────────────────────────
    public static class Result {
        private final String  label;
        private final float   confidence;
        private final boolean stable;

        public Result(String label, float confidence, boolean stable) {
            this.label      = label;
            this.confidence = confidence;
            this.stable     = stable;
        }

        public String  getLabel()            { return label; }
        public float   getConfidence()       { return confidence; }
        public int     getConfidencePercent(){ return Math.round(confidence * 100); }
        public boolean isStable()            { return stable; }
    }
}
