"""Test di contratto: migrazione additiva 4.0.1 -> 4.0.2 e ciclo della foto."""
import sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/officinastock/app/StockDb.kt').read_text(encoding='utf-8')
photo=(root/'app/src/main/java/com/officinastock/app/PhotoFiles.kt').read_text(encoding='utf-8')
ui=(root/'app/src/main/java/com/officinastock/app/ArticlePhotoField.kt').read_text(encoding='utf-8')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'null, 10)' in source, 'Versione DB non aggiornata'
assert 'ALTER TABLE items ADD COLUMN photo_file TEXT DEFAULT NULL' in source
assert 'photo_file TEXT DEFAULT NULL' in source
assert 'fun updatePhoto(itemId:' in source
assert 'PhotoFiles.removeCapturedTemp' in (root/'app/src/main/java/com/officinastock/app/StockViewModel.kt').read_text(encoding='utf-8')
assert 'androidx.core.content.FileProvider' in manifest
assert '<cache-path name="camera_capture" path="article_camera/" />' in (root/'app/src/main/res/xml/photo_paths.xml').read_text(encoding='utf-8')
assert 'onApply?.invoke(pending)' in ui
assert 'if(ok && uri!=null) stage(uri)' in ui
assert 'if(uri!=null) stage(uri.toString())' in ui
assert 'Elimina foto' in ui and 'Rifai foto' in ui
assert 'filesDir' in photo and 'article_photos' in photo
assert 'private val storedName' in photo
assert 'Bitmap.CompressFormat.JPEG,85' in photo

con=sqlite3.connect(':memory:')
con.execute('CREATE TABLE items (id INTEGER PRIMARY KEY, sku TEXT UNIQUE, name TEXT NOT NULL, quantity REAL NOT NULL)')
con.execute("INSERT INTO items (id,sku,name,quantity) VALUES (7,'RIC-000001','Cuscinetto',12)")
con.execute('ALTER TABLE items ADD COLUMN photo_file TEXT DEFAULT NULL')
assert con.execute('SELECT name,quantity,photo_file FROM items WHERE id=7').fetchone()==('Cuscinetto',12,None)
con.execute("UPDATE items SET photo_file='item_f0d41e1f-4dc5-4b84-9747-3d6468480f1f.jpg' WHERE id=7")
assert con.execute('SELECT sku,quantity,photo_file FROM items WHERE id=7').fetchone()[1]==12
con.execute('UPDATE items SET photo_file=NULL WHERE id=7')
assert con.execute('SELECT sku,name,quantity,photo_file FROM items WHERE id=7').fetchone()==('RIC-000001','Cuscinetto',12,None)
print('OK: SQLite v3/v4 additive schema + photo replacement/deletion preserve item')
print('OK: camera/gallery retry and FileProvider contract')
