# StreamBox

Android IPTV / M3U player with a MAC address + device key, plus a playlist portal website.

## 1. Run the portal (portal/server.js)
Needs Node.js 18+ on a VPS, ideally behind HTTPS (Caddy or nginx).

    BASE_URL=https://yourdomain.com node server.js

## 2. Build the APK (no PC needed)
1. Create a GitHub repository and upload this whole folder (keep .github/workflows/build.yml).
2. Open the Actions tab, run "Build APK", then download the StreamBox-apk artifact.
3. Install the APK on your phone / Android TV box.

## 3. Use it
1. Open the app, enter your server address in Settings. The app shows its MAC address and device key.
2. On your phone open the portal website, log in with that MAC and key, add M3U URLs or paste playlists.
3. In the app tap "Reload playlists". Channels appear under Live TV, Movies and Series.
