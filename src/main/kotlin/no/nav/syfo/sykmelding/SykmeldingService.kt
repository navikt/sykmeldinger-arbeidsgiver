package no.nav.syfo.sykmelding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.model.ArbeidsgiverSykmelding

class SykmeldingService(val database: DatabaseInterface) {
    fun getSykmeldinger(fnrs: List<String>): List<ArbeidsgiverSykmelding> {
        return database.getSykmeldinger(fnrs)
    }
}
