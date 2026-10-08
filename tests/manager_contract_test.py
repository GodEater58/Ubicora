"""Tests SQLite aggregation and additive manager schema, no Android runtime."""
import sqlite3
from pathlib import Path
src=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/officinastock/app/StockDb.kt').read_text()
for column in ('unit_cost REAL DEFAULT NULL','supplier TEXT NOT NULL DEFAULT','lead_days INTEGER DEFAULT NULL','ALTER TABLE items ADD COLUMN unit_cost','ALTER TABLE items ADD COLUMN supplier','ALTER TABLE items ADD COLUMN lead_days'):
    assert column in src, column
db=sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE items(id INTEGER PRIMARY KEY,warehouse_id INTEGER NOT NULL,quantity REAL NOT NULL,unit_cost REAL DEFAULT NULL,supplier TEXT DEFAULT '',lead_days INTEGER DEFAULT NULL);
CREATE TABLE movements(id INTEGER PRIMARY KEY,item_id INTEGER NOT NULL,delta REAL NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL);
INSERT INTO items VALUES(1,4,8,2.50,'Fornitore',8);
INSERT INTO items VALUES(2,5,9,NULL,'',NULL);
INSERT INTO movements VALUES(1,1,-3,'Prelievo',200000000);
INSERT INTO movements VALUES(2,1,-5,'Assegnato a Persona',200000001);
INSERT INTO movements VALUES(3,1,5,'Restituito da Persona',200000002);
INSERT INTO movements VALUES(4,1,10,'Carico',200000003);
INSERT INTO movements VALUES(5,2,-4,'Prelievo',200000004);
""")
result=db.execute("""SELECT m.item_id,SUM(CASE WHEN m.created_at>=? THEN -m.delta ELSE 0 END),
SUM(CASE WHEN m.created_at>=? THEN -m.delta ELSE 0 END),SUM(-m.delta)
FROM movements m JOIN items i ON i.id=m.item_id
WHERE i.warehouse_id=? AND m.created_at>=? AND m.delta<0 AND m.note NOT LIKE 'Assegnato a %'
GROUP BY m.item_id""",(0,0,4,0)).fetchall()
assert result==[(1,3.0,3.0,3.0)],result
assert db.execute('SELECT quantity*unit_cost FROM items WHERE id=1').fetchone()[0]==20.0
assert db.execute('SELECT unit_cost FROM items WHERE id=2').fetchone()[0] is None
print('PASS: warehouse filter, no loans in consumption, no invented cost')
