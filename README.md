# Blink Cam (Blink-android)

<img src="docs/icon.png" width="96" align="right" alt="Icona Blink Cam">

Un progetto **DaboPrint3D** — *Design & Printing* · [canale YouTube DaboPrint3D](https://www.youtube.com/@daboPrint3d)

App Android **non ufficiale** per controllare le telecamere di casa Blink, con in più la
possibilità di **aggiornare automaticamente le miniature a intervalli configurabili**.

<p>
  <img src="docs/screenshots/home.png" width="250" alt="Schermata principale">
  <img src="docs/screenshots/settings.png" width="250" alt="Impostazioni aggiornamento automatico">
  <img src="docs/screenshots/login-2fa.png" width="250" alt="Login con verifica in due passaggi">
</p>

## Funzionalità

Come l'app Blink:

- Accesso con email e password del proprio account Blink, con **verifica in due passaggi** (codice via SMS/email).
  La password non viene salvata: l'app conserva solo i token, cifrati con l'Android Keystore.
- Elenco dei sistemi con **inserimento/disinserimento** (Armato / Disarmato).
- Griglia delle telecamere con miniatura, orario dello scatto, avvisi di batteria scarica / offline.
- **Nuova miniatura** su richiesta (singola telecamera o tutte insieme).
- **Rilevamento movimento** attivabile per singola telecamera.
- Dettaglio telecamera: miniatura ingrandibile (pinch-to-zoom), batteria, segnale Wi-Fi e Sync Module,
  temperatura, firmware, **registrazione di una clip** e clip recenti.
- **Clip**: elenco per periodo (24 h / 7 / 30 / 60 giorni), filtro per telecamera, riproduzione ed eliminazione.
- Supporta telecamere con Sync Module (Outdoor, Indoor, XT…), **Blink Mini** e **Video Doorbell**.

In più — **aggiornamento automatico delle miniature**:

- Interruttore generale e **intervallo predefinito** (1 minuto … 7 giorni; valori pronti o personalizzato).
- **Intervallo diverso per ogni telecamera**, oppure esclusione della telecamera.
- **Fascia oraria** opzionale (anche a cavallo della mezzanotte, es. 22:00–06:00).
- Condizione sullo stato del sistema: sempre / solo armato / solo disarmato.
- Salta le telecamere con **batteria scarica** e quelle offline.
- In background solo con **Wi-Fi** (opzionale).
- Funziona anche **con l'app chiusa** (WorkManager) e, ad app aperta, con un ciclo in primo piano preciso.
- Stato dell'ultimo giro (aggiornate / saltate / errori) e orario del prossimo controllo;
  pulsante "Aggiorna ora".

### Come viene deciso quando aggiornare

Una telecamera viene aggiornata quando è trascorso il suo intervallo dall'**ultima miniatura disponibile**
(scattata automaticamente, a mano o da un evento di movimento) o dall'ultimo tentativo. Così non si scattano
foto inutili se la miniatura è già recente, e una telecamera offline non viene ritentata di continuo.

Ogni scatto consuma un po' di batteria sulle telecamere non alimentate: per quelle conviene un intervallo ampio.

## Installazione

1. Scarica l'APK dagli artifact della GitHub Action **Android build** (`blink-cam-apk` → `app-release.apk`),
   oppure compilalo (vedi sotto).
2. Sul telefono consenti l'installazione da origini sconosciute e apri l'APK.
3. Accedi con le credenziali Blink e inserisci il codice di verifica che ricevi.
4. Vai in **Impostazioni** (icona ingranaggio) e attiva *Aggiorna le miniature automaticamente*.

Per intervalli sotto i 15 minuti con il telefono inattivo, Android (modalità Doze) può ritardare le esecuzioni:
il pulsante **Batteria** nelle impostazioni permette di escludere l'app dall'ottimizzazione batteria.

## Compilazione

Requisiti: JDK 17+, Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest      # unit test (API simulate con MockWebServer + rendering schermate con Robolectric)
./gradlew assembleRelease        # APK in app/build/outputs/apk/release/
```

La build *release* è minificata con R8 e firmata con la chiave di debug, così l'APK è installabile direttamente
(app per uso personale, non destinata al Play Store).

## Struttura del codice

| Package | Contenuto |
|---|---|
| `data/api` | Client OAuth 2.0 + PKCE (login, 2FA, refresh), client REST Blink, modelli e URL dei media |
| `data/auth` | Sessione, cifratura Keystore, rinnovo automatico dei token |
| `data/settings` | Impostazioni dell'aggiornamento automatico e `AutoRefreshPlanner` (logica pura e testata) |
| `data/thumbs` | Cache su disco delle miniature |
| `work` | `AutoRefreshEngine`, worker WorkManager, scheduler e ciclo in primo piano |
| `ui` | Schermate Jetpack Compose (login, home, telecamera, clip, player, impostazioni) |

## Limiti noti

- **API non ufficiali**: l'app usa le stesse API dell'app Blink (documentate dal progetto open source
  [blinkpy](https://github.com/fronzbot/blinkpy)). Blink può cambiarle senza preavviso; in quel caso il punto da
  aggiornare è `data/api/BlinkConstants.kt` / `OAuthClient.kt`. L'uso è a proprio rischio e deve rispettare i
  termini di servizio Blink.
- **Visione live** non disponibile: usa un protocollo proprietario (immis) non implementato.
- **Notifiche push** di movimento non disponibili (arrivano solo all'app ufficiale).

## Icona

L'icona (una "b" turchese con la mano col guanto arancione del logo DaboPrint3D) è disegnata in vettoriale
da `tools/icon/gen.py`, che genera i drawable in `app/src/main/res/drawable/ic_launcher_*.xml`:

```bash
python3 tools/icon/gen.py /tmp/anteprime app/src/main/res
```

## Autore

Realizzato da **DaboPrint3D** — seguimi sul [canale YouTube DaboPrint3D](https://www.youtube.com/@daboPrint3d).

Progetto non affiliato né approvato da Blink o Amazon.
