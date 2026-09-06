package dev.cfmobile.app.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccountSubscription
import dev.cfmobile.app.data.repository.BillingRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUiState(
    val subscriptions: UiState<List<AccountSubscription>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** The product or plan a subscription is for, whichever Cloudflare filled in. */
fun subscriptionTitle(subscription: AccountSubscription): String =
    subscription.ratePlan?.publicName?.takeIf { it.isNotBlank() }
        ?: subscription.product?.name?.takeIf { it.isNotBlank() }
        ?: subscription.id

/** "20.00 USD / monthly" - built only from what came back, so a free plan with no price
 *  doesn't render a misleading "0". */
fun subscriptionPriceLabel(subscription: AccountSubscription): String? {
    val price = subscription.price ?: return null
    val currency = subscription.currency ?: subscription.ratePlan?.currency
    val amount = "%.2f".format(price)
    val withCurrency = currency?.let { "$amount $it" } ?: amount
    return subscription.frequency?.let { "$withCurrency / $it" } ?: withCurrency
}

class BillingViewModel(
    private val accountId: String,
    private val repository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(subscriptions = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listSubscriptions(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(subscriptions = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(subscriptions = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
