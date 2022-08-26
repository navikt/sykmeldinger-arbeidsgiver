package no.nav.syfo.dinesykmeldte.model

import no.nav.syfo.dinesykmeldte.kafka.model.NLReadCount

data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String?,
    val sykmeldinger: List<DineSykmeldteSykmelding>?,
    val aktivSykmelding: Boolean?,
    val lestStatus: NLReadCount?,
)
