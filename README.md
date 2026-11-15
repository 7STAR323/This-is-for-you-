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

Version 0.2.0. Phone calls and WhatsApp are the tested-in-code core. Telegram, Messenger, Instagram
and Snapchat use the same detector but are unproven until a real call is seen on a phone; the app
shows each one's status under Settings > Supported apps.

| Platform | Status |
|---|---|
| Phone calls | Implemented. Caller matched by number; answered, declined and missed told apart. |
| WhatsApp | Implemented; requires device testing. Matched by number when WhatsApp reveals one, otherwise by exact name. |
| Telegram | Implemented; requires device testing. Uses Android's standard call notification. |
| Messenger | Implemented; requires device testing. Works only if Messenger marks its notification as a call. |
| Instagram | Implemented; requires device testing. May announce calls as ordinary notifications, which are ignored on purpose. |
| Snapchat | Implemented; likely restricted. Shares very little in its notifications. |
| Facebook | Not possible. The Facebook app does not place calls; they happen in Messenger. |

## How a caller is identified

1. If the call reveals a phone number, the number decides. It is normalised to international
   (E.164) form, so `+91 98765 43210`, `098765 43210` and `9876543210` are the same caller.
2. If an app reveals no number (the usual case), the exact name the app shows is compared with the
   name saved for that app. Invisible formatting characters, case and extra spaces are ignored;
   everything visible must match.
3. If more than one person fits, nobody is alerted.
4. "Only trust phone numbers" in Settings switches step 2 off.

ARES never guesses a number from a name, reads another app's data, or uses accessibility tricks.

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
| Notification access | Let ARES notice calls in apps | Calling apps only announce calls through notifications. Everything that is not a call notification from a supported app is dropped unread. |
| Full-screen intent | Allow special alerts | To show the alert over the lock screen. |
| Ignore battery optimisation | Keep ARES awake | So the phone doesn't put ARES to sleep. |
| Do Not Disturb access (optional) | Show alerts in Do Not Disturb | Lets the alert show during Do Not Disturb and lets ARES warn when Do Not Disturb would mute it. |
| Vibrate, foreground service, run at startup | (no prompt) | Vibration, staying alive while ACTIVE, and coming back after a reboot. |

There is no internet permission. Nothing leaves the phone.

On OPPO / ColorOS three more switches have to be set by hand, because apps cannot read them:
auto-launch, "allow background activity", and locking ARES in the recent-apps screen. The app walks
through them under Settings > Permissions and setup.

## How it is organised

```
domain/      Plain Kotlin: people, number and name matching, the call-session state machine
data/        Room database, DataStore settings, the contact picker
detection/   SIM call receiver and call log; call notifications from apps
guard/       The master switch, permission checks, session manager, foreground service, boot restore
alert/       Sound, vibration, notifications and the full-screen alert
ui/          Compose screens and the Forest Prestige theme
```

## Tests

`./gradlew :app:testDebugUnitTest` runs 96 tests on a computer, with no phone involved:

- identity matching (number formats, shared names, changed names, hidden callers, ambiguity);
- recognising ringing, ongoing and missed call notifications, and ignoring messages;
- the life of an app call (duplicates, answered, declined, missed, missed without ringing);
- the call-session state machine and the grace timer, alert and stop behaviour in simulated time.

Nothing here proves behaviour on a real phone. What each app actually puts in its notifications,
and how ColorOS treats the background service, can only be checked on the device. Settings > Test
and diagnostics lists what ARES saw, without names, numbers or message text, and has a Copy button.

## Known limitations

- **Do Not Disturb:** the alert uses the alarm stream. It is heard in Silent and Vibrate, and in
  Do Not Disturb as long as alarms are allowed there. Current Android versions do not let an app
  switch the user's Do Not Disturb off, so ARES warns on the Home screen instead of pretending.
- **Full-screen alert:** Android only takes over the screen when the phone is locked or the screen
  is off. While you are using the phone you get a banner with a Stop button instead.
- **App calls are usually matched by name**, because apps rarely reveal a number. The name must be
  exactly what the app shows. If you rename the contact, update it in ARES too. On apps where
  people choose their own display name, a stranger using the same name would also match.
- **App call endings are inferred** from notifications appearing and disappearing. A call answered
  on another device looks like a declined call. A missed call that ARES never saw ringing is only
  recognised when the app marks it as a missed call or uses its standard English phrase.
- **On another app call:** a second call in the same app may alert with sound, because Android
  can't tell ARES whether that app is ringing or already connected.
- **ColorOS switches cannot be verified** by the app; "Needs attention" covers everything else.
- **After a reboot** protection resumes once the phone has been unlocked for the first time.
- **Already on a call:** the alert is vibration and screen only, so nothing loud plays in your ear.
- **One name per app per person** in the interface, although the data model allows several.
- **Withheld numbers** cannot be matched to anyone.
- Interface text is English only and lives in the Compose code rather than string resources.
