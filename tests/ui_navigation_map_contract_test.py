"""Regression checks for actionable home cards, readable map names and viewport clipping."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/officinastock/app'
screens=(p/'Screens.kt').read_text()
main=(p/'MainActivity.kt').read_text()
report=(p/'ManagerDashboard.kt').read_text()
map_screen=(p/'MapScreen.kt').read_text()
metrics=(p/'ManagerMetrics.kt').read_text()
gradle=(r/'app/build.gradle.kts').read_text()
checks={
    'Home interactive metrics': 'onClick={onStockFocus(StockFocus.CRITICAL)}' in screens and 'onClick=goMap' in screens,
    'Home item drilldown': 'MovementRow(move,onClick={onItemFocus(move.itemId)})' in screens,
    'Inventory filters': 'StockFocus.IDLE_90 -> item.id !in idleIds' in screens,
    'Manager card navigation': 'onActivityFocus(ActivityFocus.INBOUND,days)' in report,
    'Manager idle drilldown': 'onStockFocus(StockFocus.IDLE_90)' in report,
    'Manager threshold matches inventory': 'val under=items.filter { it.lowStock }' in metrics,
    'Map viewport hard clip': '.clipToBounds()' in map_screen and 'clipRect {' in map_screen,
    'Map labels use density-aware sp': '14.sp.toPx()' in map_screen and 'selectedLabelPx' in map_screen,
    'Map labels draw legible chips': 'drawText(label,centerX,' in map_screen and 'Color(0xF20B2332)' in map_screen,
    'Map panning bounded': '.clampedPan(' in map_screen,
    'History limit disclosed': 'ultime 250 registrazioni' in screens,
    'Package id preserved': 'applicationId = "com.ubicora.app"' in gradle
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+' '+k)
assert all(checks.values()), 'UI regression contract failed'
