package no.nav.syfo.narmesteleder.client.model

import java.util.UUID

data class Ansatt(
    val fnr: String,
    val navn: String?,
    val orgnummer: String,
    val narmestelederId: UUID
)
