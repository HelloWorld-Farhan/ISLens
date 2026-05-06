package com.islvision.islens;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * DownloadService — STUB (No longer does anything)
 * ──────────────────────────────────────────────────
 * ML Kit downloads fully replaced by MyMemory REST API.
 * This stub is kept ONLY so AndroidManifest.xml and MainActivity compile
 * without changes. It stops itself instantly if started.
 *
 * Safe to delete this file + remove <service> from AndroidManifest.xml
 * once you confirm the build is clean.
 */
public class DownloadService extends Service {

    public static final String EXTRA_LANG_CODE = "lang_code";
    public static final String EXTRA_LANG_NAME = "lang_name";

    // Kept so MainActivity.serviceReconnectConn compiles without changes
    public class LocalBinder extends Binder {
        public DownloadService getService() { return DownloadService.this; }
    }

    private final IBinder binder = new LocalBinder();

    public interface DownloadCallback {
        void onProgress(int percent, String statusText);
        void onSuccess(String langCode, String langName);
        void onFailure(String langCode);
    }

    // Kept so DownloadHelper compiles — does nothing
    public void setCallback(DownloadCallback cb) { }

    // Kept so MainActivity.serviceReconnectConn compiles — always returns false/null
    public boolean isDownloadDone()    { return false; }
    public boolean isDownloadSuccess() { return false; }
    public String  getCurrentLangCode(){ return null; }
    public String  getCurrentLangName(){ return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(); // Nothing to do — stop immediately
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }
}
