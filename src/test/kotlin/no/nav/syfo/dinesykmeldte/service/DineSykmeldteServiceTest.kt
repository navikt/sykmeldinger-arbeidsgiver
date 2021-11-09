package no.nav.syfo.dinesykmeldte.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class DineSykmeldteServiceTest : Spek({
    val sykmeldingService = mockk<SykmeldingService>()

    val dineSykmeldteService = DineSykmeldteService(sykmeldingService)

    describe("Get sykmeldinger") {
        it("Should get SykmeldingerArbeidsgiverV2") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(SykmeldingArbeidsgiverV2("lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn", getArbeidsgiverSykmelding(sykmeldingsId = "123")))
            runBlocking {
                val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
                sykmeldte.size shouldBeEqualTo 1
                sykmeldte.first().aktivSykmelding shouldBeEqualTo true
            }
        }

        it("Should return aktivSykmelding false for old sick leave") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(
                SykmeldingArbeidsgiverV2(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123", fom = LocalDate.of(2020, 1, 1), tom = LocalDate.of(2020, 1, 7))
                )
            )
            runBlocking {
                val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
                sykmeldte.size shouldBeEqualTo 1
                sykmeldte.first().aktivSykmelding shouldBeEqualTo false
            }
        }

        it("getSykmeldinger() should groupBy") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(
                SykmeldingArbeidsgiverV2(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123")
                ),
                SykmeldingArbeidsgiverV2(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123", fom = LocalDate.of(2020, 1, 1), tom = LocalDate.of(2020, 1, 7))
                )
            )
            runBlocking {
                val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
                sykmeldte.size shouldBeEqualTo 1
            }
        }
    }
})
