package com.infernalvariants;

/**
 * The cape designs. Each style describes:
 * <ul>
 *     <li>a heat gradient that repaints the cape's lava veins (plate colour -> vein edge -> vein -> hot core)</li>
 *     <li>how the rock trim, collar and inner lining are recoloured (Jagex HSL hue/saturation)</li>
 *     <li>an optional special texture treatment and emblem set</li>
 * </ul>
 */
public enum CapeStyle
{
	INFERNAL("Infernal (original)", null, -1, 0, 0, -1, 0, Special.NONE, Emblem.NONE),

	HOLY("Holy",
		grad(0f, 0xEEE8DA, .2f, 0xE4DAC4, .3f, 0xC4B48E, .5f, 0xC08A26, .8f, 0xF0C858, 1f, 0xFFF6CC),
		9, 5, 40, 9, 6, Special.NONE, Emblem.STAR),

	SHADOW("Shadow",
		grad(0f, 0x110C18, .3f, 0x2E124A, .6f, 0x7634BE, 1f, 0xD6A0FF),
		50, 2, 0, 50, 6, Special.NONE, Emblem.NONE),

	ICE("Ice",
		grad(0f, 0x161E2C, .3f, 0x204678, .6f, 0x56A4E8, 1f, 0xE2F6FF),
		36, 2, 6, 36, 6, Special.FROST, Emblem.NONE),

	BLOOD("Blood",
		grad(0f, 0x120606, .3f, 0x3E0406, .6f, 0xA80A12, .85f, 0xE82A24, 1f, 0xFF6A50),
		0, 2, 0, 0, 7, Special.NONE, Emblem.NONE),

	NATURE("Nature",
		grad(0f, 0x161A10, .3f, 0x1E3414, .5f, 0x2E5A1C, .62f, 0x4E982E, 1f, 0xC0F070),
		18, 2, 2, 18, 6, Special.MOSS, Emblem.NONE),

	LIGHTNING("Lightning",
		grad(0f, 0x111216, .3f, 0x3A3410, .6f, 0xE8C21C, .85f, 0xFFEE8A, 1f, 0xFFFFF0),
		9, 1, 0, 10, 7, Special.NONE, Emblem.NONE),

	RUNE("Rune",
		grad(0f, 0x0C161C, .3f, 0x0E3A48, .6f, 0x1A96B4, 1f, 0x96F4FF),
		32, 2, 0, 32, 6, Special.NONE, Emblem.RUNES),

	VOID("Void",
		grad(0f, 0x0C0912, .35f, 0x24103A, .65f, 0x602C96, 1f, 0xBA6EF0),
		49, 2, 0, 49, 5, Special.NONE, Emblem.VOID),

	SANDSTONE("Sandstone",
		grad(0f, 0x967A52, .3f, 0x806440, .6f, 0x564028, 1f, 0x3E2C1C),
		7, 3, 30, 7, 3, Special.GRAIN, Emblem.NONE),

	CRYSTAL("Crystal",
		grad(0f, 0x86AAC2, .35f, 0xB4D4E8, .6f, 0xF8FFFF, .8f, 0x9ED6F0, 1f, 0x182234),
		34, 1, 40, 34, 3, Special.PRISM, Emblem.NONE),

	CORRUPTED("Corrupted",
		grad(0f, 0x1A0A0E, .3f, 0x52121C, .6f, 0xD8303C, 1f, 0xFFB0B4),
		62, 2, 4, 0, 6, Special.NONE, Emblem.NONE),

	SANGUINE("Sanguine",
		grad(0f, 0x1A0606, .3f, 0x500A0C, .6f, 0xC89A2E, 1f, 0xFFE6A0),
		0, 3, 0, 0, 7, Special.NONE, Emblem.NONE),

	AMASCUT("Amascut",
		grad(0f, 0x6E5434, .3f, 0x8A6A3A, .6f, 0xE6B64A, 1f, 0xFFF0B4),
		7, 3, 22, 9, 6, Special.GRAIN, Emblem.SUN),

	GUTHIX("Guthix",
		grad(0f, 0x2A2416, .3f, 0x3C3A1E, .6f, 0x5EA040, 1f, 0xD8F09A),
		10, 3, 6, 18, 5, Special.NONE, Emblem.GUTHIX),

	TORMENTED("Tormented",
		grad(0f, 0x0C0E0A, .3f, 0x1E2A0E, .6f, 0x5AC81E, 1f, 0xDCFF8A),
		16, 1, 0, 18, 7, Special.STARS, Emblem.NONE),

	VARLAMORE("Varlamore Sunset",
		grad(0f, 0x1C0E16, .3f, 0x5A1A1E, .6f, 0xF07A2A, 1f, 0xFFE08A),
		54, 2, 0, 5, 7, Special.SUNSET, Emblem.NONE),

	CUSTOM("Custom (pick colours)",
		grad(0f, 0x101010, .3f, 0x303030, .6f, 0x808080, 1f, 0xFFFFFF),
		0, 0, 0, 0, 0, Special.NONE, Emblem.NONE);

	enum Special
	{
		NONE, GRAIN, PRISM, FROST, MOSS, SUNSET, STARS
	}

	enum Emblem
	{
		NONE, STAR, RUNES, VOID, LEAVES, SUN, GUTHIX
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

	CapeStyle(String displayName, Object[] stops, int rockHue, int rockSat, int rockLightShift,
		int glowHue, int glowSat, Special special, Emblem emblem)
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
	}

	/** The design the Custom style borrows (pattern, motion, details), recoloured with the custom colours. */
	static CapeStyle customBase = ICE;

	private CapeStyle base()
	{
		return this == CUSTOM && customBase != CUSTOM && customBase != null ? customBase : this;
	}

	/** Texture treatment; Custom uses its base design's. */
	Special special()
	{
		return this == CUSTOM ? base().special : special;
	}

	/** Emblem set; Custom uses its base design's. */
	Emblem emblem()
	{
		return this == CUSTOM ? base().emblem : emblem;
	}

	/** The design painted on the panel for this style. */
	CapePattern pattern()
	{
		switch (this)
		{
			case HOLY:
				return CapePattern.HOLY;
			case SHADOW:
				return CapePattern.SMOKE;
			case ICE:
				return CapePattern.SHARDS;
			case BLOOD:
				return CapePattern.BLOOD;
			case NATURE:
				return CapePattern.OVERGROWN;
			case LIGHTNING:
				return CapePattern.STORM;
			case RUNE:
			case VOID:
				return CapePattern.CHANNEL;
			case SANDSTONE:
				return CapePattern.PLAIN_SCALES;
			case CRYSTAL:
				return CapePattern.SHATTERED;
			case CORRUPTED:
				return CapePattern.SPLINTERS;
			case SANGUINE:
				return CapePattern.VESSELS;
			case AMASCUT:
				return CapePattern.GLYPHS;
			case GUTHIX:
				return CapePattern.GRAIN;
			case VARLAMORE:
				return CapePattern.FEATHERS;
			case CUSTOM:
				return base() == CUSTOM ? CapePattern.CRACKS : base().pattern();
			default:
				// Infernal and Tormented keep the lava cracks
				return CapePattern.CRACKS;
		}
	}

	boolean isOriginal()
	{
		return stopPos == null;
	}

	/** Colour picked from the vein gradient, used to tint emblems. */
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
