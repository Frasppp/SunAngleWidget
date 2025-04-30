package com.fraspp.sunanglewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SunAngleWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.fraspp.sunanglewidget.ACTION_UPDATE";
    private static final String UNIQUE_PERIODIC_WORK = "SunAnglePeriodicWork";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sun_angle);
            rv.setTextViewText(R.id.widget_text, "Hämtar…");
            rv.setTextViewText(R.id.widget_value, "—");
            rv.setOnClickPendingIntent(R.id.widget_layout, buildClickPI(ctx, id));
            awm.updateAppWidget(id, rv);
        }
        enqueueExpedited(ctx);                                     // direkt
        PeriodicWorkRequest p = new PeriodicWorkRequest.Builder(
                SunAngleUpdateWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, p);
    }

    @Override
    public void onReceive(Context ctx, Intent i) {
        super.onReceive(ctx, i);
        if (ACTION_UPDATE.equals(i.getAction())) enqueueExpedited(ctx);
    }

    private static PendingIntent buildClickPI(Context c, int id) {
        Intent i = new Intent(c, SunAngleWidgetProvider.class)
                .setAction(ACTION_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        return PendingIntent.getBroadcast(
                c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void enqueueExpedited(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SunAngleUpdateWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();
        WorkManager.getInstance(ctx).enqueue(req);
    }
}
