package com.ares.callguard.domain.model

data class Person(
    val id: Long = 0,
    val name: String,
    val photoPath: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    /** Where this person's calls should be recognised. Nothing outside this set ever matches. */
    val platforms: Set<Platform> = emptySet(),
    val identities: List<PlatformIdentity> = emptyList(),
    val priority: PriorityLevel = PriorityLevel.RING_THROUGH,
    val enabled: Boolean = true,
)

/** The exact name a calling app shows for this person, e.g. the WhatsApp name "Alishuu ❤️". */
data class PlatformIdentity(
    val platform: Platform,
    val displayName: String,
)

enum class Platform(val label: String) {
    SIM("Phone"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    MESSENGER("Messenger"),
    INSTAGRAM("Instagram"),
    SNAPCHAT("Snapchat");

    /** Apps tell us about calls through notifications; SIM calls come from the phone itself. */
    val isApp: Boolean get() = this != SIM

    companion object {
        val apps: List<Platform> = entries.filter { it.isApp }
    }
}

enum class PriorityLevel {
    RING_THROUGH,
    MISSED_ALERT_ONLY,
}
