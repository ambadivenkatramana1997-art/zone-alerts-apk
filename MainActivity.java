package com.zonealerts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class MainActivity extends Activity {
    WebView web;
    SharedPreferences sp;
    boolean asking = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("za", MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) askUrl("Couldn't open the app. Paste the new link:");
            }
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e) {
                if (r.isForMainFrame()) askUrl("Link rejected. Paste the new link:");
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        String url = sp.getString("url", "");
        if (url.isEmpty()) askUrl("Paste your private app link:");
        else open(url);
    }

    void open(String url) {
        web.loadUrl(url);
        startForegroundService(new Intent(this, PollService.class));
    }

    void askUrl(String msg) {
        if (asking) return;
        asking = true;
        final EditText t = new EditText(this);
        t.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        t.setText(sp.getString("url", ""));
        new AlertDialog.Builder(this)
            .setTitle("Zone Alerts")
            .setMessage(msg)
            .setView(t)
            .setCancelable(false)
            .setPositiveButton("Save", (d, w) -> {
                asking = false;
                String u = t.getText().toString().trim();
                if (u.startsWith("https://") && u.contains("k=")) {
                    sp.edit().putString("url", u).putLong("last", -1).apply();
                    stopService(new Intent(this, PollService.class));
                    open(u);
                } else {
                    askUrl("Link must start with https:// and contain ?k=");
                }
            })
            .show();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
