package no.nav.syfo.sykmelding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.model.Ansatt
import no.nav.syfo.sykmelding.db.getAnsatt
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import java.time.LocalDate

class SykmeldingService(val database: DatabaseInterface) {
    fun getSykmeldinger(lederFnr: String): List<SykmeldingArbeidsgiverV2> {
        return database.getArbeidsgiverSykmeldinger(lederFnr)
    }

    fun getSykmeldt(narmestelederId: String, fnr: String): Ansatt? {
        return database.getAnsatt(narmestelederId, fnr)
    }

    fun getSykmeldt(narmestelederId: String, fnr: String, date: LocalDate): Ansatt? {
        return database.getArbeidsgiverSykmeldinger(lederFnr = fnr, narmestelederId = narmestelederId).firstOrNull {
            it.sykmelding.sykmeldingsperioder.any { sykmeldingsdato ->
                !sykmeldingsdato.fom.isAfter(date) && !date.isAfter(sykmeldingsdato.tom)
            }
        }?.let {
            Ansatt(
                fnr = it.pasientFnr,
                navn = it.navn,
                orgnummer = it.orgnummer,
                narmestelederId = it.narmestelederId
            )
        }
    }
}
