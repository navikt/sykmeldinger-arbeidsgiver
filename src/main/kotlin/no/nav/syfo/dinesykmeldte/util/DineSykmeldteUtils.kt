package no.nav.syfo.dinesykmeldte.util

import no.nav.syfo.dinesykmeldte.model.Arbeidsevne
import no.nav.syfo.dinesykmeldte.model.Bekreftelse
import no.nav.syfo.dinesykmeldte.model.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.model.Friskmelding
import no.nav.syfo.dinesykmeldte.model.MulighetForArbeid
import no.nav.syfo.dinesykmeldte.model.Pasient
import no.nav.syfo.dinesykmeldte.model.Periode
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.narmesteleder.model.Ansatt
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2

fun SykmeldingArbeidsgiverV2.toDineSykmeldteSykmelding(ansatt: Ansatt): DineSykmeldteSykmelding {
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
        arbeidsgiver = this.orgNavn,
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
        )
    )
}

fun getSykmelderNavn(behandlerDTO: BehandlerAGDTO): String {
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

fun getAktivitetIkkeMuligBeskrivelse(sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>): String {
    return sykmeldingsperioder.mapNotNull { it.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse }.distinct().joinToString(separator = ", ")
}

fun getAktivitetIkkeMulig(sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>): List<String> {
    return sykmeldingsperioder.mapNotNull { it.aktivitetIkkeMulig?.arbeidsrelatertArsak }.flatMap { it.arsak }.map { it.name }.distinct()
}

private fun SykmeldingArbeidsgiverV2.getPerioder(): List<Periode> {
    return sykmelding.sykmeldingsperioder.map { it.toPerioder() }
}

private fun SykmeldingsperiodeAGDTO.toPerioder(): Periode {
    return Periode(
        fom = this.fom,
        tom = this.tom,
        grad = this.gradert?.grad ?: 100,
        behandlingsdager = this.behandlingsdager,
        reisetilskudd = this.reisetilskudd,
        avventende = this.innspillTilArbeidsgiver,
    )
}

fun Ansatt.toSykmeldt(): Sykmeldt {
    return Sykmeldt(
        narmestelederId = this.narmestelederId,
        orgnummer = this.orgnummer,
        fnr = this.fnr,
        navn = this.navn,
        sykmeldinger = null
    )
}
