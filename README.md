# Hold'em Chips&Fish — Android

Native Android app (Kotlin + Jetpack Compose) of **Hold'em Chips&Fish** by Onoseto Studio:
a Texas Hold'em game against the computer and a poker advisor for live games with friends.

Ported from the original web app: game vs. computer (1–9 bots with different styles), the live
advisor mode (bets, equity, outs, bluff reads, concrete action advice), player profiles with
stats (VPIP / PFR / AF, bluffs caught, moods), hand history, blinds timer, chip set, table look,
three languages (CZ / EN / DE) and data backup. Backups from the web version can be restored here.

## Download

Every push to `main` is built by GitHub Actions. The newest APK is on the
[**latest release**](../../releases/tag/latest) as `ChipsNFish.apk`.

The APK is signed with a shared test key so new builds install over old ones.
Use a private release key before publishing on Google Play.
