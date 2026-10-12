package com.officinastock.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Diagnostic only (guide workflow): which part of the join dialog keeps Compose busy under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w392dp-h850dp-xhdpi")
class JoinDialogProbeTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getenv("UBICORA_GUIDE_SHOTS")?.let(::File)

    private fun probe(name: String, content: @Composable () -> Unit) {
        Assume.assumeTrue(out != null)
        val result = runCatching { compose.setContent { StockTheme { content() } }; compose.waitForIdle() }
        File(out, "probe_$name.txt").writeText(if (result.isSuccess) "idle" else "NOT IDLE: ${result.exceptionOrNull()?.javaClass?.simpleName}")
    }
    private val ok: @Composable () -> Unit = { TextButton(onClick = {}) { Text("OK") } }

    @Test fun a_plainDialog() = probe("a_plain") { AlertDialog(onDismissRequest = {}, text = { Text("Testo") }, confirmButton = ok) }
    @Test fun b_textInput() = probe("b_text") { AlertDialog(onDismissRequest = {}, text = { Input("abc", {}, "Nome") }, confirmButton = ok) }
    @Test fun c_passwordInput() = probe("c_password") { AlertDialog(onDismissRequest = {}, text = { Input("squadra-2026", {}, "Password", password = true) }, confirmButton = ok) }
    @Test fun d_chips() = probe("d_chips") { AlertDialog(onDismissRequest = {}, text = { Row { listOf("B", "C").forEach { FilterChip(selected = it == "B", onClick = {}, label = { Text(it) }) } } }, confirmButton = ok) }
    @Test fun e_columnButtons() = probe("e_buttons") { AlertDialog(onDismissRequest = {}, text = { Text("x") }, confirmButton = { Column { ok(); ok(); ok() } }) }
    @Test fun f_nearbyAccess() = probe("f_nearby") { val n = rememberNearbyAccess { }; AlertDialog(onDismissRequest = {}, text = { Text("x") }, confirmButton = { TextButton(onClick = { n {} }) { Text("Cerca") } }) }
}
