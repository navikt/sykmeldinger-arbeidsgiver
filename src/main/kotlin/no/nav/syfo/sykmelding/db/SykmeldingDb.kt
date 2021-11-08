package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import no.nav.syfo.util.objectMapper
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate

private fun toPGObject(obj: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(obj)
}

private fun insertOrUpdateSykmeldt(
    connection: Connection,
    latestTom: LocalDate,
    fnr: String,
    navn: String
) {
    connection.prepareStatement(
        """
            insert into sykmeldt(pasient_fnr, pasient_navn, latest_tom)
                values (?,?,?)
            on conflict (pasient_fnr) do update
                set pasient_navn = ?,
                    latest_tom = ?;
        """
    ).use { ps ->
        var index = 1
        var latestDate = Date.valueOf(latestTom)
        // insert
        ps.setString(index++, fnr)
        ps.setString(index++, navn)
        ps.setDate(index++, latestDate)
        // update
        ps.setString(index++, navn)
        ps.setDate(index, latestDate)
        ps.execute()
    }
}

fun DatabaseInterface.insertOrUpdateSykmeldingArbeidsgiver(
    sendtSykmeldingKafkaMessage: SykmeldingArbeidsgiverKafkaMessage,
    person: PdlPerson,
    latestTom: LocalDate
) {
    connection.use {
        insertOrUpdateArbeidsgiverSykmelding(
            connection = it,
            sendtSykmeldingKafkaMessage = sendtSykmeldingKafkaMessage,
            latestTom = latestTom
        )
        insertOrUpdateSykmeldt(
            connection = it,
            fnr = sendtSykmeldingKafkaMessage.kafkaMetadata.fnr,
            navn = person.navn.toFormattedNameString(),
            latestTom = latestTom
        )
        it.commit()
    }
}

fun DatabaseInterface.deleteSykmelding(key: String) {
    connection.use {
        it.prepareStatement(
            """
            DELETE from sykmelding_arbeidsgiver where sykmelding_id = ?
        """
        ).use { ps ->
            ps.setString(1, key)
            ps.execute()
        }
        it.commit()
    }
}

private fun insertOrUpdateArbeidsgiverSykmelding(
    connection: Connection,
    sendtSykmeldingKafkaMessage: SykmeldingArbeidsgiverKafkaMessage,
    latestTom: LocalDate
) {
    connection.prepareStatement(
        """
             INSERT INTO sykmelding_arbeidsgiver(sykmelding_id, pasient_fnr, orgnummer, juridisk_orgnummer, timestamp, latest_tom, orgnavn, sykmelding) 
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (sykmelding_id) do update 
                    set pasient_fnr = ?,
                        orgnummer = ?,
                        juridisk_orgnummer = ?,
                        timestamp = ?,
                        latest_tom = ?,
                        orgnavn = ?,
                        sykmelding = ?;
         """
    ).use { ps ->
        val sykmeldingId = sendtSykmeldingKafkaMessage.sykmelding.id
        val pasientFnr = sendtSykmeldingKafkaMessage.kafkaMetadata.fnr
        val orgnummer = sendtSykmeldingKafkaMessage.event.arbeidsgiver!!.orgnummer
        val juridiskOrgnummer = sendtSykmeldingKafkaMessage.event.arbeidsgiver!!.juridiskOrgnummer
        val timestamp = Timestamp.from(sendtSykmeldingKafkaMessage.event.timestamp.toInstant())
        val sykmelding = toPGObject(sendtSykmeldingKafkaMessage.sykmelding)
        val orgnavn = sendtSykmeldingKafkaMessage.event.arbeidsgiver!!.orgNavn
        val latestDate = Date.valueOf(latestTom)

        var index = 1
        // INSERT
        ps.setString(index++, sykmeldingId)
        ps.setString(index++, pasientFnr)
        ps.setString(index++, orgnummer)
        ps.setString(index++, juridiskOrgnummer)
        ps.setTimestamp(index++, timestamp)
        ps.setDate(index++, latestDate)
        ps.setString(index++, orgnavn)
        ps.setObject(index++, sykmelding)
        // UPDATE
        ps.setString(index++, pasientFnr)
        ps.setString(index++, orgnummer)
        ps.setString(index++, juridiskOrgnummer)
        ps.setTimestamp(index++, timestamp)
        ps.setDate(index++, latestDate)
        ps.setString(index++, orgnavn)
        ps.setObject(index, sykmelding)
        ps.execute()
    }
}

fun DatabaseInterface.getArbeidsgiverSykmeldinger(lederFnr: String): List<SykmeldingArbeidsgiverV2> {
    return connection.use { connection ->
        connection.prepareStatement(
            """select nl.narmeste_leder_id, sa.sykmelding, nl.pasient_fnr, s.pasient_navn, nl.orgnummer, sa.orgnavn from narmesteleder as nl
                        inner join sykmelding_arbeidsgiver as sa on sa.pasient_fnr = nl.pasient_fnr and sa.orgnummer = nl.orgnummer
                        inner join sykmeldt as s on s.pasient_fnr = nl.pasient_fnr
                    where nl.leder_fnr = ?;
                """
        ).use { ps ->
            ps.setString(1, lederFnr)
            ps.executeQuery().toList { toArbeidsgiverSykmeldingV2() }
        }
    }
}

fun DatabaseInterface.getArbeidsgiverSykmeldinger(lederFnr: String, narmestelederId: String): List<SykmeldingArbeidsgiverV2> {
    return connection.use { connection ->
        connection.prepareStatement(
            """select nl.narmeste_leder_id, sa.sykmelding, nl.pasient_fnr, s.pasient_navn, nl.orgnummer, sa.orgnavn from narmesteleder as nl
                    inner join sykmelding_arbeidsgiver as sa on sa.pasient_fnr = nl.pasient_fnr and sa.orgnummer = nl.orgnummer
                    inner join sykmeldt as s on s.pasient_fnr = nl.pasient_fnr
                    where nl.leder_fnr = ?
                       and nl.narmeste_leder_id = ?;
                """
        ).use { ps ->
            ps.setString(1, lederFnr)
            ps.setString(2, narmestelederId)
            ps.executeQuery().toList { toArbeidsgiverSykmeldingV2() }
        }
    }
}

fun DatabaseInterface.deleteSykmeldinger(date: LocalDate): DeleteResult {
    return connection.use { connection ->
        val result = DeleteResult(
            deletedSykmelding = deleteSykmeldinger(connection, date),
            deletedSykmeldt = deleteSykmeldte(connection, date)
        )
        connection.commit()
        return result
    }
}

private fun deleteSykmeldte(connection: Connection, date: LocalDate): Int {
    return connection.prepareStatement("""delete from sykmeldt where latest_tom < ?;""").use { ps ->
        ps.setDate(1, Date.valueOf(date))
        ps.executeUpdate()
    }
}

private fun deleteSykmeldinger(connection: Connection, date: LocalDate): Int {
    return connection.prepareStatement("""delete from sykmelding_arbeidsgiver where latest_tom < ?;""").use { ps ->
        ps.setDate(1, Date.valueOf(date))
        ps.executeUpdate()
    }
}

fun ResultSet.toArbeidsgiverSykmeldingV2(): SykmeldingArbeidsgiverV2 {
    return SykmeldingArbeidsgiverV2(
        narmestelederId = getString("narmeste_leder_id"),
        pasientFnr = getString("pasient_fnr"),
        orgnummer = getString("orgnummer"),
        orgNavn = getString("orgnavn") ?: "",
        navn = getString("pasient_navn"),
        sykmelding = objectMapper.readValue(getString("sykmelding"))
    )
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
