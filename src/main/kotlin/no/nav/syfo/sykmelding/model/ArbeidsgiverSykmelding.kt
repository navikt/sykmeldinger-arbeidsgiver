package no.nav.syfo.sykmelding.model

import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding

data class ArbeidsgiverSykmelding(
    val pasientFnr: String,
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val sykmelding: EnkelSykmelding
)
