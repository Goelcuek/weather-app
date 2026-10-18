# Hava

Android weather app with home-screen widgets styled after Honor / MagicOS (rounded 26dp cards, dynamic sky gradients, dark and light variants).

- **Data:** [Open-Meteo](https://open-meteo.com) (free, no API key)
- **Widgets:** Mini 2×2, Strip 4×1 (now + 4 hours), Hourly 4×2 (6 hours + temperature curve), Large 4×4 (hourly curve + 5-day)
- **Widget styles:** Dynamic (changes with the weather), Dark, Light — switch in the app's settings
- **Languages:** Turkish and English (follows phone language)

## Download

Every push to `main` builds a signed APK via GitHub Actions and publishes it under **Releases**. New builds install over old ones.

## Honor tip

Settings → Battery → App launch → Hava → Manage manually → allow auto-launch and background activity, so widgets keep refreshing.
