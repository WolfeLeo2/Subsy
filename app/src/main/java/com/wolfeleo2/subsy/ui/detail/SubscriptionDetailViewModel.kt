package com.wolfeleo2.subsy.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.repository.BillingLedger
import com.wolfeleo2.subsy.data.repository.PaymentRecordRepository
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val subscription: Subscription? = null,
    val paymentHistory: List<PaymentRecord> = emptyList(),
    val totalSpent: Double = 0.0,
    val isLoading: Boolean = false,
    val isLoggingPayment: Boolean = false,
) {
    val isNotFound: Boolean get() = !isLoading && subscription == null
}

sealed interface DetailEvent {
    data object Deleted : DetailEvent

    data class PaymentLogged(
        val nextBillingDate: Long,
    ) : DetailEvent
}

class SubscriptionDetailViewModel(
    private val subscriptionId: String,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRecordRepository: PaymentRecordRepository,
) : ViewModel() {
    private val events = Channel<DetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<DetailEvent> = events.receiveAsFlow()

    private val isLoggingPayment = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> =
        combine(
            subscriptionRepository.getSubscriptionById(subscriptionId),
            paymentRecordRepository.getPaymentRecordsForSubscription(subscriptionId),
            paymentRecordRepository.getTotalSpentForSubscription(subscriptionId),
            isLoggingPayment,
        ) { subscription, history, totalSpent, logging ->
            DetailUiState(
                subscription = subscription,
                paymentHistory = history,
                totalSpent = totalSpent,
                isLoading = false,
                isLoggingPayment = logging,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DetailUiState(isLoading = true),
        )

    fun pauseSubscription() {
        viewModelScope.launch {
            subscriptionRepository.pauseSubscription(subscriptionId)
        }
    }

    fun resumeSubscription() {
        viewModelScope.launch {
            subscriptionRepository.resumeSubscription(subscriptionId)
        }
    }

    fun cancelSubscription() {
        viewModelScope.launch {
            subscriptionRepository.cancelSubscription(subscriptionId)
        }
    }

    fun logPaymentAndAdvanceCycle() {
        if (!isLoggingPayment.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val sub = subscriptionRepository.getSubscriptionByIdDirect(subscriptionId) ?: return@launch
                val next =
                    BillingLedger(
                        subscriptionRepository,
                        paymentRecordRepository,
                    ).logPaymentAndAdvance(sub, PaymentStatus.MANUALLY_LOGGED)
                events.send(DetailEvent.PaymentLogged(next))
            } finally {
                isLoggingPayment.value = false
            }
        }
    }

    fun deleteSubscription() {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscriptionById(subscriptionId)
            events.send(DetailEvent.Deleted)
        }
    }

    class Factory(
        private val subscriptionId: String,
        private val subscriptionRepository: SubscriptionRepository,
        private val paymentRecordRepository: PaymentRecordRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SubscriptionDetailViewModel(
                subscriptionId,
                subscriptionRepository,
                paymentRecordRepository,
            ) as T
    }
}
