# ButtiGoGym 2.0

- Fixed black background and white controls, independent of previously saved appearance.
- No avatar, TTS, mascot assets or mascot launcher. Existing user progress/workout photos remain intact. Only obsolete imported `coach-poses` folders are removed.
- Offline library: 293 distinct exercises, including 36 Panatta models. Search by name/model, muscle, equipment and Panatta brand. This is a selectable library, not a claim about a particular gym's inventory.
- In Scheda → Personalizza: use + to add from the library; swap icon to replace; trash to remove; manual exercise for an unlisted machine. Edits are drafts until saved. Existing sessions keep their snapshot.
- New variants/machines use distinct exercise IDs. Load history and recommendations are never copied from a different machine. In-session alternatives include matching library movements and Panatta models; recorded exercises cannot be replaced.

Panatta model names and identifiers checked against the official product list:
https://www.panattasport.com/it/free-weight-special/super-high-row/
https://www.panattasport.com/en/free-weight-special/super-high-row/
https://www.panattasport.com/download/panatta-general-catalogue.pdf
Names identify equipment; no product descriptions or product photos are redistributed.

## Send workout history

Storico → Invia storico opens the public-destination notice and preview. No network write occurs on opening it. It exports all saved sessions and raw set entries, including warmups and orphan entries; date/time, duration, exercise names, equipment, notes and RIR. Photos, body measurements, the active draft and credentials are excluded.

First connect GitHub using a fine-grained PAT restricted to ButtiGit/ButtiGoGym with **Issues: Read and write** (no Contents write permission). The token is encrypted with Android Keystore AES-GCM, never placed in saved-instance-state, logs, payloads or backups. Disconnect removes the locally stored credential. Native Android backup excludes `github_history_token.xml`; custom backups use their pre-existing allowlist.

Explicitly tapping **Invia storico su GitHub pubblico** publishes an issue in this **public** repository. JSON is split into ordered Base64 comments; Base64 is encoding, not privacy protection. A manifest records the SHA-256 and expected part count. The final `complete` marker is posted only after every part has been accepted. A retry with identical data reuses the same issue, validates existing part contents and skips received parts. Changed history creates a new full report. Failed uploads remain visible as incomplete; they are not reported as completed.

To analyze a completed report:

```sh
python tools/read_history_report.py ISSUE_NUMBER --output workout-history.json
```

The reader fetches the public issue, requires the completion marker, validates part count and archive hash, then produces JSON. Timestamps are Unix milliseconds. `metadataSource` distinguishes original snapshots from current-plan fallback and unavailable metadata. Raw entries remain exportable even without exercise metadata.

Tests use a fake transport and synthetic sessions; no test sends private workout data or uses a live token.
