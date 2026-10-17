package com.infernalvariants;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(InfernalVariantsConfig.GROUP)
public interface InfernalVariantsConfig extends Config
{
	String GROUP = "infernalvariants";

	@ConfigSection(
		name = "Other players",
		description = "Show variants on other players' infernal capes",
		position = 20
	)
	String othersSection = "others";

	@ConfigSection(
		name = "Custom style",
		description = "Colours used when the cape style is set to Custom",
		position = 30,
		closedByDefault = true
	)
	String customSection = "custom";

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
		name = "Animated veins",
		description = "Each style moves in its own way: flowing lava, pulsing glow, electric flicker, light sweeping down the cape",
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
		keyName = "customPlate",
		name = "Plate colour",
		description = "Colour of the rock plates between the veins",
		position = 31,
		section = customSection
	)
	default Color customPlate()
	{
		return new Color(0x14141E);
	}

	@ConfigItem(
		keyName = "customVein",
		name = "Vein colour",
		description = "Main colour of the glowing veins",
		position = 32,
		section = customSection
	)
	default Color customVein()
	{
		return new Color(0x3CA0FF);
	}

	@ConfigItem(
		keyName = "customCore",
		name = "Glow colour",
		description = "Colour at the hottest centre of the veins",
		position = 33,
		section = customSection
	)
	default Color customCore()
	{
		return new Color(0xE6F6FF);
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
