# Site Blocker (Android)

Paste a link, and that website is blocked in every browser on your phone.

## How it works
The app starts a local VPN that never leaves your phone. Only DNS lookups pass through it.
If a site is on your list, the lookup fails ("site does not exist"). Everything else is untouched.

## Get the APK without Android Studio
1. Create a free account at github.com.
2. Click **New repository**, name it `site-blocker`, keep it public or private, and create it.
3. Click **uploading an existing file** and drag in everything from this folder
   (make sure the `.github` folder is included; if it isn't, use **Add file > Create new file**,
   type `.github/workflows/build.yml` as the name and paste the contents of that file).
4. Commit. Open the **Actions** tab: "Build APK" starts by itself (takes about 5 minutes).
5. When it is green, open the run and download **SiteBlocker-apk** under *Artifacts*.
6. Unzip it, copy `app-debug.apk` to your phone and open it.
   Allow "Install unknown apps" for your file manager or browser when asked.

## Use
1. Open **Site Blocker**, paste a link, tap **Block this site**.
2. Switch **Blocking enabled** on and accept the VPN request.
3. Open the blocked site in your browser: it will not load.

## Limits
- Android only.
- Turn off "Use secure DNS" in Chrome (and DNS over HTTPS in Firefox), or it can bypass the blocker.
- Blocking the domain also blocks all its subdomains.
- Anyone with access to the phone can switch it off, so it is not a parental-control lock.
