package io.github.ultrafeed.app4pda.data.forum

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ScreenshotTitleTest {
    @Test
    fun screenshotHeadingsAcceptTheObservedLatinInitialWithoutTreatingOtherSectionsAsGallery() {
        assertThat(ForumTopicIcon.screenshotTitle("Скриншоты"), `is`(true))
        assertThat(ForumTopicIcon.screenshotTitle("Cкриншоты"), `is`(true))
        assertThat(ForumTopicIcon.screenshotTitle("  cкриншоты "), `is`(true))
        assertThat(ForumTopicIcon.screenshotTitle("Описание"), `is`(false))
        assertThat(ForumTopicIcon.screenshotTitle("Прошлые версии"), `is`(false))
        assertThat(ForumTopicIcon.screenshotTitle(""), `is`(false))
    }
}
