# UPN Présence — Guide Déploiement APK Android

## Architecture

```
APK Android
  └── SplashActivity (lit JSON GitHub)
        └── MainActivity (WebView → Serveur Ngrok)

GitHub Pages
  └── api-server.json  ← seul fichier à modifier quand Ngrok change

Serveur XAMPP + Ngrok
  └── Projet PHP/MySQL existant (inchangé)
```

---

## 1. Préparer GitHub Pages

1. Créer un repo GitHub (ex: `chaday419/kbg-test`)
2. Activer GitHub Pages sur la branche `main`
3. Uploader `github_pages/api-server.json` à la racine du repo
4. URL finale : `https://chaday419.github.io/kbg-test/api-server.json`

Contenu du fichier :
```json
{
  "server_url": "https://VOTRE-LIEN-NGROK.ngrok-free.dev",
  "app_version": "1.0.0",
  "maintenance": false,
  "message": ""
}
```

---

## 2. Quand Ngrok change

1. Lancer ngrok : `ngrok http 80`
2. Copier le nouveau lien (ex: `https://abc123.ngrok-free.dev`)
3. Modifier `api-server.json` sur GitHub
4. Commit → tous les APK utilisent automatiquement le nouveau lien

**Aucune réinstallation nécessaire.**

---

## 3. Compiler l'APK

### Prérequis
- Android Studio (version récente)
- JDK 11+

### Étapes
1. Ouvrir Android Studio → `Open` → sélectionner le dossier `android_apk/`
2. Attendre la synchronisation Gradle
3. `Build` → `Generate Signed Bundle / APK` → `APK`
4. Choisir un keystore (ou en créer un nouveau)
5. Sélectionner `release`
6. L'APK se trouve dans `app/release/app-release.apk`

---

## 4. Accès caché Super Admin

Dans l'APK, appuyer **3 fois en long** sur le coin supérieur droit de l'écran principal.

Affiche :
- URL serveur actuelle (depuis cache)
- URL du fichier JSON GitHub
- Bouton "Rafraîchir URL"

---

## 5. Permissions Android requises

| Permission | Usage |
|---|---|
| INTERNET | Connexion serveur |
| CAMERA | Scan QR Code |
| ACCESS_FINE_LOCATION | GPS présence |
| READ/WRITE_EXTERNAL_STORAGE | Upload fichiers |
| POST_NOTIFICATIONS | Notifications |

---

## 6. Vérification serveur PHP

Tester que le serveur répond correctement depuis l'APK :
```
GET https://VOTRE-LIEN.ngrok-free.dev/api/ping.php
```

Réponse attendue :
```json
{"status":"ok","server":"UPN Suivi Présence","time":"...","version":"1.0.0"}
```

---

## 7. Structure des fichiers créés

```
android_apk/
├── app/
│   ├── src/main/
│   │   ├── java/cd/upn/suivipresence/
│   │   │   ├── SplashActivity.java   ← lit JSON GitHub
│   │   │   └── MainActivity.java     ← WebView principal
│   │   ├── res/
│   │   │   ├── layout/activity_splash.xml
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml
│   │   │   ├── values/colors.xml
│   │   │   ├── values/themes.xml
│   │   │   └── xml/network_security_config.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
├── gradle.properties
└── github_pages/
    └── api-server.json   ← à uploader sur GitHub Pages

Projet web (modifié) :
└── config/config.php     ← header ngrok ajouté
└── api/ping.php          ← endpoint santé (nouveau)
```
