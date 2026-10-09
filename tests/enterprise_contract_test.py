"""Enterprise 4.4 compatibility contract; runs before slow Android Gradle build."""
from pathlib import Path
import sqlite3
import re

root=Path(__file__).resolve().parents[1]
base=(root/'app/src/main/java/com/officinastock/app/StockDb.kt').read_text(encoding='utf8')
ops=(root/'app/src/main/java/com/officinastock/app/OperationsDb.kt').read_text(encoding='utf8')
org=(root/'app/src/main/java/com/officinastock/app/EnterpriseDb.kt').read_text(encoding='utf8')
backup=(root/'app/src/main/java/com/officinastock/app/BackupArchive.kt').read_text(encoding='utf8')
view=(root/'app/src/main/java/com/officinastock/app/StockViewModel.kt').read_text(encoding='utf8')
screen=(root/'app/src/main/java/com/officinastock/app/EnterpriseScreen.kt').read_text(encoding='utf8')
transport=(root/'app/src/main/java/com/officinastock/app/EnterpriseServerClient.kt').read_text(encoding='utf8')
gradle=(root/'app/build.gradle.kts').read_text(encoding='utf8')
assert 'null, 8)' in base and 'oldVersion < 8' in base
assert 'EnterpriseDb.createTables(db)' in base and 'EnterpriseDb.addOperationLinks(db)' in base
assert 'versionCode = 440' in gradle
assert 'managerTables.size' in backup and 'legacyTables.size' in backup and 'companyTables' in backup
assert 'EnterpriseDirectoryPanel(model)' in (root/'app/src/main/java/com/officinastock/app/OperationsScreen.kt').read_text(encoding='utf8')
assert 'supplier_id' in ops and 'center_id' in ops
assert 'receiveOrderQuantity' in ops and 'changeStock(db,order[0].toLong(),delivered' in ops
assert 'businessSuppliers=' in view and 'costCenters=' in view
assert 'Server non configurato' in screen
assert 'HttpsURLConnection' in transport and 'instanceFollowRedirects=false' in transport
assert 'EnterpriseSyncProtocol.batch' in transport
assert (root/'server/openapi.yaml').exists()
assert (root/'server/postgresql_schema.sql').exists()
db=sqlite3.connect(':memory:')
db.execute("PRAGMA foreign_keys=ON")
def tables(source):
    return re.findall(r'db\.execSQL\("""(CREATE TABLE .*?)"""\)',source,re.S)
assert len(tables(base))==9 and len(tables(ops))==6 and len(tables(org))==5
for ddl in tables(base)+tables(ops)+tables(org):
    db.execute(ddl)
for line in re.findall(r'db\.execSQL\("(CREATE INDEX[^"\\]+)"\)',base):
    db.execute(line)
for statement in (
    'ALTER TABLE ops_orders ADD COLUMN center_id INTEGER REFERENCES org_centers(id) ON DELETE SET NULL',
    'ALTER TABLE ops_orders ADD COLUMN supplier_id INTEGER REFERENCES org_suppliers(id) ON DELETE SET NULL',
    'ALTER TABLE ops_maintenance ADD COLUMN asset_id INTEGER REFERENCES org_assets(id) ON DELETE SET NULL'):
    db.execute(statement)
count=db.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchone()[0]
assert count==20,count
db.execute("INSERT INTO profiles(id,name,salt,verifier,created_at) VALUES(1,'work','salt','hash',0)")
db.execute("INSERT INTO profiles(id,name,salt,verifier,created_at) VALUES(2,'personal','salt','hash',0)")
db.execute("INSERT INTO warehouses(id,profile_id,name,location) VALUES(1,1,'A','')")
db.execute("INSERT INTO items(id,warehouse_id,sku,name,category,quantity,minimum,unit) VALUES(1,1,'RIC-1','Cuscinetto','Ricambi',2,0,'pz')")
db.execute("INSERT INTO org_centers(profile_id,uuid,code,name,created_at) VALUES(1,'uuid-center','MNT','Manutenzione',0)")
db.execute("INSERT INTO org_suppliers(profile_id,uuid,name,created_at) VALUES(1,'uuid-supplier','Fornecedor',0)")
db.execute("INSERT INTO ops_orders(warehouse_id,item_id,quantity,received,supplier,owner,created_at,center_id,supplier_id) VALUES(1,1,3,0,'Fornecedor','Operator',0,1,1)")
db.execute("INSERT INTO sync_outbox(id,profile_id,resource,entity_uuid,operation,payload,created_at) VALUES('uuid-event',1,'cost_center','uuid-center','CREATE','{}',0)")
try:
    db.execute("INSERT INTO org_centers(profile_id,uuid,code,name,created_at) VALUES(1,'other','MNT','Duplicate',0)")
    raise AssertionError("Duplicate cost center accepted")
except sqlite3.IntegrityError:
    pass
db.execute("DELETE FROM profiles WHERE id=1")
assert db.execute("SELECT count(*) FROM org_centers").fetchone()[0]==0
assert db.execute("SELECT count(*) FROM org_suppliers").fetchone()[0]==0
assert db.execute("SELECT count(*) FROM sync_outbox").fetchone()[0]==0
assert db.execute("SELECT count(*) FROM ops_orders").fetchone()[0]==0
print("PASS enterprise SQLite v8: 20 tables, links, unique scope, cascade, backup 9/15/20 and HTTPS adapter")
