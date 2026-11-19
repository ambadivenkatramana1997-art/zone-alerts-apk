package com.zonealerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PollService extends Service {
    volatile boolean run;
    Thread th;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            "status", "Monitoring", NotificationManager.IMPORTANCE_MIN));
        NotificationChannel ch = new NotificationChannel(
            "alerts", "Zone alerts", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);

        Notification n = new Notification.Builder(this, "status")
            .setContentTitle("Zone Alerts running")
            .setContentText("Watching your zones")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }

        if (th == null || !th.isAlive()) {
            run = true;
            th = new Thread(this::loop);
            th.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        run = false;
        if (th != null) th.interrupt();
        super.onDestroy();
    }

    void loop() {
        SharedPreferences sp = getSharedPreferences("za", MODE_PRIVATE);
        while (run) {
            try {
                Uri u = Uri.parse(sp.getString("url", ""));
                String key = u.getQueryParameter("k");
                if (key != null && u.getHost() != null) {
                    HttpURLConnection c = (HttpURLConnection)
                        new URL(u.getScheme() + "://" + u.getHost() + "/api/state").openConnection();
                    c.setRequestProperty("X-Key", key);
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    if (c.getResponseCode() == 200) {
                        StringBuilder sb = new StringBuilder();
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();

                        JSONArray a = new JSONObject(sb.toString()).getJSONArray("alerts");
                        long last = sp.getLong("last", -1);
                        long max = last;
                        for (int i = a.length() - 1; i >= 0; i--) {
                            JSONObject o = a.getJSONObject(i);
                            long id = o.getLong("id");
                            if (id > max) max = id;
                            if (last >= 0 && id > last) notifyAlert(o);
                        }
                        sp.edit().putLong("last", Math.max(max, 0)).apply();
                    }
                    c.disconnect();
                }
            } catch (Exception ignored) { }
            try { Thread.sleep(15000); } catch (InterruptedException e) { break; }
        }
    }

    void notifyAlert(JSONObject o) throws JSONException {
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = (o.optBoolean("simulated") ? "[SIM] " : "")
            + "🔔 " + o.getString("index");
        String body = o.getString("condition")
            + "\nZone " + String.format("%.2f", o.getDouble("lower"))
            + " – " + String.format("%.2f", o.getDouble("upper"))
            + "\nClose " + String.format("%.2f", o.getDouble("close"));
        Notification n = new Notification.Builder(this, "alerts")
            .setContentTitle(title)
            .setContentText(o.getString("condition"))
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        getSystemService(NotificationManager.class).notify((int) o.getLong("id"), n);
    }
}
