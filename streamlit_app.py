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
