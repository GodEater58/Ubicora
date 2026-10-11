package com.officinastock.app

/**
 * Reversible map edits. [revert] undoes the edit on the database and returns the edit that redoes it,
 * so undo and redo share one code path. Ids are preserved on restore (AUTOINCREMENT never reuses them).
 */
sealed class MapEdit {
    abstract val label: String
    abstract fun revert(db: StockDb): MapEdit

    data class Created(val ids: List<Long>) : MapEdit() {
        override val label get() = if(ids.size == 1) "creazione struttura" else "creazione di ${ids.size} strutture"
        override fun revert(db: StockDb): MapEdit {
            val snapshots = ids.mapNotNull { id -> db.placement(id)?.let { it to db.itemSpots(id) } }
            snapshots.forEach { db.deletePlacement(it.first.id) }
            return Deleted(snapshots)
        }
    }
    data class Deleted(val snapshots: List<Pair<Placement, List<StockDb.ItemSpot>>>) : MapEdit() {
        override val label get() = "eliminazione struttura"
        override fun revert(db: StockDb): MapEdit {
            snapshots.forEach { (p, spots) -> db.restorePlacement(p, spots) }
            return Created(snapshots.map { it.first.id })
        }
    }
    data class Moved(val id: Long, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) : MapEdit() {
        override val label get() = "spostamento"
        override fun revert(db: StockDb): MapEdit {
            db.setPlacementPosition(id, fromX, fromY)
            return Moved(id, toX, toY, fromX, fromY)
        }
    }
    data class Reshaped(val before: Placement) : MapEdit() {
        override val label get() = "dimensioni/rotazione"
        override fun revert(db: StockDb): MapEdit {
            val current = requireNotNull(db.placement(before.id)) { "Struttura non più presente" }
            db.updatePlacementGeometry(before.id, before.width, before.depth, before.rotation)
            db.setPlacementPosition(before.id, before.x, before.y)
            return Reshaped(current)
        }
    }
    data class WallsAdded(val ids: List<Long>) : MapEdit() {
        override val label get() = "nuova parete"
        override fun revert(db: StockDb): MapEdit {
            val walls = ids.mapNotNull { db.wall(it) }
            walls.forEach { db.deleteWall(it.id) }
            return WallsDeleted(walls)
        }
    }
    data class WallsDeleted(val walls: List<Wall>) : MapEdit() {
        override val label get() = "eliminazione parete"
        override fun revert(db: StockDb): MapEdit {
            walls.forEach { db.restoreWall(it) }
            return WallsAdded(walls.map { it.id })
        }
    }
    data class WallChanged(val id: Long, val before: FloatArray) : MapEdit() {
        override val label get() = "modifica parete"
        override fun revert(db: StockDb): MapEdit {
            val w = requireNotNull(db.wall(id)) { "Parete non più presente" }
            db.updateWall(id, before)
            return WallChanged(id, floatArrayOf(w.x1, w.y1, w.x2, w.y2))
        }
        override fun equals(other: Any?) = other is WallChanged && other.id == id && other.before.contentEquals(before)
        override fun hashCode() = 31 * id.hashCode() + before.contentHashCode()
    }
}
