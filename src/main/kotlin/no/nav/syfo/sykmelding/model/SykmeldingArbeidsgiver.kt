package no.nav.syfo.sykmelding.model

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding

data class SykmeldingArbeidsgiver(
    val pasientFnr: String,
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgNavn: String,
    val sykmelding: EnkelSykmelding
)

data class SykmeldingArbeidsgiverV2(
    val navn: String,
    val pasientFnr: String,
    val orgnummer: String,
    val orgNavn: String,
    val sykmelding: ArbeidsgiverSykmelding
)
