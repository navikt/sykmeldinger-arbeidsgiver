package no.nav.syfo.dinesykmeldte.util

import no.nav.syfo.dinesykmeldte.model.Arbeidsevne
import no.nav.syfo.dinesykmeldte.model.Bekreftelse
import no.nav.syfo.dinesykmeldte.model.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.model.Friskmelding
import no.nav.syfo.dinesykmeldte.model.MulighetForArbeid
import no.nav.syfo.dinesykmeldte.model.Pasient
import no.nav.syfo.dinesykmeldte.model.Periode
import no.nav.syfo.model.sykmelding.model.BehandlerDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.narmesteleder.client.model.Ansatt
import no.nav.syfo.sykmelding.model.ArbeidsgiverSykmelding

fun ArbeidsgiverSykmelding.toDineSykmeldteSykmelding(ansatt: Ansatt): DineSykmeldteSykmelding {
    return DineSykmeldteSykmelding(
        pasient = Pasient(
            fnr = pasientFnr,
            navn = ansatt.navn
        ),
        sykmeldingId = sykmelding.id,
        mulighetForArbeid = MulighetForArbeid(
            perioder = getPerioder(),
            aktivitetIkkeMulig434 = getAktivitetIkkeMulig(this.sykmelding.sykmeldingsperioder),
            aarsakAktivitetIkkeMulig434 = getAktivitetIkkeMuligBeskrivelse(this.sykmelding.sykmeldingsperioder)
        ),
        skalViseSkravertFelt = true,
        arbeidsgiver = this.sykmelding.arbeidsgiver.navn,
        stillingsprosent = this.sykmelding.arbeidsgiver.stillingsprosent,
        innspillTilArbeidsgiver = this.sykmelding.meldingTilArbeidsgiver,
        arbeidsevne = Arbeidsevne(
            tilretteleggingArbeidsplass = this.sykmelding.tiltakArbeidsplassen
        ),
        bekreftelse = Bekreftelse(
            sykmelder = getSykmelderNavn(this.sykmelding.behandler),
            utstedelsesdato = this.sykmelding.behandletTidspunkt.toLocalDate(),
            sykmelderTlf = this.sykmelding.behandler.tlf
        ),
        friskmelding = Friskmelding(
            arbeidsfoerEtterPerioden = this.sykmelding.prognose?.arbeidsforEtterPeriode,
            hensynPaaArbeidsplassen = this.sykmelding.prognose?.hensynArbeidsplassen,
            antarReturAnnenArbeidsgiver = this.sykmelding.prognose?.erIArbeid?.annetArbeidPaSikt ?: false,
            antattDatoReturSammeArbeidsgiver = this.sykmelding.prognose?.erIArbeid?.arbeidFOM,
            antarReturSammeArbeidsgiver = this.sykmelding.prognose?.erIArbeid?.egetArbeidPaSikt ?: false
        )
    )
}

fun getSykmelderNavn(behandlerDTO: BehandlerDTO): String {
    return if (behandlerDTO.mellomnavn.isNullOrEmpty()) {
        capitalizeFirstLetter("${behandlerDTO.fornavn} ${behandlerDTO.etternavn}")
    } else {
        capitalizeFirstLetter("${behandlerDTO.fornavn} ${behandlerDTO.mellomnavn} ${behandlerDTO.etternavn}")
    }
}

private fun capitalizeFirstLetter(string: String): String {
    return string.toLowerCase()
        .split(" ").joinToString(" ") { it.capitalize() }
        .split("-").joinToString("-") { it.capitalize() }.trimEnd()
}

fun getAktivitetIkkeMuligBeskrivelse(sykmeldingsperioder: List<SykmeldingsperiodeDTO>): String {
    return sykmeldingsperioder.mapNotNull { it.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse }.distinct().joinToString(separator = ", ")
}

fun getAktivitetIkkeMulig(sykmeldingsperioder: List<SykmeldingsperiodeDTO>): List<String> {
    return sykmeldingsperioder.mapNotNull { it.aktivitetIkkeMulig?.arbeidsrelatertArsak }.flatMap { it.arsak }.map { it.name }.distinct()
}

private fun ArbeidsgiverSykmelding.getPerioder(): List<Periode> {
    return sykmelding.sykmeldingsperioder.map { it.toPerioder() }
}

private fun SykmeldingsperiodeDTO.toPerioder(): Periode {
    return Periode(
        fom = this.fom,
        tom = this.tom,
        grad = this.gradert?.grad,
        behandlingsdager = this.behandlingsdager,
        reisetilskudd = this.reisetilskudd,
        avventende = this.innspillTilArbeidsgiver,
    )
}
