package com.fraspp.sunanglewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SunAngleWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.fraspp.sunanglewidget.ACTION_UPDATE";
    private static final String UNIQUE_PERIODIC_WORK = "SunAnglePeriodicWork";

    @Override
    public void onUpdate(Context context,
                         AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        // 1) Schemalägg bakgrundsjobb var 15:e minut
        PeriodicWorkRequest periodicReq = new PeriodicWorkRequest.Builder(
                SunAngleUpdateWorker.class,
                15, TimeUnit.MINUTES
        )
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        UNIQUE_PERIODIC_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        periodicReq
                );

        // 2) Schemalägg också ett engångsjobb direkt vid första läggning
        OneTimeWorkRequest immediateReq = new OneTimeWorkRequest.Builder(
                SunAngleUpdateWorker.class
        )
                .build();
        WorkManager.getInstance(context)
                .enqueue(immediateReq);

        // 3) Sätt upp klick‑intent på varje widgetinstans
        for (int id : appWidgetIds) {
            RemoteViews rv = new RemoteViews(
                    context.getPackageName(),
                    R.layout.widget_sun_angle
            );
            Intent clickIntent = new Intent(context, SunAngleWidgetProvider.class)
                    .setAction(ACTION_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, id, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_layout, pi);
            appWidgetManager.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // När widgeten klickas: schemalägg ett engångsjobb
        if (ACTION_UPDATE.equals(intent.getAction())) {
            OneTimeWorkRequest clickReq = new OneTimeWorkRequest.Builder(
                    SunAngleUpdateWorker.class
            )
                    .build();
            WorkManager.getInstance(context)
                    .enqueue(clickReq);
        }
    }
}
