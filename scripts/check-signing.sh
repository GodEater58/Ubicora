#!/usr/bin/env bash
set -euo pipefail
# Audit only: NEVER replace a stored release key with a CI-generated debug key.
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
reference="${2:-audit-evidence/baseline-certificate.txt}"
report="${3:-app/build/reports/signing-compatibility.txt}"
mkdir -p "$(dirname "$report")"
signer="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/apksigner"
if [[ ! -x "$signer" ]]; then
  echo "apksigner 35.0.0 missing; cannot certify APK." >&2
  exit 2
fi
test -s "$apk"
test -s "$reference"
"$signer" verify --print-certs "$apk" > "${report}.verify"
current="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "${report}.verify" | head -n1)"
baseline="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$reference" | head -n1)"
if [[ -z "$current" || -z "$baseline" ]]; then
  echo "Unable to read signing fingerprints" >&2
  exit 2
fi
{
  echo "PACKAGE: $apk"
  echo "CI APK SHA-256: $current"
  echo "ARCHIVED REFERENCE SHA-256: $baseline"
  if [[ "$current" == "$baseline" ]]; then
    echo "REFERENCE MATCH: yes"
    echo "NOTE: reference match alone does not establish the signature of the user's installed APK."
  else
    echo "REFERENCE MATCH: NO"
    echo "BLOCKER: This APK cannot update the archived reference APK in place."
  fi
  echo "RELEASE KEY: not available in CI; release signing must use a stable private keystore."
  echo "NEXT STEP: compare with SHA-256 from the actual installed device APK, without uninstalling."
} | tee "$report"
