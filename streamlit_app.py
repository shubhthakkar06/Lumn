"""Rivara's small update-service landing page.

The Android app reads /app/static/update.json from this deployment. APKs are
distributed as GitHub Release assets so Streamlit only serves small metadata.
"""

import json
from pathlib import Path

import streamlit as st


st.set_page_config(page_title="Rivara Updates", page_icon="🎵", layout="centered")
manifest_path = Path(__file__).parent / "static" / "update.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

st.image("static/rivara_logo.png", width=104)
st.title("Rivara updates")
st.caption("Release information for the Rivara Android app.")
st.subheader(f"Latest version · {manifest['versionName']}")
st.write(manifest.get("releaseNotes", "Performance and stability improvements."))
st.link_button("Download Rivara for Android", manifest["apkUrl"], type="primary")
st.info(
    "Updates are optional. Dismiss the in-app prompt to continue using Rivara; the app "
    "checks for a newer release the next time you open it."
)
st.caption("The in-app update prompt requires Android to approve installation of the downloaded APK.")

# Shared web URLs remain useful when Rivara is not installed; the button invokes
# the app's custom scheme on Android and carries the original song metadata.
shared_song = st.query_params.get("song")
if shared_song:
    song_title = st.query_params.get("title", "Shared song")
    artist = st.query_params.get("artist", "Unknown artist")
    artwork = st.query_params.get("art", "")
    from urllib.parse import quote
    app_url = (
        f"rivara://song/{quote(shared_song, safe='')}"
        f"?title={quote(song_title, safe='')}&artist={quote(artist, safe='')}&art={quote(artwork, safe='')}"
    )
    st.divider()
    st.subheader(song_title)
    st.write(f"by {artist}")
    if artwork.startswith("https://"):
        st.image(artwork, width=180)
    st.link_button("Open in Rivara", app_url, type="primary")
