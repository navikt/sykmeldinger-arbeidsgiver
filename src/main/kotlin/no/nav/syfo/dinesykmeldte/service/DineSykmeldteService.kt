package no.nav.syfo.dinesykmeldte.service

import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.util.isActive
import no.nav.syfo.dinesykmeldte.util.toDineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.util.toSykmeldt
import no.nav.syfo.narmesteleder.model.Ansatt
import no.nav.syfo.sykmelding.SykmeldingService
import java.time.LocalDate

class DineSykmeldteService(
    private val sykmeldingService: SykmeldingService
) {
    fun getSykmeldt(narmestelederId: String, fnr: String): Sykmeldt? {
        return sykmeldingService.getSykmeldt(narmestelederId, fnr)?.toSykmeldt()
    }

    fun getSykmeldt(narmestelederId: String, fnr: String, date: LocalDate): Sykmeldt? {
        return sykmeldingService.getSykmeldt(narmestelederId, fnr, date)?.toSykmeldt()
    }

    fun getDineSykmeldte(fnrLeder: String): List<Sykmeldt> {
        return sykmeldingService.getSykmeldinger(fnrLeder).groupBy {
            Ansatt(
                fnr = it.pasientFnr,
                navn = it.navn,
                orgnummer = it.orgnummer,
                narmestelederId = it.narmestelederId,
                aktivSykmelding = it.sykmelding.sykmeldingsperioder.isActive()
            )
        }
            .map { ansatt ->
                Sykmeldt(
                    narmestelederId = ansatt.key.narmestelederId,
                    orgnummer = ansatt.key.orgnummer,
                    fnr = ansatt.key.fnr,
                    ansatt.key.navn,
                    sykmeldinger = ansatt.value.map { it.toDineSykmeldteSykmelding(ansatt.key) },
                    aktivSykmelding = ansatt.key.aktivSykmelding
                )
            }
    }
}
