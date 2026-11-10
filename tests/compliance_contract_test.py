import re
from pathlib import Path
p=Path(__file__).resolve().parents[1]
def load(path):return (p/path).read_text(encoding='utf8')
assert 'Version 2.0, January 2004' in load('LICENSES/Apache-2.0.txt')
assert 'END OF TERMS AND CONDITIONS' in load('app/src/main/assets/legal/Apache-2.0.txt')
assert 'Google ARCore SDK' in load('app/src/main/assets/legal/third_party_notices.txt')
assert 'ARCore Additional Terms of Service' in load('app/src/main/assets/legal/third_party_notices.txt')
assert 'ArPrivacyNoticeDialog(' in load('app/src/main/java/com/officinastock/app/MainActivity.kt')
assert 'arcore_notice_ack_v1' in load('app/src/main/java/com/officinastock/app/MainActivity.kt')
assert 'if(disclosurePrefs.getBoolean' in load('app/src/main/java/com/officinastock/app/MainActivity.kt')
assert 'onProceed' in load('app/src/main/java/com/officinastock/app/LegalInfo.kt')
assert 'PRIVACY E LICENZE' in load('app/src/main/java/com/officinastock/app/SettingsScreen.kt')
assert 'LegalInfoDialog(' in load('app/src/main/java/com/officinastock/app/SettingsScreen.kt')
assert 'legal/Apache-2.0.txt' in load('app/src/main/java/com/officinastock/app/LegalInfo.kt')
assert (match := re.search(r'\bversionCode\s*=\s*(\d+)', load('app/build.gradle.kts'))) and int(match.group(1)) >= 420
assert 'android:value="optional"' in load('app/src/main/AndroidManifest.xml')
# Future HTTPS sync is opt-in; permission is declared but no automatic outbound connection exists.
assert 'android.permission.INTERNET' in load('app/src/main/AndroidManifest.xml')
assert 'HttpsURLConnection' in load('app/src/main/java/com/officinastock/app/EnterpriseServerClient.kt')
assert 'Server non configurato' in load('app/src/main/java/com/officinastock/app/EnterpriseScreen.kt')
assert '[EMAIL DA COMPLETARE]' in load('docs/PRIVACY_POLICY_DRAFT_IT.md')
assert 'Report automatico CI' in load('docs/COMMERCIAL_RELEASE_CHECKLIST.md')
print('Compliance: attribution assets, AR first-use disclosure, privacy UI, draft and CI checks: PASS')
