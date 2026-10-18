package com.infernalvariants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Builds the repainted vein textures and the pixel-art emblems.
 * <p>
 * The vein pattern itself comes from the game's own infernal cape texture at runtime: it is
 * reduced to a "heat" map (black plate -> glowing vein core) and repainted with each style's
 * gradient, so every variant keeps the exact Jagex crack pattern. Nothing from the game cache
 * is shipped with the plugin.
 */
final class CapeTextures
{
	static final int SIZE = 128;

	private CapeTextures()
	{
	}

	/**
	 * Convert the game's infernal cape texture into a heat map in [0,1].
	 *
	 * @param pixels     texture pixels as returned by the texture provider (brightness applied)
	 * @param brightness the texture provider brightness used for those pixels
	 */
	static float[] heatMap(int[] pixels, double brightness)
	{
		float[] heat = new float[SIZE * SIZE];
		double inv = brightness > 0 ? 1.0 / brightness : 1.0;
		for (int i = 0; i < heat.length && i < pixels.length; i++)
		{
			int p = pixels[i];
			double r = Math.pow(((p >> 16) & 0xFF) / 255.0, inv) * 255.0;
			double g = Math.pow(((p >> 8) & 0xFF) / 255.0, inv) * 255.0;
			double h = (r - 15) / 240.0 * 0.62 + Math.max(0, g - 40) / 180.0 * 0.38;
			heat[i] = (float) Math.max(0, Math.min(1, h));
		}
		return heat;
	}

	static int sample(CapeStyle style, float h)
	{
		return sampleStops(style.stopPos, style.stopRgb, h);
	}

	/**
	 * Structural analysis of the crack pattern, shared by every style: which stone plate each
	 * texel belongs to, how far it sits from a vein, and a soft "glow" field around the veins.
	 */
	static final class Analysis
	{
		final float[] heat;
		/** Plate id per texel (-1 on veins). */
		final int[] plate;
		/** A stable random value per plate, 0-1. */
		final float[] plateRand;
		/** Blurred vein mask: 1 right at a vein, fading to 0 a few texels away. */
		final float[] glow;
		/** 0 at a plate's edge, rising toward its centre. */
		final float[] centre;

		Analysis(float[] heat)
		{
			this.heat = heat;
			final int n = SIZE * SIZE;
			plate = new int[n];
			java.util.Arrays.fill(plate, -2);
			int count = 0;
			int[] stack = new int[n];
			for (int i = 0; i < n; i++)
			{
				if (heat[i] >= 0.2f)
				{
					plate[i] = -1;
				}
			}
			// flood-fill plates; the texture tiles, so neighbours wrap around
			for (int i = 0; i < n; i++)
			{
				if (plate[i] != -2)
				{
					continue;
				}
				int sp = 0;
				stack[sp++] = i;
				plate[i] = count;
				while (sp > 0)
				{
					int k = stack[--sp];
					int x = k % SIZE, y = k / SIZE;
					int[] nb = {
						y * SIZE + (x + 1) % SIZE, y * SIZE + (x + SIZE - 1) % SIZE,
						((y + 1) % SIZE) * SIZE + x, ((y + SIZE - 1) % SIZE) * SIZE + x};
					for (int m : nb)
					{
						if (plate[m] == -2)
						{
							plate[m] = count;
							stack[sp++] = m;
						}
					}
				}
				count++;
			}
			plateRand = new float[Math.max(1, count)];
			Random r = new Random(59);
			for (int i = 0; i < plateRand.length; i++)
			{
				plateRand[i] = r.nextFloat();
			}

			float[] vein = new float[n];
			for (int i = 0; i < n; i++)
			{
				vein[i] = clamp((heat[i] - 0.3f) / 0.35f);
			}
			glow = blur(blur(vein, 2), 2);
			float max = 0;
			for (float g : glow)
			{
				max = Math.max(max, g);
			}
			centre = new float[n];
			float[] far = blur(blur(vein, 5), 5);
			float fmax = 0;
			for (float f : far)
			{
				fmax = Math.max(fmax, f);
			}
			for (int i = 0; i < n; i++)
			{
				glow[i] = max > 0 ? clamp(glow[i] / max * 1.6f) : 0;
				centre[i] = fmax > 0 ? 1f - clamp(far[i] / fmax * 1.8f) : 1f;
			}
		}

		private static float[] blur(float[] src, int r)
		{
			float[] tmp = new float[src.length], out = new float[src.length];
			float k = 1f / (2 * r + 1);
			for (int y = 0; y < SIZE; y++)
			{
				for (int x = 0; x < SIZE; x++)
				{
					float s = 0;
					for (int d = -r; d <= r; d++)
					{
						s += src[y * SIZE + (x + d + SIZE) % SIZE];
					}
					tmp[y * SIZE + x] = s * k;
				}
			}
			for (int y = 0; y < SIZE; y++)
			{
				for (int x = 0; x < SIZE; x++)
				{
					float s = 0;
					for (int d = -r; d <= r; d++)
					{
						s += tmp[((y + d + SIZE) % SIZE) * SIZE + x];
					}
					out[y * SIZE + x] = s * k;
				}
			}
			return out;
		}
	}

	/** The main repainted texture for a style. */
	static BufferedImage veinTexture(CapeStyle style, Analysis an)
	{
		return paint(style, an, style.stopPos, style.stopRgb, StyleLook.of(style), false);
	}

	/**
	 * A second texture faded in toward the hem (Varlamore Sunset fades to dusk).
	 */
	static boolean hasHem(CapeStyle style)
	{
		return style.special() == CapeStyle.Special.SUNSET;
	}

	static BufferedImage hemTexture(CapeStyle style, Analysis an)
	{
		if (!hasHem(style))
		{
			return null;
		}
		float[] p = {0f, .3f, .6f, 1f};
		// varlamore sunset: warm orange fades into dusk purple toward the hem
		int[] c = {0x140A1C, 0x3A1450, 0xA846D2, 0xF6C8FF};
		StyleLook base = StyleLook.of(style);
		int halo = 0x5A1478;
		StyleLook look = base.withHalo(halo);
		return paint(style, an, p, c, look, true);
	}

	/**
	 * The vein glow layer: the hot vein cores plus a little of the surrounding halo, as a
	 * translucent image. The overlay fades it in and out to animate each style.
	 */
	static BufferedImage glowTexture(CapeStyle style, Analysis an)
	{
		int core = lerp(sample(style, 1f), 0xFFFFFF, 0.3f);
		StyleLook look = StyleLook.of(style);
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		boolean dark = style == CapeStyle.SANDSTONE;
		// shattered crystal glows on the bright shard rims, not in the dark gaps between them
		boolean rims = style.special() == CapeStyle.Special.PRISM;
		if (rims)
		{
			core = 0xFFFFFF;
		}
		for (int i = 0; i < SIZE * SIZE; i++)
		{
			float h = an.heat[i];
			float a = clamp((h - 0.5f) / 0.45f);
			a = a * a * 0.9f + an.glow[i] * 0.22f * look.haloStrength;
			if (rims)
			{
				float d = (h - 0.6f) / 0.13f;
				a = (float) Math.exp(-d * d) * 0.85f;
			}
			if (dark)
			{
				a = 0;
			}
			int col = h > 0.6f ? core : lerp(look.halo, core, 0.5f);
			img.setRGB(i % SIZE, i / SIZE, ((int) (clamp(a) * 255) << 24) | col);
		}
		return img;
	}

	private static BufferedImage paint(CapeStyle style, Analysis an, float[] pos, int[] rgb, StyleLook look, boolean hem)
	{
		CapePattern pattern = style.pattern();
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		Random rng = new Random(0x1F3AL + style.ordinal() + (hem ? 99 : 0));
		// the rock's own tint: neutral grey pulled toward the style's stone colour
		int plateTint = lerp(0xA0A0A0, rgb[Math.min(1, rgb.length - 1)], 0.45f);
		for (int y = 0; y < SIZE; y++)
		{
			for (int x = 0; x < SIZE; x++)
			{
				int i = y * SIZE + x;
				float h = an.heat[i];
				int c = sampleStops(pos, rgb, h);
				float n = rng.nextFloat();
				float u = x / 127f, v = y / 127f;

				// the surface between the glowing lines: rock, cloth, metal, crystal...
				float plateness = 1f - clamp((h - 0.12f) / 0.3f);
				if (plateness > 0)
				{
					float pr = an.plate[i] >= 0 ? an.plateRand[an.plate[i]] : 0.5f;
					c = surface(pattern.surface, c, x, y, u, v, n, pr, an.centre[i], plateness, plateTint, look);
					// glow from the lines spilling onto the surface
					float spill = an.glow[i] * plateness * look.haloStrength;
					c = screen(c, look.halo, spill * 0.85f);
				}
				// a thin dark rim where stone meets vein gives the cracks depth
				if (h > 0.16f && h < 0.3f)
				{
					c = scale(c, 0.78f);
				}

				c = special(style.special(), c, h, n, u, v, x, y, an, rgb, look);
				img.setRGB(x, y, c);
			}
		}
		return img;
	}

	/** Shade one texel of the background surface for a pattern's material. */
	private static int surface(CapePattern.Surface type, int c, int x, int y, float u, float v, float n, float pr,
		float centre, float plateness, int tint, StyleLook look)
	{
		switch (type)
		{
			case ROCK:
			{
				float dome = 0.86f + 0.26f * centre;
				c = scale(c, 1f + (dome - 1f) * plateness);
				// lit from the upper right using the slope of a soft noise field
				float bump = stone(x + 1, y - 1) - stone(x - 1, y + 1);
				float amount = (0.05f * (stone(x, y) - 0.5f) * 2f + 1.1f * bump
					+ look.plateVariation * (pr - 0.5f) * 0.22f + 0.03f * centre + 0.02f * (n - 0.5f)) * plateness;
				return addTinted(c, tint, amount);
			}
			case CLOTH:
			{
				// a fine over-under weave
				boolean over = (((x >> 1) + (y >> 1)) & 1) == 0;
				float weave = (over ? 0.035f : -0.035f) + ((x & 1) == 0 ? 0.012f : -0.012f);
				float drape = (stone(x / 2, y) - 0.5f) * 0.08f;
				return addTinted(c, tint, (weave + drape + 0.015f * (n - 0.5f)) * plateness);
			}
			case METAL:
			{
				// polished: strongly domed with a bright highlight high on each piece
				float dome = 0.62f + 0.55f * centre;
				c = scale(c, 1f + (dome - 1f) * plateness);
				float hl = (float) Math.pow(centre, 6) * 0.5f;
				return screen(c, lerp(tint, 0xFFFFFF, 0.5f), hl * plateness);
			}
			case FACET:
			{
				// flat crystal faces, each its own tone, with a gentle gradient across it
				double ang = pr * Math.PI * 2;
				float grad = (float) ((u - 0.5) * Math.cos(ang) + (v - 0.5) * Math.sin(ang)) * 0.9f;
				float amount = ((pr - 0.5f) * 0.3f + grad * 0.25f) * plateness;
				return addTinted(c, tint, amount);
			}
			case SOFT:
			{
				float mottle = (stone(x, y) - 0.5f) * 0.14f + (pr - 0.5f) * look.plateVariation * 0.12f;
				float dome = 0.93f + 0.1f * centre;
				c = scale(c, 1f + (dome - 1f) * plateness);
				return addTinted(c, tint, mottle * plateness);
			}
			case SCALE:
			{
				// each little scale is lit from above: bright at its top, darker toward its tip
				int w = 32, step = 16;
				int row = Math.floorMod(y + 4, step * 2) < step ? 0 : 1;
				float sy = Math.floorMod(y + 4, step) / (float) step;
				float sx = Math.floorMod(x - (row == 1 ? w / 2 : 0), w) / (float) w;
				float lit = 0.07f * (1f - sy) - 0.05f * sy - 0.03f * Math.abs(sx - 0.5f) * 2f;
				float rough = (stone(x, y) - 0.5f) * 0.06f + (pr - 0.5f) * look.plateVariation * 0.1f;
				return addTinted(c, tint, (lit + rough + 0.015f * (n - 0.5f)) * plateness);
			}
			case SPACE:
			default:
				return addTinted(c, tint, (n - 0.5f) * 0.03f * plateness);
		}
	}

	/** Per-texel style touches. */
	private static int special(CapeStyle.Special sp, int c, float h, float n, float u, float v, int x, int y, Analysis an,
		int[] rgb, StyleLook look)
	{
		switch (sp)
		{
			case GRAIN:
				// sandstone: gritty grain and faint sediment layers
				return scale(c, 0.9f + 0.16f * n + 0.07f * (float) Math.sin(v * 44 + Math.sin(u * 9) * 1.5));
			case PRISM:
			{
				// crystal: every shard catches the light in its own pale colour, rims shimmer prismatic
				int pid = an.plate[y * SIZE + x];
				if (pid >= 0 && h < 0.45f)
				{
					float pr = an.plateRand[pid];
					int tint = Color.HSBtoRGB(0.45f + pr * 0.45f, 0.42f, 1f) & 0xFFFFFF;
					float lum = (((c >> 16) & 255) * 0.3f + ((c >> 8) & 255) * 0.59f + (c & 255) * 0.11f) / 255f;
					c = lerp(c, scale(tint, Math.min(1.15f, lum * 1.12f)), 0.6f);
				}
				if (h > 0.4f && h < 0.8f)
				{
					int rainbow = Color.HSBtoRGB((u * 0.8f + v * 0.6f) % 1f, 0.35f, 1f) & 0xFFFFFF;
					c = lerp(c, rainbow, 0.3f);
				}
				return c;
			}
			case FROST:
				// ice: rime speckles and frosted crack edges
				if (h < 0.25f && n > 0.92f)
				{
					c = lerp(c, 0xC8E0F6, 0.5f);
				}
				if (h > 0.25f && h < 0.42f)
				{
					c = lerp(c, 0xD8ECFF, 0.35f);
				}
				return c;
			case MOSS:
			{
				// nature: soft patches of moss creeping over the stone
				float m = noise(x, y, 16, 3) * 0.7f + noise(x, y, 8, 5) * 0.3f;
				if (h < 0.3f && m > 0.55f)
				{
					c = lerp(c, 0x3E6224, clamp((m - 0.55f) * 3f) * 0.7f);
					if (n > 0.85f)
					{
						c = lerp(c, 0x6A9A3A, 0.4f);
					}
				}
				return c;
			}
			case STARS:
				// tormented: drifting demon sparks in the dark stone
				if (h < 0.15f && n > 0.975f)
				{
					return lerp(c, n > 0.993f ? lerp(rgb[rgb.length - 1], 0xFFFFFF, 0.5f) : rgb[rgb.length - 2], 0.75f);
				}
				return c;
			default:
				return c;
		}
	}

	static int sampleStops(float[] pos, int[] rgb, float h)
	{
		if (h <= pos[0])
		{
			return rgb[0];
		}
		for (int i = 1; i < pos.length; i++)
		{
			if (h <= pos[i])
			{
				return lerp(rgb[i - 1], rgb[i], (h - pos[i - 1]) / (pos[i] - pos[i - 1]));
			}
		}
		return rgb[rgb.length - 1];
	}

	/** Tileable smooth value noise in [0,1]. */
	static float noise(int x, int y, int cell, int seed)
	{
		int cells = SIZE / cell;
		int cx = x / cell, cy = y / cell;
		float fx = (x % cell) / (float) cell, fy = (y % cell) / (float) cell;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		float a = hash(cx % cells, cy % cells, seed), b = hash((cx + 1) % cells, cy % cells, seed);
		float c = hash(cx % cells, (cy + 1) % cells, seed), d = hash((cx + 1) % cells, (cy + 1) % cells, seed);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	private static float hash(int x, int y, int seed)
	{
		int h = x * 374761393 + y * 668265263 + seed * 982451653;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}

	/** Soft multi-scale rock noise in [0,1], tileable. */
	private static float stone(int x, int y)
	{
		x = (x % SIZE + SIZE) % SIZE;
		y = (y % SIZE + SIZE) % SIZE;
		return noise(x, y, 16, 7) * 0.5f + noise(x, y, 8, 9) * 0.32f + noise(x, y, 4, 13) * 0.18f;
	}

	/** Add (or, for negative amounts, remove) a tinted amount of light. */
	static int addTinted(int c, int tint, float amount)
	{
		int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
		r = Math.max(0, Math.min(255, r + Math.round(((tint >> 16) & 255) * amount)));
		g = Math.max(0, Math.min(255, g + Math.round(((tint >> 8) & 255) * amount)));
		b = Math.max(0, Math.min(255, b + Math.round((tint & 255) * amount)));
		return (r << 16) | (g << 8) | b;
	}

	/** Screen blend: brightens toward {@code light} by {@code amount}. */
	static int screen(int c, int light, float amount)
	{
		amount = clamp(amount);
		int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
		int lr = (light >> 16) & 255, lg = (light >> 8) & 255, lb = light & 255;
		int sr = 255 - (255 - r) * (255 - lr) / 255;
		int sg = 255 - (255 - g) * (255 - lg) / 255;
		int sb = 255 - (255 - b) * (255 - lb) / 255;
		return lerp(c, (sr << 16) | (sg << 8) | sb, amount);
	}

	// ---------------------------------------------------------------- emblems

	/** A pixel-art emblem sprite. Drawn at 32x32 with hard edges so it reads like an OSRS texture. */
	static BufferedImage emblemSprite(CapeStyle.Emblem emblem, int variant, CapeStyle style)
	{
		final int s = 32;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		int core = style.veinColor(1f);
		int mid = style.veinColor(0.65f);
		int dark = style.veinColor(0.32f);
		switch (emblem)
		{
			case STAR:
			{
				// eight-pointed holy star: gold rays, bright heart
				Polygon big = star(16, 16, 15, 4, 4);
				Polygon small = star(16, 16, 10, 3, 4);
				small = rotate(small, 16, 16, Math.PI / 4);
				g.setColor(new Color(0x7A5A18));
				g.fill(grow(big));
				g.setColor(new Color(mid));
				g.fill(big);
				g.setColor(new Color(0xC89A34));
				g.fill(small);
				g.setColor(new Color(core));
				g.fillOval(12, 12, 8, 8);
				g.setColor(Color.WHITE);
				g.fillRect(15, 14, 2, 4);
				g.fillRect(14, 15, 4, 2);
				break;
			}
			case RUNES:
			{
				// rune circle: outer ring, inner diamond, glowing heart
				g.setStroke(new BasicStroke(3));
				g.setColor(new Color(dark));
				g.drawOval(3, 3, 26, 26);
				g.setStroke(new BasicStroke(2));
				g.setColor(new Color(mid));
				g.drawOval(4, 4, 24, 24);
				Polygon d = new Polygon(new int[]{16, 23, 16, 9}, new int[]{8, 16, 24, 16}, 4);
				g.setColor(new Color(core));
				g.draw(d);
				g.fillRect(14, 14, 4, 4);
				// a small rune notch that differs per circle
				g.setColor(new Color(mid));
				if (variant == 0)
				{
					g.fillRect(15, 1, 2, 5);
				}
				else if (variant == 1)
				{
					g.fillRect(1, 15, 5, 2);
					g.fillRect(26, 15, 5, 2);
				}
				else
				{
					g.fillRect(15, 26, 2, 5);
				}
				break;
			}
			case VOID:
			{
				if (variant == 0)
				{
					// the void eye: ring, dark iris, bright pupil
					g.setColor(new Color(dark));
					g.fillOval(2, 2, 28, 28);
					g.setColor(new Color(0x0A0610));
					g.fillOval(6, 6, 20, 20);
					g.setStroke(new BasicStroke(2));
					g.setColor(new Color(mid));
					g.drawOval(4, 4, 24, 24);
					g.setColor(new Color(core));
					g.fillOval(12, 12, 8, 8);
					g.setColor(new Color(0xF0E0FF));
					g.fillRect(14, 14, 3, 3);
				}
				else
				{
					g.setColor(new Color(mid));
					g.fillOval(8, 8, 16, 16);
					g.setColor(new Color(0x0A0610));
					g.fillOval(12, 12, 8, 8);
				}
				break;
			}
			case LEAVES:
			{
				// a single leaf; variant rotates it so the four leaves don't look stamped
				Polygon leaf = new Polygon(
					new int[]{16, 23, 26, 22, 16, 10, 6, 9},
					new int[]{2, 8, 16, 24, 30, 24, 16, 8}, 8);
				leaf = rotate(leaf, 16, 16, (variant - 1.5) * 0.7);
				g.setColor(new Color(0x1E3A12));
				g.fill(grow(leaf));
				g.setColor(new Color(mid));
				g.fill(leaf);
				g.setColor(new Color(core));
				g.setStroke(new BasicStroke(2));
				Polygon vein = rotate(new Polygon(new int[]{16, 16}, new int[]{5, 28}, 2), 16, 16, (variant - 1.5) * 0.7);
				g.drawLine(vein.xpoints[0], vein.ypoints[0], vein.xpoints[1], vein.ypoints[1]);
				break;
			}
			case SUN:
			{
				// Amascut's sun disc with short rays
				g.setColor(new Color(0x5A3C10));
				g.fillOval(7, 7, 18, 18);
				for (int i = 0; i < 8; i++)
				{
					double a = Math.PI * i / 4;
					int x1 = (int) Math.round(16 + Math.cos(a) * 10), y1 = (int) Math.round(16 + Math.sin(a) * 10);
					int x2 = (int) Math.round(16 + Math.cos(a) * 15), y2 = (int) Math.round(16 + Math.sin(a) * 15);
					g.setStroke(new BasicStroke(3));
					g.setColor(new Color(0x5A3C10));
					g.drawLine(x1, y1, x2, y2);
					g.setStroke(new BasicStroke(1.6f));
					g.setColor(new Color(mid));
					g.drawLine(x1, y1, x2, y2);
				}
				g.setColor(new Color(mid));
				g.fillOval(9, 9, 14, 14);
				g.setColor(new Color(core));
				g.fillOval(12, 12, 8, 8);
				break;
			}
			case GUTHIX:
			{
				// Guthix's balance: a ring split in three
				g.setStroke(new BasicStroke(4));
				g.setColor(new Color(0x1E2A12));
				g.drawOval(4, 4, 24, 24);
				g.setStroke(new BasicStroke(2.4f));
				g.setColor(new Color(mid));
				g.drawOval(4, 4, 24, 24);
				for (int i = 0; i < 3; i++)
				{
					double a = -Math.PI / 2 + i * 2 * Math.PI / 3;
					g.drawLine(16, 16, (int) Math.round(16 + Math.cos(a) * 12), (int) Math.round(16 + Math.sin(a) * 12));
				}
				g.setColor(new Color(core));
				g.fillOval(13, 13, 6, 6);
				break;
			}
			default:
				break;
		}
		g.dispose();
		return img;
	}

	/** Number of different lightning-bolt frames the Lightning style cycles through. */
	static final int BOLT_FRAMES = 6;

	/**
	 * Lightning: transparent frames of forked bolts that leap across the stone between the
	 * glowing cracks. The overlay flashes one at a time.
	 */
	static BufferedImage[] boltFrames(CapeStyle style, Analysis an)
	{
		BufferedImage[] out = new BufferedImage[BOLT_FRAMES];
		Random r = new Random(97);
		int glow = sample(style, 0.8f);
		for (int f = 0; f < BOLT_FRAMES; f++)
		{
			BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int bolts = 2;
			for (int b = 0; b < bolts; b++)
			{
				// start on a vein
				int sx = 0, sy = 0;
				for (int t = 0; t < 2000; t++)
				{
					sx = r.nextInt(SIZE);
					sy = r.nextInt(SIZE);
					if (an.heat[sy * SIZE + sx] > 0.8f)
					{
						break;
					}
				}
				java.awt.geom.Path2D.Float main = new java.awt.geom.Path2D.Float();
				java.awt.geom.Path2D.Float fork = new java.awt.geom.Path2D.Float();
				main.moveTo(sx, sy);
				double ang = r.nextDouble() * Math.PI * 2;
				float x = sx, y = sy;
				int segs = 9 + r.nextInt(5);
				for (int k = 0; k < segs; k++)
				{
					ang += (r.nextDouble() - 0.5) * 1.4;
					float len = 6f + r.nextFloat() * 5f;
					x += (float) Math.cos(ang) * len;
					y += (float) Math.sin(ang) * len * 1.6f;
					main.lineTo(x, y);
					if (k == segs / 3 || k == (2 * segs) / 3)
					{
						fork.moveTo(x, y);
						double fa = ang + (r.nextBoolean() ? 0.9 : -0.9);
						float fx = x, fy = y;
						for (int m = 0; m < 4; m++)
						{
							fa += (r.nextDouble() - 0.5) * 1.2;
							fx += (float) Math.cos(fa) * 5f;
							fy += (float) Math.sin(fa) * 5f * 1.6f;
							fork.lineTo(fx, fy);
						}
					}
				}
				for (int dx = -SIZE; dx <= SIZE; dx += SIZE)
				{
					for (int dy = -SIZE; dy <= SIZE; dy += SIZE)
					{
						java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(dx, dy);
						Shape m = at.createTransformedShape(main), fk = at.createTransformedShape(fork);
						g.setColor(new Color((glow & 0xFFFFFF) | 0x40000000, true));
						g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(m);
						g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(fk);
						g.setColor(new Color(glow));
						g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(m);
						g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(fk);
						g.setColor(Color.WHITE);
						g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(m);
						g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g.draw(fk);
					}
				}
			}
			g.dispose();
			out[f] = img;
		}
		return out;
	}

	/** A soft round glow in the style's hot colour, drawn behind emblems. */
	static BufferedImage glowSprite(CapeStyle style)
	{
		final int s = 32;
		int col = lerp(style.veinColor(0.85f), 0xFFFFFF, 0.2f);
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < s; y++)
		{
			for (int x = 0; x < s; x++)
			{
				double d = Math.hypot(x + 0.5 - s / 2.0, y + 0.5 - s / 2.0) / (s / 2.0);
				float a = (float) Math.max(0, 1 - d);
				a = a * a * 0.8f;
				img.setRGB(x, y, ((int) (a * 255) << 24) | col);
			}
		}
		return img;
	}

	// ---------------------------------------------------------------- recolours

	/** Encode Jagex HSL (6-bit hue, 3-bit saturation, 7-bit lightness). */
	static short jagexHsl(int hue, int sat, int light)
	{
		hue = Math.max(0, Math.min(63, hue));
		sat = Math.max(0, Math.min(7, sat));
		light = Math.max(0, Math.min(127, light));
		return (short) ((hue << 10) | (sat << 7) | light);
	}

	/**
	 * Map one of the cape's original model colours to the style. Greys are the rock trim
	 * (collar, edges); saturated reds/oranges are the hem accents and the inner lining.
	 */
	static short mapColor(CapeStyle style, short src)
	{
		int c = src & 0xFFFF;
		int hue = (c >> 10) & 63;
		int sat = (c >> 7) & 7;
		int light = c & 127;
		if (sat == 0)
		{
			if (style.rockHue < 0)
			{
				return src;
			}
			return jagexHsl(style.rockHue, style.rockSat, light + style.rockLightShift);
		}
		if (style.glowHue < 0)
		{
			return src;
		}
		// keep the original's warm-to-hot ramp: orange accents sit one step "hotter"
		int shift = hue >= 2 ? 1 : 0;
		return jagexHsl(style.glowHue + shift, style.glowSat, light);
	}

	// ---------------------------------------------------------------- helpers

	static int lerp(int a, int b, float t)
	{
		t = clamp(t);
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	static int scale(int c, float f)
	{
		int r = Math.min(255, (int) (((c >> 16) & 255) * f));
		int g = Math.min(255, (int) (((c >> 8) & 255) * f));
		int b = Math.min(255, (int) ((c & 255) * f));
		return (r << 16) | (g << 8) | b;
	}

	static float clamp(float v)
	{
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	private static Polygon star(int cx, int cy, int outer, int inner, int points)
	{
		Polygon p = new Polygon();
		for (int i = 0; i < points * 2; i++)
		{
			double a = Math.PI * i / points - Math.PI / 2;
			int r = (i % 2 == 0) ? outer : inner;
			p.addPoint((int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r));
		}
		return p;
	}

	private static Polygon rotate(Polygon src, int cx, int cy, double a)
	{
		Polygon p = new Polygon();
		double cos = Math.cos(a), sin = Math.sin(a);
		for (int i = 0; i < src.npoints; i++)
		{
			double x = src.xpoints[i] - cx, y = src.ypoints[i] - cy;
			p.addPoint((int) Math.round(cx + x * cos - y * sin), (int) Math.round(cy + x * sin + y * cos));
		}
		return p;
	}

	/** Slightly enlarged copy of a polygon, used for a dark outline behind each emblem. */
	private static Polygon grow(Polygon src)
	{
		double cx = 0, cy = 0;
		for (int i = 0; i < src.npoints; i++)
		{
			cx += src.xpoints[i];
			cy += src.ypoints[i];
		}
		cx /= src.npoints;
		cy /= src.npoints;
		Polygon p = new Polygon();
		for (int i = 0; i < src.npoints; i++)
		{
			double dx = src.xpoints[i] - cx, dy = src.ypoints[i] - cy;
			double len = Math.max(1, Math.hypot(dx, dy));
			p.addPoint((int) Math.round(src.xpoints[i] + dx / len * 1.6), (int) Math.round(src.ypoints[i] + dy / len * 1.6));
		}
		return p;
	}
}
