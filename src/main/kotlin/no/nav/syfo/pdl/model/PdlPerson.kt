package no.nav.syfo.pdl.model

import no.nav.syfo.dinesykmeldte.util.capitalizeFirstLetter

data class PdlPerson(
    val navn: Navn,
    val aktorId: String?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun Navn.toFormattedNameString(): String {
    return if (mellomnavn.isNullOrEmpty()) {
        capitalizeFirstLetter("$fornavn $etternavn")
    } else {
        capitalizeFirstLetter("$fornavn $mellomnavn $etternavn")
    }
}
