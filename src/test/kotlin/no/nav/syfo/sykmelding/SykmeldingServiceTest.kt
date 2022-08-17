package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiver
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class SykmeldingServiceTest : FunSpec({

    val database = mockk<DatabaseInterface>(relaxed = true)
    val sykmeldingsService = SykmeldingService(database)

    mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingDbKt")

    context("Test av SykmeldingsService") {

        val ansatt = Sykmeldt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = true,
            sykmeldinger = null,
            lestStatus = null,
        )
        val inaktivAnsatt = Sykmeldt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = false,
            sykmeldinger = null,
            lestStatus = null,
        )

        test("getSykmeldt returnerer ansatt med aktivSykmelding = false") {

            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiver(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.of(2021, 10, 1),
                        tom = LocalDate.of(2021, 10, 9)
                    ),
                    lestStatus = null,
                )
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr") shouldBeEqualTo inaktivAnsatt
        }

        test("getSykmeldt returnerer ansatt med aktivSykmelding = true") {

            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiver(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(1)
                    ),
                    null,
                )
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr") shouldBeEqualTo ansatt
        }
        test("Getsykmeldt should return aktiv = true") {
            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiver(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now().minusDays(10),
                        tom = LocalDate.now().minusDays(8)
                    ),
                    null,
                ),
                SykmeldingArbeidsgiver(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now().minusDays(7),
                        tom = LocalDate.now().plusDays(8)
                    ),
                    null,
                ),
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr")?.aktivSykmelding shouldBeEqualTo true
        }
    }
})
