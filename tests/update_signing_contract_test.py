from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
signed = (root / ".github/workflows/ubicora-signed-update.yml").read_text(encoding="utf-8")
debug = (root / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
guide = (root / "docs/AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md").read_text(encoding="utf-8")
script = (root / "scripts/verify-update-on-device.ps1").read_text(encoding="utf-8")

assert 'applicationId = "com.ubicora.app"' in gradle
assert re.search(r"versionCode\s*=\s*[1-9][0-9]*", gradle)
assert 'signingConfigs.create("ubicoraStable")' in gradle
assert "UBICORA_RELEASE_STORE_FILE" in gradle
assert "UBICORA_RELEASE_STORE_PASSWORD" in gradle
assert "UBICORA_RELEASE_KEY_ALIAS" in gradle
assert "UBICORA_RELEASE_KEY_PASSWORD" in gradle
assert 'signingConfig = signingConfigs.getByName("ubicoraStable")' in gradle
assert 'signingConfig = signingConfigs.getByName("debug")' not in gradle

assert "workflow_dispatch:" in signed
assert "if: github.ref == 'refs/heads/main'" in signed
assert "secrets.UBICORA_KEYSTORE_BASE64" in signed
assert "secrets.UBICORA_STORE_PASSWORD" in signed
assert "secrets.UBICORA_KEY_ALIAS" in signed
assert "secrets.UBICORA_KEY_PASSWORD" in signed
assert "vars.UBICORA_SIGNING_CERT_SHA256" in signed
assert "expected_version_code" in signed
assert "assembleRelease" in signed
assert "apksigner" in signed and "verify --verbose --print-certs" in signed
assert 'versionCode=\'$EXPECTED_VERSION_CODE\'' in signed
assert "Signing certificate differs" in signed
assert "app/build/outputs/apk/release/app-release.apk" in signed
assert "name: ubicora-signed-update-code-" in signed
assert 'cancel-in-progress: false' in signed
assert 'UBICORA_KEYSTORE_BASE64' not in debug

assert "verify-update-on-device.ps1" in guide
assert "NON" in guide or "non" in guide
assert "apksigner verify --print-certs" in script
assert "exit 1" in script and "exit 0" in script
assert "adb pull" in script
assert "Remove-Item" in script
print("Permanent signing / no-uninstall update contract: PASS")
