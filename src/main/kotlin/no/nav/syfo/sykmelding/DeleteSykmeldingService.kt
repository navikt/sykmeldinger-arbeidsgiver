package no.nav.syfo.sykmelding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.sykmelding.db.deleteSykmeldinger
import no.nav.syfo.util.Unbounded
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class DeleteSykmeldingService(val database: DatabaseInterface, val applicationState: ApplicationState) {
    @OptIn(ExperimentalTime::class)
    fun start() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while(applicationState.ready) {
                try {
                    val result = database.deleteSykmeldinger(getDeleteDate())
                    log.info("Deleted ${result.deletedSykmelding} sykmeldinger and ${result.deletedSykmeldt} sykmeldte")
                } catch (ex: Exception) {
                    log.error("Could not delte sykmeldinger/sykmeldt")
                }
                delay(Duration.hours(1))
            }
        }
    }

    private fun getDeleteDate(): LocalDate {
        return LocalDate.now().minusMonths(4)
    }
}
