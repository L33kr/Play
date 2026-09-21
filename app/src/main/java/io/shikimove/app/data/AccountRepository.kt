package io.shikimove.app.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncState(val running: Boolean = false, val error: String? = null, val lastSync: Long? = null, val needsLogin: Boolean = false)

@OptIn(FlowPreview::class)
class AccountRepository private constructor(context: Context) {
    val store = LocalStore.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state = _state.asStateFlow()
    private var generation = 0
    private var job: Job? = null
    init { scope.launch { store.pending.debounce(1200).collect { if (it.isNotEmpty()) sync() } } }
    suspend fun finishLogin(): Boolean {
        val cookie = CookieManager.getInstance().getCookie(AccountApi.ORIGIN).orEmpty()
        if (cookie.isBlank()) return false
        val user = AccountApi(cookie).whoami() ?: return false
        generation++; job?.cancel()
        store.account(user); CookieManager.getInstance().flush()
        _state.value = SyncState(); sync()
        return true
    }
    fun logout() {
        generation++; job?.cancel(); store.account(null); _state.value = SyncState()
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
    }
    fun sync() {
        if (store.user.value == null || job?.isActive == true) return
        job = scope.launch { mutex.withLock {
            val owner = store.user.value?.id ?: return@withLock
            val epoch = generation
            fun ensureOwner() { if (generation != epoch || store.user.value?.id != owner) throw CancellationException() }
            _state.value = _state.value.copy(running = true, error = null)
            try {
                val cookie = CookieManager.getInstance().getCookie(AccountApi.ORIGIN).orEmpty()
                if (cookie.isBlank()) throw SessionExpired()
                val api = AccountApi(cookie)
                if (api.whoami()?.id != owner) throw SessionExpired()
                ensureOwner()
                // Snapshot + revision acknowledgements keep edits made during a request in the queue.
                for (mutation in store.pending.value.toList()) {
                    ensureOwner(); api.push(owner, mutation); ensureOwner(); store.acknowledge(owner, mutation)
                }
                val rates = api.rates(owner); ensureOwner(); store.applyRemote(owner, rates)
                _state.value = SyncState(lastSync = System.currentTimeMillis())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureOwner()
                _state.value = _state.value.copy(running = false, error = e.message ?: "Не удалось синхронизировать список", needsLogin = e is SessionExpired)
            }
        } }
    }
    companion object {
        @Volatile private var instance: AccountRepository? = null
        fun get(context: Context): AccountRepository = instance ?: synchronized(this) { instance ?: AccountRepository(context.applicationContext).also { instance = it } }
    }
}
