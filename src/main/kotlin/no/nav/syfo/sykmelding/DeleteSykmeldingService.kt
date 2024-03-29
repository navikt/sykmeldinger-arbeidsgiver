package no.nav.syfo.sykmelding

import java.time.LocalDate
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.log
import no.nav.syfo.sykmelding.db.deleteSykmeldinger
import no.nav.syfo.util.Unbounded

class DeleteSykmeldingService(
    private val database: DatabaseInterface,
    private val leaderElection: LeaderElection,
    private val applicationState: ApplicationState,
) {

    @DelicateCoroutinesApi
    fun start() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                if (leaderElection.isLeader()) {
                    try {
                        val result = database.deleteSykmeldinger(getDeleteDate())
                        log.info(
                            "Deleted ${result.deletedSykmelding} sykmeldinger and ${result.deletedSykmeldt} sykmeldte"
                        )
                    } catch (ex: Exception) {
                        log.error("Could not delete sykmeldinger/sykmeldt", ex)
                    }
                }
                delay(60_000.times(60))
            }
        }
    }

    private fun getDeleteDate(): LocalDate {
        return LocalDate.now().minusMonths(4)
    }
}
