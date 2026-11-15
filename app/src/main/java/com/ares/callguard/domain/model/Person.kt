package com.ares.callguard.domain.model

data class Person(
    val id: Long = 0,
    val name: String,
    val photoPath: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    val identities: List<PlatformIdentity> = emptyList(),
    val priority: PriorityLevel = PriorityLevel.RING_THROUGH,
    val enabled: Boolean = true,
) {
    val platforms: Set<Platform>
        get() = buildSet {
            if (phoneNumbers.isNotEmpty()) add(Platform.SIM)
            identities.forEach { add(it.platform) }
        }
}

/** The exact name a calling app shows for this person, e.g. the WhatsApp name "Alishuu ❤️". */
data class PlatformIdentity(
    val platform: Platform,
    val displayName: String,
)

enum class Platform(val label: String) {
    SIM("Phone"),
    WHATSAPP("WhatsApp"),
}

enum class PriorityLevel {
    RING_THROUGH,
    MISSED_ALERT_ONLY,
}
