package com.infernalvariants;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * A tiny particle system for one cape. Particles live in the scene's local coordinate space
 * (x/y in local units, h = height where negative is up, same as the game) so they stay put in the
 * world as the player moves, and are projected every frame. They are drawn as hard-edged pixel
 * squares/glints so they sit comfortably next to OSRS graphics.
 */
final class CapeParticles
{
	private static final int MAX = 90;

	enum Kind
	{
		DOT, STREAK, GLINT, ARC, LEAF, GLYPH, PUFF
	}

	static final class Particle
	{
		float x, y, h;
		float vx, vy, vh;
		float ax, ay, ah;
		// optional target for particles pulled into the cape (void)
		boolean homing;
		float tx, ty, th;
		float age, life;
		float size;
		int color, color2;
		float alpha;
		Kind kind;
		float seed;
		// arc points relative to the particle, in local units
		float[] arc;
	}

	private final List<Particle> particles = new ArrayList<>();
	private final Random rng = new Random();
	private float spawnDebt;
	long lastSeen;

	int count()
	{
		return particles.size();
	}

	List<Particle> list()
	{
		return particles;
	}

	void clear()
	{
		particles.clear();
		spawnDebt = 0;
	}

	/** Per-second spawn rate for an effect at normal intensity. */
	static float rate(CapeStyle.Effect e)
	{
		switch (e)
		{
			case EMBERS:
				return 10;
			case MOTES:
				return 6;
			case WISPS:
				return 7;
			case SNOW:
				return 9;
			case DRIPS:
				return 3;
			case LEAVES:
				return 2.5f;
			case SPARKS:
				return 5;
			case GLYPHS:
				return 4;
			case DRAGONFIRE:
				return 12;
			case VOID:
				return 8;
			case SAND:
				return 6;
			case GLINTS:
				return 6;
			case SMOKE:
				return 5;
			case GOLD:
				return 7;
			default:
				return 6;
		}
	}

	/** True for effects that should come off the bottom of the cape. */
	static boolean fromHem(CapeStyle.Effect e)
	{
		return e == CapeStyle.Effect.DRIPS || e == CapeStyle.Effect.SAND || e == CapeStyle.Effect.SMOKE;
	}

	/** True for effects that fall from the upper half (snow, leaves). */
	static boolean fromTop(CapeStyle.Effect e)
	{
		return e == CapeStyle.Effect.SNOW || e == CapeStyle.Effect.LEAVES;
	}

	float takeSpawns(float dt, float perSecond)
	{
		spawnDebt += dt * perSecond;
		float n = (float) Math.floor(spawnDebt);
		spawnDebt -= n;
		return n;
	}

	/**
	 * Spawn one particle at a point on the cape.
	 *
	 * @param px,py,ph   surface point (local coordinates)
	 * @param nx,ny,nh   outward surface normal (local, unit length)
	 * @param hemness    0 at the collar, 1 at the hem
	 */
	void spawn(CapeStyle style, float px, float py, float ph, float nx, float ny, float nh, float hemness)
	{
		if (particles.size() >= MAX)
		{
			return;
		}
		Particle p = new Particle();
		p.kind = Kind.DOT;
		p.alpha = 1f;
		p.seed = rng.nextFloat() * 100f;
		float out = 2f + rng.nextFloat() * 3f;
		p.x = px + nx * out;
		p.y = py + ny * out;
		p.h = ph + nh * out;
		switch (style.effect)
		{
			case EMBERS:
				p.vx = nx * 14 + jit(10);
				p.vy = ny * 14 + jit(10);
				p.vh = -45 - rng.nextFloat() * 40;
				p.life = 0.9f + rng.nextFloat() * 0.7f;
				p.size = 5 + rng.nextFloat() * 2;
				p.color = rng.nextFloat() < 0.6f ? 0xFF8A1C : 0xFFD24A;
				p.color2 = 0x8A1A08;
				break;
			case MOTES:
				p.vx = nx * 6 + jit(5);
				p.vy = ny * 6 + jit(5);
				p.vh = -20 - rng.nextFloat() * 16;
				p.life = 1.6f + rng.nextFloat() * 0.8f;
				p.size = 5;
				p.color = rng.nextFloat() < 0.5f ? 0xFFE89A : 0xFFFFFF;
				p.color2 = 0xE2B04A;
				p.kind = rng.nextFloat() < 0.25f ? Kind.GLINT : Kind.DOT;
				break;
			case WISPS:
				p.vx = nx * 12 + jit(6);
				p.vy = ny * 12 + jit(6);
				p.vh = -8 - rng.nextFloat() * 8;
				p.life = 1.2f + rng.nextFloat() * 0.6f;
				p.size = 6;
				p.color = rng.nextFloat() < 0.5f ? 0x7A3CC8 : 0x3A1660;
				p.color2 = 0x120818;
				p.alpha = 0.7f;
				p.kind = Kind.PUFF;
				break;
			case SNOW:
				p.vx = nx * 5 + jit(6);
				p.vy = ny * 5 + jit(6);
				p.vh = 28 + rng.nextFloat() * 14;
				p.life = 1.3f + rng.nextFloat() * 0.8f;
				p.size = 4 + rng.nextFloat() * 2;
				p.color = rng.nextFloat() < 0.6f ? 0xFFFFFF : 0xBFE2FF;
				p.color2 = p.color;
				p.kind = rng.nextFloat() < 0.2f ? Kind.GLINT : Kind.DOT;
				break;
			case DRIPS:
				p.vx = jit(2);
				p.vy = jit(2);
				p.vh = 10;
				p.ah = 260;
				p.life = 0.55f + rng.nextFloat() * 0.2f;
				p.size = 4;
				p.color = rng.nextFloat() < 0.5f ? 0xB80E14 : 0x7E080C;
				p.color2 = 0x50060A;
				p.kind = Kind.STREAK;
				break;
			case LEAVES:
				p.vx = nx * 10 + jit(8);
				p.vy = ny * 10 + jit(8);
				p.vh = 22 + rng.nextFloat() * 10;
				p.life = 1.8f + rng.nextFloat() * 0.8f;
				p.size = 6;
				int[] greens = {0x4E982E, 0x6CB83A, 0x3A7424, 0x9CCB4A};
				p.color = greens[rng.nextInt(greens.length)];
				p.color2 = 0x24461A;
				p.kind = Kind.LEAF;
				break;
			case SPARKS:
				if (rng.nextFloat() < 0.65f)
				{
					p.kind = Kind.ARC;
					p.life = 0.12f + rng.nextFloat() * 0.1f;
					p.arc = new float[12];
					float dx = jit(1), dy = jit(1), dh = jit(1);
					float len = (float) Math.max(0.2, Math.sqrt(dx * dx + dy * dy + dh * dh));
					float seg = 3.5f + rng.nextFloat() * 2.5f;
					float cx = 0, cy = 0, ch = 0;
					for (int i = 0; i < 4; i++)
					{
						cx += dx / len * seg + jit(2.5f);
						cy += dy / len * seg + jit(2.5f);
						ch += dh / len * seg + jit(2.5f);
						p.arc[i * 3] = cx;
						p.arc[i * 3 + 1] = cy;
						p.arc[i * 3 + 2] = ch;
					}
					p.color = rng.nextFloat() < 0.5f ? 0xFFF6C0 : 0xFFE060;
				}
				else
				{
					p.vx = nx * 60 + jit(30);
					p.vy = ny * 60 + jit(30);
					p.vh = -20 + jit(30);
					p.ah = 160;
					p.life = 0.25f + rng.nextFloat() * 0.15f;
					p.color = 0xFFF2A0;
				}
				p.color2 = 0xE8B01E;
				p.size = 4;
				break;
			case GLYPHS:
				p.vx = nx * 6 + jit(4);
				p.vy = ny * 6 + jit(4);
				p.vh = -22 - rng.nextFloat() * 12;
				p.life = 1.2f + rng.nextFloat() * 0.6f;
				p.size = 7;
				p.color = rng.nextFloat() < 0.6f ? 0x7EF0FF : 0x2AB8D8;
				p.color2 = 0x0E5A6E;
				p.kind = rng.nextFloat() < 0.6f ? Kind.GLYPH : Kind.DOT;
				break;
			case DRAGONFIRE:
				if (hemness > 0.6f && rng.nextFloat() < 0.6f)
				{
					// blue flame licking off the hem
					p.vx = nx * 10 + jit(6);
					p.vy = ny * 10 + jit(6);
					p.vh = -55 - rng.nextFloat() * 25;
					p.life = 0.35f + rng.nextFloat() * 0.25f;
					p.size = 5;
					p.color = rng.nextFloat() < 0.5f ? 0x4C86FF : 0xA8D0FF;
					p.color2 = 0x1A2A8A;
					p.kind = Kind.STREAK;
				}
				else
				{
					p.vx = nx * 16 + jit(10);
					p.vy = ny * 16 + jit(10);
					p.vh = -50 - rng.nextFloat() * 40;
					p.life = 0.8f + rng.nextFloat() * 0.6f;
					p.size = 5 + rng.nextFloat() * 2;
					p.color = rng.nextFloat() < 0.6f ? 0xFF4A14 : 0xFFA03A;
					p.color2 = 0x6A0C06;
				}
				break;
			case VOID:
			{
				// motes that start away from the cape and are drawn into it
				float d = 18 + rng.nextFloat() * 10;
				p.tx = px;
				p.ty = py;
				p.th = ph;
				p.x = px + nx * d + jit(8);
				p.y = py + ny * d + jit(8);
				p.h = ph + nh * d + jit(8);
				p.homing = true;
				p.life = 0.7f + rng.nextFloat() * 0.4f;
				p.size = 5;
				p.color = rng.nextFloat() < 0.5f ? 0xB46EF0 : 0x6A2CA8;
				p.color2 = 0x1A0A28;
				break;
			}
			case SAND:
				p.vx = nx * 6 + jit(5);
				p.vy = ny * 6 + jit(5);
				p.vh = 15;
				p.ah = 180;
				p.life = 0.7f + rng.nextFloat() * 0.3f;
				p.size = 3 + rng.nextFloat() * 2;
				int[] sands = {0xC8A46C, 0xA88452, 0xE0C890};
				p.color = sands[rng.nextInt(sands.length)];
				p.color2 = p.color;
				break;
			case GLINTS:
				p.x = px + nx;
				p.y = py + ny;
				p.h = ph + nh;
				p.life = 0.35f + rng.nextFloat() * 0.3f;
				p.size = 8;
				p.color = rng.nextFloat() < 0.5f ? 0xFFFFFF : 0xB8FFF6;
				p.color2 = p.color;
				p.kind = Kind.GLINT;
				break;
			case SMOKE:
				p.vx = nx * 6 + jit(4);
				p.vy = ny * 6 + jit(4);
				p.vh = -14 - rng.nextFloat() * 8;
				p.life = 1.3f + rng.nextFloat() * 0.6f;
				p.size = 5;
				p.color = rng.nextFloat() < 0.7f ? 0x2A2424 : 0x5A1418;
				p.color2 = 0x0E0C0C;
				p.alpha = 0.55f;
				p.kind = Kind.PUFF;
				break;
			case GOLD:
				if (rng.nextFloat() < 0.6f)
				{
					p.x = px + nx;
					p.y = py + ny;
					p.h = ph + nh;
					p.life = 0.4f + rng.nextFloat() * 0.3f;
					p.size = 8;
					p.color = 0xFFF0B0;
					p.kind = Kind.GLINT;
				}
				else
				{
					p.vx = nx * 8 + jit(5);
					p.vy = ny * 8 + jit(5);
					p.vh = 10;
					p.ah = 120;
					p.life = 0.8f + rng.nextFloat() * 0.3f;
					p.size = 4;
					p.color = 0xE8B83A;
				}
				p.color2 = 0x8A6418;
				break;
			default:
				return;
		}
		particles.add(p);
	}

	void update(float dt)
	{
		Iterator<Particle> it = particles.iterator();
		while (it.hasNext())
		{
			Particle p = it.next();
			p.age += dt;
			if (p.age >= p.life)
			{
				it.remove();
				continue;
			}
			if (p.homing)
			{
				float k = Math.min(1f, dt * 4.5f);
				p.x += (p.tx - p.x) * k;
				p.y += (p.ty - p.y) * k;
				p.h += (p.th - p.h) * k;
				continue;
			}
			p.vx += p.ax * dt;
			p.vy += p.ay * dt;
			p.vh += p.ah * dt;
			float sway = 0;
			if (p.kind == Kind.LEAF || p.kind == Kind.GLINT && p.vh > 0)
			{
				sway = (float) Math.sin((p.age * 4 + p.seed)) * 18f;
			}
			p.x += (p.vx + sway) * dt;
			p.y += (p.vy + sway * 0.5f) * dt;
			p.h += p.vh * dt;
		}
	}

	/**
	 * Draw one particle.
	 *
	 * @param sx,sy     projected screen position
	 * @param pxPerUnit how many screen pixels one local unit covers at the particle's depth
	 * @param arcX,arcY projected arc points (only for ARC particles)
	 */
	static void draw(Graphics2D g, Particle p, int sx, int sy, float pxPerUnit, int[] arcX, int[] arcY)
	{
		float t = p.age / p.life;
		float fade = t < 0.15f ? t / 0.15f : (1f - t) / 0.85f;
		if (p.kind == Kind.GLINT)
		{
			// grow then shrink
			fade = (float) Math.sin(Math.PI * t);
		}
		float a = Math.max(0f, Math.min(1f, fade * p.alpha));
		if (a <= 0.02f)
		{
			return;
		}
		int color = p.color2 != 0 && p.kind != Kind.GLINT ? CapeTextures.lerp(p.color, p.color2, t * 0.8f) : p.color;
		int px = Math.max(1, Math.min(7, Math.round(p.size * pxPerUnit)));
		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
		g.setColor(new Color(color));
		switch (p.kind)
		{
			case DOT:
				g.fillRect(sx - px / 2, sy - px / 2, px, px);
				break;
			case STREAK:
				g.fillRect(sx - px / 2, sy - px, Math.max(1, px - 1), px * 2);
				break;
			case PUFF:
			{
				int s = Math.max(2, Math.round(px * (1f + t * 1.4f)));
				g.fillRect(sx - s / 2, sy - s / 2, s, s);
				break;
			}
			case GLINT:
			{
				int arm = Math.max(1, Math.round(px * (0.5f + 0.5f * fade)));
				g.fillRect(sx - arm, sy, arm * 2 + 1, 1);
				g.fillRect(sx, sy - arm, 1, arm * 2 + 1);
				if (arm > 2)
				{
					g.fillRect(sx - 1, sy - 1, 3, 3);
				}
				break;
			}
			case LEAF:
			{
				// a 2-3 pixel leaf that flips as it flutters
				boolean flip = Math.sin(p.age * 6 + p.seed) > 0;
				int w = Math.max(2, px), h = Math.max(1, px / 2 + 1);
				if (flip)
				{
					g.fillRect(sx - w / 2, sy - h / 2, w, h);
				}
				else
				{
					g.fillRect(sx - h / 2, sy - w / 2, h, w);
				}
				g.setColor(new Color(p.color2));
				g.fillRect(sx, sy, 1, 1);
				break;
			}
			case GLYPH:
			{
				// a little rune mark: a diamond outline
				int r = Math.max(1, px / 2);
				g.drawLine(sx, sy - r, sx + r, sy);
				g.drawLine(sx + r, sy, sx, sy + r);
				g.drawLine(sx, sy + r, sx - r, sy);
				g.drawLine(sx - r, sy, sx, sy - r);
				break;
			}
			case ARC:
			{
				if (arcX != null)
				{
					g.setStroke(new BasicStroke(Math.max(1f, px / 2.5f)));
					int lx = sx, ly = sy;
					for (int i = 0; i < arcX.length; i++)
					{
						if (arcX[i] == Integer.MIN_VALUE)
						{
							break;
						}
						g.drawLine(lx, ly, arcX[i], arcY[i]);
						lx = arcX[i];
						ly = arcY[i];
					}
					g.setStroke(new BasicStroke(1f));
				}
				break;
			}
		}
		g.setComposite(old);
	}

	private float jit(float r)
	{
		return (rng.nextFloat() * 2f - 1f) * r;
	}
}
