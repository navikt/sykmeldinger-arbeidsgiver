package no.nav.syfo.sykmelding

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

internal class SykmeldingServiceTest : Spek({

    val database = mockk<DatabaseInterface>(relaxed = true)
    val sykmeldingsService = SykmeldingService(database)

    mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingDbKt")

    describe("Test av SykmeldingsService") {

        val ansatt = Sykmeldt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = true,
            sykmeldinger = null
        )
        val inaktivAnsatt = Sykmeldt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = false,
            sykmeldinger = null
        )

        it("getSykmeldt returnerer ansatt med aktivSykmelding = false") {

            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.of(2021, 10, 1),
                        tom = LocalDate.of(2021, 10, 9)
                    )
                )
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr") shouldBeEqualTo inaktivAnsatt
        }

        it("getSykmeldt returnerer ansatt med aktivSykmelding = true") {

            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(1)
                    )
                )
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr") shouldBeEqualTo ansatt
        }
        it("Getsykmeldt should return aktiv = true") {
            every { database.getArbeidsgiverSykmeldinger(any(), any()) } returns listOf(
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now().minusDays(10),
                        tom = LocalDate.now().minusDays(8)
                    )
                ),
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.now().minusDays(7),
                        tom = LocalDate.now().plusDays(8)
                    )
                ),
            )
            sykmeldingsService.getSykmeldt("lederId", "fnr")?.aktivSykmelding shouldBeEqualTo true
        }
    }
})
