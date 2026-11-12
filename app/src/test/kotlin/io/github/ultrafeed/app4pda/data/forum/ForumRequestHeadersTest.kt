package io.github.ultrafeed.app4pda.data.forum

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ForumRequestHeadersTest {
    @Test
    fun ordinaryForbiddenIsNotAutomaticallyAChallenge() {
        assertThat(isForumChallenge(null, "<h1>Forbidden</h1>"), `is`(false))
        assertThat(isForumChallenge("challenge", ""), `is`(true))
        assertThat(isForumChallenge(null, "<script>window._cf_chl_opt={}</script>"), `is`(true))
        assertThat(isForumChallenge(null, "<p>Explanation of _cf_chl_opt</p>"), `is`(false))
        assertThat(
            forumSessionCookies("cf_clearance=private; member_id=7; __cf_bm=private"),
            `is`(
                "member_id=7",
            ),
        )
    }
}
