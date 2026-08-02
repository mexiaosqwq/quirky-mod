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
		GrassColorMatrix matrix = new GrassColorMatrix(1.5F);
		assertEquals(0xFFFFFFFF, matrix.convolve(0xFFC0C0C0));
	}

	@Test
	void halfMultiplierMutesEffect() {
		GrassColorMatrix matrix = new GrassColorMatrix(0.5F);
		int out = matrix.convolve(0xFF91BD59);
		assertEquals(65, (out >> 16) & 0xFF, "R = 0x91 * 0.89 * 0.5");
		assertEquals(105, (out >> 8) & 0xFF, "G = 0xBD * 1.11 * 0.5");
		assertEquals(40, out & 0xFF, "B = 0x59 * 0.89 * 0.5");
	}
}
