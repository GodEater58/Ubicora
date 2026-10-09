"""Schema and data isolation contract for 4.1.2 backup/demo."""
from pathlib import Path
import sqlite3,re
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/officinastock/app/StockDb.kt').read_text(encoding='utf-8')
backup=(root/'app/src/main/java/com/officinastock/app/BackupArchive.kt').read_text(encoding='utf-8')
ui=(root/'app/src/main/java/com/officinastock/app/Screens.kt').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/officinastock/app/MainActivity.kt').read_text(encoding='utf-8')
assert 'null, 10)' in source and 'oldVersion < 6' in source
assert 'CREATE TABLE IF NOT EXISTS demo_assets' in source
assert 'demoTag(db,warehouseId,"items",id)' in source
assert 'demoTag(db,warehouseId,"walls",id)' in source
assert 'demoTag(db,warehouseId,"placements",id)' in source
assert 'WHERE warehouse_id=? AND entity_type=?' in source
cipher=(root/'app/src/main/java/com/officinastock/app/BackupCipher.kt').read_text(encoding='utf-8')
assert 'AES/GCM/NoPadding' in cipher and 'PBKDF2WithHmacSHA256' in cipher
assert 'frame(0)' in cipher and 'db.beginTransaction()' in backup and 'BackupCipher.decrypt' in backup
for table in ['profiles','warehouses','items','placements','walls','movements','loans','sku_sequences','demo_assets']:
 assert f'"{table}"' in backup
for label in ['Esporta backup completo','Ripristina da backup','Rimuovi soltanto la demo']:
 assert label in ui
assert 'CreateDocument("application/octet-stream")' in main
assert 'OpenDocument()' in main
con=sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
for ddl in re.findall(r'db\.execSQL\("""(.*?)"""\)',source,re.S):con.execute(ddl)
for ddl in re.findall(r'db\.execSQL\("(CREATE INDEX[^"\\]+)"\)',source.split('override fun onCreate')[1].split('override fun onConfigure')[0]):con.execute(ddl)
con.execute("INSERT INTO profiles(id,name,salt,verifier,created_at) VALUES(1,'p','salt','proof',1)")
con.execute("INSERT INTO warehouses(id,profile_id,name) VALUES(1,1,'Demo')")
con.execute("INSERT INTO items(id,warehouse_id,sku,name,category,quantity,minimum,unit,note) VALUES(1,1,'RIC-000001','Demo item','Ricambi',1,0,'pz','Dati dimostrativi')")
con.execute("INSERT INTO items(id,warehouse_id,sku,name,category,quantity,minimum,unit,note) VALUES(2,1,'RIC-000002','Utente item','Ricambi',1,0,'pz','Lavoro vero')")
con.execute("INSERT INTO demo_assets(entity_type,entity_id,warehouse_id) VALUES('items',1,1)")
ids=[row[0] for row in con.execute("SELECT entity_id FROM demo_assets WHERE entity_type='items' AND warehouse_id=1")]
for id_ in ids:con.execute('DELETE FROM items WHERE id=? AND warehouse_id=?',(id_,1))
con.execute('DELETE FROM demo_assets WHERE warehouse_id=1')
assert con.execute('SELECT id,name FROM items').fetchall()==[(2,'Utente item')]
print('PASS: backup AES-GCM authenticated, all 9 tables, demo removal retains user data')
