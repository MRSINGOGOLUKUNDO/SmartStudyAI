package com.zut.smartstudy

import com.zut.smartstudy.domain.KeyPointGenerator
import com.zut.smartstudy.notifications.ReminderScheduler
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPointGeneratorTest {

    private val doc = """
        Software testing is the process of evaluating a program to find defects before release.
        Unit tests check individual functions in isolation from the rest of the system.

        Photosynthesis converts sunlight into chemical energy inside the chloroplasts of plants.
        Integration testing verifies that separate modules work correctly when combined together.
    """.trimIndent()

    @Test
    fun returnsOnlyTopicRelevantSentences() {
        val points = KeyPointGenerator.generate(doc, "software testing")
        assertTrue(points.isNotEmpty())
        assertTrue(points.none { it.contains("Photosynthesis") })
    }

    @Test
    fun respectsMaxPoints() {
        assertTrue(KeyPointGenerator.generate(doc, "testing", maxPoints = 1).size <= 1)
    }

    @Test
    fun unrelatedTopicReturnsEmpty() {
        assertTrue(KeyPointGenerator.generate(doc, "astronomy").isEmpty())
    }

    @Test
    fun blankTopicReturnsEmpty() {
        assertTrue(KeyPointGenerator.generate(doc, "   ").isEmpty())
    }

    @Test
    fun reminderDelayRollsToNextDayWhenTimePassed() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val delay = ReminderScheduler.delayUntilNext(9, 0, now)
        assertEquals(23 * 60 * 60 * 1000L, delay)
    }
}
