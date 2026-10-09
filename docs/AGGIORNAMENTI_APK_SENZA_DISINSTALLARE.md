# Ubicora: aggiornamenti APK che conservano i dati

## Diagnosi

Android accetta un aggiornamento in-place se il nuovo APK usa lo **stesso `applicationId`**, una **firma compatibile** e un `versionCode` adeguato. Gli APK di tipo **debug** prodotti su runner GitHub temporanei possono usare un certificato diverso a ogni esecuzione. Il nome del file o il numero di versione mostrato non cambiano questa regola.

Ubicora attuale: `com.ubicora.app`. Il vecchio identificativo `com.officinastock.app` è una *app diversa* per Android.

Il workflow storico `android-build.yml` continua a generare APK debug per test: **non usarli per aggiornare installazioni contenenti dati reali**.

Il nuovo workflow `ubicora-signed-update.yml` crea un **APK release firmato sempre con la stessa chiave privata conservata fuori dal repository** e blocca la pubblicazione se la chiave, il certificato o il codice versione non corrispondono alle attese.

## Prima di ogni modifica, conserva i dati reali

1. Sul telefono apri **Ubicora → Impostazioni → Esporta backup completo**.
2. Salva il file cifrato `.osbackup` in due posti distinti e conserva al sicuro la password e i PIN di **tutti** i profili.
3. Verifica il backup su un dispositivo di test, verificando che profili, foto, movimenti, prestiti, articoli e planimetrie si ricostruiscano. Il ripristino sostituisce tutti i dati nell'installazione di destinazione.
4. **Non disinstallare l'app attuale** finché il backup non è stato verificato.

## Posso aggiornare direttamente la versione già installata?

**Soltanto se esiste ancora la chiave privata che ha firmato quell'APK.** Il certificato pubblico SHA-256 da solo non permette di ricreare la firma: non esiste una modifica Gradle che possa aggirare questo controllo.

Per confrontare APK installato e candidato da un PC Windows con Android SDK, adb e telefono collegato via USB con debug autorizzato, usa PowerShell:

```powershell
.\scripts\verify-update-on-device.ps1 -CandidateApk "C:\Percorso\Ubicora-nuova.apk"
```

Lo script legge temporaneamente l'APK attuale con ADB, confronta pacchetto, firma e versionCode, mostra PASS/FAIL e **non installa né disinstalla nulla**.

Se fallisce per firma:
- cerca la chiave privata originale tra i file di firma personali, backup del progetto o archivio della macchina che compilava l'APK;
- se non esiste più, prepara **una singola migrazione controllata** tramite backup cifrato e ripristino su una build firmata con una nuova chiave persistente;
- da quel punto in poi aggiorna solo con APK firmati con **quella stessa chiave**.

## Configura una sola volta la chiave permanente

Da un PC locale Windows con Java `keytool`, nella tua cartella privata (NON dentro il repository):

```powershell
keytool -genkeypair -v -keystore ubicora-stable.p12 -storetype PKCS12 -alias ubicora -keyalg RSA -keysize 3072 -validity 10000
keytool -list -v -keystore ubicora-stable.p12 -alias ubicora
```

Questi comandi servono **solo se non hai più la chiave originale**. Se la hai, *riutilizza quella*, senza generare una nuova chiave.

Salva almeno due copie del keystore in posti distinti, cifrati, con password conservata separatamente. **Non inviare il keystore o le password in chat e non aggiungerli mai al repository Git.**

In GitHub, repository `GodEater58/OfficinaStock-Android`, apri **Settings → Secrets and variables → Actions**. Configura queste quattro **Repository secrets**:

| Nome secret | Valore |
| --- | --- |
| `UBICORA_KEYSTORE_BASE64` | Contenuto binario del keystore, codificato in base64 |
| `UBICORA_STORE_PASSWORD` | Password del keystore |
| `UBICORA_KEY_ALIAS` | Nome alias della chiave, normalmente `ubicora` |
| `UBICORA_KEY_PASSWORD` | Password della chiave |

Per produrre la codifica base64 dal PC Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Percorso\ubicora-stable.p12")) | Set-Clipboard
```

In **Variables**, crea la variabile `UBICORA_SIGNING_CERT_SHA256`, con l'impronta SHA-256 del certificato emessa da `keytool -list -v`. È un dato pubblico e deve restare invariato per tutta la vita di questa linea di aggiornamenti.

**Importante:** se disponi della chiave privata originale, inserisci *quella* e il suo certificato, non una chiave nuova.

## Compilare ogni nuova versione senza reinstallare

1. Aggiorna il codice mantenendo `applicationId = "com.ubicora.app"`.
2. Incrementa `versionCode` in `app/build.gradle.kts` (es. da 420 a 421). Cambia anche `versionName`.
3. Fai superare i test e integra il codice in `main`.
4. GitHub → **Actions → Ubicora Stable Signed Update → Run workflow**, scegli `main` e indica lo stesso `expected_version_code` del Gradle.
5. Il workflow fallisce se manca un secret, se il certificato non combacia con la fingerprint permanente, se non è `com.ubicora.app` o se la versione indicata è sbagliata.
6. Scarica esclusivamente l'artefatto `ubicora-signed-update-code-...`; estrai `app-release.apk`. Prima del primo aggiornamento di una installazione reale, esegui lo script di compatibilità.
7. Installa il nuovo APK sopra quello esistente senza disinstallare. Se Android dice "app non installata" o "conflitto", **non disinstallare**, controlla la firma e la versione.

Il file APK **debug** generato da `android-build.yml` non è un APK di aggiornamento stabile. La release firmata usa lo stesso certificato solo se i segreti sono configurati e mantenuti costanti.

## Limiti del collaudo

La compatibilità della firma non certifica da sola la migrazione del database. Fare almeno un backup prima di ogni versione importante. Il controllo della firma dell'APK installato richiede il dispositivo (o l'APK originale) e non può essere ricavato da un nome di file o da uno screenshot. In assenza della chiave privata originaria, **non è possibile trasformare una build con firma diversa in un aggiornamento in-place**.
