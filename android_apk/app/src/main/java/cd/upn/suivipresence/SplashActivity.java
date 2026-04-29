package cd.upn.suivipresence;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final String CONFIG_URL = "https://chaday419.github.io/kbg-test/api-server.json";
    private static final String PREFS_NAME = "UPNPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String FALLBACK_URL = "https://crimson-hypnosis-hankie.ngrok-free.dev";

    private TextView statusText;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        statusText = findViewById(R.id.statusText);
        fetchServerConfig();
    }

    private void fetchServerConfig() {
        updateStatus("Connexion au serveur...");
        executor.execute(() -> {
            String serverUrl = fetchUrlFromGitHub();
            if (serverUrl == null || serverUrl.isEmpty()) {
                // Utiliser l'URL en cache si disponible
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                serverUrl = prefs.getString(KEY_SERVER_URL, FALLBACK_URL);
                Log.w(TAG, "GitHub inaccessible, utilisation du cache: " + serverUrl);
            } else {
                // Sauvegarder en cache
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_SERVER_URL, serverUrl).apply();
            }
            final String finalUrl = serverUrl;
            mainHandler.postDelayed(() -> launchMain(finalUrl), 800);
        });
    }

    private String fetchUrlFromGitHub() {
        try {
            URL url = new URL(CONFIG_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cache-Control", "no-cache");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                return json.getString("server_url");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur fetch config: " + e.getMessage());
        }
        return null;
    }

    private void updateStatus(String msg) {
        mainHandler.post(() -> { if (statusText != null) statusText.setText(msg); });
    }

    private void launchMain(String serverUrl) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("server_url", serverUrl);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
