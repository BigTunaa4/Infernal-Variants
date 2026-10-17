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
		8, 2, 4, 8, 6, Special.SHEEN, Emblem.NONE, Effect.GOLD);

	enum Special
	{
		NONE, DRAGON, GRAIN, PRISM, FROST, MOSS, SHEEN
	}

	enum Emblem
	{
		NONE, STAR, RUNES, VOID, HOOD, LEAVES
	}

	enum Effect
	{
		EMBERS, MOTES, WISPS, SNOW, DRIPS, LEAVES, SPARKS, GLYPHS, DRAGONFIRE, VOID, SAND, GLINTS, SMOKE, GOLD
	}

	private final String displayName;
	/** Gradient stops as alternating position/RGB pairs; null keeps the original lava texture. */
	final float[] stopPos;
	final int[] stopRgb;
	/** Jagex HSL hue (0-63), saturation (0-7) and lightness shift (0-127) for the grey rock trim. -1 = untouched. */
	final int rockHue, rockSat, rockLightShift;
	/** Jagex HSL hue/saturation for the red/orange accents and inner lining. -1 = untouched. */
	final int glowHue, glowSat;
	final Special special;
	final Emblem emblem;
	final Effect effect;

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
