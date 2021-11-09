package no.nav.syfo.narmesteleder.model

data class Ansatt(
    val fnr: String,
    val navn: String,
    val orgnummer: String,
    val narmestelederId: String
)
