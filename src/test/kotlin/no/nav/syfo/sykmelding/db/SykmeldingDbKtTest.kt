package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.FunSpec
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
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.narmesteleder.db.NarmestelederDB
import no.nav.syfo.narmesteleder.kafka.model.Narmesteleder
import no.nav.syfo.narmesteleder.model.Ansatt
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class SykmeldingDbKtTest : FunSpec({

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
    val narmestelederDb = NarmestelederDB(database)

    val nlId = UUID.randomUUID()
    val nl = Narmesteleder(nlId, "12345678901", orgnummer = "123456789", narmesteLederFnr = "lederFnr", narmesteLederTelefonnummer = "telefon", narmesteLederEpost = "epost", aktivFom = LocalDate.now(), aktivTom = null, arbeidsgiverForskutterer = true, OffsetDateTime.now())
    narmestelederDb.insertOrUpdate(nl)

    beforeTest {
        database.connection.use {
            it.prepareStatement(
                """
                delete from sykmelding_arbeidsgiver;
                delete from narmesteleder;
                delete from sykmeldt;
                """.trimIndent()
            ).use { ps ->
                ps.execute()
            }
            it.commit()
        }
    }

    context("test database") {
        test("Save ArbeidsgiverSykmelding") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            narmestelederDb.insertOrUpdate(nl)
            val saved = database.getArbeidsgiverSykmeldinger("lederFnr")
            val savedSykmelding = saved.first()
            savedSykmelding.navn shouldBeEqualTo "Fornavn Mellomnavn Etternavn"
            savedSykmelding.orgNavn shouldBeEqualTo arbeidsgiverSykmelding.event.arbeidsgiver?.orgNavn
            savedSykmelding.pasientFnr shouldBeEqualTo arbeidsgiverSykmelding.kafkaMetadata.fnr
            savedSykmelding.sykmelding shouldBeEqualTo arbeidsgiverSykmelding.sykmelding
        }

        test("test should delete") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                fom = LocalDate.of(2021, 1, 1),
                tom = LocalDate.of(2021, 2, 1)
            )
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            val updated = database.deleteSykmeldinger(LocalDate.of(2021, 2, 2))
            updated.deletedSykmeldt shouldBeEqualTo 1
            updated.deletedSykmelding shouldBeEqualTo 1
        }

        test("test should not delete") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                fom = LocalDate.of(2021, 1, 1),
                tom = LocalDate.of(2021, 2, 1)
            )
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            val updated = database.deleteSykmeldinger(LocalDate.of(2021, 2, 1))
            updated.deletedSykmeldt shouldBeEqualTo 0
            updated.deletedSykmelding shouldBeEqualTo 0
        }

        test("Save ArbeidsgiverSykmelding with updated name and tom") {

            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val newSykmelding = arbeidsgiverSykmelding.copy(sykmelding = arbeidsgiverSykmelding.sykmelding.copy(id = "1234", sykmeldingsperioder = arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.map { it.copy(fom = LocalDate.now().plusDays(10), tom = LocalDate.now().plusDays(20)) }))
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            narmestelederDb.insertOrUpdate(nl)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.insertOrUpdateSykmeldingArbeidsgiver(newSykmelding, person.copy(Navn("Test", null, "Tester")), newSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })

            val saved = database.getArbeidsgiverSykmeldinger("lederFnr")
            saved.filter { it.sykmelding == newSykmelding.sykmelding }.size shouldBeEqualTo 1
            saved.filter { it.sykmelding == arbeidsgiverSykmelding.sykmelding }.size shouldBeEqualTo 1

            saved.forEach {
                it.navn shouldBeEqualTo "Test Tester"
                it.orgNavn shouldBeEqualTo arbeidsgiverSykmelding.event.arbeidsgiver?.orgNavn
                it.pasientFnr shouldBeEqualTo arbeidsgiverSykmelding.kafkaMetadata.fnr
            }
        }

        test("Get ArbeidsgiverSykmeldinger from leder fnr") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val uuid = UUID.randomUUID().toString()
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.getArbeidsgiverSykmeldinger("lederFnr").size shouldBeEqualTo 0
            database.getAnsatt(uuid, "lederFnr") shouldBe null
            narmestelederDb.insertOrUpdate(nl)
            database.getArbeidsgiverSykmeldinger("lederFnr").size shouldBeEqualTo 1
            database.getAnsatt(nl.narmesteLederId.toString(), "lederFnr") shouldBeEqualTo Ansatt("12345678901", "Fornavn Mellomnavn Etternavn", "123456789", nl.narmesteLederId.toString())
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding.copy(sykmelding = arbeidsgiverSykmelding.sykmelding.copy(id = "1234"), event = arbeidsgiverSykmelding.event.copy(arbeidsgiver = arbeidsgiverSykmelding.event.arbeidsgiver!!.copy(orgnummer = "123456788"))), person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.getArbeidsgiverSykmeldinger("lederFnr").size shouldBeEqualTo 1
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding.copy(sykmelding = arbeidsgiverSykmelding.sykmelding.copy(id = "12345")), person.copy(navn = person.navn.copy(mellomnavn = null)), arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.getArbeidsgiverSykmeldinger("lederFnr").size shouldBeEqualTo 2
            database.getAnsatt(nl.narmesteLederId.toString(), "lederFnr") shouldBeEqualTo Ansatt("12345678901", "Fornavn Etternavn", "123456789", nl.narmesteLederId.toString())
        }

        test("Get ArbeidsgiverSykmeldinger from leder fnr and narmeste leder id") {
            val arbeidsgiverSykmelding: SykmeldingArbeidsgiverKafkaMessage = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now()
            )
            val person = PdlPerson(navn = Navn("Fornavn", mellomnavn = "Mellomnavn", "Etternavn"), aktorId = null)
            database.insertOrUpdateSykmeldingArbeidsgiver(arbeidsgiverSykmelding, person, arbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            narmestelederDb.insertOrUpdate(nl)
            database.getArbeidsgiverSykmeldinger(lederFnr = nl.narmesteLederFnr, narmestelederId = nl.narmesteLederId.toString()).size shouldBeEqualTo 1

            val nyArbeidsgiverSykmelding = getSykmeldingArbeidsgiverKafkaMessage(
                LocalDate.now(),
                LocalDate.now(),
                sykmeldtFnr = "22221145712"
            )

            database.insertOrUpdateSykmeldingArbeidsgiver(nyArbeidsgiverSykmelding, person, nyArbeidsgiverSykmelding.sykmelding.sykmeldingsperioder.maxOf { it.tom })
            database.getArbeidsgiverSykmeldinger(lederFnr = nl.narmesteLederFnr, narmestelederId = nl.narmesteLederId.toString()).size shouldBeEqualTo 1
        }
    }
})

fun getSykmeldingArbeidsgiverKafkaMessage(fom: LocalDate, tom: LocalDate, sykmeldingsId: String = UUID.randomUUID().toString(), sykmeldtFnr: String = "12345678901"): SykmeldingArbeidsgiverKafkaMessage {
    return SykmeldingArbeidsgiverKafkaMessage(
        event = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingsId,
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
            sykmeldingsId, OffsetDateTime.now(ZoneOffset.UTC), fnr = sykmeldtFnr, source = "user"
        ),
        sykmelding = getArbeidsgiverSykmelding(fom = fom, tom = tom, sykmeldingsId = sykmeldingsId)
    )
}

fun getArbeidsgiverSykmelding(fom: LocalDate = LocalDate.now(), tom: LocalDate = LocalDate.now(), sykmeldingsId: String = "123"): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = sykmeldingsId,
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
