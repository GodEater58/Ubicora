from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
screens=(ROOT/'app/src/main/java/com/officinastock/app/Screens.kt').read_text()
labels=(ROOT/'app/src/main/java/com/officinastock/app/QrLabels.kt').read_text()
backup=(ROOT/'app/src/main/java/com/officinastock/app/BackupArchive.kt').read_text()
gradle=(ROOT/'app/build.gradle.kts').read_text()
assert 'android:label="@string/app_name"' in manifest
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert '<string name="app_name">Ubicora</string>' in (ROOT/'app/src/main/res/values/strings.xml').read_text()
assert '<adaptive-icon' in (ROOT/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').read_text()
assert (ROOT/'app/src/main/res/drawable/ic_ubicora_foreground.xml').exists()
assert 'UbicoraWordmark' in screens and 'OFFICINA STOCK 4.0' not in screens
assert 'Text("Ubicora"' in screens
assert '"Ubicora",101f' in labels
assert 'versionCode = 415' in gradle
assert 'applicationId = "com.officinastock.app"' in gradle
assert 'put("app","OfficinaStock")' in backup
assert 'manifest.optString("app")=="OfficinaStock"' in backup
print('Brand identity + backward compatibility OK')
