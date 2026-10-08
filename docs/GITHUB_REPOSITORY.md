# Pubblicazione in un repository GitHub dedicato

Repository consigliato: `GodEater58/OfficinaStock-Android` (privato).

1. Aprire https://github.com/new?name=OfficinaStock-Android&description=Officina%20Stock%204.0%20-%20Android%20native%20app%20(Kotlin%20%2B%20Jetpack%20Compose).
2. Scegliere **Private**. Non è necessario includere README, .gitignore o licenza: sono già nel progetto.
3. Creare il repository e copiarne l'URL. L'account GitHub collegato a ChatGPT ha accesso ai repository dell'installazione corrente, ma la sua integrazione API non offre il comando di creazione nuovi repository.
4. Su un PC con Git configurato per GitHub, pubblicare il repository locale con:

```bash
git remote add origin https://github.com/GodEater58/OfficinaStock-Android.git
git push -u origin main
```

5. La prima push avvia `.github/workflows/android-build.yml` in **Actions**. Una build verde produce l'artifact `officina-stock-4-native-debug-apk` contenente `app-debug.apk` nella pagina della run.

La build non è stata verificata in un ambiente Android SDK reale: se fallisce, aprire i log della run e correggere la causa; non considerare l'APK funzionante fino a compilazione e installazione riuscite.

## Alternativa: importazione del bundle Git

Il file `OfficinaStock_4_0_Native_Repo.bundle` contiene il commit e il ramo `main` già predisposti. Su un computer:

```bash
git clone OfficinaStock_4_0_Native_Repo.bundle OfficinaStock-Android
cd OfficinaStock-Android
git remote add origin https://github.com/GodEater58/OfficinaStock-Android.git
git push -u origin main
```
