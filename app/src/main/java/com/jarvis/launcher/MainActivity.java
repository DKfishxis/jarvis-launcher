package com.jarvis.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // Ponte de comunicação com o HTML
        webView.addJavascriptInterface(new WebAppInterface(), "Jarvis");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadInstalledApps();
            }
        });

        webView.loadUrl("file:///android_asset/painel_tv.html");
        setContentView(webView);
    }

    // Intercepta o botão Home para garantir que o Jarvis responda a ele
    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("if(window.jarvisBack) { window.jarvisBack(); }", null);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void launchApp(String packageName) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void requestApps(String featuredJsonStr) {
            loadInstalledApps();
        }
    }

    private void loadInstalledApps() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> allApps = pm.queryIntentActivities(intent, 0);

                Collections.sort(allApps, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
                    }
                });

                JSONArray allArray = new JSONArray();
                for (ResolveInfo ri : allApps) {
                    String pkg = ri.activityInfo.packageName;
                    String name = ri.loadLabel(pm).toString();
                    String iconBase64 = "";
                    try {
                        Drawable icon = ri.loadIcon(pm);
                        iconBase64 = drawableToBase64(icon);
                    } catch (Exception ignored) {}

                    JSONObject obj = new JSONObject();
                    obj.put("pkg", pkg);
                    obj.put("name", name);
                    obj.put("icon", iconBase64);
                    allArray.put(obj);
                }

                JSONObject payload = new JSONObject();
                payload.put("all", allArray);

                runOnUiThread(() -> {
                    webView.evaluateJavascript("if(window.onAppsLoaded) { window.onAppsLoaded(" + payload.toString() + "); }", null);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String drawableToBase64(Drawable drawable) {
        try {
            Bitmap bitmap;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                if (width <= 0) width = 96;
                if (height <= 0) height = 96;
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.Format.PNG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
