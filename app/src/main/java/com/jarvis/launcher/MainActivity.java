package com.jarvis.launcher;

import android.app.Activity;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        webView.addJavascriptInterface(new WebAppInterface(), "Jarvis");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadApps();
            }
        });

        webView.loadUrl("file:///android_asset/painel_tv.html");
        setContentView(webView);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("if(window.jarvisBack) { window.jarvisBack(); }", null);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void launchApp(String pkg) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            } catch (Exception e) {}
        }

        @JavascriptInterface
        public void requestApps(String json) {
            loadApps();
        }
    }

    private void loadApps() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

                JSONArray arr = new JSONArray();
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    String name = ri.loadLabel(pm).toString();
                    String iconB64 = "";
                    try {
                        Drawable dr = ri.loadIcon(pm);
                        iconB64 = drawableToBase64(dr);
                    } catch (Exception e) {}

                    JSONObject obj = new JSONObject();
                    obj.put("pkg", pkg);
                    obj.put("name", name);
                    obj.put("icon", iconB64);
                    obj.put("installed", true);
                    arr.put(obj);
                }

                JSONObject payload = new JSONObject();
                payload.put("all", arr);
                payload.put("featured", arr);

                runOnUiThread(() -> {
                    webView.evaluateJavascript("if(window.onAppsLoaded) { window.onAppsLoaded(" + payload.toString() + "); }", null);
                });
            } catch (Exception e) {}
        }).start();
    }

    private String drawableToBase64(Drawable dr) {
        try {
            Bitmap bmp;
            if (dr instanceof BitmapDrawable) {
                bmp = ((BitmapDrawable) dr).getBitmap();
            } else {
                int w = dr.getIntrinsicWidth() > 0 ? dr.getIntrinsicWidth() : 96;
                int h = dr.getIntrinsicHeight() > 0 ? dr.getIntrinsicHeight() : 96;
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                dr.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                dr.draw(canvas);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 80, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
