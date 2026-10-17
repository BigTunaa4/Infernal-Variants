package com.infernalvariants;

/**
 * The finer art direction for each style: the colour that bleeds out of the veins onto the
 * stone (so veins read as two-tone and glowing rather than flat), how much the individual
 * stone plates vary, and how the cape moves.
 */
final class StyleLook
{
	enum Motion
	{
		/** Steady downward flow with a gentle glow breath. */
		FLOW,
		/** Glow swells and fades on a slow cycle. */
		PULSE,
		/** Irregular electric flicker with sudden surges. */
		FLICKER,
		/** A band of light sweeps down the cape. */
		SHIMMER,
		/** Several soft waves travel down the cape. */
		RIPPLE,
		/** Uneven firelight flicker, brighter toward the hem. */
		BURN,
	}

	/** Colour of the glow that spills from the veins onto the plates (RGB). */
	final int halo;
	/** 0-1: how strongly that glow spills. */
	final float haloStrength;
	/** 0-1: how much individual plates differ in shade. */
	final float plateVariation;
	final Motion motion;
	/** How fast the veins flow down the cape, in texels per second. */
	final float flowSpeed;
	/** Glow layer opacity range and cycle length. */
	final float glowMin, glowMax, period;

	private StyleLook(int halo, float haloStrength, float plateVariation, Motion motion, float flowSpeed,
		float glowMin, float glowMax, float period)
	{
		this.halo = halo;
		this.haloStrength = haloStrength;
		this.plateVariation = plateVariation;
		this.motion = motion;
		this.flowSpeed = flowSpeed;
		this.glowMin = glowMin;
		this.glowMax = glowMax;
		this.period = period;
	}

	StyleLook withHalo(int newHalo)
	{
		return new StyleLook(newHalo, haloStrength, plateVariation, motion, flowSpeed, glowMin, glowMax, period);
	}

	static StyleLook of(CapeStyle s)
	{
		switch (s)
		{
			case HOLY:
				return new StyleLook(0xFFF2C8, 0.45f, 0.10f, Motion.SHIMMER, 10, 0.05f, 0.75f, 4.5f);
			case SHADOW:
				return new StyleLook(0x5A1E96, 0.55f, 0.18f, Motion.PULSE, 8, 0.0f, 0.55f, 4.0f);
			case ICE:
				return new StyleLook(0x2E6CC0, 0.50f, 0.20f, Motion.SHIMMER, 6, 0.0f, 0.6f, 6.0f);
			case BLOOD:
				return new StyleLook(0x6A0408, 0.60f, 0.18f, Motion.FLOW, 18, 0.1f, 0.45f, 2.2f);
			case NATURE:
				return new StyleLook(0x2E6A18, 0.45f, 0.28f, Motion.PULSE, 7, 0.05f, 0.45f, 5.0f);
			case LIGHTNING:
				return new StyleLook(0xA88A10, 0.40f, 0.14f, Motion.FLICKER, 22, 0.0f, 0.95f, 0.9f);
			case RUNE:
				return new StyleLook(0x0C4E8A, 0.65f, 0.16f, Motion.RIPPLE, 12, 0.05f, 0.7f, 3.0f);
			case DRAGONFIRE:
				return new StyleLook(0x8A0E04, 0.70f, 0.16f, Motion.BURN, 26, 0.15f, 0.75f, 1.0f);
			case VOID:
				return new StyleLook(0x8A1E8A, 0.55f, 0.14f, Motion.PULSE, 8, 0.0f, 0.65f, 3.2f);
			case SANDSTONE:
				return new StyleLook(0x3A2816, 0.30f, 0.34f, Motion.FLOW, 5, 0.0f, 0.0f, 1f);
			case CRYSTAL:
				return new StyleLook(0xC8F0FF, 0.45f, 0.24f, Motion.SHIMMER, 8, 0.05f, 0.8f, 3.5f);
			case ROGUELIKE:
				return new StyleLook(0x3C0808, 0.55f, 0.12f, Motion.PULSE, 9, 0.0f, 0.4f, 5.5f);
			case GOLDEN:
				return new StyleLook(0x8A5A0A, 0.50f, 0.20f, Motion.SHIMMER, 10, 0.0f, 0.8f, 3.0f);
			case TOXIC:
				return new StyleLook(0x0E6A3A, 0.65f, 0.18f, Motion.FLOW, 14, 0.1f, 0.55f, 1.8f);
			case CORRUPTED:
				return new StyleLook(0x8A1426, 0.60f, 0.30f, Motion.FLICKER, 10, 0.05f, 0.7f, 2.4f);
			case SANGUINE:
				return new StyleLook(0x8A0A10, 0.70f, 0.16f, Motion.PULSE, 12, 0.1f, 0.65f, 2.6f);
			case AMASCUT:
				return new StyleLook(0xFFD27A, 0.35f, 0.30f, Motion.SHIMMER, 7, 0.0f, 0.7f, 5.0f);
			case TIDAL:
				return new StyleLook(0x0E5C8A, 0.60f, 0.20f, Motion.RIPPLE, 14, 0.05f, 0.65f, 2.6f);
			case SARADOMIN:
				return new StyleLook(0xC8D8FF, 0.40f, 0.14f, Motion.SHIMMER, 10, 0.05f, 0.8f, 4.0f);
			case ZAMORAK:
				return new StyleLook(0x7A0806, 0.70f, 0.16f, Motion.BURN, 18, 0.15f, 0.7f, 1.2f);
			case GUTHIX:
				return new StyleLook(0x3C5A1A, 0.40f, 0.30f, Motion.PULSE, 6, 0.05f, 0.45f, 6.0f);
			case ABYSSAL:
				return new StyleLook(0x5A0A3A, 0.65f, 0.18f, Motion.PULSE, 9, 0.05f, 0.6f, 2.8f);
			case TORMENTED:
				return new StyleLook(0x1E5A0A, 0.70f, 0.16f, Motion.BURN, 20, 0.15f, 0.8f, 1.0f);
			case VARLAMORE:
				return new StyleLook(0x9A2A1A, 0.60f, 0.18f, Motion.BURN, 12, 0.1f, 0.6f, 1.6f);
			case TWISTED:
				return new StyleLook(0x0E4A2A, 0.60f, 0.18f, Motion.RIPPLE, 8, 0.05f, 0.55f, 3.6f);
			case CUSTOM:
			{
				// borrow the base design's motion and finish, with a halo in the custom colours
				CapeStyle base = CapeStyle.customBase;
				StyleLook b = base == null || base == CapeStyle.CUSTOM || base == CapeStyle.INFERNAL
					? new StyleLook(0, 0.55f, 0.18f, Motion.FLOW, 12, 0.1f, 0.55f, 2.5f)
					: of(base);
				return b.withHalo(CapeTextures.lerp(s.stopRgb[0], s.stopRgb[2], 0.55f));
			}
			default:
				return new StyleLook(0xA02008, 0.5f, 0.15f, Motion.FLOW, 50, 0f, 0f, 1f);
		}
	}

	/**
	 * Glow layer opacity at time {@code t} (seconds) for a face at {@code hemness}
	 * (0 = collar, 1 = hem). {@code seed} varies per face so flicker isn't uniform.
	 */
	float glowAlpha(float t, float hemness, float seed)
	{
		float span = glowMax - glowMin;
		float a;
		switch (motion)
		{
			case PULSE:
			{
				float w = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI / period + hemness * 1.2));
				a = glowMin + span * w * w;
				break;
			}
			case FLICKER:
			{
				// layered fast sines give an irregular crackle, with an occasional full surge
				double n = Math.sin(t * 23.0 + seed * 7.0) * Math.sin(t * 9.0 + seed * 3.0) * Math.sin(t * 3.1 + seed);
				float w = (float) Math.max(0, n) * 1.6f;
				float surge = (float) Math.pow(Math.max(0, Math.sin(t * 2 * Math.PI / period)), 24);
				a = glowMin + span * Math.min(1f, w + surge);
				break;
			}
			case SHIMMER:
			{
				// a soft band travels from collar to hem, then pauses before the next pass
				float phase = (t % period) / period * 1.6f - 0.3f;
				float d = hemness - phase;
				float w = (float) Math.exp(-d * d / 0.018);
				a = glowMin + span * w;
				break;
			}
			case RIPPLE:
			{
				float w = (float) (0.5 + 0.5 * Math.sin(hemness * 11.0 - t * 2 * Math.PI / period));
				a = glowMin + span * w * w * w;
				break;
			}
			case BURN:
			{
				double n = 0.5 + 0.25 * Math.sin(t * 11.0 + seed * 5.0) + 0.25 * Math.sin(t * 17.0 + seed * 2.0);
				a = glowMin + span * (float) n * (0.55f + 0.45f * hemness);
				break;
			}
			case FLOW:
			default:
			{
				float w = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI / period + seed));
				a = glowMin + span * w;
				break;
			}
		}
		return Math.max(0f, Math.min(1f, a));
	}
}
