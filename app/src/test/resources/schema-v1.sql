CREATE TABLE profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            salt TEXT NOT NULL, verifier TEXT NOT NULL,
            created_at INTEGER NOT NULL);
CREATE TABLE warehouses (
            id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
            name TEXT NOT NULL, location TEXT NOT NULL DEFAULT '');
CREATE TABLE placements (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            kind TEXT NOT NULL, label TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL,
            locked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE items (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            sku TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL,
            quantity REAL NOT NULL DEFAULT 0 CHECK(quantity >= 0),
            minimum REAL NOT NULL DEFAULT 0 CHECK(minimum >= 0),
            unit TEXT NOT NULL DEFAULT 'pz', placement_id INTEGER REFERENCES placements(id) ON DELETE SET NULL,
            note TEXT NOT NULL DEFAULT '', UNIQUE(warehouse_id,sku));
CREATE TABLE movements (
            id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
            delta REAL NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE loans (
            id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
            borrower TEXT NOT NULL, quantity REAL NOT NULL CHECK(quantity > 0),
            created_at INTEGER NOT NULL, returned_at INTEGER);
CREATE TABLE walls (
            id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
            x1 REAL NOT NULL, y1 REAL NOT NULL, x2 REAL NOT NULL, y2 REAL NOT NULL,
            source TEXT NOT NULL DEFAULT 'manual');
