package no.nav.syfo.dinesykmeldte.model

import java.time.LocalDate

data class Friskmelding(
    val arbeidsfoerEtterPerioden: Boolean?,
    val hensynPaaArbeidsplassen: String?,
    val antarReturSammeArbeidsgiver: Boolean?,
    val antattDatoReturSammeArbeidsgiver: LocalDate?
)
