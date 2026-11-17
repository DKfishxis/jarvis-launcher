package com.jarvis.launcher;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "Jarvis");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadInstalledApps();
            }
        });

        webView.loadUrl("file:///android_asset/painel_tv.html");
    }

    public class WebAppInterface {
        private Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            try {
                Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void requestApps(String featuredJsonStr) {
            loadInstalledApps();
        }

        @JavascriptInterface
        public void downloadAndInstall(String url) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle("J.A.R.V.I.S. Update");
                request.setDescription("A transferir a nova versão...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "jarvis-launcher.apk");
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);

                DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

        private void loadInstalledApps() {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> allApps = pm.queryIntentActivities(intent, 0);

            // Lista dos pacotes fixos da tela inicial (igual ao do HTML)
            String[] featuredPkgs = {
                "com.unitvnet.tvod",
                "com.google.android.youtube.tv",
                "com.instantbits.cast.receiver",
                "com.kick.mobile",
                "tv.twitch.android.app",
                "com.android.vending",
                "com.android.tv.settings"
            };

            JSONArray jsonAll = new JSONArray();
            JSONArray jsonFeatured = new JSONArray();

            for (ResolveInfo ri : allApps) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                
                String name = ri.loadLabel(pm).toString();
                
                String iconBase64 = "";
                try {
                    Drawable icon = pm.getApplicationIcon(pkg);
                    Bitmap bitmap = drawableToBitmap(icon);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    iconBase64 = "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JSONObject appObj = new JSONObject();
                appObj.put("pkg", pkg);
                appObj.put("name", name);
                appObj.put("icon", iconBase64);
                
                jsonAll.put(appObj);

                // Verifica se este aplicativo faz parte dos destaques da tela inicial
                for (String fPkg : featuredPkgs) {
                    if (fPkg.equals(pkg)) {
                        jsonFeatured.put(appObj);
                        break;
                    }
                }
            }

            // Garante que mesmo os apps destacados que não venham no launcher nativo sejam enviados (ou tratados)
            final String script = "if(window.onAppsLoaded){ window.onAppsLoaded({featured: " + jsonFeatured.toString() + ", all: " + jsonAll.toString() + "}); }";
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(script, null);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = 96;
        if (height <= 0) height = 96;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("if(window.jarvisBack){ window.jarvisBack(); }", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
