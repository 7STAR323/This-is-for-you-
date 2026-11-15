# ARES CALL GUARD — Product Specification (v2)

**Universal Priority Call Protection for Android**

> **How to use this document**
> This is the source of truth for the ARES CALL GUARD product. It describes *what* the app must do and *how it should feel*. Where it says "Suggested", you may propose something better, but explain why.
>
> **When you first receive this document, do not write code.** Instead, reply with:
> 1. A short summary of your understanding of the product (max 10 bullets).
> 2. Any contradictions, gaps or risky assumptions you notice.
> 3. Which features are **fully possible**, **partially possible** or **not possible** on Android for an ordinary Play-Store-style app, platform by platform.
> 4. Up to 5 questions you need answered before building.
>
> Wait for my go-ahead before you start building. Once I say go, follow the build instructions in Section 14.

---

## 1. Product in one sentence

ARES CALL GUARD lets my phone stay silent for the world, but it makes sure I never miss calls from the few people I choose.

**The problem:** I keep my phone on Silent, Vibrate or Do Not Disturb (DND) most of the time, so I miss calls from people who matter to me.

**What it is not:** It is not a "make every call louder" app. Ordinary callers must behave exactly as they do today.

---

## 2. Core principles (in priority order)

When requirements conflict, follow this order:

1. **Never do harm to calls.** Never answer, reject, mute, redial or interfere with any call.
2. **Honesty over illusion.** Never claim a capability the phone or a platform doesn't actually allow. Show limitations clearly in plain language.
3. **Only chosen people.** Only explicitly configured people get special treatment. No guessing and no fuzzy matches unless I turn that on.
4. **Simple to use.** A first-time user understands the home screen in seconds. A five-year-old can tell ACTIVE from INACTIVE.
5. **Premium feel.** It should look expensive without looking complicated.
6. **Private by design.** Everything stays on the device.

---

## 3. Target device and environment

- **Primary device:** OPPO Reno 16C, ColorOS on Android `[fill in: Android version / ColorOS version]`.
- **Must survive:** battery optimisation, ColorOS auto-start restrictions, the "clear recent apps" gesture, and device reboot.
- **Suggested stack:** Kotlin, Jetpack Compose, Material 3 (heavily themed), Room for local storage and DataStore for settings. Target the latest stable SDK. Propose a minimum SDK with reasoning.

---

## 4. Supported platforms and rollout phases

| Phase | Platform | Expected identity signal | Notes |
|---|---|---|---|
| 1 | Normal phone calls (SIM) | Phone number | Most reliable. Uses system call-state APIs. |
| 1 | WhatsApp (voice and video) | Display name in the call notification (usually the name saved in my contacts) | Matching is by name, so it must be exact. |
| 2 | Telegram | Display name | |
| 2 | Instagram | Username / display name | |
| 2 | Facebook Messenger / Facebook | Display name | |
| 3 | Snapchat | Display name / username | Notification content may be limited. |

**Rules:**
- Before building each platform, check what that app's incoming-call and missed-call notifications actually show on the target device. Document each one as **Confirmed**, **Partial** or **Unsupported**.
- Show that status honestly in the app. Example: *"Snapchat: we can only see the name, not the number."*
- **Never** treat every caller from a platform as priority.
- **Never** create a call session from an ordinary message, a group-chat notification or any other non-call notification.

---

## 5. People (priority contacts)

### 5.1 Data for each person
- Name
- Photo (optional, taken from the phone contact if available)
- One or more phone numbers, normalised to international format so that `+91 98xxx`, `098xxx` and `98xxx` all match
- One or more platform identities. Each one has a platform and the exact identifier that platform shows (for example the WhatsApp display name "Alishuu ❤️", or the Instagram username "@alishuu")
- Priority level (see 5.3)
- Enabled / disabled

**Example:** *Alishuu* has her mobile number, her WhatsApp name, her Instagram username and her Telegram name. A call from any of them is recognised as Alishuu.

### 5.2 Actions
Add (manually or from phone contacts), edit, remove, enable/disable, and add or remove numbers and platform identities.

### 5.3 Priority levels — keep it to two
- **Ring through:** the full escalation (Section 7).
- **Missed-call alert only:** no loud ring during the call, but a prominent alert if the call is missed.

*(If you think a different model is simpler for users, propose it.)*

### 5.4 Matching rules
- Phone numbers: exact match after normalisation.
- Platform names: exact match by default, ignoring case and leading or trailing spaces.
- If two people share the same platform name, warn me when I save the second one.
- Add a **"Test match"** helper in People. It shows recent caller names that ARES saw from each platform, so I can pick the exact name instead of typing it.

---

## 6. One main control: ACTIVE / INACTIVE

There is exactly **one** master control. No other screen may contain a second master switch.

**ACTIVE means:**
- Watching for calls from my people.
- The 10-second system and alerts are enabled.
- Any background service the app needs is running (with a minimal, calm persistent notification if Android requires one).

**INACTIVE means:**
- Stop any ringing and vibration immediately.
- Cancel active alert sessions.
- Ignore new call events.
- Stop background work.

**Behaviour:**
- Remember the state across app restarts and **device reboots**.
- If a required permission is missing, the control cannot turn fully ACTIVE. Instead it shows a single clear message such as *"1 step left: Allow special alerts"*, and tapping it goes straight to the right setting.
- Add a third visual state, **"Needs attention"**, for when the user turned it on but something (a revoked permission, battery restriction and so on) is stopping it from working. Never show ACTIVE when protection isn't actually working.

### 6.1 Designing the control (creative freedom)
You decide the shape, icon, animation and interaction. Requirements:
- Understandable instantly, and readable without colour (state shown with text and shape, not colour alone).
- Clearly different in ACTIVE, INACTIVE and Needs attention.
- Large touch target with haptic feedback.
- Accessible to screen readers (announces the state and action).
- Premium and distinctive, fitting Forest Prestige. Avoid a generic power-button icon unless it's genuinely the best choice.

**Present 2–3 design concepts with a short rationale before choosing one.**

---

## 7. The 10-second priority system (core feature)

### 7.1 Normal phone calls and VoIP calls that are still ringing

```
Priority person calls
        │
        ▼
Phone behaves normally (silent stays silent, vibrate stays vibrate)
10-second grace timer starts
        │
        ├── Answered / rejected / ended within 10 s ──► stop, no alert
        │      (if it ended unanswered ──► missed-call alert, 7.3)
        │
        └── Still ringing at 10 s AND phone is in Silent / Vibrate / DND
                    │
                    ▼
            PRIORITY ALERT starts (7.2)
                    │
            Stops immediately when: I tap STOP ALERT, OR the call is
            answered, rejected or ends, OR ARES is set to INACTIVE
                    │
            If the call ended unanswered ──► missed-call alert (7.3)
```

- If the phone is already in **normal ring mode**, no escalation is needed. The phone is already ringing.
- **Grace period:** 10 seconds by default. Suggested: adjustable from 5–30 s in Settings, but 10 s out of the box.

### 7.2 Priority alert (while the call is still ringing)
- Loud custom ringtone, played through the strongest audio route that's allowed (likely the alarm stream). Choose the best method and explain why.
- Strong repeating vibration pattern.
- A full-screen alert, even on the lock screen, where Android permits it.
- Repeats until I acknowledge it or the call ends.
- Stops within ~0.5 s when acknowledged.

### 7.3 Missed-call alert
- Shown when a priority call ends unanswered, whether before or after the 10 seconds.
- Shown when a platform only reports a missed call afterwards. In that case **don't pretend the call is still active**. Show a missed-call alert only.
- Prominent but shorter: a distinct sound and vibration that plays once or a few times, plus a persistent notification with **Call back** (opens the right app) and **Dismiss**.
- Logged in History.

### 7.4 Call states the app must tell apart
Incoming, answered, rejected, ended, missed. For each platform, document which of these can actually be detected.

### 7.5 Edge cases to handle
- I'm already on another call when a priority call arrives.
- Two priority calls arrive at the same time.
- The same person calls again within a short time (avoid duplicate alerts).
- Headphones or Bluetooth are connected (where should the alert play?).
- The phone is locked, the screen is off, or battery saver is on.
- The call is answered on another device (for example WhatsApp Web or a smartwatch).
- The app was killed by the system, or the phone just rebooted.
- Notification text is in another language, or contains emojis.

---

## 8. Silent mode, Vibrate and DND — the honest version

Research and use the **official, supported** Android mechanisms, including notification policy (DND) access, notification channels with the right importance and bypass-DND settings, audio attributes and streams, audio focus, the vibration APIs, full-screen intents and foreground services.

**Requirements:**
- Request every permission explicitly, one at a time, each with a plain-language reason.
- Never claim to bypass restrictions the app can't actually bypass.
- For each limitation, use the strongest supported fallback and tell me what it is.
- Include ColorOS-specific setup guidance (auto-start, battery "no restrictions", locking the app in recents) as a friendly step-by-step screen.

---

## 9. Screens and navigation

Bottom navigation: **Home · People · History · Settings**

### Home
Shows only:
1. ARES CALL GUARD name and mark
2. The main control (the visual focus)
3. Status in one line (for example *"Watching for important calls"*)
4. *"3 people can reach you"*
5. A setup warning, only if something is missing
6. An **Add Person** button

No technical details on Home.

### People
Profile cards showing the photo, name, platform icons, priority level and an enabled toggle. Includes **Add Person**.

### Add Person flow (maximum 4 steps)
1. Pick from contacts, or type a name.
2. Add phone numbers (pre-filled if picked from contacts).
3. Choose platforms and the exact name/username for each one, with "Test match" help.
4. Save.

Sensible defaults: Ring through, enabled. No other options in this flow.

### History
A simple list: who called, which app, the time, and what happened (*Answered after alert / Missed / Stopped by you*). Option to clear history. Keep only the last 30 days by default.

### Settings
Ringtone, vibration pattern, grace period, permissions and setup, platform support status, diagnostics (a **"Send a test alert"** button and recent detection log), privacy info and an About page.

### Alert screen
- Very large text: **who** is calling, **which app**, and **why the phone is alerting** (*"Your important call needs attention"*).
- One huge **STOP ALERT** button.
- Nothing else.

---

## 10. Language: everyday words only

| Technical | Show instead |
|---|---|
| Notification Listener Service Active | Watching for important calls |
| Priority Contact Management | People who can reach you |
| Notification Policy Access Required | Allow special alerts |
| Escalation Triggered | Your important call needs attention |
| Battery optimisation exemption | Keep ARES awake |
| Permission denied | ARES needs one more step |

Use the same tone everywhere: short, warm and calm.

---

## 11. Visual identity: Forest Prestige

**Palette**
| Name | Hex | Suggested role |
|---|---|---|
| Forest Green | `#183B2B` | Primary surfaces, ACTIVE state |
| Deep Olive | `#34483B` | Secondary surfaces, cards on dark |
| Sage Green | `#78947A` | Accents, icons |
| Soft Green | `#B5C5AE` | Subtle fills, dividers |
| Warm Ivory | `#F1EFE5` | Main background, text on dark |
| Muted Champagne | `#D5C7A3` | Highlights, premium details (use sparingly) |

**Feel:** rich forest-green surfaces, warm ivory backgrounds, soft natural shadows, generously rounded cards, refined typography (propose a font pairing), smooth micro-animations (respect the system "reduce motion" setting), generous spacing and high readability. All text must meet WCAG AA contrast.

**Avoid:** plain black-and-white, neon, heavy gradients, gaming aesthetics, heavy glassmorphism, cheap metallic effects and cluttered dashboards.

**Dark mode:** propose how the palette adapts.

---

## 12. Privacy

- Everything is processed and stored on the device. No servers, no analytics, no account.
- Only read notifications from the supported call apps. Ignore everything else immediately and never store it.
- Never record calls, capture audio or read message contents.
- Never access other apps' private data.
- Request only the permissions the app needs, and explain each one.
- Store only the minimum event data (person, platform, time, outcome).
- Include a short "What ARES can see" screen in Settings.

---

## 13. Definition of done (acceptance checks)

The product is done when all of these pass on the target device:

- [ ] A priority SIM call in Silent mode starts the priority alert at ~10 s if unanswered.
- [ ] A priority SIM call answered within 10 s produces no alert.
- [ ] A non-priority caller behaves exactly as it does without the app.
- [ ] STOP ALERT silences everything in under 1 s.
- [ ] Ending the call stops the alert and shows a missed-call alert.
- [ ] A WhatsApp call from a configured name gets the same behaviour. A WhatsApp *message* from that person triggers nothing.
- [ ] Switching to INACTIVE stops everything instantly, and the state survives a reboot.
- [ ] Revoking a permission shows "Needs attention", never a false ACTIVE.
- [ ] Protection still works after the app is swiped from recents (with the ColorOS steps done).
- [ ] A first-time user can add a person in under 30 seconds.

---

## 14. Build instructions (for the AI building this)

### 14.1 Use skills, and find them if missing
- Before each part of the work (UI design, Android architecture, testing, accessibility, code review and so on), check which of your installed skills apply, and use them without waiting for me to ask.
- If no installed skill fits, search for one (for example with a find-skills tool or a skills registry).
- Before you install a skill, check that it is safe. It should come from a trusted, well-known source, have no suspicious scripts and no unexplained network access, and be actively maintained with good quality.
- Install the best one and use it.
- Each time, tell me in one line which skill you used, and whether you installed something new (with its name and source).
- If you can't find a suitable skill, say so and continue using best practices.

### 14.2 Build both layers properly
Note: ARES has **no server**. Everything runs on the phone (Section 12). The "backend" is the on-device logic layer.

**Front end (UI layer)**
- Jetpack Compose screens: Home, People, Add Person, History, Settings and the Alert screen, plus permission onboarding.
- A reusable Forest Prestige theme: colours, typography, shapes and spacing defined in one place.
- Every screen handles its loading, empty, error and permission-missing states. No blank or broken screens.
- Accessibility: content descriptions, large touch targets, support for system font scaling and reduce-motion.

**Back end (on-device logic layer)**
- **Call detection:** SIM call-state monitoring, plus a notification listener that reads only the supported call apps.
- **Matching engine:** phone-number normalisation and platform-name matching (Section 5.4).
- **Call session manager:** the 10-second timer and the state machine for incoming, answered, rejected, ended and missed (Section 7).
- **Alert engine:** ringtone, vibration, full-screen alert and stop logic.
- **Persistence:** a Room database for people and history, and DataStore for settings and the ACTIVE state.
- **Reliability:** a foreground service, restore on boot, and a permission and health checker that drives the "Needs attention" state.
- **Clean architecture:** separate UI, domain and data layers (suggested: MVVM + repository pattern + dependency injection). UI code must never touch Android services directly.

**Quality**
- Unit tests for the matching engine and the call-session state machine (these are the most important to get right).
- Build in small, working steps. After each phase, the app must compile and run.
- At the end of each phase, give me a short summary: what was built, what's left, and any limitations you found on the device.

### 14.3 Write human-quality code
The code should read as if a thoughtful senior Android developer wrote it by hand. It should not look AI-generated.
- Use clear, natural names (`CallSessionManager`, `isRingingTooLong`), not generic ones (`DataHandler`, `processData2`, `helperUtil`).
- Comment only where the *why* isn't obvious, such as an Android quirk, a ColorOS workaround or a timing decision. Don't add comments that repeat what the code says, and don't narrate ("// Now we create the list").
- No boilerplate filler: no unused functions, no placeholder TODOs left behind, and no layers or abstractions the app doesn't need.
- Keep functions small and focused, files organised by feature, and formatting consistent (follow the official Kotlin style guide).
- Write commit messages like a human developer: short, specific, in the present tense (for example "Add 10-second grace timer for priority calls").
- Error messages shown to users follow the everyday-language rules in Section 10.
- Include a simple README: what the app does, how to build it, the permissions it needs and why, and known limitations.

---

## 15. Final principle

**Premium in appearance. Simple in operation. Intelligent in behaviour. Honest about its limits.**

It should feel like a carefully designed flagship product, not a technical utility, and it should never need a user manual.
