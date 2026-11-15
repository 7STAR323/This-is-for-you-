package com.ares.callguard.data

import com.ares.callguard.data.db.PeopleDao
import com.ares.callguard.data.db.PersonEntity
import com.ares.callguard.data.db.PersonWithDetails
import com.ares.callguard.domain.model.Person
import com.ares.callguard.domain.model.PlatformIdentity
import com.ares.callguard.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPeopleRepository @Inject constructor(
    private val dao: PeopleDao,
) : PeopleRepository {

    override val people: Flow<List<Person>> =
        dao.observeAll().map { rows -> rows.map { it.toPerson() } }

    override suspend fun currentPeople(): List<Person> = dao.getAll().map { it.toPerson() }

    override suspend fun person(id: Long): Person? = dao.get(id)?.toPerson()

    override suspend fun save(person: Person): Long = dao.save(
        person = PersonEntity(
            id = person.id,
            name = person.name.trim(),
            photoPath = person.photoPath,
            priority = person.priority,
            enabled = person.enabled,
            platforms = person.platforms,
        ),
        numbers = person.phoneNumbers.map(String::trim).filter(String::isNotEmpty).distinct(),
        identities = person.identities
            .map { it.platform to it.displayName.trim() }
            .filter { (_, name) -> name.isNotEmpty() }
            .distinct(),
    )

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun remove(id: Long) = dao.delete(id)

    private fun PersonWithDetails.toPerson() = Person(
        id = person.id,
        name = person.name,
        photoPath = person.photoPath,
        phoneNumbers = numbers.map { it.number },
        platforms = person.platforms,
        identities = identities.map { PlatformIdentity(it.platform, it.displayName) },
        priority = person.priority,
        enabled = person.enabled,
    )
}
