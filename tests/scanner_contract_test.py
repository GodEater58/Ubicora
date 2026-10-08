from pathlib import Path
base=Path(__file__).resolve().parents[1]
models=(base/"app/src/main/java/com/officinastock/app/Models.kt").read_text()
main=(base/"app/src/main/java/com/officinastock/app/MainActivity.kt").read_text()
vm=(base/"app/src/main/java/com/officinastock/app/StockViewModel.kt").read_text()
screens=(base/"app/src/main/java/com/officinastock/app/Screens.kt").read_text()
build=(base/"app/build.gradle.kts").read_text()
assert "parseScan(raw: String)" in models
assert '"OFFICINA-STOCK:1:W$warehouseId:$sku"' in models
assert 'ScanContract()' in main and 'ScanOptions.QR_CODE' in main
assert "scannedItemId" in vm and "target.warehouseId != warehouse.id" in vm
assert "InventoryScreen(model,onScan" in main, "Lo scanner deve essere collegato alla schermata inventario"
assert "ScanContract()" in main and "onScan" in main, "Il comando scanner deve restare disponibile"
assert "LaunchedEffect(u.scannedItemId,u.items)" in screens, "Il QR deve aprire la scheda articolo"
assert "LaunchedEffect(u.scannedItemId,u.items)" in screens
assert 'zxing-android-embedded:4.3.0' in build
print("Scanner QR: payload warehouse, scanner, UI e lookup presenti")
