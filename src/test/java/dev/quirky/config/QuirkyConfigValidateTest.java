package dev.quirky.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 配置合理性：validatePostLoad 越界 clamp + lang 键与字段机器对齐。 */
class QuirkyConfigValidateTest {

	@Test
	void validateClampsOutOfRangeValues() throws Exception {
		QuirkyConfig c = new QuirkyConfig();
		c.maxTransportRange = 500;
		c.aiTemperature = 10F;
		c.aiMaxTokens = 1;
		c.heartbeatIntervalSeconds = -5;
		c.modelScale = 100F;
		c.boomerangBreakChance = -1F;
		c.validatePostLoad();
		assertEquals(128, c.maxTransportRange);
		assertEquals(2F, c.aiTemperature);
		assertEquals(64, c.aiMaxTokens);
		assertEquals(0, c.heartbeatIntervalSeconds);
		assertEquals(10F, c.modelScale);
		assertEquals(0F, c.boomerangBreakChance);
	}

	@Test
	void validateKeepsInRangeDefaults() throws Exception {
		QuirkyConfig c = new QuirkyConfig();
		c.validatePostLoad();
		assertEquals(64, c.maxTransportRange);
		assertEquals(0.7F, c.aiTemperature);
		assertEquals(256, c.aiMaxTokens);
		assertEquals(30, c.heartbeatIntervalSeconds);
	}

	@Test
	void langKeysCoverAllConfigFields() throws Exception {
		Set<String> fields = new HashSet<>();
		for (var f : QuirkyConfig.class.getFields()) {
			if (f.isAnnotationPresent(ConfigEntry.Gui.Excluded.class)) {
				continue; // GUI 隐藏字段无 lang 键（json5 可改，不渲染）
			}
			fields.add(f.getName());
		}
		try (var stream = QuirkyConfigValidateTest.class.getClassLoader()
			.getResourceAsStream("assets/quirky/lang/en_us.json")) {
			assertNotNull(stream, "en_us.json 必须在测试 classpath");
			JsonObject lang = JsonParser.parseString(new String(stream.readAllBytes())).getAsJsonObject();
			Set<String> optionKeys = new HashSet<>();
			Set<String> tooltipKeys = new HashSet<>();
			lang.keySet().forEach(k -> {
				if (k.startsWith("text.autoconfig.quirky.option.")) {
					String field = k.substring("text.autoconfig.quirky.option.".length());
					if (field.endsWith(".@Tooltip")) {
						tooltipKeys.add(field.substring(0, field.length() - ".@Tooltip".length()));
					} else {
						optionKeys.add(field);
					}
				}
			});
			assertEquals(fields, optionKeys, "option 键必须覆盖全部可见配置字段");
			assertEquals(fields, tooltipKeys, "@Tooltip 键必须覆盖全部可见配置字段");
		}
	}

	@Test
	void hiddenFieldsAreGuiExcluded() throws Exception {
		// 抽查代表字段：防漏注解（漏了会出现在 GUI，对齐铁律即失败）
		for (String name : new String[] {"quiverCapacity", "boomerangRange", "deathCamDuration",
			"aiTemperature", "modelScale", "aiSummaryTokens"}) {
			var f = QuirkyConfig.class.getField(name);
			assertNotNull(f.getAnnotation(ConfigEntry.Gui.Excluded.class), name + " 必须 Gui.Excluded");
		}
	}
}
