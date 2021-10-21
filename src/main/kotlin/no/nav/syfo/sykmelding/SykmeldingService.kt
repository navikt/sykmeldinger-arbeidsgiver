package no.nav.syfo.sykmelding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiver

class SykmeldingService(val database: DatabaseInterface) {
    fun getSykmeldinger(fnrs: List<String>): List<SykmeldingArbeidsgiver> {
        return database.getSykmeldinger(fnrs)
    }
}
