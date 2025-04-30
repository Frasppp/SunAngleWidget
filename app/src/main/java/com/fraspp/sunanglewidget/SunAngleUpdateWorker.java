package com.fraspp.sunanglewidget;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Tasks;

import org.shredzone.commons.suncalc.SunPosition;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SunAngleUpdateWorker extends Worker {
    private static final String TAG = "SunAngleWorker";
    private static final double FALLBACK_LAT = 64.52;
    private static final double FALLBACK_LON = 20.65;
    private static final Pattern POSTCODE_PATTERN =
            Pattern.compile("\\b\\d{3}\\s\\d{2}\\s*([\\p{L}ÅÄÖåäö]+)", Pattern.UNICODE_CASE);
    private static final String PREFS = "sun_prefs";

    public SunAngleUpdateWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        int[] ids = AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(new ComponentName(ctx, SunAngleWidgetProvider.class));
        for (int id : ids) updateWidgetNow(ctx, id);
        return Result.success();
    }

    static void updateWidgetNow(Context ctx, int widgetId) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sun_angle);

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, widgetId,
                new Intent(ctx, SunAngleWidgetProvider.class)
                        .setAction(SunAngleWidgetProvider.ACTION_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_layout, pi);

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            rv.setTextViewText(R.id.widget_text, "Behörighet saknas");
            awm.updateAppWidget(widgetId, rv);
            return;
        }

        double lat;
        double lon;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        try {
            FusedLocationProviderClient flp = LocationServices.getFusedLocationProviderClient(ctx);
            Location loc = null;
            try {
                loc = Tasks.await(flp.getLastLocation(), 2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            if (loc == null) {
                CancellationTokenSource cts = new CancellationTokenSource();
                loc = Tasks.await(flp.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken()),
                        10, TimeUnit.SECONDS);
            }
            if (loc == null) throw new Exception("location null");
            lat = loc.getLatitude();
            lon = loc.getLongitude();
            p.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
        } catch (Exception e) {
            lat = p.getFloat("lat", Float.NaN);
            lon = p.getFloat("lon", Float.NaN);
            if (Double.isNaN(lat))
                lat = FALLBACK_LAT;
            if (Double.isNaN(lon))
                lon = FALLBACK_LON;
            Log.w(TAG, "Location error, using cached/fallback", e);
        }

        String locText = String.format(Locale.US, "%.2f,%.2f", lat, lon);
        Geocoder geocoder = new Geocoder(ctx, Locale.forLanguageTag("en"));
        CountDownLatch latch = new CountDownLatch(1);
        String[] res = {locText};
        geocoder.getFromLocation(lat, lon, 1, list -> {
            if (list != null && !list.isEmpty())
                res[0] = extractName(list.get(0), res[0]);
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        locText = res[0];

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        double elev = SunPosition.compute().on(now).at(lat, lon).execute().getAltitude();

        rv.setTextViewText(R.id.widget_text, locText);
        rv.setTextViewText(R.id.widget_value,
                String.format(Locale.US, "%.2f°", elev));
        awm.updateAppWidget(widgetId, rv);
    }

    private static String extractName(Address adr, String fallback) {
        String name = null;
        if (adr.getLocality() != null && !adr.getLocality().isEmpty())
            name = adr.getLocality();
        else if (adr.getSubLocality() != null && !adr.getSubLocality().isEmpty())
            name = adr.getSubLocality();
        if ((name == null || name.isEmpty()) && adr.getMaxAddressLineIndex() >= 0) {
            Matcher m = POSTCODE_PATTERN.matcher(adr.getAddressLine(0));
            if (m.find()) name = m.group(1);
        }
        return (name != null && !name.isEmpty()) ? name : fallback;
    }
}
