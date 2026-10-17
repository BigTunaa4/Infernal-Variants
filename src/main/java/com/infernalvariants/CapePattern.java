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
	LATTICE("Holy lattice", Surface.CLOTH),
	SMOKE("Smoke wisps", Surface.SOFT),
	SHARDS("Ice shards", Surface.FACET),
	DRIPS("Drips", Surface.SOFT),
	VINES("Vines", Surface.SOFT),
	BOLTS("Lightning", Surface.SOFT),
	RUNIC("Rune stones", Surface.ROCK),
	NEBULA("Nebula", Surface.SPACE),
	STRATA("Strata", Surface.ROCK),
	FACETS("Crystal facets", Surface.FACET),
	QUILT("Quilted leather", Surface.CLOTH),
	SCALES("Scale mail", Surface.METAL),
	BUBBLES("Bubbles", Surface.SOFT),
	SPLINTERS("Splinters", Surface.FACET),
	VESSELS("Vessels", Surface.SOFT),
	GLYPHS("Glyph bands", Surface.ROCK),
	WAVES("Waves", Surface.SOFT),
	HERALDIC("Heraldic", Surface.CLOTH),
	GRAIN("Wood grain", Surface.SOFT),
	TENDRILS("Tendrils", Surface.SOFT),
	FEATHERS("Feathers", Surface.SOFT),
	BRAID("Braid", Surface.SOFT);

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
			case CRACKS:
				return cracks;
			case LATTICE:
				return lattice();
			case SMOKE:
				return smoke();
			case SHARDS:
				return voronoi(4, 1.6f, 11, false);
			case DRIPS:
				return drips();
			case VINES:
				return vines();
			case BOLTS:
				return bolts();
			case RUNIC:
				return runic();
			case NEBULA:
				return nebula();
			case STRATA:
				return strata();
			case FACETS:
				return voronoi(4, 1.4f, 23, false);
			case QUILT:
				return quilt();
			case SCALES:
				return scales();
			case BUBBLES:
				return bubbles();
			case SPLINTERS:
				return voronoi(5, 1.5f, 37, true);
			case VESSELS:
				return vessels();
			case GLYPHS:
				return glyphs();
			case WAVES:
				return waves();
			case HERALDIC:
				return heraldic();
			case GRAIN:
				return grain();
			case TENDRILS:
				return tendrils();
			case FEATHERS:
				return feathers();
			case BRAID:
				return braid();
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

	/** Holy: a gold diamond lattice with a small star at every crossing. */
	private static float[] lattice()
	{
		Canvas c = new Canvas();
		c.level(1f);
		for (int i = -4; i <= 4; i++)
		{
			c.stroke(new Line2D.Float(i * 32, 0, i * 32 + 64, 128), 1.8f);
			c.stroke(new Line2D.Float(i * 32, 128, i * 32 + 64, 0), 1.8f);
		}
		for (int y = 0; y < N; y += 64)
		{
			for (int x = 0; x < N; x += 32)
			{
				c.fill(starTall(x, y, 7f, 1.8f));
				c.fill(starTall(x + 16, y + 32, 5f, 1.4f));
			}
		}
		return glowLines(c.values(), 0.55f);
	}

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

	/** Blood: streaks running down the cape, each ending in a heavy drop. */
	private static float[] drips()
	{
		Canvas c = new Canvas();
		Random r = new Random(5);
		for (int i = 0; i < 12; i++)
		{
			float x = (i + r.nextFloat() * 0.6f) * (N / 12f);
			float y0 = r.nextFloat() * N;
			float len = 30 + r.nextFloat() * 60;
			float w = 2.2f + r.nextFloat() * 2.4f;
			c.level(0.75f + r.nextFloat() * 0.25f);
			Path2D.Float p = new Path2D.Float();
			p.moveTo(x, y0);
			float wob = r.nextFloat() * 2 - 1;
			p.quadTo(x + wob * 2, y0 + len / 2, x + wob, y0 + len);
			c.stroke(p, w);
			c.fill(new Ellipse2D.Float(x + wob - w * 0.9f, y0 + len - w * 0.5f, w * 1.8f, w * 2.3f));
		}
		return glowLines(c.values(), 0.5f);
	}

	/** Nature: winding vines with leaves. */
	private static float[] vines()
	{
		Canvas c = new Canvas();
		Random r = new Random(9);
		for (int v = 0; v < 2; v++)
		{
			float x0 = v * 64 + 16 + r.nextFloat() * 24;
			float amp = 10 + r.nextFloat() * 8;
			float ph = r.nextFloat() * 6.28f;
			Path2D.Float p = new Path2D.Float();
			for (int y = 0; y <= N; y += 2)
			{
				float x = x0 + amp * (float) Math.sin(y * 2 * Math.PI / N * 2 + ph);
				if (y == 0)
				{
					p.moveTo(x, y);
				}
				else
				{
					p.lineTo(x, y);
				}
			}
			c.level(0.9f);
			c.stroke(p, 2.2f);
			// leaves alternate sides along the vine
			for (int k = 0; k < 8; k++)
			{
				float y = k * (N / 8f) + 6;
				float x = x0 + amp * (float) Math.sin(y * 2 * Math.PI / N * 2 + ph);
				int side = (k % 2 == 0) ? 1 : -1;
				Shape leaf = new Ellipse2D.Float(0, -3f, 13, 6f);
				AffineTransform at = AffineTransform.getTranslateInstance(x, y);
				at.rotate(side > 0 ? -0.5 : Math.PI + 0.5);
				c.level(0.7f);
				c.fill(at.createTransformedShape(leaf));
			}
		}
		return glowLines(c.values(), 0.45f);
	}

	/** Lightning: jagged forked bolts running down the cape. */
	private static float[] bolts()
	{
		Canvas c = new Canvas();
		Random r = new Random(17);
		for (int b = 0; b < 3; b++)
		{
			float startX = b * 43 + r.nextFloat() * 18;
			float x = startX;
			float y = 0;
			Path2D.Float p = new Path2D.Float();
			p.moveTo(x, y);
			while (y < N)
			{
				float ny = Math.min(N, y + 6 + r.nextFloat() * 8);
				// drift back toward the start x near the bottom so the bolt tiles seamlessly
				float pull = (startX - x) * (ny / N) * (ny / N);
				float nx = ny >= N ? startX : x + (r.nextFloat() * 2 - 1) * 9 + pull * 0.5f;
				p.lineTo(nx, ny);
				if (r.nextFloat() < 0.22f)
				{
					// a short fork
					Path2D.Float f = new Path2D.Float();
					f.moveTo(nx, ny);
					float fx = nx, fy = ny;
					for (int k = 0; k < 3; k++)
					{
						fx += (r.nextBoolean() ? 1 : -1) * (3 + r.nextFloat() * 5);
						fy += 4 + r.nextFloat() * 5;
						f.lineTo(fx, fy);
					}
					c.level(0.7f);
					c.stroke(f, 1f);
				}
				x = nx;
				y = ny;
			}
			c.level(1f);
			c.stroke(p, 1.7f);
		}
		float[] lines = glowLines(c.values(), 0.6f);
		// faint storm cloud behind the bolts
		for (int i = 0; i < lines.length; i++)
		{
			float cloud = fbm(i % N, i / N, 61);
			lines[i] = Math.max(lines[i], (cloud - 0.45f) * 0.35f);
		}
		return lines;
	}

	/**
	 * Rune: rune-stone symbols (air, water, earth, fire, mind, body) carved into dark stone,
	 * joined by a glowing channel running down each column.
	 */
	private static float[] runic()
	{
		Canvas c = new Canvas();
		int[] cols = {32, 96};
		for (int ci = 0; ci < cols.length; ci++)
		{
			float x = cols[ci];
			c.level(0.55f);
			c.stroke(new Line2D.Float(x, 0, x, N), 1.4f);
			for (int k = 0; k < 2; k++)
			{
				float y = k * 64 + (ci == 0 ? 20 : 52);
				int type = (ci * 2 + k) % 6;
				float rw = 11, rh = 11 * TALL;
				// the rune stone's ring
				c.level(0.9f);
				c.stroke(new Ellipse2D.Float(x - rw, y - rh, rw * 2, rh * 2), 1.6f);
				c.level(1f);
				AffineTransform at = AffineTransform.getTranslateInstance(x, y);
				at.scale(1, TALL);
				Path2D.Float g = new Path2D.Float();
				switch (type)
				{
					case 0:
						// air: a swirl
						g.append(new Arc2D.Float(-6, -6, 12, 12, 90, 270, Arc2D.OPEN), false);
						g.append(new Arc2D.Float(-3, -6, 6, 6, 270, 180, Arc2D.OPEN), false);
						break;
					case 1:
						// water: a drop
						g.moveTo(0, -7);
						g.quadTo(6, 1, 0, 6);
						g.quadTo(-6, 1, 0, -7);
						break;
					case 2:
						// earth: a mound with a line
						g.moveTo(-7, 5);
						g.lineTo(0, -6);
						g.lineTo(7, 5);
						g.closePath();
						g.moveTo(-4, 1);
						g.lineTo(4, 1);
						break;
					default:
						// fire: a flame
						g.moveTo(0, -7);
						g.quadTo(7, 0, 3, 6);
						g.quadTo(0, 2, -3, 6);
						g.quadTo(-7, 0, 0, -7);
						break;
				}
				c.stroke(at.createTransformedShape(g), 1.6f);
			}
		}
		return glowLines(c.values(), 0.5f);
	}

	/** Void: drifting nebula clouds scattered with stars. */
	private static float[] nebula()
	{
		float[] out = new float[N * N];
		Random r = new Random(29);
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float warp = fbm(x, y, 71) * 30;
				float n = fbm(x + Math.round(warp), y, 73);
				float cloud = CapeTextures.clamp((n - 0.36f) * 2.4f);
				out[y * N + x] = cloud * 0.74f;
			}
		}
		for (int i = 0; i < 70; i++)
		{
			int x = r.nextInt(N), y = r.nextInt(N);
			float b = 0.6f + r.nextFloat() * 0.4f;
			out[y * N + x] = Math.max(out[y * N + x], b);
			if (b > 0.93f)
			{
				// a few brighter stars with a tiny cross
				out[y * N + (x + 1) % N] = Math.max(out[y * N + (x + 1) % N], 0.6f);
				out[y * N + (x + N - 1) % N] = Math.max(out[y * N + (x + N - 1) % N], 0.6f);
				out[((y + 1) % N) * N + x] = Math.max(out[((y + 1) % N) * N + x], 0.6f);
				out[((y + N - 1) % N) * N + x] = Math.max(out[((y + N - 1) % N) * N + x], 0.6f);
			}
		}
		return out;
	}

	/** Sandstone: layered, wavy rock strata with a few fault cracks. */
	private static float[] strata()
	{
		float[] out = new float[N * N];
		for (int y = 0; y < N; y++)
		{
			for (int x = 0; x < N; x++)
			{
				float warp = fbm(x, y, 81) * 14f + (float) Math.sin(x * 2 * Math.PI / N * 2) * 3f;
				float layer = frac((y + warp) / 32f);
				float line = CapeTextures.clamp(1f - Math.abs(layer - 0.5f) * 2f * 9f);
				out[y * N + x] = line * 0.9f;
			}
		}
		return glowLines(out, 0.3f);
	}

	/** Roguelike: diagonal quilting seams with stitch marks. */
	private static float[] quilt()
	{
		Canvas c = new Canvas();
		c.level(0.9f);
		float[] dash = {3f, 3f};
		for (int i = -4; i <= 6; i++)
		{
			c.g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
			Shape a = new Line2D.Float(i * 32, 0, i * 32 + 64, 128);
			Shape b = new Line2D.Float(i * 32, 128, i * 32 + 64, 0);
			for (int dx = -N; dx <= N; dx += N)
			{
				for (int dy = -N; dy <= N; dy += N)
				{
					c.g.draw(AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(a));
					c.g.draw(AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(b));
				}
			}
		}
		return glowLines(c.values(), 0.35f);
	}

	/** Golden: overlapping rows of rounded scales. */
	private static float[] scales()
	{
		Canvas c = new Canvas();
		c.level(1f);
		int w = 32;
		float h = w * TALL;
		for (int row = 0; row < N / 16 + 1; row++)
		{
			float off = (row % 2 == 0) ? 0 : w / 2f;
			for (int col = -1; col < N / w + 1; col++)
			{
				float cx = col * w + off;
				float cy = row * 16;
				c.stroke(new Arc2D.Float(cx - w / 2f, cy - h / 2f, w, h, 180, 180, Arc2D.OPEN), 1.8f);
			}
		}
		return glowLines(c.values(), 0.5f);
	}

	/** Toxic: bubbles of every size rising through ooze. */
	private static float[] bubbles()
	{
		Canvas c = new Canvas();
		Random r = new Random(31);
		for (int i = 0; i < 24; i++)
		{
			float s = 5 + (float) Math.pow(r.nextFloat(), 2) * 22;
			float x = r.nextFloat() * N, y = r.nextFloat() * N;
			c.level(0.95f);
			c.stroke(new Ellipse2D.Float(x, y, s, s * TALL), s > 12 ? 1.8f : 1.3f);
			// glint on the bubble
			c.level(1f);
			float gl = Math.max(1.5f, s * 0.18f);
			c.fill(new Ellipse2D.Float(x + s * 0.22f, y + s * TALL * 0.2f, gl, gl * TALL));
		}
		float[] lines = glowLines(c.values(), 0.5f);
		for (int i = 0; i < lines.length; i++)
		{
			lines[i] = Math.max(lines[i], (fbm(i % N, i / N, 91) - 0.5f) * 0.4f);
		}
		return lines;
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

	/** Tidal: rows of concentric wave scallops. */
	private static float[] waves()
	{
		Canvas c = new Canvas();
		int w = 32;
		for (int row = 0; row < N / 16 + 2; row++)
		{
			float off = (row % 2 == 0) ? 0 : w / 2f;
			for (int col = -1; col < N / w + 1; col++)
			{
				float cx = col * w + off, cy = row * 16 + 16;
				for (int ring = 0; ring < 3; ring++)
				{
					float rr = w / 2f - ring * 5f;
					float rh = rr * TALL;
					c.level(ring == 0 ? 1f : 0.6f);
					c.stroke(new Arc2D.Float(cx - rr, cy - rh, rr * 2, rh * 2, 0, 180, Arc2D.OPEN), ring == 0 ? 1.8f : 1.2f);
				}
			}
		}
		return glowLines(c.values(), 0.4f);
	}

	/** Saradomin: gold trim stripes and stars on blue cloth. */
	private static float[] heraldic()
	{
		Canvas c = new Canvas();
		c.level(1f);
		for (int x = 0; x < N; x += 64)
		{
			c.stroke(new Line2D.Float(x + 2, 0, x + 2, N), 1.3f);
			c.stroke(new Line2D.Float(x + 6, 0, x + 6, N), 0.9f);
		}
		for (int y = 0; y < N; y += 32)
		{
			c.fill(starTall(36, y + 16, 8f, 2f));
			c.fill(starTall(100, y, 5f, 1.4f));
		}
		return glowLines(c.values(), 0.5f);
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

	/** Abyssal: thick wavy tendrils with rows of suckers. */
	private static float[] tendrils()
	{
		Canvas c = new Canvas();
		Random r = new Random(53);
		for (int t = 0; t < 3; t++)
		{
			float x0 = t * 43 + 10;
			float amp = 8 + r.nextFloat() * 6;
			float ph = r.nextFloat() * 6.28f;
			Path2D.Float p = new Path2D.Float();
			for (int y = 0; y <= N; y += 2)
			{
				float x = x0 + amp * (float) Math.sin(y * 2 * Math.PI / N + ph);
				if (y == 0)
				{
					p.moveTo(x, y);
				}
				else
				{
					p.lineTo(x, y);
				}
			}
			c.level(0.42f);
			c.stroke(p, 10f);
			c.level(1f);
			c.stroke(p, 1.6f);
			for (int k = 0; k < 10; k++)
			{
				float y = k * (N / 10f);
				float x = x0 + amp * (float) Math.sin(y * 2 * Math.PI / N + ph);
				c.level(0.9f);
				c.stroke(new Ellipse2D.Float(x + 3.5f, y - 1.5f, 3, 3), 0.9f);
				c.stroke(new Ellipse2D.Float(x - 6.5f, y + 4.5f, 3, 3), 0.9f);
			}
		}
		return glowLines(c.values(), 0.35f);
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

	/** Twisted: two strands spiralling around each other down the cape. */
	private static float[] braid()
	{
		Canvas c = new Canvas();
		for (int strand = 0; strand < 3; strand++)
		{
			float x0 = strand * 128f / 3f + 21;
			for (int s = 0; s < 2; s++)
			{
				Path2D.Float p = new Path2D.Float();
				for (int y = 0; y <= N; y += 2)
				{
					float x = x0 + 12 * (float) Math.sin(y * 2 * Math.PI / N * 2 + s * Math.PI);
					if (y == 0)
					{
						p.moveTo(x, y);
					}
					else
					{
						p.lineTo(x, y);
					}
				}
				c.level(s == 0 ? 1f : 0.7f);
				c.stroke(p, 2.4f);
			}
		}
		return glowLines(c.values(), 0.45f);
	}

	private static Shape starTall(float cx, float cy, float outer, float inner)
	{
		AffineTransform at = AffineTransform.getTranslateInstance(cx, cy);
		at.scale(1, TALL);
		return at.createTransformedShape(star(0, 0, outer, inner));
	}

	private static Shape star(float cx, float cy, float outer, float inner)
	{
		Path2D.Float p = new Path2D.Float();
		for (int i = 0; i < 8; i++)
		{
			double a = Math.PI * i / 4 - Math.PI / 2;
			float r = (i % 2 == 0) ? outer : inner;
			float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
			if (i == 0)
			{
				p.moveTo(x, y);
			}
			else
			{
				p.lineTo(x, y);
			}
		}
		p.closePath();
		return p;
	}
}
