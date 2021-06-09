package no.nav.syfo.dinesykmeldte.service

import no.nav.syfo.dinesykmeldte.model.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.util.toDineSykmeldteSykmelding
import no.nav.syfo.narmesteleder.client.NarmestelederClient
import no.nav.syfo.narmesteleder.client.model.Ansatt
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.ArbeidsgiverSykmelding
import java.time.LocalDate

class DineSykmeldteService(
    val narmestelederClient: NarmestelederClient,
    val sykmeldingService: SykmeldingService
) {
    suspend fun getDineSykmeldte(bearerToken: String): Map<Ansatt, List<DineSykmeldteSykmelding>?> {
        val ansatte = narmestelederClient.getAnsatte(bearerToken).distinctBy { it.narmestelederId }
        val arbeidsgiverSykmelding = getArbeidsgiversSykmeldinger(ansatte).groupBy { it.pasient.fnr }

        return ansatte.associateWith { ansatt -> arbeidsgiverSykmelding[ansatt.fnr] }
    }

    private fun getArbeidsgiversSykmeldinger(ansatte: List<Ansatt>): List<DineSykmeldteSykmelding> {
        return sykmeldingService.getSykmeldinger(ansatte.map { it.fnr })
            .filter { filterOutOldSykmeldinger(it) }
            .map { it.toDineSykmeldteSykmelding(ansatte.first { ansatt -> ansatt.fnr == it.pasientFnr }) }
    }

    private fun filterOutOldSykmeldinger(it: ArbeidsgiverSykmelding) =
        it.sykmelding.sykmeldingsperioder.maxOf { periode -> periode.tom } >= LocalDate.now().minusMonths(4)
}
