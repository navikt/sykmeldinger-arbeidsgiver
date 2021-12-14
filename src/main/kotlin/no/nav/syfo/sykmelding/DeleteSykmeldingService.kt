package no.nav.syfo.sykmelding

import kotlinx.coroutines.DelicateCoroutinesApi
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

class DeleteSykmeldingService(val database: DatabaseInterface, val applicationState: ApplicationState) {

    @DelicateCoroutinesApi
    fun start() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    val result = database.deleteSykmeldinger(getDeleteDate())
                    log.info("Deleted ${result.deletedSykmelding} sykmeldinger and ${result.deletedSykmeldt} sykmeldte")
                } catch (ex: Exception) {
                    log.error("Could not delte sykmeldinger/sykmeldt")
                }
                delay(60_000.times(60))
            }
        }
    }

    private fun getDeleteDate(): LocalDate {
        return LocalDate.now().minusMonths(4)
    }
}
