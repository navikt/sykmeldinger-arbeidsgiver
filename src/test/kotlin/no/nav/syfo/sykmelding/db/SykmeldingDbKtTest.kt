package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.PrognoseAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.model.sykmelding.model.BehandlerDTO
import no.nav.syfo.model.sykmelding.model.ErIArbeidDTO
import no.nav.syfo.model.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.PrognoseDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class SykmeldingDbKtTest : Spek({

    val mockEnv = mockk<Environment>(relaxed = true)
    every { mockEnv.databaseUsername } returns "username"
    every { mockEnv.databasePassword } returns "password"

    val psqlContainer = PsqlContainer()
        .withExposedPorts(5432)
        .withUsername("username")
        .withPassword("password")
        .withDatabaseName("database")
        .withInitScript("db/testdb-init.sql")

    psqlContainer.start()

    every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl

    val database: DatabaseInterface = Database(mockEnv)

    describe("test database") {
        it("Test saving") {
            database.insertOrUpdateSykmelding(getSykmeldingSendtMessage())
        }
        it("test get sykmelding") {
            val sykmeldinger = database.getSykmeldinger(listOf("12345678901"))
            sykmeldinger.size shouldBeEqualTo 1
        }
        it("Test updating") {
            val sykmelding = getSykmeldingSendtMessage()
            database.insertOrUpdateSykmelding(sykmelding)
            database.insertOrUpdateSykmelding(getSykmeldingSendtMessage().copy(event = sykmelding.event.copy(arbeidsgiver = sykmelding.event.arbeidsgiver!!.copy(orgNavn = "TEST"))))
            val sykmeldinger = database.getSykmeldinger(listOf("12345678901"))
            sykmeldinger[0].orgNavn shouldBeEqualTo "TEST"
        }
        it("get empty list") {
            val sykmeldinger = database.getSykmeldinger(listOf("12345678900"))
            (0 until 100).forEach {
                database.getSykmeldinger(listOf("12345678900"))
                database.getSykmeldinger(listOf("12345678900"))
            }

            sykmeldinger.size shouldBeEqualTo 0
        }
        it("Get correct orgName") {
            val sykmelding = getSykmeldingSendtMessage()
            val event = sykmelding.event.copy(arbeidsgiver = sykmelding.event.arbeidsgiver!!.copy(orgNavn = "CORRECT NAME"))
            database.insertOrUpdateSykmelding(getSykmeldingSendtMessage().copy(event = event))

            val saved = database.getSykmeldinger(listOf("12345678901")).first()
            saved.orgNavn shouldBeEqualTo "CORRECT NAME"
        }

        it("handle sykmeldinger with juridisk_orgnummer == null") {
            val sykmelding = getSykmeldingSendtMessage()
            val event = sykmelding.event.copy(arbeidsgiver = sykmelding.event.arbeidsgiver!!.copy(orgNavn = "CORRECT NAME", juridiskOrgnummer = null))
            database.insertOrUpdateSykmelding(getSykmeldingSendtMessage().copy(event = event))

            val saved = database.getSykmeldinger(listOf("12345678901")).first()
            saved.orgNavn shouldBeEqualTo "CORRECT NAME"
        }

        it("Save ArbeidsgiverSykmelding") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            val saved = database.getArbeidsgiverSykmeldinger(listOf("12345678901"))

            val savedSykmelding = saved.first()
            savedSykmelding.navn shouldBeEqualTo "Fornavn Mellomnavn Etternavn"
            savedSykmelding.orgNavn shouldBeEqualTo arbeidsgiverSykmelding.event.arbeidsgiver?.orgNavn
            savedSykmelding.pasientFnr shouldBeEqualTo arbeidsgiverSykmelding.kafkaMetadata.fnr
            savedSykmelding.sykmelding shouldBeEqualTo arbeidsgiverSykmelding.sykmelding
        }

        it("Save ArbeidsgiverSykmelding with updated name and tom") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val newSykmelding = arbeidsgiverSykmelding.copy(sykmelding = arbeidsgiverSykmelding.sykmelding.copy(id = "1234", sykmeldingsperioder = arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.map { it.copy(fom = LocalDate.now().plusDays(10), tom = LocalDate.now().plusDays(20)) }))
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.insertOrUpdateSykmeldingArbeidsgiver(newSykmelding,  person.copy(Navn("Test", null, "Tester")), newSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })


            val saved = database.getArbeidsgiverSykmeldinger(listOf("12345678901"))
            saved.filter { it.sykmelding == newSykmelding.sykmelding }.size shouldBeEqualTo 1
            saved.filter { it.sykmelding == arbeidsgiverSykmelding.sykmelding }.size shouldBeEqualTo 1

            saved.forEach {
                it.navn shouldBeEqualTo "Test Tester"
                it.orgNavn shouldBeEqualTo arbeidsgiverSykmelding.event.arbeidsgiver?.orgNavn
                it.pasientFnr shouldBeEqualTo arbeidsgiverSykmelding.kafkaMetadata.fnr
            }
        }
    }
})


private fun DatabaseInterface.getArbeidsgiverSykmeldinger(fnrs: List<String>): List<SykmeldingArbeidsgiverV2> {
    return connection.use { connection ->
        connection.prepareStatement("""SELECT * FROM sykmelding_arbeidsgiver as sa inner join sykmeldt as s on sa.pasient_fnr = s.pasient_fnr where sa.pasient_fnr = ANY (?)""")
            .use {
                it.setArray(1, connection.createArrayOf("VARCHAR", fnrs.toTypedArray()))
                it.executeQuery().toList { toSykmeldingArbeidsgiverV2() }
            }
    }
}

fun ResultSet.toSykmeldingArbeidsgiverV2() : SykmeldingArbeidsgiverV2 {
    return SykmeldingArbeidsgiverV2(
        pasientFnr = getString("pasient_fnr"),
        orgnummer = getString("orgnummer"),
        orgNavn = getString("orgnavn") ?: "",
        sykmelding = objectMapper.readValue(getString("sykmelding")),
        navn = getString("pasient_navn")
    )

}


fun getSykmeldingArbeidsgiverKafkaMessage(fom: LocalDate, tom: LocalDate): SykmeldingArbeidsgiverKafkaMessage {
    return SykmeldingArbeidsgiverKafkaMessage(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = "213",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            statusEvent = "SENDT",
            arbeidsgiver = ArbeidsgiverStatusDTO(
                orgnummer = "123456789",
                juridiskOrgnummer = "234567891",
                orgNavn = "arbeidsgiver"
            ),
            emptyList()
        ),
        kafkaMetadata = KafkaMetadataDTO(
            "213", OffsetDateTime.now(ZoneOffset.UTC), fnr = "12345678901", source = "user"
        ),
        sykmelding = getArbeidsgiverSykmelding(fom = fom ,tom = tom)
    )
}

fun getArbeidsgiverSykmelding(fom: LocalDate = LocalDate.now(), tom: LocalDate = LocalDate.now()): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = "123",
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        meldingTilArbeidsgiver = "",
        tiltakArbeidsplassen = "",
        syketilfelleStartDato = null,
        behandler = BehandlerAGDTO(
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            hpr = null,
            adresse = AdresseDTO(null, null, null, null, null),
            tlf = null
        ),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = fom,
                tom = tom,
                gradert = null,
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = null,
                reisetilskudd = false
            )
        ),
        arbeidsgiver = ArbeidsgiverAGDTO(
            navn = "arbeidsgiver",
            yrkesbetegnelse = "yrke"
        ),
        kontaktMedPasient = KontaktMedPasientAGDTO(
            kontaktDato = LocalDate.now()
        ),
        prognose = PrognoseAGDTO(
            arbeidsforEtterPeriode = true,
            hensynArbeidsplassen = null
        ),
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = emptyList()
    )
}

fun getSykmeldingSendtMessage(): SendtSykmeldingKafkaMessage {
    return SendtSykmeldingKafkaMessage(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = "213",
            timestamp = OffsetDateTime.now(),
            statusEvent = "SENDT",
            arbeidsgiver = ArbeidsgiverStatusDTO(
                orgnummer = "123456789",
                juridiskOrgnummer = "234567891",
                orgNavn = "arbeidsgiver"
            ),
            emptyList()
        ),
        kafkaMetadata = KafkaMetadataDTO(
            "213", OffsetDateTime.now(), fnr = "12345678901", source = "user"
        ),
        sykmelding = enkelSykmelding()
    )
}

fun enkelSykmelding() = EnkelSykmelding(
    id = "123",
    mottattTidspunkt = OffsetDateTime.now(),
    legekontorOrgnummer = "123456789",
    behandletTidspunkt = OffsetDateTime.now(),
    meldingTilArbeidsgiver = "",
    navnFastlege = null,
    tiltakArbeidsplassen = "",
    syketilfelleStartDato = null,
    behandler = BehandlerDTO(
        fornavn = "fornavn",
        mellomnavn = null,
        etternavn = "etternavn",
        aktoerId = "aktorId",
        fnr = "legefnr",
        hpr = null,
        her = null,
        adresse = AdresseDTO(null, null, null, null, null),
        tlf = null
    ),
    sykmeldingsperioder = listOf(
        SykmeldingsperiodeDTO(
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            gradert = null,
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            aktivitetIkkeMulig = null,
            reisetilskudd = false
        )
    ),
    arbeidsgiver = ArbeidsgiverDTO(
        navn = "arbeidsgiver",
        stillingsprosent = 100
    ),
    kontaktMedPasient = KontaktMedPasientDTO(
        kontaktDato = LocalDate.now(),
        begrunnelseIkkeKontakt = null
    ),
    prognose = PrognoseDTO(
        arbeidsforEtterPeriode = true,
        hensynArbeidsplassen = null,
        erIArbeid = ErIArbeidDTO(
            false, false, null, null
        ),
        erIkkeIArbeid = null
    ),
    egenmeldt = false,
    papirsykmelding = false,
    harRedusertArbeidsgiverperiode = false,
    merknader = emptyList()
)
