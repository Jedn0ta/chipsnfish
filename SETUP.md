# Online setup (Supabase)

1. supabase.com → **New project** → name `ChipsNFish`, region **Central EU (Frankfurt)**, generate a database password and keep it.
2. **Authentication → Sign In / Providers** → turn on **Allow anonymous sign-ins** → Save.
3. **SQL Editor → New query** → paste the whole [`supabase/schema.sql`](supabase/schema.sql) → **Run**.
   Run it again whenever this file changes (it is safe to run repeatedly).
4. **Project Settings → API** (or the **Connect** button) → copy the **Project URL** and the
   **anon / publishable key** into `app/src/main/java/com/onoseto/chipsnfish/online/Supa.kt` (`SupaCfg`).

## Invite links

Upload the content of [`web/`](web/) to the root of **onoseto.com**:

- `web/.well-known/assetlinks.json` → `https://onoseto.com/.well-known/assetlinks.json`
- `web/cf/index.html` → `https://onoseto.com/cf/index.html`

Then `https://onoseto.com/cf/?t=CODE` opens the table straight in the app.
The fingerprint in `assetlinks.json` belongs to the test key in `app/keystore/`; update it when
switching to the Google Play signing key.

## Keep-alive

The app pings the server every two days in the background (`cf_ping`), so the free
Supabase project is not paused for inactivity. After 5 days without opening the app, a reminder
notification appears.
