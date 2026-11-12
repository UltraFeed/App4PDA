package io.github.ultrafeed.app4pda.data.forum

import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTitleTest {
    @Test
    fun screenshotHeadingsAcceptTheObservedLatinInitialWithoutTreatingOtherSectionsAsGallery() {
        assertTrue(ForumTopicIcon.screenshotTitle("Скриншоты"))
        assertTrue(ForumTopicIcon.screenshotTitle("Cкриншоты"))
        assertTrue(ForumTopicIcon.screenshotTitle("  cкриншоты "))
        Assert.assertFalse(ForumTopicIcon.screenshotTitle("Описание"))
        Assert.assertFalse(ForumTopicIcon.screenshotTitle("Прошлые версии"))
        Assert.assertFalse(ForumTopicIcon.screenshotTitle(""))
    }
}
