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
		float[] pos = style.stopPos;
		int[] rgb = style.stopRgb;
		if (h <= pos[0])
		{
			return rgb[0];
		}
		for (int i = 1; i < pos.length; i++)
		{
			if (h <= pos[i])
			{
				float t = (h - pos[i - 1]) / (pos[i] - pos[i - 1]);
				return lerp(rgb[i - 1], rgb[i], t);
			}
		}
		return rgb[rgb.length - 1];
	}

	/** The main repainted texture for a style. */
	static BufferedImage veinTexture(CapeStyle style, float[] heat)
	{
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		Random rng = new Random(0x1F3AL + style.ordinal());
		for (int y = 0; y < SIZE; y++)
		{
			for (int x = 0; x < SIZE; x++)
			{
				float h = heat[y * SIZE + x];
				int c = sample(style, h);
				float n = rng.nextFloat();
				float u = x / 127f, v = y / 127f;
				switch (style.special)
				{
					case GRAIN:
						// sandstone: gritty, layered grain across the plates
						c = scale(c, 0.86f + 0.22f * n + 0.06f * (float) Math.sin(v * 38 + u * 6));
						break;
					case PRISM:
					{
						// crystal: veins shimmer through a soft rainbow
						float k = clamp((h - 0.38f) / 0.42f) * 0.72f;
						int rainbow = Color.HSBtoRGB((u * 0.8f + v * 0.6f) % 1f, 0.5f, 1f) & 0xFFFFFF;
						c = lerp(c, rainbow, k);
						if (h < 0.2f && n > 0.985f)
						{
							c = 0xF4FFFF;
						}
						break;
					}
					case FROST:
						// ice: rime speckles on the plates
						if (h < 0.25f && n > 0.93f)
						{
							c = lerp(c, 0xBFD8F0, 0.55f);
						}
						break;
					case MOSS:
						// nature: mossy flecks growing on the stone
						if (h < 0.3f && n > 0.8f)
						{
							c = lerp(c, 0x3C5A22, 0.5f * n);
						}
						break;
					case SHEEN:
						// golden: a faint polished diagonal sheen across the plates
						if (h < 0.35f)
						{
							float band = (float) Math.sin((u + v) * 9.0) * 0.5f + 0.5f;
							c = lerp(c, 0x6A5220, band * band * 0.35f);
						}
						break;
					case SHARDS:
					{
						// corrupted: faceted crystal shards catch the light across the plates
						if (h < 0.35f)
						{
							float f = (float) Math.abs(Math.sin(u * 23.0 + v * 7.0) * Math.cos(v * 19.0 - u * 5.0));
							c = lerp(c, 0x6A2430, f > 0.82f ? 0.6f : f * 0.18f);
						}
						break;
					}
					default:
						break;
				}
				img.setRGB(x, y, c);
			}
		}
		return img;
	}

	/**
	 * A second texture faded in toward the hem (Dragonfire burns blue, Varlamore Sunset fades to dusk).
	 */
	static boolean hasHem(CapeStyle style)
	{
		return style.special == CapeStyle.Special.DRAGON || style.special == CapeStyle.Special.SUNSET;
	}

	static BufferedImage hemTexture(CapeStyle style, float[] heat)
	{
		if (!hasHem(style))
		{
			return null;
		}
		float[] p = {0f, .3f, .6f, 1f};
		int[] c = style.special == CapeStyle.Special.DRAGON
			// dragonfire: the veins burn blue at the bottom
			? new int[]{0x160907, 0x161A5A, 0x3264FA, 0xBEE1FF}
			// varlamore sunset: warm orange fades into dusk purple toward the hem
			: new int[]{0x120A1A, 0x3A1450, 0xA03CC8, 0xF0B4FF};
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < SIZE * SIZE; i++)
		{
			float h = heat[i];
			int col = c[c.length - 1];
			for (int k = 1; k < p.length; k++)
			{
				if (h <= p[k])
				{
					col = lerp(c[k - 1], c[k], (h - p[k - 1]) / (p[k] - p[k - 1]));
					break;
				}
			}
			img.setRGB(i % SIZE, i / SIZE, col);
		}
		return img;
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
				// Saradomin's star: four long points over four short ones, gold on blue
				Polygon big = star(16, 16, 15, 3, 4);
				Polygon small = rotate(star(16, 16, 9, 3, 4), 16, 16, Math.PI / 4);
				g.setColor(new Color(0x0A1228));
				g.fill(grow(grow(big)));
				g.setColor(new Color(mid));
				g.fill(big);
				g.setColor(new Color(0x2A4C8C));
				g.fill(small);
				g.setColor(new Color(core));
				g.fillRect(14, 14, 4, 4);
				break;
			}
			case ZAMORAK:
			{
				// Zamorak's mark: a downward chevron with horns
				Polygon v = new Polygon(new int[]{3, 10, 16, 22, 29, 16}, new int[]{3, 3, 17, 3, 3, 29}, 6);
				g.setColor(new Color(0x1A0404));
				g.fill(grow(v));
				g.setColor(new Color(mid));
				g.fill(v);
				g.setColor(new Color(0x1A0404));
				g.fillPolygon(new int[]{11, 16, 21}, new int[]{8, 19, 8}, 3);
				g.setColor(new Color(core));
				g.fillRect(15, 22, 2, 3);
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
				// a twisted bow silhouette
				g.setStroke(new BasicStroke(3));
				g.setColor(new Color(0x08140C));
				g.drawArc(4, 2, 20, 28, 270, 180);
				g.setStroke(new BasicStroke(2));
				g.setColor(new Color(mid));
				g.drawArc(4, 2, 20, 28, 270, 180);
				g.setColor(new Color(core));
				g.drawLine(14, 3, 14, 29);
				g.setColor(new Color(dark));
				g.fillRect(20, 14, 4, 4);
				break;
			}
			default:
				break;
		}
		g.dispose();
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
