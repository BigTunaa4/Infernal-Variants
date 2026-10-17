package com.infernalvariants;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(InfernalVariantsConfig.GROUP)
public interface InfernalVariantsConfig extends Config
{
	String GROUP = "infernalvariants";

	@ConfigSection(
		name = "Effects",
		description = "Particle effects around the cape",
		position = 10
	)
	String effectsSection = "effects";

	@ConfigSection(
		name = "Other players",
		description = "Show variants on other players' infernal capes",
		position = 20
	)
	String othersSection = "others";

	@ConfigItem(
		keyName = "style",
		name = "Cape style",
		description = "The design painted onto your infernal cape",
		position = 1
	)
	default CapeStyle style()
	{
		return CapeStyle.DRAGONFIRE;
	}

	@ConfigItem(
		keyName = "flowing",
		name = "Flowing veins",
		description = "Let the glowing veins slowly flow, like the original lava",
		position = 2
	)
	default boolean flowing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "emblems",
		name = "Emblems",
		description = "Show the style's emblem (Holy star, Rune circles, Void eye, Roguelike hood, Nature leaves)",
		position = 3
	)
	default boolean emblems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxCape",
		name = "Include infernal max cape",
		description = "Also repaint the infernal max cape",
		position = 4
	)
	default boolean maxCape()
	{
		return true;
	}

	@ConfigItem(
		keyName = "effects",
		name = "Cape effects",
		description = "Small particle effects that match the style (embers, snow, leaves, sparks...)",
		position = 11,
		section = effectsSection
	)
	default boolean effects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "intensity",
		name = "Effect amount",
		description = "How many particles the cape gives off",
		position = 12,
		section = effectsSection
	)
	default Intensity intensity()
	{
		return Intensity.NORMAL;
	}

	@ConfigItem(
		keyName = "others",
		name = "Other players' capes",
		description = "Off: only your cape. Same style: everyone's infernal cape uses your style. "
			+ "Random: each player gets their own style (picked from their name, so it stays the same)",
		position = 21,
		section = othersSection
	)
	default OthersMode others()
	{
		return OthersMode.OFF;
	}

	@ConfigItem(
		keyName = "othersEffects",
		name = "Effects on others",
		description = "Also show particle effects on other players' capes",
		position = 22,
		section = othersSection
	)
	default boolean othersEffects()
	{
		return false;
	}

	enum Intensity
	{
		SUBTLE(0.5f),
		NORMAL(1f),
		STRONG(1.7f);

		final float multiplier;

		Intensity(float multiplier)
		{
			this.multiplier = multiplier;
		}
	}

	enum OthersMode
	{
		OFF("Off"),
		SAME("Same style"),
		RANDOM("Random per player");

		private final String name;

		OthersMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
