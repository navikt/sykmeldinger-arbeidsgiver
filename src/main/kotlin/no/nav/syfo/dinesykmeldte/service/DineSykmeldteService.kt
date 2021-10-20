package no.nav.syfo.dinesykmeldte.service

import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.util.toDineSykmeldteSykmelding
import no.nav.syfo.narmesteleder.client.NarmestelederClient
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiver
import java.time.LocalDate

data class ArbeidsgiverKey(
    val fnr: String,
    val orgnummer: String
)

class DineSykmeldteService(
    private val narmestelederClient: NarmestelederClient,
    private val sykmeldingService: SykmeldingService
) {
    suspend fun getDineSykmeldte(bearerToken: String): List<Sykmeldt> {
        val ansatte = narmestelederClient.getAnsatte(bearerToken).distinctBy { it.narmestelederId }.groupBy { ArbeidsgiverKey(it.fnr, it.orgnummer) }.mapValues { it.value.first() }
        val arbeidsgiverSykmelding = getArbeidsgiversSykmeldinger(ansatte.keys.map { it.fnr }.distinct()).groupBy { ArbeidsgiverKey(it.pasientFnr, it.orgnummer) }

        return arbeidsgiverSykmelding.map { entry ->
            when (val ansatt = ansatte[entry.key]) {
                null -> null
                else -> Sykmeldt(
                    narmestelederId = ansatt.narmestelederId.toString(),
                    orgnummer = ansatt.orgnummer,
                    fnr = ansatt.fnr,
                    navn = ansatt.navn,
                    sykmeldinger = entry.value.map { it.toDineSykmeldteSykmelding(ansatt) }
                )
            }
        }.filterNotNull()
    }

    private fun getArbeidsgiversSykmeldinger(fnr: List<String>): List<SykmeldingArbeidsgiver> {
        return sykmeldingService.getSykmeldinger(fnr)
            .filter { filterOutOldSykmeldinger(it) }
    }

    private fun filterOutOldSykmeldinger(it: SykmeldingArbeidsgiver) =
        it.sykmelding.sykmeldingsperioder.maxOf { periode -> periode.tom } >= LocalDate.now().minusMonths(4)

    suspend fun getSykmeldt(narmestelederId: String, bearerToken: String): Sykmeldt? {
        val ansatt = narmestelederClient.getAnsatt(narmestelederId, bearerToken)
        return when (ansatt) {
            null -> null
            else -> {
                val sykmeldinger = getArbeidsgiversSykmeldinger(listOf(ansatt.fnr)).filter { it.orgnummer == ansatt.orgnummer }
                when (sykmeldinger.isEmpty()) {
                    true -> null
                    else -> return Sykmeldt(
                        narmestelederId = narmestelederId,
                        orgnummer = ansatt.orgnummer,
                        fnr = ansatt.fnr,
                        navn = ansatt.navn,
                        sykmeldinger = null
                    )
                }
            }
        }
    }
}
