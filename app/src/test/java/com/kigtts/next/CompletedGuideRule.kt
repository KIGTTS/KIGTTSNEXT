package com.kigtts.next

import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.onboarding.FirstRunStore
import org.junit.rules.ExternalResource

/** Activity tests for an established user's home flow start with the guide already completed. */
class CompletedGuideRule : ExternalResource() {
    override fun before() {
        FirstRunStore(ApplicationProvider.getApplicationContext(), existingInstall = true)
    }
}
