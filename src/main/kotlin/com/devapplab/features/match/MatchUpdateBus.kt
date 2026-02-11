package com.devapplab.features.match

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MatchUpdateBus {
    private val flows = ConcurrentHashMap<UUID, MutableSharedFlow<Unit>>()

    fun updates(matchId: UUID): SharedFlow<Unit> =
        flows.getOrPut(matchId) { MutableSharedFlow(extraBufferCapacity = 64) }

    fun publish(matchId: UUID) {
        flows[matchId]?.tryEmit(Unit)
    }
}