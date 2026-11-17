package com.xuleo.xmplus.md3.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xuleo.xmplus.md3.core.model.XmlyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Runs one operation at a time and exposes its progress and failure code to Compose. */
class Operations(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var errorCode by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun launch(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        errorCode = null
        job = scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorCode = (failure as? XmlyException)?.code ?: GenericFailure
            } finally {
                busy = false
            }
        }
    }

    fun fail(code: String) {
        errorCode = code
    }

    fun clearError() {
        errorCode = null
    }

    fun cancel() {
        job?.cancel()
    }

    private companion object {
        const val GenericFailure = "account_operation_failed"
    }
}