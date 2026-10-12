#!/usr/bin/env python3
"""Backup da una vecchia APK debug senza reinstallarla o cambiare firma.

Requisiti: Android SDK Platform Tools (adb), debug USB attivo, telefono sbloccato.
Eseguire PRIMA di disinstallare l'APK esistente.
Non invia dati a Internet.
"""
import argparse
import datetime
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "it.pulse.workout"
NAMESPACES = ("pulse_v1", "buttigogym_wellness_v1", "pulse_plan")


def read_prefs(namespace):
    command = ["adb", "exec-out", "run-as", PACKAGE,
               "cat", "shared_prefs/" + namespace + ".xml"]
    run = subprocess.run(command, capture_output=True)
    if run.returncode != 0:
        message = run.stderr.decode("utf-8", "replace").strip()
        if "No such file" in message or "not found" in message:
            return {}
        raise RuntimeError(f"Impossibile leggere {namespace}: {message}")
    root = ET.fromstring(run.stdout)
    if root.tag != "map":
        raise ValueError("Formato SharedPreferences inatteso")
    values = {}
    for item in root:
        if item.tag == "string" and item.get("name"):
            values[item.attrib["name"]] = item.text or ""
    return values


def main():
    parser = argparse.ArgumentParser(description="Salva i progressi prima del cambio di firma APK")
    parser.add_argument("--output", default="ButtiGoGym-backup-migrazione.json")
    args = parser.parse_args()
    if not shutil.which("adb"):
        parser.error("adb non trovato. Installa Android SDK Platform Tools")
    devices = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
    connected = [line for line in devices.stdout.splitlines()[1:] if line.endswith("\tdevice")]
    if len(connected) != 1:
        parser.error("Collega e autorizza esattamente un telefono tramite USB debugging")
    data = {namespace: read_prefs(namespace) for namespace in NAMESPACES}
    if not data["pulse_v1"]:
        print("Attenzione: nessuno storico trovato in pulse_v1")
    payload = {
        "format": "ButtiGoGymBackup",
        "schemaVersion": 1,
        "package": PACKAGE,
        "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "data": data
    }
    output = Path(args.output).resolve()
    if output.exists():
        parser.error(f"Il file {output} esiste già: scegli un percorso diverso")
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Backup locale salvato:", output)
    print("NON disinstallare se il file è vuoto o il backup non ti sembra corretto.")
    print("Dopo avere installato un APK con firma nuova, apri Oggi > I tuoi dati > Ripristina un backup.")


if __name__ == "__main__":
    main()
