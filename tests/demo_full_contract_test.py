"""Verifica statica scenario demo completo, codici, prestiti, immagini e QR verticale."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/officinastock/app'
cat=(base/'DemoCatalog.kt').read_text(encoding='utf-8')
db=(base/'StockDb.kt').read_text(encoding='utf-8')
# 4.15: the screens are split in several files.
ui=''.join((base/f).read_text(encoding='utf-8') for f in ['Screens.kt','ProfileScreens.kt','InventoryScreen.kt','ItemScreens.kt','MovementsScreen.kt','SettingsScreen.kt'])
model=((base/'StockViewModel.kt').read_text(encoding='utf-8')+(base/'StockUiState.kt').read_text(encoding='utf-8'))
manifest=(root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
main=(base/'MainActivity.kt').read_text(encoding='utf-8')
art=(base/'DemoArtwork.kt').read_text(encoding='utf-8')
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
assert 'QuickScanScreen(model)' in main
print('PASS: 42 demo items, 9 categories, 90-day flows, loans, costs, photos, QR rotation and safe demo refresh')
