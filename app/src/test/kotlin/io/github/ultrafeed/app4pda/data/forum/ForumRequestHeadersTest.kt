package io.github.ultrafeed.app4pda.data.forum

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumRequestHeadersTest {
    @Test
    fun ordinaryForbiddenIsNotAutomaticallyAChallenge() {
        assertFalse(isForumChallenge(null, "<h1>Forbidden</h1>"))
        assertTrue(isForumChallenge("challenge", ""))
        assertTrue(isForumChallenge(null, "<script>window._cf_chl_opt={}</script>"))
        assertFalse(isForumChallenge(null, "<p>Explanation of _cf_chl_opt</p>"))
        assertThat(
            forumSessionCookies("cf_clearance=private; member_id=7; __cf_bm=private"),
            CoreMatchers.`is`(
                "member_id=7",
            ),
        )
    }
}
