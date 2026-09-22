package com.chefotech.jadibuti.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which family member the Home screen is showing. Lives for the process only: every cold start
 * of a family with several members asks again, which is the behaviour the family asked for.
 * `null` means everyone.
 */
@Singleton
class MemberFocus @Inject constructor() {
    private val _selectedMemberId = MutableStateFlow<String?>(null)
    private val _chosen = MutableStateFlow(false)

    val selectedMemberId: StateFlow<String?> = _selectedMemberId
    /** True once the user has answered the "whose medicines?" question in this process. */
    val chosen: StateFlow<Boolean> = _chosen

    fun choose(memberId: String?) {
        _selectedMemberId.value = memberId
        _chosen.value = true
    }

    fun askAgain() { _chosen.value = false }
}
