package no.nav.syfo.sykmelding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.model.Ansatt
import no.nav.syfo.sykmelding.db.getAnsatt
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2

class SykmeldingService(val database: DatabaseInterface) {
    fun getSykmeldinger(lederFnr: String): List<SykmeldingArbeidsgiverV2> {
        return database.getArbeidsgiverSykmeldinger(lederFnr)
    }

    fun getSykmeldt(narmestelederId: String, fnr: String): Ansatt? {
        return database.getAnsatt(narmestelederId, fnr)
    }
}
