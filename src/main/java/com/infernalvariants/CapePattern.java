package com.infernalvariants;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The designs painted on the cape panel. Each pattern produces a tileable 128x128 "heat" field:
 * 0 for the background surface, rising to 1 at the brightest, glowing lines. The same painting
 * pipeline then colours any pattern with any style's palette.
 * <p>
 * The cape's texture coordinates run left-to-right across the panel and collar-to-hem down it,
 * wrapping in bands, so every pattern must tile seamlessly in both directions.
 */
public enum CapePattern
{
	CRACKS("Lava cracks", Surface.ROCK),
	SMOKE("Smoke wisps", Surface.SOFT),
	SHARDS("Ice shards", Surface.FACET),
	SPLINTERS("Splinters", Surface.FACET),
	VESSELS("Vessels", Surface.SOFT),
	GLYPHS("Glyph bands", Surface.ROCK),
	GRAIN("Wood grain", Surface.SOFT),
	FEATHERS("Feathers", Surface.SOFT),
	CHANNEL("Centre channel", Surface.SCALE),
	PLAIN_SCALES("Plain scales", Surface.SCALE),
	// --- reworked from the concept art ---
	BLOOD("Bleeding cracks", Surface.ROCK),
	OVERGROWN("Overgrown cracks", Surface.ROCK),
	STORM("Storm cracks", Surface.ROCK),
	SHATTERED("Shattered crystal", Surface.FACET),
	HOLY("Holy feathers", Surface.SOFT);

	/** What the background between the glowing lines is made of; drives its shading. */
	enum Surface
	{
		/** Lit, bumpy stone, domed plates. */
		ROCK,
		/** Fine woven cloth, flat. */
		CLOTH,
		/** Smooth polished metal, strongly domed. */
		METAL,
		/** Flat crystal facets, each with its own tone. */
		FACET,
		/** Soft, slightly mottled. */
		SOFT,
		/** Deep space: dark, no relief. */
		SPACE,
		/** Small overlapping downward scales, each lit from above (the concept-art look). */
		SCALE,
	}

	private static final int N = CapeTextures.SIZE;
	/**
	 * On the cape a texel covers less height than width (about 1.6x), so round motifs are
	 * drawn taller than wide here to look round when worn.
	 */
	private static final float TALL = 1.6f;

	private final String displayName;
	final Surface surface;

	CapePattern(String displayName, Surface surface)
	{
		this.displayName = displayName;
		this.surface = surface;
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	/**
	 * Build the heat field. {@code cracks} is the game's own crack heat map, used by CRACKS.
	 */
	float[] heat(float[] cracks)
	{
		switch (this)
		{
			case SMOKE:
				return smoke();
			case SHARDS:
				return voronoi(4, 1.6f, 11, false);
			case SPLINTERS:
				return voronoi(5, 1.5f, 37, true);
			case VESSELS:
				return vessels();
			case GLYPHS:
				return glyphs();
			case GRAIN:
				return grain();
			case FEATHERS:
				return feathers();
			case CHANNEL:
				return channel();
			case PLAIN_SCALES:
				return plainScales();
			case BLOOD:
				return bleeding(cracks);
			case OVERGROWN:
				return overgrown(cracks);
			case SHATTERED:
				return shattered();
			case HOLY:
				return holy();
			case STORM:
			case CRACKS:
			default:
				return cracks;
		}
	}

	// ---------------------------------------------------------------- drawing helpers

	/** A grayscale canvas where everything drawn wraps around the edges. */
	private static final class Canvas
	{
		final BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();

		Canvas()
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, N, N);
		}

		void level(float v)
		{
			int c = Math.max(0, Math.min(255, Math.round(v * 255)));
			g.setColor(new Color(c, c, c));
		}

		void stroke(Shape s, float width)
		{
			g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			forWrap(s, true);
		}

		void fill(Shape s)
		{
			forWrap(s, false);
		}

		private void forWrap(Shape s, boolean draw)
		{
			for (int dx = -N; dx <= N; dx += N)
			{
				for (int dy = -N; dy <= N; dy += N)
				{
					Shape t = AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(s);
					if (draw)
					{
						g.draw(t);
					}
					else
					{
						g.fill(t);
					}
				}
			}
		}

		float[] values()
		{
			float[] out = new float[N * N];
			for (int i = 0; i < out.length; i++)
			{
				out[i] = (img.getRGB(i % N, i / N) & 255) / 255f;
			}
			g.dispose();
			return out;
		}
	}

	/** Turn crisp drawn lines into a glowing heat field: a bright core with a soft edge. */
	private static float[] glowLines(float[] core, float softness)
	{
		float[] soft = boxBlur(core, 2);
		float[] out = new float[core.length];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = CapeTextures.clamp(core[i] * 0.8f + soft[i] * softness);
		}
		return out;
	}

	private static float[] boxBlur(float[] src, int r)
	{
		float[] tmp = new float[src.length], out = new float[src.length];
		float k = 1f / (2 * r + 1);
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float s = 0;
				for (int d = -r; d <= r; d++)
				{
					s += src[y * N + (x + d + N) % N];
				}
				tmp[y * N + x] = s * k;
			}
		}
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float s = 0;
				for (int d = -r; d <= r; d++)
				{
					s += tmp[((y + d + N) % N) * N + x];
				}
				out[y * N + x] = s * k;
			}
		}
		return out;
	}

	private static float fbm(int x, int y, int seed)
	{
		x = (x % N + N) % N;
		y = (y % N + N) % N;
		return CapeTextures.noise(x, y, 32, seed) * 0.5f + CapeTextures.noise(x, y, 16, seed + 1) * 0.28f
			+ CapeTextures.noise(x, y, 8, seed + 2) * 0.14f + CapeTextures.noise(x, y, 4, seed + 3) * 0.08f;
	}

	/** fbm sampled at a fractional position (bilinear between texels). */
	private static float fbmF(float x, float y, int seed)
	{
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		float fx = x - x0, fy = y - y0;
		float a = fbm(x0, y0, seed), b = fbm(x0 + 1, y0, seed), c = fbm(x0, y0 + 1, seed), d = fbm(x0 + 1, y0 + 1, seed);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	private static float frac(float v)
	{
		return v - (float) Math.floor(v);
	}

	// ---------------------------------------------------------------- patterns

	/** Shadow: soft contour lines of a drifting noise field, like rising smoke. */
	private static float[] smoke()
	{
		float[] out = new float[N * N];
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float warp = fbm(x, y, 41) * 18f;
				float n = fbmF(x + warp, y, 47);
				float band = Math.abs(frac(n * 4f) - 0.5f) * 2f;
				out[y * N + x] = (float) Math.pow(band, 3) * 0.85f;
			}
		}
		return glowLines(boxBlur(out, 1), 0.45f);
	}

	/**
	 * Tileable Voronoi: bright cell borders. {@code cells} per side; {@code width} line width.
	 * {@code splintered} stretches the cells vertically for jagged crystal splinters.
	 */
	private static float[] voronoi(int cells, float width, int seed, boolean splintered)
	{
		Random r = new Random(seed);
		float cs = N / (float) cells;
		float[][] pts = new float[cells * cells][2];
		for (int i = 0; i < pts.length; i++)
		{
			pts[i][0] = ((i % cells) + 0.15f + r.nextFloat() * 0.7f) * cs;
			pts[i][1] = ((i / cells) + 0.15f + r.nextFloat() * 0.7f) * cs;
		}
		float sy = (splintered ? 0.7f : 1f) / TALL;
		float[] out = new float[N * N];
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
				for (float[] p : pts)
				{
					float dx = Math.abs(x - p[0]);
					float dy = Math.abs(y - p[1]);
					dx = Math.min(dx, N - dx);
					dy = Math.min(dy, N - dy) * sy;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					if (d < d1)
					{
						d2 = d1;
						d1 = d;
					}
					else if (d < d2)
					{
						d2 = d;
					}
				}
				float edge = d2 - d1;
				out[y * N + x] = CapeTextures.clamp(1f - edge / (width * 2.2f));
			}
		}
		return glowLines(out, 0.4f);
	}

	/** Sanguine: a branching network of vessels (ridged noise). */
	private static float[] vessels()
	{
		float[] out = new float[N * N];
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float n = fbm(x, y, 101);
				float ridge = 1f - Math.abs(n * 2f - 1f);
				float n2 = fbm(x + 37, y + 11, 107);
				float ridge2 = 1f - Math.abs(n2 * 2f - 1f);
				out[y * N + x] = Math.max((float) Math.pow(ridge, 14), (float) Math.pow(ridge2, 22) * 0.8f);
			}
		}
		return glowLines(out, 0.3f);
	}

	/** Amascut: horizontal bands of borders, zigzags and simple glyphs. */
	private static float[] glyphs()
	{
		Canvas c = new Canvas();
		Random r = new Random(41);
		for (int band = 0; band < 4; band++)
		{
			float y = band * 32;
			c.level(0.95f);
			c.stroke(new Line2D.Float(0, y + 2, N, y + 2), 1.2f);
			c.stroke(new Line2D.Float(0, y + 30, N, y + 30), 1.2f);
			if (band % 2 == 0)
			{
				// zigzag border band
				Path2D.Float z = new Path2D.Float();
				z.moveTo(0, y + 22);
				for (int x = 0; x <= N; x += 8)
				{
					z.lineTo(x + 4, y + 10);
					z.lineTo(x + 8, y + 22);
				}
				c.stroke(z, 1.3f);
			}
			else
			{
				// glyph band: sun discs, ankh-like crosses and eyes
				for (int k = 0; k < 4; k++)
				{
					float x = k * 32 + 16;
					int t = (k + band) % 3;
					if (t == 0)
					{
						c.stroke(new Ellipse2D.Float(x - 5, y + 9, 10, 10), 1.3f);
						c.fill(new Ellipse2D.Float(x - 2, y + 12, 4, 4));
					}
					else if (t == 1)
					{
						c.stroke(new Ellipse2D.Float(x - 3, y + 6, 6, 7), 1.2f);
						c.stroke(new Line2D.Float(x, y + 13, x, y + 26), 1.4f);
						c.stroke(new Line2D.Float(x - 5, y + 17, x + 5, y + 17), 1.4f);
					}
					else
					{
						c.stroke(new Arc2D.Float(x - 7, y + 10, 14, 10, 0, 180, Arc2D.OPEN), 1.3f);
						c.stroke(new Arc2D.Float(x - 7, y + 10, 14, 10, 180, 180, Arc2D.OPEN), 1.3f);
						c.fill(new Ellipse2D.Float(x - 2, y + 13, 4, 4));
					}
				}
			}
		}
		return glowLines(c.values(), 0.4f);
	}

	/** Guthix: flowing wood grain with the odd knot. */
	private static float[] grain()
	{
		float[] out = new float[N * N];
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float warp = fbmF(x, y * 0.5f, 111) * 26f;
				// knots bend the grain around them
				float knot = 0;
				for (int k = 0; k < 2; k++)
				{
					float kx = x - (k == 0 ? 40f : 96f), ky = y - (k == 0 ? 34f : 98f);
					knot += 10f / (1f + (kx * kx + ky * ky) / 50f);
				}
				float g = frac((x + warp + knot) / 10.67f);
				out[y * N + x] = CapeTextures.clamp(1f - Math.abs(g - 0.5f) * 2f * 3.2f) * 0.8f;
			}
		}
		return glowLines(boxBlur(out, 1), 0.3f);
	}

	/** Varlamore: rows of sunbird feathers, each a chevron with a central quill. */
	private static float[] feathers()
	{
		Canvas c = new Canvas();
		int w = 32, h = 32;
		for (int row = 0; row < N / h + 1; row++)
		{
			float off = (row % 2 == 0) ? 0 : w / 2f;
			for (int col = -1; col < N / w + 1; col++)
			{
				float cx = col * w + off + w / 2f, top = row * h;
				Path2D.Float f = new Path2D.Float();
				f.moveTo(cx - w / 2f + 1, top);
				f.quadTo(cx - w / 2f + 1, top + h * 0.8f, cx, top + h + 2);
				f.quadTo(cx + w / 2f - 1, top + h * 0.8f, cx + w / 2f - 1, top);
				c.level(1f);
				c.stroke(f, 1.8f);
				c.level(0.6f);
				c.stroke(new Line2D.Float(cx, top + 3, cx, top + h), 1.2f);
				// barbs
				for (int b = 1; b < 4; b++)
				{
					float by = top + b * h / 4f;
					c.level(0.35f);
					c.stroke(new Line2D.Float(cx, by + 4, cx - w * 0.28f, by), 0.8f);
					c.stroke(new Line2D.Float(cx, by + 4, cx + w * 0.28f, by), 0.8f);
				}
			}
		}
		return glowLines(c.values(), 0.45f);
	}

	/**
	 * The scale texture under the concept-art styles: rows of small downward-pointing scales.
	 * Returns the dim scale outlines (0-1).
	 */
	private static float[] scaleOutlines(float level)
	{
		Canvas c = new Canvas();
		int w = 32, step = 16;
		for (int row = 0; row < N / step + 2; row++)
		{
			float off = (row % 2 == 0) ? 0 : w / 2f;
			for (int col = -1; col < N / w + 1; col++)
			{
				float cx = col * w + off + w / 2f, top = row * step - 4;
				Path2D.Float sc = new Path2D.Float();
				sc.moveTo(cx - w / 2f, top);
				sc.quadTo(cx - w / 2f + 1, top + step * 1.1f, cx, top + step * 1.6f);
				sc.quadTo(cx + w / 2f - 1, top + step * 1.1f, cx + w / 2f, top);
				c.level(level);
				c.stroke(sc, 1f);
			}
		}
		return c.values();
	}

	/**
	 * Concept-art chevrons: nested V-shaped seams running down the cape over small scales.
	 * The V spans the full panel width (the texture's u runs edge to edge across the cape).
	 * {@code extra}: 0 none, 1 drips hanging from each V, 2 leaves along the V, 3 crackle.
	 */
	/** How far the point of each chevron sits below its arms, in texels (about 45 degrees when worn). */
	private static final float DEPTH = 64f;

	/** Rune / Void: a glowing channel down the centre with faint side lines, over scales. */
	private static float[] channel()
	{
		Canvas c = new Canvas();
		c.level(1f);
		c.stroke(new Line2D.Float(64, 0, 64, N), 2f);
		c.level(0.5f);
		c.stroke(new Line2D.Float(30, 0, 30, N), 1f);
		c.stroke(new Line2D.Float(98, 0, 98, N), 1f);
		// faint chevrons for structure
		for (int k = -2; k < N / 32; k++)
		{
			float y0 = k * 32;
			c.level(0.4f);
			c.stroke(new Line2D.Float(0, y0, 64, y0 + DEPTH), 1.2f);
			c.stroke(new Line2D.Float(64, y0 + DEPTH, 128, y0), 1.2f);
		}
		return combineWithScales(glowLines(c.values(), 0.5f), 0.18f);
	}

	/** Sandstone: just the scales, with soft seams and no bright lines. */
	private static float[] plainScales()
	{
		float[] sc = scaleOutlines(1f);
		float[] out = new float[sc.length];
		float[] soft = boxBlur(sc, 1);
		for (int i = 0; i < out.length; i++)
		{
			out[i] = CapeTextures.clamp(sc[i] * 0.5f + soft[i] * 0.4f);
		}
		return out;
	}

	private static float[] combineWithScales(float[] lines, float scaleLevel)
	{
		float[] sc = scaleOutlines(1f);
		for (int i = 0; i < lines.length; i++)
		{
			lines[i] = Math.max(lines[i], sc[i] * scaleLevel);
		}
		return lines;
	}

	/** Blood: the cape's own cracks, with blood running off the bottom of the veins. */
	private static float[] bleeding(float[] cracks)
	{
		float[] out = cracks.clone();
		Canvas c = new Canvas();
		Random r = new Random(13);
		int made = 0;
		for (int tries = 0; tries < 8000 && made < 16; tries++)
		{
			int x = r.nextInt(N), y = r.nextInt(N);
			if (cracks[y * N + x] < 0.7f)
			{
				continue;
			}
			// walk down to the bottom edge of the vein
			int yb = y;
			while (yb - y < 10 && cracks[((yb + 1) % N) * N + x] > 0.35f)
			{
				yb++;
			}
			if (cracks[((yb + 1) % N) * N + x] > 0.2f)
			{
				continue;
			}
			float len = 14 + (float) Math.pow(r.nextFloat(), 1.3) * 22;
			float w = 4f + r.nextFloat() * 2f;
			// a tapered run of blood ending in a heavy drop
			Path2D.Float run = new Path2D.Float();
			run.moveTo(x - w * 0.6f, yb - 1);
			run.lineTo(x + w * 0.6f, yb - 1);
			run.quadTo(x + w * 0.3f, yb + len * 0.6f, x + w * 0.25f, yb + len);
			run.lineTo(x - w * 0.25f, yb + len);
			run.quadTo(x - w * 0.3f, yb + len * 0.6f, x - w * 0.6f, yb - 1);
			run.closePath();
			c.level(0.82f + r.nextFloat() * 0.18f);
			c.fill(run);
			float d = w * 1.6f;
			c.fill(new Ellipse2D.Float(x - d / 2, yb + len - d * 0.5f, d, d * TALL));
			made++;
		}
		float[] drips = glowLines(c.values(), 0.3f);
		for (int i = 0; i < out.length; i++)
		{
			out[i] = Math.max(out[i], drips[i]);
		}
		return out;
	}

	/** Nature: the cape's own cracks, overgrown with leaves clinging along the glowing veins. */
	private static float[] overgrown(float[] cracks)
	{
		float[] out = new float[N * N];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = cracks[i] * 0.95f;
		}
		Canvas c = new Canvas();
		Random r = new Random(19);
		int made = 0;
		for (int tries = 0; tries < 12000 && made < 55; tries++)
		{
			int x = r.nextInt(N), y = r.nextInt(N);
			if (cracks[y * N + x] < 0.75f)
			{
				continue;
			}
			float len = 10 + r.nextFloat() * 6;
			float wid = len * 0.4f;
			AffineTransform at = AffineTransform.getTranslateInstance(x, y);
			at.scale(1, TALL);
			at.rotate(r.nextDouble() * Math.PI * 2);
			Path2D.Float leaf = new Path2D.Float();
			leaf.moveTo(0, 0);
			leaf.quadTo(len * 0.5f, -wid, len, 0);
			leaf.quadTo(len * 0.5f, wid, 0, 0);
			leaf.closePath();
			// leaves sit just below the vein glow so they read as darker green foliage
			c.level(0.5f + r.nextFloat() * 0.1f);
			c.fill(at.createTransformedShape(leaf));
			c.level(0.68f);
			c.stroke(at.createTransformedShape(new Line2D.Float(1, 0, len - 2f, 0)), 0.9f);
			made++;
		}
		float[] leaves = c.values();
		for (int i = 0; i < out.length; i++)
		{
			if (leaves[i] > 0.05f)
			{
				out[i] = Math.max(leaves[i], out[i] * 0.6f);
			}
		}
		return out;
	}

	/**
	 * Crystal: elongated crystal shards with dark fracture gaps, bright rims and a few internal
	 * facet edges. Heat runs 0 (shard face) to ~0.6 (bright rim) to 1 (the dark gap itself).
	 */
	private static float[] shattered()
	{
		float[] out = voronoi(5, 1.1f, 61, true);
		Canvas c = new Canvas();
		Random r = new Random(67);
		for (int i = 0; i < 40; i++)
		{
			float x = r.nextFloat() * N, y = r.nextFloat() * N;
			double ang = r.nextDouble() * Math.PI;
			float len = 6 + r.nextFloat() * 10;
			c.level(0.58f);
			c.stroke(new Line2D.Float(x, y, x + (float) Math.cos(ang) * len, y + (float) Math.sin(ang) * len * TALL), 0.9f);
		}
		float[] facets = c.values();
		for (int i = 0; i < out.length; i++)
		{
			out[i] = Math.max(out[i], facets[i]);
		}
		return out;
	}

	/**
	 * Holy: soft layered white feathers with gold seams and studs down the centre (the star
	 * emblems sit on the seam). The gold rim around the cape comes from its recoloured trim.
	 */
	private static float[] holy()
	{
		Canvas c = new Canvas();
		float w = N / 6f;
		int h = 32;
		for (int row = 0; row < N / h + 2; row++)
		{
			float off = (row % 2 == 0) ? 0 : w / 2f;
			for (int col = -1; col < 7; col++)
			{
				float cx = col * w + off + w / 2f, top = row * h - 8;
				Path2D.Float f = new Path2D.Float();
				f.moveTo(cx - w / 2f, top);
				f.quadTo(cx - w / 2f, top + h * 0.95f, cx, top + h * 1.25f);
				f.quadTo(cx + w / 2f, top + h * 0.95f, cx + w / 2f, top);
				c.level(0.25f);
				c.stroke(f, 1f);
				c.level(0.21f);
				c.stroke(new Line2D.Float(cx, top + 4, cx, top + h * 1.05f), 0.7f);
			}
		}
		// gold seams: one down the centre, a finer pair either side
		c.level(0.7f);
		c.stroke(new Line2D.Float(44, 0, 44, N), 1.1f);
		c.stroke(new Line2D.Float(84, 0, 84, N), 1.1f);
		c.level(0.95f);
		c.stroke(new Line2D.Float(64, 0, 64, N), 2.2f);
		for (int y = 16; y < N; y += 32)
		{
			Path2D.Float d = new Path2D.Float();
			d.moveTo(64, y - 4 * TALL);
			d.lineTo(67, y);
			d.lineTo(64, y + 4 * TALL);
			d.lineTo(61, y);
			d.closePath();
			c.level(1f);
			c.fill(d);
		}
		return glowLines(c.values(), 0.35f);
	}
}
