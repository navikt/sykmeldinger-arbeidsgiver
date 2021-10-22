package no.nav.syfo.sykmelding.model

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding

data class SykmeldingArbeidsgiverV2(
    val narmestelederId: String,
    val navn: String,
    val pasientFnr: String,
    val orgnummer: String,
    val orgNavn: String,
    val sykmelding: ArbeidsgiverSykmelding
)
