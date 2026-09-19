package hu.csabi.architecture.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Lesson 02 — a dispatcher soha ne legyen bedrótozva.
 *
 * `Dispatchers.IO` közvetlen hívása a kódban teszteléskor fáj: nem tudod lecserélni
 * determinisztikus test dispatcherre. Ezért interface mögé tesszük — a 06. leckében
 * ezt fogja Hilt injektálni, a 10.-ben teszt dupla kerül a helyére.
 *
 * - Main: UI. Csak állapotfrissítés, semmi blokkolás.
 * - IO: blokkoló hívások (hálózat, fájl, DB). Nagy, elasztikus pool — a szálak nagy
 *   része amúgy is I/O-ra vár.
 * - Default: CPU-igényes munka. Pool mérete = magok száma, mert többől nincs haszon.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
