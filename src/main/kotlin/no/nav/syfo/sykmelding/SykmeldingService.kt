package no.nav.syfo.sykmelding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.util.isActive
import no.nav.syfo.sykmelding.db.getArbeidsgiverSykmeldinger
import no.nav.syfo.sykmelding.model.SykmeldingArbeidsgiverV2
import java.time.LocalDate

class SykmeldingService(val database: DatabaseInterface) {
    fun getSykmeldinger(lederFnr: String): List<SykmeldingArbeidsgiverV2> {
        return database.getArbeidsgiverSykmeldinger(lederFnr)
    }

    /**
     * Get a sykmeldt for for a given narmesteleder + lederFnr
     */
    fun getSykmeldt(narmestelederId: String, lederFnr: String): Sykmeldt? {
        val arbeidsgiverSykmeldinger =
            database.getArbeidsgiverSykmeldinger(lederFnr = lederFnr, narmestelederId = narmestelederId)

        val sykmeldingArbeidsgiverV2 =
            arbeidsgiverSykmeldinger.firstOrNull()

        return if (sykmeldingArbeidsgiverV2 != null) {
            Sykmeldt(
                narmestelederId = sykmeldingArbeidsgiverV2.narmestelederId,
                orgnummer = sykmeldingArbeidsgiverV2.orgnummer,
                fnr = sykmeldingArbeidsgiverV2.pasientFnr,
                navn = sykmeldingArbeidsgiverV2.navn,
                sykmeldinger = null,
                aktivSykmelding = arbeidsgiverSykmeldinger.any { it.sykmelding.sykmeldingsperioder.isActive() }
            )
        } else null
    }

    /**
     * Get a sykmeldt for a given narmesteleder + lederFnr filtered by active sykmelding
     */
    fun getSykmeldt(narmestelederId: String, lederFnr: String, date: LocalDate): Sykmeldt? {
        return database.getArbeidsgiverSykmeldinger(lederFnr = lederFnr, narmestelederId = narmestelederId)
            .firstOrNull {
                it.sykmelding.sykmeldingsperioder.isActive(date)
            }?.let {
                Sykmeldt(
                    narmestelederId = it.narmestelederId,
                    orgnummer = it.orgnummer,
                    fnr = it.pasientFnr,
                    navn = it.navn,
                    sykmeldinger = null,
                    aktivSykmelding = true
                )
            }
    }
}
