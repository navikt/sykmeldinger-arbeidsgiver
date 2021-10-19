package no.nav.syfo.narmesteleder.kafka.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Narmesteleder(
    val narmesteLederId: UUID,
    val fnr: String,
    val orgnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime
)
