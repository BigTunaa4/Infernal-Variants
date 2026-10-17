package com.infernalvariants;

/**
 * The cape designs. Each style describes:
 * <ul>
 *     <li>a heat gradient that repaints the cape's lava veins (plate colour -> vein edge -> vein -> hot core)</li>
 *     <li>how the rock trim, collar and inner lining are recoloured (Jagex HSL hue/saturation)</li>
 *     <li>an optional special texture treatment, emblem set and particle effect</li>
 * </ul>
 */
public enum CapeStyle
{
	INFERNAL("Infernal (original)", null, -1, 0, 0, -1, 0, Special.NONE, Emblem.NONE, Effect.EMBERS),

	HOLY("Holy",
		grad(0f, 0xD2CCBC, .25f, 0xE2DCC8, .45f, 0xE6BE5A, .75f, 0xF8D670, 1f, 0xFFF8D2),
		8, 1, 44, 9, 4, Special.NONE, Emblem.STAR, Effect.MOTES),

	SHADOW("Shadow",
		grad(0f, 0x110C18, .3f, 0x2E124A, .6f, 0x7634BE, 1f, 0xD6A0FF),
		50, 2, 0, 50, 6, Special.NONE, Emblem.NONE, Effect.WISPS),

	ICE("Ice",
		grad(0f, 0x161E2C, .3f, 0x204678, .6f, 0x56A4E8, 1f, 0xE2F6FF),
		36, 2, 6, 36, 6, Special.FROST, Emblem.NONE, Effect.SNOW),

	BLOOD("Blood",
		grad(0f, 0x140808, .3f, 0x460608, .6f, 0xAA0E14, 1f, 0xF6483C),
		0, 2, 0, 0, 7, Special.NONE, Emblem.NONE, Effect.DRIPS),

	NATURE("Nature",
		grad(0f, 0x1A1E14, .3f, 0x243E18, .6f, 0x4E982E, 1f, 0xB8EC68),
		18, 2, 2, 18, 6, Special.MOSS, Emblem.LEAVES, Effect.LEAVES),

	LIGHTNING("Lightning",
		grad(0f, 0x121316, .3f, 0x4A420E, .6f, 0xF0D020, .85f, 0xFFF29A, 1f, 0xFFFFFF),
		9, 1, 0, 10, 7, Special.NONE, Emblem.NONE, Effect.SPARKS),

	RUNE("Rune",
		grad(0f, 0x0C161C, .3f, 0x0E3A48, .6f, 0x1A96B4, 1f, 0x96F4FF),
		32, 2, 0, 32, 6, Special.NONE, Emblem.RUNES, Effect.GLYPHS),

	DRAGONFIRE("Dragonfire",
		grad(0f, 0x160907, .3f, 0x601008, .6f, 0xDE3410, 1f, 0xFFB446),
		0, 2, 0, 1, 7, Special.DRAGON, Emblem.NONE, Effect.DRAGONFIRE),

	VOID("Void",
		grad(0f, 0x0C0912, .35f, 0x24103A, .65f, 0x602C96, 1f, 0xBA6EF0),
		49, 2, 0, 49, 5, Special.NONE, Emblem.VOID, Effect.VOID),

	SANDSTONE("Sandstone",
		grad(0f, 0x967A52, .3f, 0x806440, .6f, 0x564028, 1f, 0x3E2C1C),
		7, 3, 30, 7, 3, Special.GRAIN, Emblem.NONE, Effect.SAND),

	CRYSTAL("Crystal",
		grad(0f, 0xA2C4D4, .3f, 0x84B0C8, .6f, 0x6EDCE6, 1f, 0xF0FFFF),
		34, 1, 44, 34, 4, Special.PRISM, Emblem.NONE, Effect.GLINTS),

	ROGUELIKE("Roguelike",
		grad(0f, 0x0E0C0C, .35f, 0x1E0E0E, .65f, 0x78141A, 1f, 0xC42E2E),
		0, 1, 0, 0, 6, Special.NONE, Emblem.HOOD, Effect.SMOKE),

	GOLDEN("Golden",
		grad(0f, 0x24190C, .3f, 0x5A3A0A, .6f, 0xC8881A, .85f, 0xF2C04A, 1f, 0xFFE494),
		8, 2, 4, 8, 6, Special.SHEEN, Emblem.NONE, Effect.GOLD),

	TOXIC("Toxic",
		grad(0f, 0x0C1614, .3f, 0x0E3A2C, .6f, 0x3CC23A, 1f, 0xC8FF6A),
		26, 2, 0, 20, 6, Special.NONE, Emblem.NONE, Effect.VENOM),

	CORRUPTED("Corrupted",
		grad(0f, 0x1A0A0E, .3f, 0x52121C, .6f, 0xD8303C, 1f, 0xFFB0B4),
		62, 2, 4, 0, 6, Special.SHARDS, Emblem.NONE, Effect.STYLED_GLINTS),

	SANGUINE("Sanguine",
		grad(0f, 0x1A0606, .3f, 0x500A0C, .6f, 0xC89A2E, 1f, 0xFFE6A0),
		0, 3, 0, 0, 7, Special.NONE, Emblem.NONE, Effect.MIST),

	AMASCUT("Amascut",
		grad(0f, 0x6E5434, .3f, 0x8A6A3A, .6f, 0xE6B64A, 1f, 0xFFF0B4),
		7, 3, 22, 9, 6, Special.GRAIN, Emblem.SUN, Effect.SAND),

	TIDAL("Tidal",
		grad(0f, 0x081624, .3f, 0x0C3450, .6f, 0x2AB49C, 1f, 0xC8FFF0),
		38, 3, 2, 30, 6, Special.NONE, Emblem.NONE, Effect.DROPLETS),

	SARADOMIN("Saradomin",
		grad(0f, 0x16244A, .3f, 0x22407C, .6f, 0xE2B442, 1f, 0xFFF4C0),
		40, 3, 6, 9, 6, Special.NONE, Emblem.SARADOMIN, Effect.STYLED_MOTES),

	ZAMORAK("Zamorak",
		grad(0f, 0x100808, .3f, 0x3A0A0A, .6f, 0xC4201A, 1f, 0xFF8A50),
		0, 2, 0, 0, 7, Special.NONE, Emblem.ZAMORAK, Effect.STYLED_EMBERS),

	GUTHIX("Guthix",
		grad(0f, 0x2A2416, .3f, 0x3C3A1E, .6f, 0x5EA040, 1f, 0xD8F09A),
		10, 3, 6, 18, 5, Special.MOSS, Emblem.GUTHIX, Effect.LEAVES),

	ABYSSAL("Abyssal",
		grad(0f, 0x140A0E, .3f, 0x3C0E1E, .6f, 0x8E1E48, 1f, 0xF07AA0),
		58, 2, 0, 60, 6, Special.NONE, Emblem.NONE, Effect.MIST),

	TORMENTED("Tormented",
		grad(0f, 0x0C0E0A, .3f, 0x1E2A0E, .6f, 0x5AC81E, 1f, 0xDCFF8A),
		16, 1, 0, 18, 7, Special.NONE, Emblem.NONE, Effect.FLAMES),

	VARLAMORE("Varlamore Sunset",
		grad(0f, 0x1C0E16, .3f, 0x5A1A1E, .6f, 0xF07A2A, 1f, 0xFFE08A),
		54, 2, 0, 5, 7, Special.SUNSET, Emblem.NONE, Effect.STYLED_EMBERS),

	TWISTED("Twisted",
		grad(0f, 0x0A100C, .3f, 0x14301E, .6f, 0x2E8A54, 1f, 0xA8F0C0),
		22, 2, 0, 24, 5, Special.NONE, Emblem.TWISTED, Effect.MIST),

	CUSTOM("Custom (pick colours)",
		grad(0f, 0x101010, .3f, 0x303030, .6f, 0x808080, 1f, 0xFFFFFF),
		0, 0, 0, 0, 0, Special.NONE, Emblem.NONE, Effect.EMBERS);

	enum Special
	{
		NONE, DRAGON, GRAIN, PRISM, FROST, MOSS, SHEEN, SUNSET, SHARDS
	}

	enum Emblem
	{
		NONE, STAR, RUNES, VOID, HOOD, LEAVES, SUN, SARADOMIN, ZAMORAK, GUTHIX, TWISTED
	}

	public enum Effect
	{
		EMBERS("Embers"), MOTES("Holy motes"), WISPS("Shadow wisps"), SNOW("Snow"), DRIPS("Blood drips"),
		LEAVES("Leaves"), SPARKS("Lightning arcs"), GLYPHS("Rune marks"), DRAGONFIRE("Dragonfire"),
		VOID("Void pull"), SAND("Sand"), GLINTS("Crystal glints"), SMOKE("Smoke"), GOLD("Gold glints"),
		// these take their colours from the cape's own palette
		VENOM("Venom"), MIST("Mist"), DROPLETS("Droplets"), FLAMES("Hem flames"),
		STYLED_MOTES("Motes (cape colour)"), STYLED_EMBERS("Embers (cape colour)"), STYLED_GLINTS("Glints (cape colour)");

		private final String name;

		Effect(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final String displayName;
	/** Gradient stops as alternating position/RGB pairs; null keeps the original lava texture. */
	final float[] stopPos;
	final int[] stopRgb;
	/** Jagex HSL hue (0-63), saturation (0-7) and lightness shift (0-127) for the grey rock trim. -1 = untouched. */
	int rockHue, rockSat, rockLightShift;
	/** Jagex HSL hue/saturation for the red/orange accents and inner lining. -1 = untouched. */
	int glowHue, glowSat;
	final Special special;
	final Emblem emblem;
	Effect effect;

	CapeStyle(String displayName, Object[] stops, int rockHue, int rockSat, int rockLightShift,
		int glowHue, int glowSat, Special special, Emblem emblem, Effect effect)
	{
		this.displayName = displayName;
		if (stops == null)
		{
			this.stopPos = null;
			this.stopRgb = null;
		}
		else
		{
			this.stopPos = (float[]) stops[0];
			this.stopRgb = (int[]) stops[1];
		}
		this.rockHue = rockHue;
		this.rockSat = rockSat;
		this.rockLightShift = rockLightShift;
		this.glowHue = glowHue;
		this.glowSat = glowSat;
		this.special = special;
		this.emblem = emblem;
		this.effect = effect;
	}

	boolean isOriginal()
	{
		return stopPos == null;
	}

	/** Colour picked from the vein gradient, used to tint particles and emblems. */
	int veinColor(float heat)
	{
		if (stopPos == null)
		{
			return heat > 0.8f ? 0xFFD040 : 0xFF7A10;
		}
		return CapeTextures.sample(this, heat);
	}

	private static Object[] grad(Object... a)
	{
		int n = a.length / 2;
		float[] p = new float[n];
		int[] c = new int[n];
		for (int i = 0; i < n; i++)
		{
			p[i] = ((Number) a[i * 2]).floatValue();
			c[i] = (Integer) a[i * 2 + 1];
		}
		return new Object[]{p, c};
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
