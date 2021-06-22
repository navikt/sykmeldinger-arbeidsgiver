package no.nav.syfo.dinesykmeldte.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.narmesteleder.client.NarmestelederClient
import no.nav.syfo.narmesteleder.client.model.Ansatt
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.enkelSykmelding
import no.nav.syfo.sykmelding.model.ArbeidsgiverSykmelding
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class DineSykmeldteServiceTest : Spek({
    val narmestelederClient = mockk<NarmestelederClient>()
    val sykmeldingService = mockk<SykmeldingService>()

    val dineSykmeldteService = DineSykmeldteService(narmestelederClient, sykmeldingService)

    describe("Get sykmeldinger") {
        it("get empty response from both") {
            coEvery { narmestelederClient.getAnsatte("token") } returns emptyList()
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns emptyList()

            runBlocking {
                val sykmeldinger = dineSykmeldteService.getDineSykmeldte("token")
                sykmeldinger.size shouldBeEqualTo 0
            }
        }

        it("get empty response from database") {
            coEvery { narmestelederClient.getAnsatte("token") } returns listOf(Ansatt("1", "navn", "2", UUID.randomUUID()))
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns emptyList()

            runBlocking {
                val sykmeldinger = dineSykmeldteService.getDineSykmeldte("token")
                sykmeldinger.size shouldBeEqualTo 0
            }
        }

        it("Get sykmeldinger for ansatt") {
            coEvery { narmestelederClient.getAnsatte("token") } returns listOf(Ansatt("1", "navn", "2", UUID.randomUUID()))
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(ArbeidsgiverSykmelding("1", "2", "3", "navn",enkelSykmelding()))

            runBlocking {
                val sykmeldinger = dineSykmeldteService.getDineSykmeldte("token")
                sykmeldinger.size shouldBeEqualTo 1
            }
        }

        it("Should filter out sykmeldinger on different orgnr") {
            coEvery { narmestelederClient.getAnsatte("token") } returns listOf(Ansatt("1", "navn", "2", UUID.randomUUID()))
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(ArbeidsgiverSykmelding("1", "3", "3", "navn",enkelSykmelding()))

            runBlocking {
                val sykmeldinger = dineSykmeldteService.getDineSykmeldte("token")
                sykmeldinger.size shouldBeEqualTo 0
            }
        }
        it("should get sykmeldinger from multiple ansatte") {
            coEvery { narmestelederClient.getAnsatte("token") } returns listOf(
                Ansatt("1", "navn", "2", UUID.randomUUID()),
                Ansatt("2", "navn", "2", UUID.randomUUID())
            )
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(
                ArbeidsgiverSykmelding("1", "2", "3", "navn",  enkelSykmelding()),
                ArbeidsgiverSykmelding("2", "2", "4", "navn",enkelSykmelding())
            )

            runBlocking {
                val sykmeldinger = dineSykmeldteService.getDineSykmeldte("token")
                sykmeldinger.size shouldBeEqualTo 2
            }
        }
        it("Should get one ansatt") {
            val nlId = UUID.randomUUID()
            coEvery { narmestelederClient.getAnsatt(nlId.toString(), "token") } returns Ansatt("1", "navn", "3", nlId)
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(ArbeidsgiverSykmelding("1", "3", "3", "navn", enkelSykmelding()))
            runBlocking {
                val sykmeldt = dineSykmeldteService.getSykmeldt(nlId.toString(), "token")
                sykmeldt shouldNotBe null
                sykmeldt!!.sykmeldinger.size shouldBeEqualTo 1
            }
        }

        it("Should get one ansatt, filter out other sykmeldinger") {
            val nlId = UUID.randomUUID()
            coEvery { narmestelederClient.getAnsatt(nlId.toString(), "token") } returns Ansatt("1", "navn", "2", nlId)
            coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(ArbeidsgiverSykmelding("1", "3", "3", "navn", enkelSykmelding()))
            runBlocking {
                val sykmeldt = dineSykmeldteService.getSykmeldt(nlId.toString(), "token")
                sykmeldt shouldNotBe null
                sykmeldt!!.sykmeldinger.size shouldBeEqualTo 0
            }
        }

        it("Should get null for ansatt") {
            val nlId = UUID.randomUUID()
            coEvery { narmestelederClient.getAnsatt(nlId.toString(), "token") } returns null
            runBlocking {
                val sykmeldt = dineSykmeldteService.getSykmeldt(nlId.toString(), "token")
                sykmeldt shouldBe null
            }
        }
    }
})
