package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VocabSrsTest {

    private val today = 20_000L

    @Test
    fun `new word correct advances to stage 0 with interval 1 day`() {
        val (stage, due) = VocabSrs.nextState(-1, true, today)
        assertEquals(0, stage)
        assertEquals(today + 1, due)
    }

    @Test
    fun `correct answer lengthens interval progressively`() {
        val (s1, d1) = VocabSrs.nextState(0, true, today)
        assertEquals(1, s1)
        assertEquals(today + 2, d1) // 间隔 1->2 天

        val (s2, d2) = VocabSrs.nextState(s1, true, today)
        assertEquals(2, s2)
        assertEquals(today + 4, d2) // 间隔 2->4 天
    }

    @Test
    fun `wrong answer resets stage to 0 and due tomorrow`() {
        val (stage, due) = VocabSrs.nextState(3, false, today)
        assertEquals(0, stage)
        assertEquals(today + 1, due)
    }

    @Test
    fun `stage caps at max and stays at longest interval`() {
        var cur = -1
        repeat(20) {
            cur = VocabSrs.nextState(cur, true, today).first
        }
        assertEquals(5, cur)
        val (_, due) = VocabSrs.nextState(cur, true, today)
        assertEquals(today + 30, due)
    }

    @Test
    fun `mastery threshold at stage 4`() {
        // 阶段 3 答对 → 阶段 4（达到"记住"阈值），间隔 15 天
        val (stage, due) = VocabSrs.nextState(3, true, today)
        assertEquals(4, stage)
        assertEquals(today + 15, due)
    }

    @Test
    fun `bookKey differs across mode and level`() {
        val wAll = VocabSrs.bookKey(false, VocabData.ALL)
        val vAll = VocabSrs.bookKey(true, VocabData.ALL)
        assertEquals("w.all", wAll)
        assertEquals("v.all", vAll)
        assertNotEquals(wAll, vAll)

        assertEquals("w.S8", VocabSrs.bookKey(false, "S8"))
    }
}