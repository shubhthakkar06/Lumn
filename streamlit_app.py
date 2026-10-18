"""Lumn's small update-service landing page.

The Android app reads /app/static/update.json from this deployment. APKs are
distributed as GitHub Release assets so Streamlit only serves small metadata.
"""

import json
from pathlib import Path

import streamlit as st


st.set_page_config(page_title="Lumn Updates", page_icon="🎵", layout="centered")
manifest_path = Path(__file__).parent / "static" / "update.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

st.title("Lumn updates")
st.caption("Release information for the Lumn Android app.")
st.subheader(f"Latest version · {manifest['versionName']}")
st.write(manifest.get("releaseNotes", "Performance and stability improvements."))
st.link_button("Download Lumn for Android", manifest["apkUrl"], type="primary")
st.info(
    "For an update to become mandatory, publish the new APK as a GitHub Release, "
    "then set minimumVersionCode in static/update.json to that release's version code."
)
st.caption("The in-app update prompt requires Android to approve installation of the downloaded APK.")
