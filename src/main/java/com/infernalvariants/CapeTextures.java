package com.infernalvariants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
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
	 * A second texture faded in toward the hem (Dragonfire burns blue, Varlamore Sunset fades to dusk).
	 */
	static boolean hasHem(CapeStyle style)
	{
		return style.special == CapeStyle.Special.DRAGON || style.special == CapeStyle.Special.SUNSET;
	}

	static BufferedImage hemTexture(CapeStyle style, Analysis an)
	{
		if (!hasHem(style))
		{
			return null;
		}
		float[] p = {0f, .3f, .6f, 1f};
		int[] c = style.special == CapeStyle.Special.DRAGON
			// dragonfire: the veins burn blue at the bottom
			? new int[]{0x120808, 0x14185A, 0x3A6CFF, 0xD0E8FF}
			// varlamore sunset: warm orange fades into dusk purple toward the hem
			: new int[]{0x140A1C, 0x3A1450, 0xA846D2, 0xF6C8FF};
		StyleLook base = StyleLook.of(style);
		int halo = style.special == CapeStyle.Special.DRAGON ? 0x10208A : 0x5A1478;
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
		for (int i = 0; i < SIZE * SIZE; i++)
		{
			float h = an.heat[i];
			float a = clamp((h - 0.5f) / 0.45f);
			a = a * a * 0.9f + an.glow[i] * 0.22f * look.haloStrength;
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

				// stone plates: each one its own shade, domed toward the middle, with a lit rocky surface
				float plateness = 1f - clamp((h - 0.12f) / 0.3f);
				if (plateness > 0)
				{
					float pr = an.plate[i] >= 0 ? an.plateRand[an.plate[i]] : 0.5f;
					float dome = 0.86f + 0.26f * an.centre[i];
					c = scale(c, 1f + (dome - 1f) * plateness);
					// light the rock from the upper right using the slope of a soft noise field
					float bump = stone(x + 1, y - 1) - stone(x - 1, y + 1);
					float amount = (0.05f * (stone(x, y) - 0.5f) * 2f
						+ 1.1f * bump
						+ look.plateVariation * (pr - 0.5f) * 0.22f
						+ 0.03f * an.centre[i]
						+ 0.02f * (n - 0.5f)) * plateness;
					c = addTinted(c, plateTint, amount);
					// glow from the veins spilling onto the stone
					float spill = an.glow[i] * plateness * look.haloStrength;
					c = screen(c, look.halo, spill * 0.85f);
				}
				// a thin dark rim where stone meets vein gives the cracks depth
				if (h > 0.16f && h < 0.3f)
				{
					c = scale(c, 0.78f);
				}

				c = special(style.special, c, h, n, u, v, x, y, an, rgb, look);
				img.setRGB(x, y, c);
			}
		}
		decorate(style.special, img, an, rgb);
		return img;
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
				// crystal: veins shimmer through a soft rainbow, the stone has bright facets
				float k = clamp((h - 0.38f) / 0.42f) * 0.72f;
				int rainbow = Color.HSBtoRGB((u * 0.8f + v * 0.6f) % 1f, 0.5f, 1f) & 0xFFFFFF;
				c = lerp(c, rainbow, k);
				float facet = (float) Math.abs(Math.sin(u * 31 + v * 13) * Math.sin(v * 27 - u * 9));
				if (h < 0.2f)
				{
					c = lerp(c, 0xF0FCFF, facet > 0.9f ? 0.45f : 0f);
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
			case SHEEN:
				// golden: polished diagonal highlights across the plates
				if (h < 0.3f)
				{
					// thin diagonal glints, like light catching polished metal
					float band = (float) Math.sin((u + v) * 14.0) * 0.5f + 0.5f;
					float streak = (float) Math.pow(band, 18);
					c = screen(c, 0xC8A050, streak * 0.55f * an.centre[y * SIZE + x]);
				}
				return c;
			case SHARDS:
			{
				// corrupted: each plate is a crystal shard, split by a bright diagonal facet edge
				if (h < 0.25f)
				{
					int pid = an.plate[y * SIZE + x];
					float pr = pid >= 0 ? an.plateRand[pid] : 0.5f;
					double ang = pr * Math.PI;
					double d = (u - 0.5) * Math.cos(ang) + (v - 0.5) * Math.sin(ang);
					double edge = Math.abs(((d * 9 + pr * 3) % 1 + 1) % 1 - 0.5);
					boolean light = ((int) Math.floor(d * 9 + pr * 3) & 1) == 0;
					c = light ? screen(c, 0x7A1C2A, 0.35f) : scale(c, 0.85f);
					if (edge > 0.47)
					{
						c = screen(c, 0xFF8090, 0.45f * an.centre[y * SIZE + x]);
					}
				}
				return c;
			}
			case CAUSTIC:
			{
				// tidal: rippling light lines like sunlight through water (abyssal: drifting tendrils)
				if (h < 0.3f)
				{
					double w = Math.sin(u * 20 + Math.sin(v * 13) * 2.2) + Math.sin(v * 17 + Math.sin(u * 11) * 2.0);
					float line = (float) Math.max(0, 1 - Math.abs(w) * 3.2);
					c = screen(c, look.halo, line * 0.6f);
				}
				return c;
			}
			case STARS:
				// void: a faint starfield inside the dark plates (tormented: drifting demon sparks)
				if (h < 0.15f && n > 0.975f)
				{
					return lerp(c, n > 0.993f ? lerp(rgb[rgb.length - 1], 0xFFFFFF, 0.5f) : rgb[rgb.length - 2], 0.75f);
				}
				return c;
			case BUBBLES:
			case BOLTS:
			case ETCH:
			default:
				return c;
		}
	}

	/** Larger hand-placed details drawn over the finished texture. */
	private static void decorate(CapeStyle.Special sp, BufferedImage img, Analysis an, int[] rgb)
	{
		if (sp != CapeStyle.Special.BUBBLES && sp != CapeStyle.Special.BOLTS && sp != CapeStyle.Special.ETCH)
		{
			return;
		}
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		Random r = new Random(1234 + sp.ordinal());
		int vein = rgb[rgb.length - 2];
		int core = rgb[rgb.length - 1];
		for (int k = 0; k < 400; k++)
		{
			int x = r.nextInt(SIZE), y = r.nextInt(SIZE);
			int i = y * SIZE + x;
			// only on the middle of a plate
			if (an.heat[i] > 0.1f || an.centre[i] < 0.55f)
			{
				continue;
			}
			switch (sp)
			{
				case BUBBLES:
				{
					// toxic: little venom bubbles in the stone
					int s = 2 + r.nextInt(3);
					g.setColor(new Color(lerp(vein, 0x000000, 0.35f)));
					g.drawOval(x, y, s, s);
					g.setColor(new Color(core));
					g.fillRect(x + 1, y + 1, 1, 1);
					k += 25;
					break;
				}
				case BOLTS:
				{
					// lightning: a faint forked crackle of charge across the stone
					g.setColor(new Color(lerp(vein, 0x000000, 0.55f)));
					int lx = x, ly = y;
					for (int s = 0; s < 3; s++)
					{
						int nx = lx + (r.nextBoolean() ? 2 : -2), ny = ly + 2;
						g.drawLine(lx, ly, nx, ny);
						if (s == 1)
						{
							g.drawLine(nx, ny, nx + (nx > lx ? 2 : -2), ny + 1);
						}
						lx = nx;
						ly = ny;
					}
					k += 45;
					break;
				}
				case ETCH:
				{
					// rune: faint carved rune marks
					g.setColor(new Color(lerp(vein, 0x000000, 0.45f)));
					int t = r.nextInt(3);
					if (t == 0)
					{
						g.drawLine(x, y - 2, x + 2, y);
						g.drawLine(x + 2, y, x, y + 2);
						g.drawLine(x, y + 2, x - 2, y);
						g.drawLine(x - 2, y, x, y - 2);
					}
					else if (t == 1)
					{
						g.drawLine(x, y - 3, x, y + 3);
						g.drawLine(x, y - 1, x + 2, y - 3);
					}
					else
					{
						g.drawLine(x - 2, y - 2, x + 2, y + 2);
						g.drawLine(x - 2, y + 2, x + 2, y - 2);
					}
					k += 30;
					break;
				}
				default:
					break;
			}
		}
		g.dispose();
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
			case HOOD:
			{
				// a hooded figure sigil: for those who walk their own path
				Polygon hood = new Polygon(
					new int[]{16, 24, 28, 27, 22, 10, 5, 4, 8},
					new int[]{2, 7, 17, 28, 30, 30, 28, 17, 7}, 9);
				g.setColor(new Color(0x2A0A0C));
				g.fill(grow(hood));
				g.setColor(new Color(mid));
				g.fill(hood);
				g.setColor(new Color(0x0C0606));
				g.fillOval(10, 11, 12, 14);
				g.setColor(new Color(core));
				g.fillRect(12, 17, 3, 2);
				g.fillRect(18, 17, 3, 2);
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
			case SARADOMIN:
			{
				// Saradomin's star: a bold four-pointed star, long vertical arms, bright heart
				Polygon star = new Polygon(
					new int[]{16, 19, 29, 19, 16, 13, 3, 13},
					new int[]{1, 12, 16, 20, 31, 20, 16, 12}, 8);
				Polygon inner = new Polygon(
					new int[]{16, 18, 24, 18, 16, 14, 8, 14},
					new int[]{7, 14, 16, 18, 25, 18, 16, 14}, 8);
				g.setColor(new Color(0x0A1430));
				g.fill(grow(grow(star)));
				g.setColor(new Color(0xB08A2A));
				g.fill(grow(star));
				g.setColor(new Color(mid));
				g.fill(star);
				g.setColor(new Color(core));
				g.fill(inner);
				g.setColor(Color.WHITE);
				g.fillRect(15, 14, 2, 4);
				g.fillRect(14, 15, 4, 2);
				break;
			}
			case ZAMORAK:
			{
				// Zamorak's crest: a horned shield with a burning eye
				Polygon crest = new Polygon(
					new int[]{3, 9, 12, 16, 20, 23, 29, 25, 16, 7},
					new int[]{2, 9, 7, 11, 7, 9, 2, 20, 30, 20}, 10);
				g.setColor(new Color(0x140202));
				g.fill(grow(grow(crest)));
				g.setColor(new Color(dark));
				g.fill(grow(crest));
				g.setColor(new Color(mid));
				g.fill(crest);
				g.setColor(new Color(0x1A0202));
				g.fillPolygon(new int[]{10, 16, 22, 16}, new int[]{16, 12, 16, 24}, 4);
				g.setColor(new Color(core));
				g.fillRect(14, 15, 4, 3);
				g.setColor(new Color(0xFFE0A0));
				g.fillRect(15, 16, 2, 1);
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
			case TWISTED:
			{
				// the twisted bow: a thick curved limb with a taut string and a green gem
				g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(0x061008));
				g.drawArc(5, 2, 22, 28, 270, 180);
				g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(mid));
				g.drawArc(5, 2, 22, 28, 270, 180);
				g.setStroke(new BasicStroke(1.2f));
				g.setColor(new Color(lerp(core, 0xFFFFFF, 0.3f)));
				g.drawLine(16, 3, 16, 29);
				g.setColor(new Color(0x061008));
				g.fillOval(22, 12, 8, 8);
				g.setColor(new Color(core));
				g.fillOval(23, 13, 6, 6);
				g.setColor(Color.WHITE);
				g.fillRect(24, 14, 2, 2);
				break;
			}
			default:
				break;
		}
		g.dispose();
		return img;
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
