package no.nav.syfo.dinesykmeldte.api.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.kafka.model.DineSykmeldteLestStatusKafkaMessage
import no.nav.syfo.dinesykmeldte.kafka.model.NLReadCount
import no.nav.syfo.util.objectMapper
import no.nav.syfo.util.toPGObject
import java.sql.ResultSet

fun DatabaseInterface.insertOrUpdateReadStatus(
    dineSykmeldteLestStatusKafkaMessage: DineSykmeldteLestStatusKafkaMessage,
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
        INSERT INTO narmesteleder_read_status (narmesteleder_id, read_status)
        VALUES (?, ?)
        ON CONFLICT (narmesteleder_id) DO UPDATE
            SET narmesteleder_id = ?,
                read_status      = ?
            """.trimIndent()
        ).use { ps ->
            val narmestelederId = dineSykmeldteLestStatusKafkaMessage.nlReadCount.narmestelederId
            val readCount = toPGObject(dineSykmeldteLestStatusKafkaMessage.nlReadCount)

            // insert
            ps.setString(1, narmestelederId)
            ps.setObject(2, readCount)

            // update
            ps.setString(3, narmestelederId)
            ps.setObject(4, readCount)

            ps.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.deleteReadStatus(narmestelederId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
        DELETE FROM narmesteleder_read_status
        WHERE narmesteleder_id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, narmestelederId)
            ps.execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.getReadStatusForNarmesteleder(narmestelederId: String): NLReadCount? =
    connection.use { connection ->
        connection.prepareStatement(
            """
        SELECT read_status
        FROM narmesteleder_read_status
        WHERE narmesteleder_id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, narmestelederId)
            ps.executeQuery().toReadCount()
        }
    }

private fun ResultSet.toReadCount(): NLReadCount? {
    return when (next()) {
        false -> null
        else -> objectMapper.readValue(getString("read_status"))
    }
}
