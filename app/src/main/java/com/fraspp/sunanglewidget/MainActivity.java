package com.fraspp.sunanglewidget;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

public class MainActivity extends ComponentActivity {
    private static final int REQ_FINE = 1;
    private static final int REQ_BG   = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFineIfNeeded();
    }

    private void requestFineIfNeeded() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, REQ_FINE);
        } else requestBgIfNeeded();
    }

    private void requestBgIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{ Manifest.permission.ACCESS_BACKGROUND_LOCATION }, REQ_BG);
        } else {
            triggerImmediateUpdate();
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_FINE && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED)
            requestBgIfNeeded();
        else if (code == REQ_BG)
            triggerImmediateUpdate();
        finish();
    }

    private void triggerImmediateUpdate() {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SunAngleUpdateWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();
        WorkManager.getInstance(this).enqueue(req);
    }
}
