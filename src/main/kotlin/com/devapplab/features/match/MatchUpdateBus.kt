package com.devapplab.features.match

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.UUID

class MatchUpdateBus {
    private val _updates = MutableSharedFlow<UUID>(
        replay = 1,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun publish(matchId: UUID) {
        _updates.tryEmit(matchId)
    }

    fun updates(matchId: UUID): Flow<UUID> {
        return _updates.filter { it == matchId }
    }
}