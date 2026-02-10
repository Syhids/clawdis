package ai.openclaw.android.ui.chat.forms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which forms have been submitted/cancelled/expired so they
 * render as disabled in the chat.
 */
class FormSubmissionManager {
  private val _submittedForms = MutableStateFlow<Set<String>>(emptySet())
  val submittedForms: StateFlow<Set<String>> = _submittedForms.asStateFlow()

  fun markSubmitted(formId: String) {
    _submittedForms.value = _submittedForms.value + formId
  }

  fun isSubmitted(formId: String): Boolean = formId in _submittedForms.value

  fun clear() {
    _submittedForms.value = emptySet()
  }
}
