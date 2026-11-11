"""Regression checks for actionable home cards, readable map names and viewport clipping."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/officinastock/app'
# 4.15: the screens are split in several files.
screens=''.join((p/f).read_text(encoding='utf-8') for f in ['Screens.kt','ProfileScreens.kt','InventoryScreen.kt','ItemScreens.kt','MovementsScreen.kt','SettingsScreen.kt'])
today=(p/'TodayScreen.kt').read_text(encoding='utf-8')
main=(p/'MainActivity.kt').read_text(encoding='utf-8')
report=(p/'ManagerDashboard.kt').read_text(encoding='utf-8')
map_screen=((p/'MapScreen.kt').read_text(encoding='utf-8')+(p/'MapDialogs.kt').read_text(encoding='utf-8'))
metrics=(p/'ManagerMetrics.kt').read_text(encoding='utf-8')
gradle=(r/'app/build.gradle.kts').read_text(encoding='utf-8')
checks={
    # 4.15: the first screen lives in TodayScreen.kt; numbers and tasks stay actionable.
    'Home interactive metrics': 'onStockFocus(StockFocus.CRITICAL)' in today and 'onStockFocus(StockFocus.UNLOCATED)' in today and 'onActivityFocus(ActivityFocus.LOANS, 0)' in today,
    'Home item drilldown': 'onItemFocus(move.itemId)' in today,
    'Home opens settings and team': 'goSettings={go(Tab.SETTINGS)}' in main and 'goTeam={go(Tab.TEAM)}' in main and 'Tab.TEAM -> TeamScreen(model,' in main and 'StockSections(movements=true' in main,
    'Inventory filters': 'StockFocus.IDLE_90 -> item.id !in idleIds' in screens,
    'Manager card navigation': 'onActivityFocus(ActivityFocus.INBOUND,days)' in report,
    'Manager idle drilldown': 'onStockFocus(StockFocus.IDLE_90)' in report,
    'Manager threshold matches inventory': 'val under=items.filter { it.lowStock }' in metrics,
    'Map viewport hard clip': '.clipToBounds()' in map_screen and 'clipRect {' in map_screen,
    'Map labels use density-aware sp': '14.sp.toPx()' in map_screen and 'selectedLabelPx' in map_screen,
    'Map labels draw legible chips': 'drawText(label,centerX,' in map_screen and 'Color(0xF20B2332)' in map_screen,
    'Map panning bounded': '.clampedPan(' in map_screen,
    'History paginated with filtered SQLite queries': 'model.historyPage(warehouseId,focus,days,page)' in screens and 'Mostra altre operazioni' in screens and 'fun movementsPage(' in (p/'StockDb.kt').read_text(encoding='utf-8'),
    'Item edit action and safe database path': 'EditItemDetailsDialog(' in screens and 'model.updateItemDetails(' in screens and 'fun updateItemDetails(' in (p/'StockDb.kt').read_text(encoding='utf-8'),
    'Stable optional release signing': 'UBICORA_RELEASE_STORE_FILE' in gradle and 'signingConfigs.create("ubicoraStable")' in gradle,
    'Android 16 target': 'targetSdk = 36' in gradle,
    'Package id preserved': 'applicationId = "com.ubicora.app"' in gradle
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+' '+k)
assert all(checks.values()), 'UI regression contract failed'
