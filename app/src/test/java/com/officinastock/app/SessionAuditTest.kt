package com.officinastock.app

import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SessionAuditTest {
    private lateinit var model:StockViewModel
    private var first=0L
    private var second=0L
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        app.getSharedPreferences("login_attempts",0).edit().clear().commit()
        val db=StockDb(app)
        try {first=db.createProfile("A","1234");second=db.createProfile("B","5678")} finally {db.close()}
        model=StockViewModel(app);awaitIdle()
    }
    private suspend fun awaitIdle() {withTimeout(15000){while(model.ui.busy)delay(10)}}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun startupLockedAndBackgroundDoesNotAutomaticallyRestoreLogin() = runBlocking {
        assertTrue(model.ui.initialized);assertNull(model.ui.profile)
        model.unlock(Profile(first,"A"),"1234");awaitIdle();assertEquals(first,model.ui.profile!!.id)
        model.lockForBackground();model.refresh();awaitIdle();assertTrue(model.ui.sessionLocked)
        model.unlock(Profile(first,"A"),"1234");awaitIdle();assertFalse(model.ui.sessionLocked)
    }
    @Test fun archiveCannotReadOtherProfilesWithoutTheirPins() = runBlocking {
        model.unlock(Profile(first,"A"),"1234");awaitIdle()
        val failed=runCatching{model.authorizeArchive(mapOf(first to "1234"))}.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
        val uri=android.net.Uri.fromFile(java.io.File(RuntimeEnvironment.getApplication().cacheDir,"sessions.osbackup"))
        assertTrue(runCatching{model.exportBackup(uri,"password-test")}.isFailure)
        model.authorizeArchive(mapOf(first to "1234",second to "5678"))
        model.exportBackup(uri,"password-test")
        assertTrue(java.io.File(uri.path!!).length()>0)
        assertTrue(runCatching{model.exportBackup(uri,"password-test")}.isFailure)
    }
    @Test fun backgroundDuringUnlockCannotBypassGate() = runBlocking {
        model.unlock(Profile(first,"A"),"1234")
        model.lockForBackground();awaitIdle()
        assertTrue(model.ui.sessionLocked)
    }
}
