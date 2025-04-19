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

    public static final String ACTION_UPDATE =
            "com.fraspp.sunanglewidget.ACTION_UPDATE";

    private static final String UNIQUE_PERIODIC_WORK = "SunAnglePeriodicWork";

    @Override
    public void onUpdate(Context context,
                         AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {

        for (int id : appWidgetIds) {
            RemoteViews rv = new RemoteViews(
                    context.getPackageName(),
                    R.layout.widget_sun_angle
            );
            rv.setTextViewText(R.id.widget_text, "Hämtar…");
            rv.setTextViewText(R.id.widget_value, "—");
            rv.setOnClickPendingIntent(
                    R.id.widget_layout,
                    buildClickIntent(context, id)
            );
            appWidgetManager.updateAppWidget(id, rv);
        }

        enqueueExpeditedWork(context);

        PeriodicWorkRequest periodicReq = new PeriodicWorkRequest.Builder(
                SunAngleUpdateWorker.class,
                15, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicReq
        );
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_UPDATE.equals(intent.getAction())) {
            enqueueExpeditedWork(context);
        }
    }

    private static PendingIntent buildClickIntent(Context ctx, int widgetId) {
        Intent i = new Intent(ctx, SunAngleWidgetProvider.class)
                .setAction(ACTION_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

        return PendingIntent.getBroadcast(
                ctx, widgetId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void enqueueExpeditedWork(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(
                SunAngleUpdateWorker.class)
                .setExpedited(
                        OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        WorkManager.getInstance(ctx).enqueue(req);
    }
}
