# Repository GitHub

Repository: `GodEater58/Ubicora` (privato). Fino alla 4.1 si chiamava `OfficinaStock-Android`: i vecchi indirizzi vengono reindirizzati da GitHub, ma vanno usati quelli nuovi.

| Cosa | Dove |
|---|---|
| Codice aggiornato | ramo `main`; ogni versione arriva con una pull request (`feature/…`, `fix/…`) |
| Controlli automatici | Actions → `android-build`, `encrypted-archive`, `web-tests`, `server-tests` |
| APK firmato per aggiornare | Actions → **Ubicora Stable Signed Update** → bozza in *Releases* ([procedura](AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md)) |
| Novità | [cronologia delle versioni](CHANGELOG.md) |

Per collegare una copia locale già esistente al nuovo nome:

```bash
git remote set-url origin https://github.com/GodEater58/Ubicora.git
```
