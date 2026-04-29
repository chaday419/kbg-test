package cd.upn.suivipresence;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "UPNPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String CONFIG_URL = "https://chaday419.github.io/kbg-test/api-server.json";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST = 101;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView errorText;
    private View errorLayout;
    private String serverUrl;
    private ValueCallback<Uri[]> filePathCallback;
    private GestureDetector gestureDetector;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Compteur pour accès caché admin (appui long sur logo)
    private int logoLongPressCount = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        errorText = findViewById(R.id.errorText);
        ImageView logoView = findViewById(R.id.logoUPN);

        serverUrl = getIntent().getStringExtra("server_url");
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_SERVER_URL, "");
        }

        setupWebView();
        requestPermissions();
        setupLogoLongPress(logoView);

        if (!serverUrl.isEmpty()) {
            loadApp();
        } else {
            showError("Serveur non configuré. Vérifiez votre connexion.");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " UPN-Android-App/1.0");

        // Interface JS pour communication native
        webView.addJavascriptInterface(new UPNJSInterface(), "UPNAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                // Injecter CSS mobile si nécessaire
                injectMobileCSS();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    showError("Impossible de joindre le serveur.\nVérifiez votre connexion internet.");
                    // Tenter de rafraîchir l'URL depuis GitHub
                    refreshServerUrl();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Garder les URLs du serveur dans le WebView
                if (url.startsWith(serverUrl) || url.contains("ngrok-free.dev") || url.contains("ngrok.io")) {
                    return false;
                }
                // Ouvrir les liens externes dans le navigateur
                if (url.startsWith("http") || url.startsWith("https")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                              FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "JS: " + consoleMessage.message());
                return true;
            }
        });
    }

    private void loadApp() {
        String loginUrl = serverUrl + "/index.php?controller=auth&action=login";
        webView.loadUrl(loginUrl);
        errorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void injectMobileCSS() {
        String css = "document.querySelector('meta[name=viewport]') || " +
            "document.head.insertAdjacentHTML('beforeend', " +
            "'<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0\">');";
        webView.evaluateJavascript("(function(){" + css + "})();", null);
    }

    private void showError(String message) {
        errorLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        errorText.setText(message);
    }

    private void refreshServerUrl() {
        executor.execute(() -> {
            try {
                URL url = new URL(CONFIG_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject json = new JSONObject(sb.toString());
                    String newUrl = json.getString("server_url");
                    if (!newUrl.equals(serverUrl)) {
                        serverUrl = newUrl;
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit().putString(KEY_SERVER_URL, serverUrl).apply();
                        mainHandler.post(this::loadApp);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Refresh URL failed: " + e.getMessage());
            }
        });
    }

    private void setupLogoLongPress(ImageView logoView) {
        if (logoView == null) return;
        logoView.setOnLongClickListener(v -> {
            logoLongPressCount++;
            if (logoLongPressCount >= 3) {
                logoLongPressCount = 0;
                showAdminPanel();
            } else {
                Toast.makeText(this, "Encore " + (3 - logoLongPressCount) + " fois...", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void showAdminPanel() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentUrl = prefs.getString(KEY_SERVER_URL, serverUrl);
        new AlertDialog.Builder(this)
            .setTitle("⚙️ Paramètres Serveur")
            .setMessage("URL actuelle :\n" + currentUrl + "\n\nConfig GitHub :\n" + CONFIG_URL)
            .setPositiveButton("Rafraîchir URL", (d, w) -> {
                refreshServerUrl();
                Toast.makeText(this, "Mise à jour en cours...", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Fermer", null)
            .show();
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        boolean needRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && !serverUrl.isEmpty()) {
            loadApp();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = (resultCode == RESULT_OK && data != null) ?
                    new Uri[]{data.getData()} : null;
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Quitter")
                .setMessage("Voulez-vous quitter l'application ?")
                .setPositiveButton("Oui", (d, w) -> finish())
                .setNegativeButton("Non", null)
                .show();
        }
    }

    public void onRetryClick(View v) {
        if (!serverUrl.isEmpty()) {
            loadApp();
        } else {
            refreshServerUrl();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (webView != null) webView.destroy();
    }

    // Interface JavaScript → Android
    private class UPNJSInterface {
        @android.webkit.JavascriptInterface
        public String getDeviceInfo() {
            return "{\"platform\":\"android\",\"version\":\"" + android.os.Build.VERSION.RELEASE + "\"}";
        }

        @android.webkit.JavascriptInterface
        public void showToast(String message) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }
}
