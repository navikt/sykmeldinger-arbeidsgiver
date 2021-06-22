package no.nav.syfo.sykmelding.db

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
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
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDate
import java.time.OffsetDateTime

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
        it("get empty list") {
            val sykmeldinger = database.getSykmeldinger(listOf("12345678900"))
            (0 until 100).forEach {
                database.getSykmeldinger(listOf("12345678900"))
                database.getSykmeldinger(listOf("12345678900"))
            }

            sykmeldinger.size shouldBeEqualTo 0
        }
    }
})

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
