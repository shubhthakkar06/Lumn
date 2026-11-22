# Cloud library setup (one time)

Rivara keeps each Google account's library (liked songs, playlists, followed artists and
shows, profile photo) in Firebase Realtime Database, so phone, Mac and watch stay in sync live.

1. Firebase console → project **lumn-24ba5** → **Build → Realtime Database → Create database**.
   Choose the **United States (us-central1)** location (the apps use
   `https://lumn-24ba5-default-rtdb.firebaseio.com`). Start in **locked mode**.
2. Realtime Database → **Rules** → paste `database.rules.json` from this folder → **Publish**.
3. **Authentication → Sign-in method**: make sure **Google** is enabled, and enable
   **Anonymous** (used only by the Mac app to link itself to your phone's account).

If you pick another location, either download the updated `google-services.json` into `app/`
(it then contains the database URL), or put `databaseUrl=<your url>` in
`~/.rivara/firebase.properties` on the Mac.

Rules summary: only the signed-in owner (or a computer the owner linked) can read or write
`users/<uid>/data`; only the owner can grant or revoke linked computers.
