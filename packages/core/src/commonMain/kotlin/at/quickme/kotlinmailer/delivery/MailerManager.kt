package at.quickme.kotlinmailer.delivery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Global registry for [Mailer] instances.
 */
@OptIn(ExperimentalAtomicApi::class)
public object MailerManager {
    private val default = AtomicReference<Mailer?>(null)
    private val registered = AtomicReference(emptyList<Mailer>())

    /**
     * The mailer used by [at.quickme.kotlinmailer.email.Email.send] if no other mailer is passed.
     *
     * @throws IllegalStateException on read if no default mailer has been set
     */
    public var defaultMailer: Mailer
        get() = checkNotNull(default.load()) { "No default mailer set, assign MailerManager.defaultMailer first" }
        set(value) {
            default.store(value)
            registerMailer(value)
        }

    /** True if [defaultMailer] has been set. */
    public val hasDefaultMailer: Boolean get() = default.load() != null

    /** Register [mailer], so it is closed by [shutdownMailers]. Mailer builders do this automatically. */
    public fun registerMailer(mailer: Mailer) {
        while (true) {
            val current = registered.load()
            if (mailer in current || registered.compareAndSet(current, current + mailer)) return
        }
    }

    /** Close and unregister all registered mailers, including the default one. */
    public suspend fun shutdownMailers() {
        val mailers = registered.exchange(emptyList())
        default.store(null)
        coroutineScope {
            mailers.map { async { it.close() } }.awaitAll()
        }
    }
}
