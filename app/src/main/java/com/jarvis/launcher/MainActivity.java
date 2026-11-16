package com.jarvis.launcher;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Coloca a aplicação em ecrã inteiro (estilo TV Box / Launcher)
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

        // Adiciona a ponte Javascript para o HTML interagir com o Android
        webView.addJavascriptInterface(new WebAppInterface(this), "Jarvis");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Quando a página carregar, envia a lista de apps instalados
                loadInstalledApps();
            }
        });

        // Carrega o painel HTML que está guardado nos assets
        webView.loadUrl("file:///android_asset/painel_tv.html");
    }

    // Classe de Comunicação entre o WebView (HTML) e o Android nativo
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

        // Função inteligente de Download e Atualização Automática
        @JavascriptInterface
        public void downloadAndInstall(String url) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle("J.A.R.V.I.S. Update");
                request.setDescription("A transferir a nova versão do sistema...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                
                // Guarda diretamente na pasta de Downloads pública do dispositivo
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

    // Recolhe todos os aplicativos instalados na TV Box para popular a gaveta
    private void loadInstalledApps() {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> allApps = pm.queryIntentActivities(intent, 0);

            JSONArray jsonAll = new JSONArray();
            for (ResolveInfo ri : allApps) {
                String pkg = ri.activityInfo.packageName;
                // Ignora o próprio launcher para não aparecer na lista de apps
                if (pkg.equals(getPackageName())) continue;
                
                String name = ri.loadLabel(pm).toString();

                JSONObject appObj = new JSONObject();
                appObj.put("pkg", pkg);
                appObj.put("name", name);
                // Pode adicionar ícones em base64 se desejar no futuro, por agora vai o pacote e nome
                jsonAll.put(appObj);
            }

            final String script = "if(window.onAppsLoaded){ window.onAppsLoaded({featured: [], all: " + jsonAll.toString() + "}); }";
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Mapeia o botão "Voltar" do comando para o ecossistema do painel HTML
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("if(window.jarvisBack){ window.jarvisBack(); }", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
