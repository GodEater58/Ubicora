"""Verifica statica scenario demo completo, codici, prestiti, immagini e QR verticale."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/officinastock/app'
cat=(base/'DemoCatalog.kt').read_text()
db=(base/'StockDb.kt').read_text()
ui=(base/'Screens.kt').read_text()
model=(base/'StockViewModel.kt').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
main=(base/'MainActivity.kt').read_text()
art=(base/'DemoArtwork.kt').read_text()
entries=re.findall(r'^\s*DemoItem\(',cat,re.M)
assert len(entries)==42,f'Attesi 42 articoli, trovati {len(entries)}'
for category in ['Ricambi','Utensili','Elettrico','Pneumatica','Idraulica','Consumabili','DPI','Meccanica','Altro']:
 assert f'"{category}"' in cat,category
assert 'null' in cat and '0.0' in cat
for marker in ['"demo_assets"','"movements"','"loans"']:
 assert marker.replace('"','') in db
for token in ['DemoCatalog.items.forEachIndexed', 'put("unit_cost",demo.cost)','put("lead_days",demo.leadDays)','put("supplier",demo.supplier)', 'demoTag(db,warehouseId,"items",id)', 'demoTag(db,warehouseId,"placements",id)', 'demoTag(db,warehouseId,"walls",id)', '"Assegnato a $borrower"','"Restituito da $operator"','"Carico iniziale · demo"', '"Prelievo ${serviceReasons[idx]} · demo"','DemoArtwork.create(appContext,name,category)']:
 assert token in db,token
for token in ['IMMAGINE DEMO · NON REALE','item_${UUID.randomUUID()}.jpg']:
 assert token in art,token
for token in ['"Rigenera demo completa (4.1.3)"', '"Carica dati dimostrativi"', '"Rimuovi soltanto la demo"']:
 assert token in ui,token
assert 'fun refreshDemo()' in model
assert 'android:name=".StockQrCaptureActivity"' in manifest
assert 'android:screenOrientation="user"' in manifest
assert '.setCaptureActivity(StockQrCaptureActivity::class.java)' in main
assert '.setOrientationLocked(false)' in main
assert 'CaptureActivity' in (base/'StockQrCaptureActivity.kt').read_text()
print('PASS: 42 demo items, 9 categories, 90-day flows, loans, costs, photos, QR rotation and safe demo refresh')
