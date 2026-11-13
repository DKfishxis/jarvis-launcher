package com.jarvis.launcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("jarvis://")) {
                    handleJarvisIntent(url);
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl("file:///android_asset/painel_tv.html");
    }

    private void handleJarvisIntent(String url) {
        try {
            Uri uri = Uri.parse(url);
            String action = uri.getHost();

            if ("kick".equals(action)) {
                String channel = uri.getPathSegments().size() > 0 ? uri.getPathSegments().get(0) : "";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://kick.com/" + channel));
                intent.setPackage("com.kick.mobile");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "App da Kick não encontrado. Abrindo web...", Toast.LENGTH_SHORT).show();
                    openWeb("https://kick.com/" + channel);
                }
            } else if ("twitch".equals(action)) {
                String channel = uri.getPathSegments().size() > 0 ? uri.getPathSegments().get(0) : "";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://twitch.tv/" + channel));
                intent.setPackage("tv.twitch.android.app");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "App da Twitch não encontrado.", Toast.LENGTH_SHORT).show();
                    openWeb("https://twitch.tv/" + channel);
                }
            } else if ("web".equals(action)) {
                String webUrl = uri.getQueryParameter("url");
                if (webUrl != null) {
                    openWeb(webUrl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openWeb(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // Ignora o voltar na Home para não fechar o Launcher
    }
}
