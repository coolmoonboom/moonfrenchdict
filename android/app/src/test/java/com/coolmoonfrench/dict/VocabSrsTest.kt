package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabSrsTest {

    private val today = 20_000L

    @Test
    fun `new word enters review the same day`() {
        val (stage, due) = VocabSrs.nextState(-1, true, today)
        assertEquals(0, stage)
        assertEquals(today, due) // 艾宾浩斯首个间隔为当天
    }

    @Test
    fun `correct answer lengthens interval along forgetting curve`() {
        val (s1, d1) = VocabSrs.nextState(0, true, today)
        assertEquals(1, s1)
        assertEquals(today + 1, d1)

        val (s2, d2) = VocabSrs.nextState(s1, true, today)
        assertEquals(2, s2)
        assertEquals(today + 2, d2)

        val (s3, d3) = VocabSrs.nextState(s2, true, today)
        assertEquals(3, s3)
        assertEquals(today + 4, d3)
    }

    @Test
    fun `wrong answer resets stage to zero and reviews same day`() {
        val (stage, due) = VocabSrs.nextState(3, false, today)
        assertEquals(0, stage)
        assertEquals(today, due)
    }

    @Test
    fun `stage caps at max and stays at longest interval`() {
        var cur = -1
        repeat(20) {
            cur = VocabSrs.nextState(cur, true, today).first
        }
        assertEquals(7, cur)
        val (_, due) = VocabSrs.nextState(cur, true, today)
        assertEquals(today + 60, due)
    }

    @Test
    fun `mastery threshold at stage 6`() {
        val (stage, due) = VocabSrs.nextState(5, true, today)
        assertEquals(VocabSrs.MASTERED_STAGE, stage)
        assertEquals(6, stage)
        assertEquals(today + 30, due)
    }

    @Test
    fun `interval table follows ebbinghaus curve`() {
        assertEquals(0, VocabSrs.intervalDays(0))
        assertEquals(1, VocabSrs.intervalDays(1))
        assertEquals(2, VocabSrs.intervalDays(2))
        assertEquals(4, VocabSrs.intervalDays(3))
        assertEquals(7, VocabSrs.intervalDays(4))
        assertEquals(15, VocabSrs.intervalDays(5))
        assertEquals(30, VocabSrs.intervalDays(6))
        assertEquals(60, VocabSrs.intervalDays(7))
    }

    @Test
    fun `parse supports new and legacy records`() {
        assertEquals(Triple(2, 100L, 90L), VocabSrs.parse("2:100:90"))
        // 旧数据没有学习日，回退为到期日
        assertEquals(Triple(1, 100L, 100L), VocabSrs.parse("1:100"))
        assertNull(VocabSrs.parse(null))
        assertNull(VocabSrs.parse("bad"))
    }

    @Test
    fun `bookKey differs across mode and level`() {
        val wAll = VocabSrs.bookKey(false, VocabData.ALL)
        val vAll = VocabSrs.bookKey(true, VocabData.ALL)
        assertEquals("w.all", wAll)
        assertEquals("v.all", vAll)
        assertNotEquals(wAll, vAll)

        assertEquals("w.TFS8", VocabSrs.bookKey(false, "TFS8"))
    }
}
