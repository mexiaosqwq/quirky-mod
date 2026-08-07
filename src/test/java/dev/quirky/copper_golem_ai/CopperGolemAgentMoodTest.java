package dev.quirky.copper_golem_ai;

import dev.quirky.copper_golem_ai.CopperGolemAgentMood.Mood;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAgentMoodTest {

	@Test
	void positiveWords() {
		assertEquals(1, CopperGolemAgentMood.processWord("谢谢你"));
		assertEquals(1, CopperGolemAgentMood.processWord("你真棒！"));
		assertEquals(1, CopperGolemAgentMood.processWord("乖，去搬吧"));
		assertEquals(1, CopperGolemAgentMood.processWord("好厉害"));
		assertEquals(1, CopperGolemAgentMood.processWord("感谢帮忙"));
		assertEquals(1, CopperGolemAgentMood.processWord("棒"));
	}

	@Test
	void negativeWords() {
		assertEquals(-1, CopperGolemAgentMood.processWord("你这个笨蛋"));
		assertEquals(-1, CopperGolemAgentMood.processWord("真蠢"));
		assertEquals(-1, CopperGolemAgentMood.processWord("废物"));
		assertEquals(-1, CopperGolemAgentMood.processWord("真没用"));
		assertEquals(-1, CopperGolemAgentMood.processWord("傻呀你"));
		assertEquals(-1, CopperGolemAgentMood.processWord("讨厌"));
	}

	@Test
	void neutralWords() {
		assertEquals(0, CopperGolemAgentMood.processWord("把铁锭搬过来"));
		assertEquals(0, CopperGolemAgentMood.processWord(""));
	}

	@Test
	void moodFromScoreBoundaries() {
		assertEquals(Mood.HAPPY, CopperGolemAgentMood.moodFor(2));
		assertEquals(Mood.HAPPY, CopperGolemAgentMood.moodFor(5));
		assertEquals(Mood.CALM, CopperGolemAgentMood.moodFor(1));
		assertEquals(Mood.CALM, CopperGolemAgentMood.moodFor(0));
		assertEquals(Mood.UPSET, CopperGolemAgentMood.moodFor(-1));
		assertEquals(Mood.UPSET, CopperGolemAgentMood.moodFor(-2));
		assertEquals(Mood.ANGRY, CopperGolemAgentMood.moodFor(-3));
		assertEquals(Mood.ANGRY, CopperGolemAgentMood.moodFor(-10));
	}

	@Test
	void decayMovesTowardZero() {
		assertEquals(1, CopperGolemAgentMood.decay(2));
		assertEquals(0, CopperGolemAgentMood.decay(1));
		assertEquals(0, CopperGolemAgentMood.decay(0));
		assertEquals(-1, CopperGolemAgentMood.decay(-2));
		assertEquals(0, CopperGolemAgentMood.decay(-1));
	}

	@Test
	void promptsAreNonEmpty() {
		for (Mood m : Mood.values()) {
			assertFalse(CopperGolemAgentMood.toPrompt(m).isBlank());
		}
	}
}
