package com.officinastock.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GOOGLE_AR_TERMS = "https://developers.google.com/ar/develop/terms"
private const val GOOGLE_PRIVACY = "https://policies.google.com/privacy"
private const val GOOGLE_TERMS = "https://policies.google.com/terms"
private const val GOOGLE_AR_DATA = "https://support.google.com/ar/answer/12148145"

/** Avviso visualizzato prima del primo accesso alle funzioni ARCore. */
@Composable
fun ArPrivacyNoticeDialog(onProceed: () -> Unit, onDismiss: () -> Unit) {
    val links = LocalUriHandler.current
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Informativa mappatura AR")},
        text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("La funzione di mappatura utilizza la fotocamera e Google Play Services per AR (ARCore), fornito da Google LLC.",fontWeight=FontWeight.SemiBold)
            Text("Durante la scansione, ARCore elabora immagini e dati dei sensori per individuare superfici. Il servizio Google può trattare anche dati del dispositivo, diagnostica e utilizzo secondo la propria informativa. L'utilizzo di ARCore è soggetto ai termini di Google.",fontSize=13.sp)
            Text("La planimetria salvata da questa app rimane nella memoria locale. La scansione AR è facoltativa; puoi continuare a usare l'inventario senza abilitarla.",fontSize=13.sp)
            TextButton(onClick={links.openUri(GOOGLE_AR_DATA)}) {Text("Come Google gestisce i dati AR")}
            TextButton(onClick={links.openUri(GOOGLE_PRIVACY)}) {Text("Privacy Google")}
            TextButton(onClick={links.openUri(GOOGLE_TERMS)}) {Text("Termini Google")}
        }},
        confirmButton={Button(onClick=onProceed) {Text("Continua alla mappatura")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Non usare AR")}}
    )
}

/** Informazioni accessibili in ogni momento dalle impostazioni dell'app. */
@Composable
fun LegalInfoDialog(onDismiss: () -> Unit) {
    val context=LocalContext.current
    val links=LocalUriHandler.current
    var licenses by remember {mutableStateOf(false)}
    val licensesText=remember(context) {
        runCatching {context.assets.open("legal/third_party_notices.txt").bufferedReader().use {it.readText()}+"\n\n--- Testo integrale Apache License 2.0 ---\n\n"+context.assets.open("legal/Apache-2.0.txt").bufferedReader().use {it.readText()} }
            .getOrElse {"Informazioni non disponibili: "+it.message}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(licenses)"Licenze di terze parti" else "Privacy e informazioni")},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=!licenses,onClick={licenses=false},label={Text("Privacy")})
                FilterChip(selected=licenses,onClick={licenses=true},label={Text("Licenze")})
            }
            if(licenses) {
                SelectionContainer {Text(licensesText,fontSize=11.sp)}
            } else {
                Text("Officina Stock: gestione inventario con dati memorizzati sul dispositivo.",fontWeight=FontWeight.Bold)
                Text("Profili, PIN, articoli, movimenti, prestiti e planimetrie sono registrati localmente. Il PIN limita l'accesso nell'app, ma il database non è cifrato.",fontSize=13.sp)
                Text("La fotocamera serve a scansionare codici QR, fotografare articoli e, su richiesta, mappare l'ambiente. Le foto degli articoli sono salvate nell'archivio privato dell'app.",fontSize=13.sp)
                Text("Puoi esportare CSV non cifrati o backup completi cifrati con password. La destinazione dei file esportati viene scelta da te; conserva i backup con cura.",fontSize=13.sp)
                Text("Quando attivi la mappatura AR, Google Play Services per AR (ARCore) di Google LLC può raccogliere dati di utilizzo, identificativi del dispositivo e diagnostica e trattare dati di fotocamera/sensori in conformità alle policy Google. L'app non attiva ARCore per la normale gestione inventariale.",fontSize=13.sp)
                Text("I dati rimangono sul dispositivo finché non li elimini o disinstalli l'app. Backup e CSV esportati restano dove li hai salvati e non vengono cancellati automaticamente dall'app.",fontSize=13.sp)
                Text("Questa è un'informativa tecnica della versione Alpha, non ancora la privacy policy definitiva per una distribuzione pubblica.",fontSize=12.sp,color=Amber)
                TextButton(onClick={links.openUri(GOOGLE_AR_DATA)}) {Text("Trattamento dati Google AR")}
                TextButton(onClick={links.openUri(GOOGLE_AR_TERMS)}) {Text("Condizioni di utilizzo ARCore")}
                TextButton(onClick={links.openUri(GOOGLE_PRIVACY)}) {Text("Privacy Google")}
                TextButton(onClick={links.openUri(GOOGLE_TERMS)}) {Text("Termini Google")}
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("Chiudi")}})
}
