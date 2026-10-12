from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/officinastock/app'
db=(p/'StockDb.kt').read_text(encoding='utf-8')
ui=(p/'MapScreen.kt').read_text(encoding='utf-8')
model=(p/'Models.kt').read_text(encoding='utf-8')
geom=(p/'PlanGeometry.kt').read_text(encoding='utf-8')
checks={
    'versioned migration': 'null, 12)' in db and 'oldVersion < 5' in db,
    'metric room size': 'map_width REAL NOT NULL DEFAULT 12' in db,
    'metric furniture schema': 'width_m REAL NOT NULL DEFAULT 1.6' in db,
    'safe legacy wall conversion': 'x1=x1*0.12' in db,
    'safe legacy furniture conversion': 'SET x = x * 0.12' in db,
    'furniture rotation persisted': 'fun updatePlacementGeometry' in db,
    'metric coordinates in render': 'PlanViewport' in ui and 'camera.toScreen' in ui,
    'unified pointer ownership': 'awaitEachGesture' in ui,
    'no competing taps': 'detectTapGestures' not in ui,
    'wall tool': 'EditorTool.WALL' in ui and 'PlanGeometry.validWall' in ui,
    'furniture tool': 'EditorTool.FURNITURE' in ui,
    'grid snapping': 'PlanGeometry.snap' in ui,
    # 4.7.1: wall points go through PlanGeometry.wallPoint, which applies orthogonal mode unless a corner is hooked.
    'right-angle snapping': 'PlanGeometry.wallPoint' in ui and 'orthogonal(from,base)' in geom,
    'shape closing on corners': 'landed.closes' in ui and 'WallSnapKind.VERTEX' in geom,
    'metric measurement labels': 'numberM(roomWidth)' in ui,
    'independent physical geometry': 'data class PlanViewport' in geom and 'fun toWorld' in geom,
    'model preserves furniture orientation': 'rotation: Float = 0f' in model
}
for label,ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {label}")
assert all(checks.values()),'Plan contract broken'
