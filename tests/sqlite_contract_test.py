"""Convalida parte DDL SQLite senza runtime Android. Non sostituisce test strumentati."""
from pathlib import Path
import sqlite3, re

src=(Path(__file__).resolve().parents[1]/"app/src/main/java/com/officinastock/app/StockDb.kt").read_text()
multis=re.findall(r'db\.execSQL\("""(.*?)"""\)',src,re.S)
single=re.findall(r'db\.execSQL\("(CREATE INDEX[^"\\]+)"\)',src.split("override fun onCreate")[1].split("override fun onConfigure")[0])
con=sqlite3.connect(":memory:")
con.execute("PRAGMA foreign_keys=ON")
for ddl in multis+single:
    con.execute(ddl)
assert len(multis)==9, len(multis)
assert len(single)==5, len(single)
con.execute("INSERT INTO sku_sequences(prefix,next_value) VALUES('RIC',1)")
try:
    con.execute("INSERT INTO sku_sequences(prefix,next_value) VALUES('RIC',2)")
    raise AssertionError('Numerazione categoria duplicata')
except sqlite3.IntegrityError:
    pass
con.execute("INSERT INTO profiles (id,name,salt,verifier,created_at) VALUES(1,'Test','x','y',1)")
con.execute("INSERT INTO warehouses (id,profile_id,name,location) VALUES(1,1,'Officina','Casa')")
con.execute("INSERT INTO items (warehouse_id,sku,name,category,quantity,minimum,unit) VALUES(1,'M8','Vite M8','Meccanica',5,1,'pz')")
try:
    con.execute("INSERT INTO items (warehouse_id,sku,name,category,quantity,minimum,unit) VALUES(1,'M8','Duplicato','Meccanica',1,0,'pz')")
    raise AssertionError('Codice duplicato accettato')
except sqlite3.IntegrityError:
    pass
try:
    con.execute("INSERT INTO items (warehouse_id,sku,name,category,quantity,minimum,unit) VALUES(1,'BAD','Negativo','Altro',-1,0,'pz')")
    raise AssertionError('Quantita negativa accettata')
except sqlite3.IntegrityError:
    pass
con.execute("INSERT INTO placements (id,warehouse_id,kind,label,x,y) VALUES(1,1,'Scaffale','A',40,40)")
con.execute("UPDATE items SET placement_id=1 WHERE sku='M8'")
con.execute("DELETE FROM placements WHERE id=1")
assert con.execute("SELECT placement_id FROM items WHERE sku='M8'").fetchone()[0] is None
con.execute("INSERT INTO loans(item_id,borrower,quantity,created_at) VALUES(1,'Operatore',1,2)")
try:
    con.execute("INSERT INTO loans(item_id,borrower,quantity,created_at) VALUES(1,'Operatore',-1,2)")
    raise AssertionError('Prestito negativo accettato')
except sqlite3.IntegrityError:
    pass
con.execute("DELETE FROM profiles WHERE id=1")
assert con.execute("SELECT count(*) FROM items").fetchone()[0]==0
print('SQLite: 9 tabelle, 5 indici e 5 controlli integrità OK')
