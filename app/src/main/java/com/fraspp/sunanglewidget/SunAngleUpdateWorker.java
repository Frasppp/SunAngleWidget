package com.fraspp.sunanglewidget;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Tasks;

import org.shredzone.commons.suncalc.SunPosition;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SunAngleUpdateWorker extends Worker {
    private static final String TAG = "SunAngleWorker";
    private static final double FALLBACK_LAT = 64.28;
    private static final double FALLBACK_LON = 20.57;

    public SunAngleUpdateWorker(@NonNull Context ctx,
                                @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        ComponentName cn = new ComponentName(ctx, SunAngleWidgetProvider.class);
        int[] ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(cn);
        for (int id : ids) {
            updateWidgetNow(ctx, id);
        }
        return Result.success();
    }

    static void updateWidgetNow(Context ctx, int widgetId) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        RemoteViews rv = new RemoteViews(
                ctx.getPackageName(),
                R.layout.widget_sun_angle
        );

        // 1) Behörighet
        if (ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            rv.setTextViewText(R.id.widget_text, "Ingen behörighet");
            awm.updateAppWidget(widgetId, rv);
            return;
        }

        double lat, lon;
        boolean isFallback = false;
        try {
            // 2) Hämta aktuell plats
            FusedLocationProviderClient flp =
                    LocationServices.getFusedLocationProviderClient(ctx);
            CancellationTokenSource cts = new CancellationTokenSource();
            android.location.Location loc = Tasks.await(
                    flp.getCurrentLocation(
                            com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY,
                            cts.getToken()
                    ),
                    3, TimeUnit.SECONDS
            );
            if (loc == null) throw new Exception("Location null");
            lat = loc.getLatitude();
            lon = loc.getLongitude();
            Log.d(TAG, "Got location: " + lat + ", " + lon);
        } catch (Exception e) {
            // 3) Fallback till statiska Burträsk‑koordinater
            Log.w(TAG, "Location-fel – fallback till Burträsk", e);
            lat = FALLBACK_LAT;
            lon = FALLBACK_LON;
            isFallback = true;
        }

        // 4) Bestäm display‑text för plats
        String locText;
        if (isFallback) {
            locText = "Burträsk";
        } else {
            // Försök Geocoder för stad/by
            locText = String.format(Locale.US, "%.2f,%.2f", lat, lon);
            try {
                Geocoder gc = new Geocoder(ctx, Locale.getDefault());
                List<Address> list = gc.getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address adr = list.get(0);
                    if (adr.getLocality() != null && !adr.getLocality().isEmpty()) {
                        locText = adr.getLocality();
                    } else if (adr.getSubLocality() != null &&
                            !adr.getSubLocality().isEmpty()) {
                        locText = adr.getSubLocality();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocoder-fel, använder koordinater", e);
            }
        }

        // 5) Beräkna solvinkel
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        double elev = SunPosition.compute()
                .on(now).at(lat, lon).execute().getAltitude();
        String sunText = String.format(Locale.US, "%.2f°", elev);

        // 6) Uppdatera widgeten
        rv.setTextViewText(R.id.widget_text, locText);
        rv.setTextViewText(R.id.widget_value, sunText);
        awm.updateAppWidget(widgetId, rv);
        Log.d(TAG, "Widget " + widgetId + " → ["
                + locText + "] [" + sunText + "]");
    }
}
