package at.quickme.kotlinmailer.delivery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.simplejavamail.api.mailer.Mailer


object MailerManager {
    private const val DEFAULT_SMTP_HOST = "localhost"
    private const val DEFAULT_SMTP_PORT = 25

    private val LOCAL_MAILER by lazy { mailerBuilder(DEFAULT_SMTP_HOST, DEFAULT_SMTP_PORT) }

    private var DEFAULT_MAILER: Mailer? = null

    /**
     * The default mailer instance, that is used if
     * the no other instance is provided.
     */
    var defaultMailer: Mailer
        get() = DEFAULT_MAILER ?: LOCAL_MAILER
        set(value) {
            DEFAULT_MAILER = value
        }

    private val registeredMailers = HashSet<Mailer>()

    /**
     * Register this mailer instance.
     *
     * Note that mailer instances built with the mailerBuilder
     * are registered automatically.
     */
    fun registerMailer(mailer: Mailer) = registeredMailers.add(mailer)

    /**
     * Shutdown all registered mailers.
     */
    suspend fun shutdownMailers() {
        coroutineScope {
            val shutdownJobs = mutableSetOf<Job>()
            registeredMailers.removeAll {
                shutdownJobs += launch(Dispatchers.IO) {
                    it.shutdownConnectionPool().get()
                }
                true
            }
            shutdownJobs.forEach { it.join() }
        }
    }
}
