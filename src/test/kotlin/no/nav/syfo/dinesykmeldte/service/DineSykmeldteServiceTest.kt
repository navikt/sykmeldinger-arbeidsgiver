package no.nav.syfo.dinesykmeldte.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiver
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class DineSykmeldteServiceTest : FunSpec({
    val sykmeldingService = mockk<SykmeldingService>()

    val dineSykmeldteService = DineSykmeldteService(sykmeldingService)

    context("Get sykmeldinger") {
        test("Should get SykmeldingerArbeidsgiverV2") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(SykmeldingArbeidsgiver(
                "lederFnr",
                "Fornavn Etternavn",
                "pasientFnr",
                "orgnummer",
                "Orgnavn",
                getArbeidsgiverSykmelding(sykmeldingsId = "123"),
                null,
            ))
            val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
            sykmeldte.size shouldBeEqualTo 1
            sykmeldte.first().aktivSykmelding shouldBeEqualTo true
        }

        test("Should return aktivSykmelding false for old sick leave") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(
                SykmeldingArbeidsgiver(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123", fom = LocalDate.of(2020, 1, 1), tom = LocalDate.of(2020, 1, 7)),
                    null,
                )
            )
            val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
            sykmeldte.size shouldBeEqualTo 1
            sykmeldte.first().aktivSykmelding shouldBeEqualTo false
        }

        test("getSykmeldinger() should groupBy") {
            every { sykmeldingService.getSykmeldinger("123") } returns listOf(
                SykmeldingArbeidsgiver(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123"),
                    null,
                ),
                SykmeldingArbeidsgiver(
                    "lederFnr", "Fornavn Etternavn", "pasientFnr", "orgnummer", "Orgnavn",
                    getArbeidsgiverSykmelding(sykmeldingsId = "123", fom = LocalDate.of(2020, 1, 1), tom = LocalDate.of(2020, 1, 7)),
                    null,
                )
            )
            val sykmeldte = dineSykmeldteService.getDineSykmeldte("123")
            sykmeldte.size shouldBeEqualTo 1
        }
    }
})
