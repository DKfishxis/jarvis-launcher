package com.jarvis.launcher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int ICON_SIZE_FEATURED = 160;
    private static final int ICON_SIZE_DRAWER = 96;

    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Cache de ícones (acessado apenas pela thread do executor)
    private final Map<String, String> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        // Bridge: no HTML fica disponível como window.Jarvis
        webView.addJavascriptInterface(new JarvisBridge(), "Jarvis");

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

    // ------------------------------------------------------------------
    // Bridge JS <-> Android
    // ------------------------------------------------------------------
    private class JarvisBridge {

        /**
         * Chamado pelo HTML. Recebe um JSON com a lista de pacotes em destaque
         * (na ordem desejada) e devolve, de forma assíncrona, via
         * window.onAppsLoaded({featured:[...], all:[...]}).
         */
        @JavascriptInterface
        public void requestApps(final String featuredJson) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    String payload;
                    try {
                        payload = buildPayload(featuredJson);
                    } catch (Exception e) {
                        e.printStackTrace();
                        payload = "{\"featured\":[],\"all\":[]}";
                    }
                    // Segurança para separadores de linha unicode em JS antigo
                    payload = payload
                            .replace(String.valueOf((char) 0x2028), "\\u2028")
                            .replace(String.valueOf((char) 0x2029), "\\u2029");
                    final String js = "window.onAppsLoaded && window.onAppsLoaded(" + payload + ");";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(js, null);
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public void launchApp(final String packageName) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    launchPackage(packageName);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Montagem da lista de apps + ícones (Base64)
    // ------------------------------------------------------------------
    private String buildPayload(String featuredJson) throws Exception {
        PackageManager pm = getPackageManager();

        // 1) Apps em destaque (ordem definida pelo HTML)
        JSONArray requested = new JSONArray(featuredJson);
        JSONArray featured = new JSONArray();
        for (int i = 0; i < requested.length(); i++) {
            String pkg = requested.getString(i);
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                o.put("installed", true);
                o.put("icon", iconToBase64(pkg, pm.getApplicationIcon(ai), ICON_SIZE_FEATURED));
            } catch (PackageManager.NameNotFoundException e) {
                o.put("installed", false);
            }
            featured.put(o);
        }

        // 2) Todos os apps lançáveis (launcher normal + leanback/TV)
        final Map<String, String> labels = new HashMap<>();
        final Map<String, Drawable> icons = new HashMap<>();
        collect(pm, Intent.CATEGORY_LAUNCHER, labels, icons);
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, labels, icons);

        List<String> pkgs = new ArrayList<>(labels.keySet());
        Collections.sort(pkgs, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return labels.get(a).compareToIgnoreCase(labels.get(b));
            }
        });

        JSONArray all = new JSONArray();
        for (String pkg : pkgs) {
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            o.put("name", labels.get(pkg));
            o.put("icon", iconToBase64(pkg, icons.get(pkg), ICON_SIZE_DRAWER));
            all.put(o);
        }

        JSONObject result = new JSONObject();
        result.put("featured", featured);
        result.put("all", all);
        return result.toString();
    }

    private void collect(PackageManager pm, String category,
                         Map<String, String> labels, Map<String, Drawable> icons) {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue; // não listar o próprio launcher
            if (labels.containsKey(pkg)) continue;
            labels.put(pkg, String.valueOf(ri.loadLabel(pm)));
            icons.put(pkg, ri.loadIcon(pm));
        }
    }

    private String iconToBase64(String pkg, Drawable drawable, int size) {
        String key = pkg + ":" + size;
        String cached = iconCache.get(key);
        if (cached != null) return cached;
        if (drawable == null) return "";

        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            String b64 = "data:image/png;base64,"
                    + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            iconCache.put(key, b64);
            return b64;
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }

    // ------------------------------------------------------------------
    // Abrir apps instalados
    // ------------------------------------------------------------------
    private void launchPackage(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        }
        if (intent == null && "com.android.settings".equals(pkg)) {
            intent = new Intent(Settings.ACTION_SETTINGS);
        }
        if (intent == null) {
            Toast.makeText(this, "App não instalado: " + pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o app.", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // Intents customizadas jarvis:// (mantidas)
    // Novo: jarvis://app/<pacote>
    // ------------------------------------------------------------------
    private void handleJarvisIntent(String url) {
        try {
            Uri uri = Uri.parse(url);
            String action = uri.getHost();

            if ("app".equals(action)) {
                String pkg = uri.getPathSegments().size() > 0 ? uri.getPathSegments().get(0) : "";
                if (!pkg.isEmpty()) launchPackage(pkg);
            } else if ("kick".equals(action)) {
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

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();
        // Ao voltar de outro app, atualiza a lista (apps instalados/removidos)
        if (webView != null) {
            webView.evaluateJavascript("window.jarvisRefresh && window.jarvisRefresh();", null);
        }
    }

    @Override
    public void onBackPressed() {
        // Na Home não fecha o launcher; só fecha a gaveta se estiver aberta
        webView.evaluateJavascript("window.jarvisBack && window.jarvisBack();", null);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
