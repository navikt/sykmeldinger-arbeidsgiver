package no.nav.syfo.sykmelding

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.model.Ansatt
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

        val ansatt = Ansatt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = true
        )
        val inaktivAnsatt = Ansatt(
            fnr = "pasientFnr",
            navn = "Fornavn Etternavn",
            orgnummer = "orgnummer",
            narmestelederId = "lederId",
            aktivSykmelding = false
        )

        it("getSykmeldt filtrerer på dato") {

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
                ),
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.of(2021, 10, 10),
                        tom = LocalDate.of(2021, 10, 20)
                    )
                ),
                SykmeldingArbeidsgiverV2(
                    "lederId",
                    "Fornavn Etternavn",
                    "pasientFnr",
                    "orgnummer",
                    "Orgnavn",
                    getArbeidsgiverSykmelding(
                        fom = LocalDate.of(2021, 11, 21),
                        tom = LocalDate.of(2021, 11, 30)
                    )
                )
            )

            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 10, 1)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 10, 9)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 10, 10)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 10, 20)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 10, 21)) shouldBeEqualTo null
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 11, 21)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 11, 30)) shouldBeEqualTo ansatt
            sykmeldingsService.getSykmeldt("lederId", "fnr", LocalDate.of(2021, 11, 20)) shouldBeEqualTo null
        }

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
    }
})
