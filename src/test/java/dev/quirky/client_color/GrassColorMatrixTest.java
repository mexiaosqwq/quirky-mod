package dev.quirky.client_color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GrassColorMatrixTest {
	@Test
	void defaultMatrixGreensUp() {
		GrassColorMatrix matrix = new GrassColorMatrix(1.0F);
		int out = matrix.convolve(0xFF91BD59);
		assertEquals(0xFF, (out >> 24) & 0xFF, "alpha 保留");
		assertEquals(129, (out >> 16) & 0xFF, "R = 0x91 * 0.89");
		assertEquals(210, (out >> 8) & 0xFF, "G = 0xBD * 1.11");
		assertEquals(79, out & 0xFF, "B = 0x59 * 0.89");
	}

	@Test
	void preservesNonOpaqueAlpha() {
		GrassColorMatrix matrix = new GrassColorMatrix(1.0F);
		int out = matrix.convolve(0x8091BD59);
		assertEquals(0x80, (out >> 24) & 0xFF);
		assertEquals(129, (out >> 16) & 0xFF);
	}

	@Test
	void clampsChannelsTo255() {
		// 1.5 外推：G 放大 1.165，高亮度输入被 clamp 到 255
		GrassColorMatrix matrix = new GrassColorMatrix(1.5F);
		int out = matrix.convolve(0xFFF0F0F0);
		assertEquals(200, (out >> 16) & 0xFF, "R = 0xF0 * 0.835");
		assertEquals(255, (out >> 8) & 0xFF, "G = 0xF0 * 1.165 溢出 clamp");
		assertEquals(200, out & 0xFF, "B = 0xF0 * 0.835");
	}

	@Test
	void halfMultiplierInterpolatesTowardVanilla() {
		// 0.5 时插值趋近恒等矩阵（不整体变暗）：R = 0.91*0.945、G = 0xBD*1.055、B = 0x59*0.945
		GrassColorMatrix matrix = new GrassColorMatrix(0.5F);
		int out = matrix.convolve(0xFF91BD59);
		assertEquals(137, (out >> 16) & 0xFF, "R = 0x91 * 0.945");
		assertEquals(199, (out >> 8) & 0xFF, "G = 0xBD * 1.055");
		assertEquals(84, out & 0xFF, "B = 0x59 * 0.945");
	}
}
