package dev.quirky.copper_golem_ai;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** transport 卡住计数纯函数测试（不可达目标快速中止的判定基础）。 */
class CopperGolemStuckTicksTest {

	@Test
	void incrementsWhenStayingInPlace() {
		BlockPos p = new BlockPos(1, 2, 3);
		assertEquals(1, CopperGolemAiService.nextStuckTicks(p, p, 0));
		assertEquals(2, CopperGolemAiService.nextStuckTicks(p, p, 1));
		assertEquals(100, CopperGolemAiService.nextStuckTicks(p, p, 99)); // 100 tick 阈值触发中止
	}

	@Test
	void resetsWhenMoving() {
		BlockPos a = new BlockPos(1, 2, 3);
		BlockPos b = new BlockPos(4, 2, 3);
		assertEquals(0, CopperGolemAiService.nextStuckTicks(b, a, 50)); // 换格 → 归零
	}

	@Test
	void firstTickNeverCounts() {
		BlockPos p = new BlockPos(1, 2, 3);
		// lastPos=null（首次记录）：不递增，先记录位置
		assertEquals(0, CopperGolemAiService.nextStuckTicks(p, null, 0));
		// 与自己的位置比较：首轮已记录位置，下一轮同位置才 +1
		assertEquals(1, CopperGolemAiService.nextStuckTicks(p, p, 0));
	}
}
