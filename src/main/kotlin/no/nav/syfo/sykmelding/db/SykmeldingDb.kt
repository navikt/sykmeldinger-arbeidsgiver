package no.nav.syfo.sykmelding.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.util.objectMapper
import org.postgresql.util.PGobject
import java.sql.Timestamp

private fun toPGObject(obj: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(obj)
}

fun DatabaseInterface.insertOrUpdateSykmelding(sendtSykmeldingKafkaMessage: SendtSykmeldingKafkaMessage) {
    connection.use {
        it.prepareStatement(
            """
             INSERT INTO sykmelding(sykmelding_id, pasient_fnr, orgnummer, juridisk_orgnummer, timestamp, sykmelding) 
                    values (?, ?, ?, ?, ?, ?)
                on conflict (sykmelding_id) do update 
                    set pasient_fnr = ?,
                        orgnummer = ?,
                        juridisk_orgnummer = ?,
                        timestamp = ?,
                        sykmelding =?
         """
        ).use { ps ->
            val sykmeldingId = sendtSykmeldingKafkaMessage.sykmelding.id
            val pasientFnr = sendtSykmeldingKafkaMessage.kafkaMetadata.fnr
            val orgnummer = sendtSykmeldingKafkaMessage.event.arbeidsgiver!!.orgnummer
            val juridiskOrgnummer = sendtSykmeldingKafkaMessage.event.arbeidsgiver!!.juridiskOrgnummer
            val timestamp = Timestamp.from(sendtSykmeldingKafkaMessage.event.timestamp.toInstant())
            val sykmelding = toPGObject(sendtSykmeldingKafkaMessage.sykmelding)

            var index = 1
            ps.setString(index++, sykmeldingId)
            ps.setString(index++, pasientFnr)
            ps.setString(index++, orgnummer)
            ps.setString(index++, juridiskOrgnummer)
            ps.setTimestamp(index++, timestamp)
            ps.setObject(index++, sykmelding)
            ps.setString(index++, pasientFnr)
            ps.setString(index++, orgnummer)
            ps.setString(index++, juridiskOrgnummer)
            ps.setTimestamp(index++, timestamp)
            ps.setObject(index, sykmelding)
            ps.execute()
        }
        it.commit()
    }
}

fun DatabaseInterface.deleteSykmelding(key: String) {
    connection.use {
        it.prepareStatement(
            """
            DELETE from sykmelding where sykmelding_id = ?
        """
        ).use { ps ->
            ps.setString(1, key)
            ps.execute()
        }
        it.commit()
    }
}
