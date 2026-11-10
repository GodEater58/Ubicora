import re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
# 4.15: the screens are split in several files.
screens=''.join((ROOT/'app/src/main/java/com/officinastock/app'/f).read_text(encoding='utf-8') for f in ['Screens.kt','ProfileScreens.kt','InventoryScreen.kt','ItemScreens.kt','MovementsScreen.kt','SettingsScreen.kt'])
labels=(ROOT/'app/src/main/java/com/officinastock/app/QrLabels.kt').read_text(encoding='utf-8')
backup=(ROOT/'app/src/main/java/com/officinastock/app/BackupArchive.kt').read_text(encoding='utf-8')
gradle=(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'android:label="@string/app_name"' in manifest
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert '<string name="app_name">Ubicora</string>' in (ROOT/'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
assert '<adaptive-icon' in (ROOT/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').read_text(encoding='utf-8')
assert (ROOT/'app/src/main/res/drawable-nodpi/ic_ubicora_u_selected.png').exists()
assert 'android:drawable="@drawable/ic_ubicora_u_selected"' in (ROOT/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').read_text(encoding='utf-8')
assert 'painterResource(R.drawable.ic_ubicora_u_selected)' in screens
assert 'UbicoraWordmark' in screens and 'OFFICINA STOCK 4.0' not in screens
assert 'Text("Ubicora"' in screens
assert '"Ubicora",101f' in labels
assert (match := re.search(r'\bversionCode\s*=\s*(\d+)', gradle)) and int(match.group(1)) >= 420
assert 'applicationId = "com.ubicora.app"' in gradle
assert 'put("app","OfficinaStock")' in backup
assert 'manifest.optString("app")=="OfficinaStock"' in backup
print('Brand identity + backward compatibility OK')
