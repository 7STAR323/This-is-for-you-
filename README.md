# ARES Call Guard

Your phone stays silent for the world, and the few people you choose still get through.

ARES watches for calls from a short list of people. If one of them calls while the phone is on
Silent, Vibrate or Do Not Disturb and the call is still ringing after a grace period (10 seconds
by default), ARES plays a loud alert with vibration and a full-screen **STOP ALERT** button. If the
call is missed, it leaves a missed-call alert with **Call back** and **Dismiss**.

ARES only observes. It never answers, rejects, mutes or redials a call, and everyone who is not on
the list behaves exactly as before.

The full product specification is in [ARES_SPEC.md](ARES_SPEC.md).

## Status

Phase 1: SIM calls and WhatsApp calls. Telegram, Instagram, Messenger and Snapchat are planned.

## Build and install

Requirements: Android Studio (recent stable), JDK 17 or newer, a phone on Android 12 or newer.

1. Open the project folder in Android Studio and let it sync.
2. Connect the phone with USB debugging on and press **Run**.

From a terminal:

```
./gradlew :app:installDebug     # build and install on the connected phone
./gradlew :app:testDebugUnitTest
```

The app is meant to be installed directly (sideloaded). It reads the call log, which Google Play
only allows for dialer apps, so it would not pass Play review as it stands.

## Permissions, and why

| Permission | What you see | Why ARES needs it |
|---|---|---|
| Notifications | Allow notifications | To show the alert and missed-call notices. |
| Phone state and call log | Let ARES see who is calling | The caller's number is matched against your people; the call log tells a declined call from a missed one. |
| Notification access | Let ARES notice WhatsApp calls | WhatsApp only announces calls through notifications. Everything that is not a WhatsApp call notification is dropped unread. |
| Full-screen intent | Allow special alerts | To show the alert over the lock screen. |
| Ignore battery optimisation | Keep ARES awake | So the phone doesn't put ARES to sleep. |
| Vibrate, foreground service, run at startup | (no prompt) | Vibration, staying alive while ACTIVE, and coming back after a reboot. |

There is no internet permission. Nothing leaves the phone.

On OPPO / ColorOS three more switches have to be set by hand, because apps cannot read them:
auto-launch, "allow background activity", and locking ARES in the recent-apps screen. The app walks
through them under Settings > Permissions and setup.

## How it is organised

```
domain/      Plain Kotlin: people, number and name matching, the call-session state machine
data/        Room database, DataStore settings, the contact picker
detection/   SIM call receiver and call log; WhatsApp call notifications
guard/       The master switch, setup checks, session manager, foreground service, boot restore
alert/       Sound, vibration, notifications and the full-screen alert
ui/          Compose screens and the Forest Prestige theme
```

The two pieces that must never be wrong, matching and the call-session state machine, are pure
Kotlin and covered by unit tests in `app/src/test`.

## Known limitations

- **Do Not Disturb:** the alert uses the alarm stream. It is heard in Silent and Vibrate, and in
  Do Not Disturb as long as alarms are allowed there. ARES does not change your Do Not Disturb settings.
- **Full-screen alert:** Android only takes over the screen when the phone is locked or the screen
  is off. While you are using the phone you get a banner with a Stop button instead.
- **WhatsApp is matched by name.** The name must be exactly what WhatsApp shows. If you rename the
  contact, update it in ARES too. Someone else with the same displayed name would also match.
- **WhatsApp endings are inferred** from notifications appearing and disappearing. A call answered
  on another device looks like a declined call. A missed call that ARES never saw ringing is only
  recognised when WhatsApp marks it as a missed call or says so in English.
- **ColorOS switches cannot be verified** by the app; "Needs attention" covers everything else.
- **After a reboot** protection resumes once the phone has been unlocked for the first time.
- **Already on a call:** the alert is vibration and screen only, so nothing loud plays in your ear.
- **One WhatsApp name per person** in the interface for now, although the data model allows several.
- **Withheld numbers** cannot be matched to anyone.
- Interface text is English only and lives in the Compose code rather than string resources.
