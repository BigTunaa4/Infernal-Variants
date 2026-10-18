package com.infernalvariants;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Paints the chosen design over the infernal cape.
 * <p>
 * The game can't recolour a texture, and the infernal cape's whole outer panel is one
 * animated lava texture. So every frame this overlay takes the player's animated model, finds
 * the faces that use that texture, works out their texture coordinates exactly the way the
 * client does, and draws the repainted texture onto those triangles. Back-facing triangles are
 * skipped, so the cape only shows from angles where the real one would. Anyone standing in
 * front of the cape is cut out of the drawing area so they aren't painted over.
 */
class CapeOverlay extends Overlay
{
	private static final int PANEL_FACES_STANDARD = 30;
	private static final float TEX = CapeTextures.SIZE;

	private static final String[][] EMBLEM_LAYOUT_NAMES = {
		{},                              // NONE
		{"CS", "CL"},                    // STAR
		{"C1", "C2", "C3"},              // RUNES
		{"C2", "C1", "C3"},              // VOID
		{"LS", "RS", "LL", "RL"},        // LEAVES
		{"CS"},                          // SUN
		{"CS"},                          // GUTHIX
	};
	/** Emblem size in model units, per slot. */
	private static final float[][] EMBLEM_SIZES = {
		{},
		{24, 12},
		{13, 13, 13},
		{22, 9, 9},
		{10, 10, 10, 10},
		{22},
		{21},
	};
	/** Which sprite variant each slot uses. */
	private static final int[][] EMBLEM_SPRITE = {
		{},
		{0, 0},
		{0, 1, 2},
		{0, 1, 1},
		{0, 1, 2, 3},
		{0},
		{0},
	};

	private final Client client;
	private final InfernalVariantsPlugin plugin;
	private final InfernalVariantsConfig config;
	private final Mips mips = new Mips();


	// scratch buffers, grown as needed
	private int[] sx = new int[0], sy = new int[0];
	private float[] lx = new float[0], ly = new float[0], lh = new float[0], depth = new float[0];

	@Inject
	CapeOverlay(Client client, InfernalVariantsPlugin plugin, InfernalVariantsConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	void reset()
	{
		mips.clear();
	}

	/** One face of the cape panel, ready to draw. */
	private static final class PanelFace
	{
		int order;
		int a, b, c;
		float[] u = new float[3], v = new float[3];
		float depth;
		float shade;
		float hemness;
		boolean visible;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		try
		{
			return paint(g);
		}
		finally
		{
			// the scene is drawn: give the real lava panels back (they were hidden for this frame only)
			plugin.restorePanels();
		}
	}

	private Dimension paint(Graphics2D g)
	{
		if (!plugin.texturesReady())
		{
			return null;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		// smooth the design across the cape (sharp up close, no shimmering pixels zoomed out; see Mips)
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		WorldView wv = client.getTopLevelWorldView();
		for (Player p : wv.players())
		{
			if (p == null)
			{
				continue;
			}
			boolean isLocal = p == local;
			if (!isLocal && config.others() == InfernalVariantsConfig.OthersMode.OFF)
			{
				continue;
			}
			CapeStyle style = plugin.styleFor(p);
			if (style == null)
			{
				continue;
			}
			try
			{
				drawCape(g, wv, p, style);
			}
			catch (RuntimeException ex)
			{
				// never let a bad frame take the overlay down
			}
		}
		return null;
	}

	private void drawCape(Graphics2D g, WorldView wv, Player p, CapeStyle style)
	{
		LocalPoint lp = p.getLocalLocation();
		Model model = p.getModel();
		if (lp == null || model == null)
		{
			return;
		}
		short[] faceTex = model.getFaceTextures();
		if (faceTex == null)
		{
			return;
		}

		final int vCount = model.getVerticesCount();
		final float[] vx = model.getVerticesX();
		final float[] vy = model.getVerticesY();
		final float[] vz = model.getVerticesZ();
		final int[] f1 = model.getFaceIndices1();
		final int[] f2 = model.getFaceIndices2();
		final int[] f3 = model.getFaceIndices3();
		final int fCount = model.getFaceCount();

		ensure(vCount);
		final int baseZ = Perspective.getFootprintTileHeight(client, lp, wv.getPlane(), p.getFootprintSize()) - p.getAnimationHeightOffset();
		final int orient = p.getCurrentOrientation();
		Perspective.modelToCanvas(client, wv, vCount, lp.getX(), lp.getY(), baseZ, orient, vx, vz, vy, sx, sy);

		// local-space positions and camera depth for every vertex
		final double ang = orient * Math.PI / 1024.0;
		final float rs = (float) Math.sin(ang), rc = (float) Math.cos(ang);
		final Camera cam = new Camera(client);
		for (int i = 0; i < vCount; i++)
		{
			float x = vx[i], z = vz[i];
			lx[i] = x * rc + z * rs + lp.getX();
			ly[i] = z * rc - x * rs + lp.getY();
			lh[i] = vy[i] + baseZ;
			depth[i] = cam.depth(lx[i], ly[i], lh[i]);
		}

		// collect the lava-textured panel faces
		List<PanelFace> faces = new ArrayList<>(64);
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int f = 0; f < fCount; f++)
		{
			if (faceTex[f] != InfernalVariantsPlugin.INFERNAL_TEXTURE)
			{
				continue;
			}
			PanelFace pf = new PanelFace();
			pf.order = faces.size();
			pf.a = f1[f];
			pf.b = f2[f];
			pf.c = f3[f];
			computeUv(model, f, pf);
			faces.add(pf);
			float cy = (vy[pf.a] + vy[pf.b] + vy[pf.c]) / 3f;
			minY = Math.min(minY, cy);
			maxY = Math.max(maxY, cy);
		}
		if (faces.isEmpty())
		{
			return;
		}

		boolean anyVisible = false;
		for (PanelFace pf : faces)
		{
			int a = pf.a, b = pf.b, c = pf.c;
			pf.hemness = maxY > minY ? ((vy[a] + vy[b] + vy[c]) / 3f - minY) / (maxY - minY) : 0f;
			pf.depth = (depth[a] + depth[b] + depth[c]) / 3f;

			// outward normal in model space (y is down), then lit like the game: from above
			float e1x = vx[b] - vx[a], e1y = vy[b] - vy[a], e1z = vz[b] - vz[a];
			float e2x = vx[c] - vx[a], e2y = vy[c] - vy[a], e2z = vz[c] - vz[a];
			float nx = e1y * e2z - e1z * e2y;
			float ny = e1z * e2x - e1x * e2z;
			float nz = e1x * e2y - e1y * e2x;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0)
			{
				nx /= len;
				ny /= len;
				nz /= len;
			}
			float light = 0.2f * nx - 0.7f * ny + 0.65f * nz;
			pf.shade = 0.74f + 0.26f * Math.max(0f, Math.min(1f, light));

			if (sx[a] == Integer.MIN_VALUE || sx[b] == Integer.MIN_VALUE || sx[c] == Integer.MIN_VALUE)
			{
				continue;
			}
			// same winding test the client uses to cull back faces
			long cross = (long) (sx[a] - sx[b]) * (sy[c] - sy[b]) - (long) (sy[a] - sy[b]) * (sx[c] - sx[b]);
			pf.visible = cross > 0;
			anyVisible |= pf.visible;
		}

		if (!anyVisible)
		{
			return;
		}
		// where the cape is on screen, so only things that actually overlap it are cut out
		int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, by1 = Integer.MIN_VALUE;
		// how far along the ground (toward the camera's view direction) the visible cape sits
		float capeNear = Float.MAX_VALUE, capeSum = 0;
		int capeCount = 0;
		for (PanelFace pf : faces)
		{
			if (pf.visible)
			{
				for (int v : new int[]{pf.a, pf.b, pf.c})
				{
					bx0 = Math.min(bx0, sx[v]);
					by0 = Math.min(by0, sy[v]);
					bx1 = Math.max(bx1, sx[v]);
					by1 = Math.max(by1, sy[v]);
					float gd = cam.ground(lx[v], ly[v]);
					capeNear = Math.min(capeNear, gd);
					capeSum += gd;
					capeCount++;
				}
			}
		}
		float capeMid = capeCount > 0 ? capeSum / capeCount : capeNear;
		Rectangle capeBounds = new Rectangle(bx0 - 2, by0 - 2, bx1 - bx0 + 4, by1 - by0 + 4);

		Shape oldClip = g.getClip();
		Shape clip = clipAroundBlockers(wv, p, capeNear, capeMid, cam, oldClip, capeBounds);
		g.setClip(clip);

		if (anyVisible && !style.isOriginal())
		{
			drawPanel(g, style, faces);
			if (config.emblems() && style.emblem() != CapeStyle.Emblem.NONE && faces.size() == PANEL_FACES_STANDARD)
			{
				drawEmblems(g, p, style, faces);
			}
		}

		g.setClip(oldClip);
	}

	// ---------------------------------------------------------------- panel

	private void drawPanel(Graphics2D g, CapeStyle style, List<PanelFace> faces)
	{
		BufferedImage tex = plugin.veinTexture(style);
		if (tex == null)
		{
			return;
		}
		BufferedImage hem = plugin.hemTexture(style);
		BufferedImage glow = plugin.glowTexture(style);
		StyleLook look = plugin.look(style);
		boolean animate = config.flowing();
		float t = client.getGameCycle() / 50f;
		// every style's veins flow down the cape, each at its own pace (texture v runs collar -> hem)
		float scroll = animate ? ((t * look.flowSpeed) % TEX + TEX) % TEX : 0f;
		Rectangle2D.Float anchor = new Rectangle2D.Float(0, scroll, TEX, TEX);
		// each texture at full size and smaller, chosen per triangle by how big it is on screen
		BufferedImage[] texMips = mips.of(tex), hemMips = mips.of(hem), glowMips = mips.of(glow);
		TexturePaint[] paints = new TexturePaint[texMips.length];
		TexturePaint[] hemPaints = hemMips == null ? null : new TexturePaint[hemMips.length];
		TexturePaint[] glowPaints = glowMips == null ? null : new TexturePaint[glowMips.length];
		// lightning: a bolt flashes across the stone every so often
		BufferedImage[] boltMips = null;
		float boltAlpha = 0f;
		BufferedImage[] bolts = plugin.boltFrames(style);
		if (bolts != null && animate)
		{
			final float slot = 0.45f;
			long n = (long) Math.floor(t / slot);
			long hsh = n * 0x9E3779B97F4A7C15L;
			hsh ^= (hsh >>> 29);
			if ((hsh & 7) < 5)
			{
				float ph = (t - n * slot) / slot;
				// flash, dip, flash again, fade
				boltAlpha = ph < 0.12f ? 1f : ph < 0.2f ? 0.25f : ph < 0.3f ? 0.95f : Math.max(0f, 1f - (ph - 0.3f) * 2.2f);
				boltMips = mips.of(bolts[(int) ((hsh >>> 8) % bolts.length)]);
			}
		}
		else if (bolts != null)
		{
			boltMips = mips.of(bolts[0]);
			boltAlpha = 0.8f;
		}
		TexturePaint[] boltPaints = boltMips == null ? null : new TexturePaint[boltMips.length];

		faces.sort((x, y) -> Float.compare(y.depth, x.depth));
		AffineTransform base = g.getTransform();
		Composite baseComposite = g.getComposite();
		for (PanelFace pf : faces)
		{
			if (!pf.visible)
			{
				continue;
			}
			AffineTransform at = texToScreen(pf);
			if (at == null)
			{
				continue;
			}
			Path2D.Float tri = grownTexTriangle(pf);
			int level = Mips.level(texArea(pf), screenArea(pf), texMips.length);
			TexturePaint paint = paintAt(paints, texMips, level, anchor);
			TexturePaint hemPaint = paintAt(hemPaints, hemMips, level, anchor);
			TexturePaint glowPaint = paintAt(glowPaints, glowMips, level, anchor);
			TexturePaint boltPaint = paintAt(boltPaints, boltMips, level, anchor);
			g.setTransform(base);
			g.transform(at);
			g.setPaint(paint);
			g.fill(tri);
			if (hemPaint != null)
			{
				float k = CapeTextures.clamp((pf.hemness - 0.5f) / 0.4f);
				if (k > 0.01f)
				{
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, k));
					g.setPaint(hemPaint);
					g.fill(tri);
					g.setComposite(baseComposite);
				}
			}
			if (pf.shade < 0.995f)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - pf.shade));
				g.setPaint(Color.BLACK);
				g.fill(tri);
				g.setComposite(baseComposite);
			}
			// the glowing veins sit on top of the shading, so they read as light, not paint
			if (glowPaint != null)
			{
				float a = animate ? look.glowAlpha(t, pf.hemness, pf.order * 0.37f) : (look.glowMin + look.glowMax) * 0.5f;
				// the cracks flare up when a bolt strikes
				a = Math.min(1f, a + boltAlpha * 0.6f);
				if (a > 0.02f)
				{
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
					g.setPaint(glowPaint);
					g.fill(tri);
					g.setComposite(baseComposite);
				}
			}
			if (boltPaint != null && boltAlpha > 0.02f)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, boltAlpha));
				g.setPaint(boltPaint);
				g.fill(tri);
				g.setComposite(baseComposite);
			}
		}
		g.setTransform(base);
	}

	/** The paint for this texture at this size, made the first time it's needed this frame. */
	private static TexturePaint paintAt(TexturePaint[] paints, BufferedImage[] images, int level, Rectangle2D.Float anchor)
	{
		if (paints == null || images == null)
		{
			return null;
		}
		int l = Math.min(level, images.length - 1);
		if (paints[l] == null)
		{
			paints[l] = new TexturePaint(images[l], anchor);
		}
		return paints[l];
	}

	/** How many texture pixels the face covers. */
	private static float texArea(PanelFace pf)
	{
		float du1 = (pf.u[1] - pf.u[0]) * TEX, dv1 = (pf.v[1] - pf.v[0]) * TEX;
		float du2 = (pf.u[2] - pf.u[0]) * TEX, dv2 = (pf.v[2] - pf.v[0]) * TEX;
		return Math.abs(du1 * dv2 - du2 * dv1) / 2f;
	}

	/** How many screen pixels the face covers. */
	private float screenArea(PanelFace pf)
	{
		long cross = (long) (sx[pf.b] - sx[pf.a]) * (sy[pf.c] - sy[pf.a]) - (long) (sy[pf.b] - sy[pf.a]) * (sx[pf.c] - sx[pf.a]);
		return Math.abs(cross) / 2f;
	}

	/** Affine transform taking texture pixels (u*128, v*128) to screen coordinates for one face. */
	private AffineTransform texToScreen(PanelFace pf)
	{
		float u0 = pf.u[0] * TEX, v0 = pf.v[0] * TEX;
		float du1 = pf.u[1] * TEX - u0, dv1 = pf.v[1] * TEX - v0;
		float du2 = pf.u[2] * TEX - u0, dv2 = pf.v[2] * TEX - v0;
		float det = du1 * dv2 - du2 * dv1;
		if (Math.abs(det) < 1e-4f)
		{
			return null;
		}
		float x0 = sx[pf.a], y0 = sy[pf.a];
		float dx1 = sx[pf.b] - x0, dy1 = sy[pf.b] - y0;
		float dx2 = sx[pf.c] - x0, dy2 = sy[pf.c] - y0;
		// [dx;dy] = M * [du;dv]  ->  M = D * inverse(T)
		float i00 = dv2 / det, i01 = -du2 / det, i10 = -dv1 / det, i11 = du1 / det;
		float m00 = dx1 * i00 + dx2 * i10;
		float m01 = dx1 * i01 + dx2 * i11;
		float m10 = dy1 * i00 + dy2 * i10;
		float m11 = dy1 * i01 + dy2 * i11;
		float tx = x0 - (m00 * u0 + m01 * v0);
		float ty = y0 - (m10 * u0 + m11 * v0);
		return new AffineTransform(m00, m10, m01, m11, tx, ty);
	}

	/** The face's triangle in texture space, grown by about a pixel so neighbours meet without seams. */
	private Path2D.Float grownTexTriangle(PanelFace pf)
	{
		float cu = (pf.u[0] + pf.u[1] + pf.u[2]) / 3f * TEX;
		float cv = (pf.v[0] + pf.v[1] + pf.v[2]) / 3f * TEX;
		float edge = (float) (Math.hypot(sx[pf.a] - sx[pf.b], sy[pf.a] - sy[pf.b])
			+ Math.hypot(sx[pf.b] - sx[pf.c], sy[pf.b] - sy[pf.c])
			+ Math.hypot(sx[pf.c] - sx[pf.a], sy[pf.c] - sy[pf.a])) / 3f;
		float grow = 1f + 2f / Math.max(edge, 1f);
		Path2D.Float path = new Path2D.Float();
		for (int i = 0; i < 3; i++)
		{
			float u = cu + (pf.u[i] * TEX - cu) * grow;
			float v = cv + (pf.v[i] * TEX - cv) * grow;
			if (i == 0)
			{
				path.moveTo(u, v);
			}
			else
			{
				path.lineTo(u, v);
			}
		}
		path.closePath();
		return path;
	}

	/**
	 * Texture coordinates for a face, computed from the model's texture triangle (P/M/N vertices)
	 * the same way the client does. Uses the animated vertices, so the pattern sticks to the cape.
	 */
	private static void computeUv(Model m, int face, PanelFace out)
	{
		byte[] texFaces = m.getTextureFaces();
		int t = texFaces == null ? -1 : texFaces[face];
		if (t == -1)
		{
			out.u[0] = 0f;
			out.v[0] = 0f;
			out.u[1] = 1f;
			out.v[1] = 0f;
			out.u[2] = 0f;
			out.v[2] = 1f;
			return;
		}
		t &= 0xFF;
		float[] X = m.getVerticesX(), Y = m.getVerticesY(), Z = m.getVerticesZ();
		int ta = m.getTexIndices1()[t], tb = m.getTexIndices2()[t], tc = m.getTexIndices3()[t];
		float v1x = X[ta], v1y = Y[ta], v1z = Z[ta];
		float v2x = X[tb] - v1x, v2y = Y[tb] - v1y, v2z = Z[tb] - v1z;
		float v3x = X[tc] - v1x, v3y = Y[tc] - v1y, v3z = Z[tc] - v1z;
		float v7x = v2y * v3z - v2z * v3y;
		float v7y = v2z * v3x - v2x * v3z;
		float v7z = v2x * v3y - v2y * v3x;

		float ux = v3y * v7z - v3z * v7y;
		float uy = v3z * v7x - v3x * v7z;
		float uz = v3x * v7y - v3y * v7x;
		float fu = 1f / (ux * v2x + uy * v2y + uz * v2z);

		float wx = v2y * v7z - v2z * v7y;
		float wy = v2z * v7x - v2x * v7z;
		float wz = v2x * v7y - v2y * v7x;
		float fv = 1f / (wx * v3x + wy * v3y + wz * v3z);

		int[] idx = {out.a, out.b, out.c};
		for (int i = 0; i < 3; i++)
		{
			float px = X[idx[i]] - v1x, py = Y[idx[i]] - v1y, pz = Z[idx[i]] - v1z;
			out.u[i] = (ux * px + uy * py + uz * pz) * fu;
			out.v[i] = (wx * px + wy * py + wz * pz) * fv;
		}
	}

	// ---------------------------------------------------------------- emblems

	private void drawEmblems(Graphics2D g, Player p, CapeStyle style, List<PanelFace> facesSortedByDepth)
	{
		// anchors reference faces in model order, so rebuild that order
		PanelFace[] ordered = orderedFaces(facesSortedByDepth);
		if (ordered == null)
		{
			return;
		}
		boolean female = p.getPlayerComposition() != null && p.getPlayerComposition().getGender() == 1;
		float[][][] anchors = female ? CapeAnchors.FEMALE : CapeAnchors.MALE;
		int e = style.emblem().ordinal();
		String[] names = EMBLEM_LAYOUT_NAMES[e];
		BufferedImage[] sprites = plugin.emblems(style);

		if (style.emblem() == CapeStyle.Emblem.RUNES)
		{
			// the thin channel joining the rune circles
			float[] top = anchorPoint(ordered, anchors[CapeAnchors.indexOf("C1")][0]);
			float[] bot = anchorPoint(ordered, anchors[CapeAnchors.indexOf("C3")][0]);
			if (top != null && bot != null)
			{
				float[] right = anchorPoint(ordered, anchors[CapeAnchors.indexOf("C2")][1]);
				float[] mid = anchorPoint(ordered, anchors[CapeAnchors.indexOf("C2")][0]);
				float w = (right != null && mid != null) ? (float) Math.hypot(right[0] - mid[0], right[1] - mid[1]) * 0.35f : 1f;
				g.setStroke(new BasicStroke(Math.max(1f, w)));
				g.setColor(new Color(style.veinColor(0.7f)));
				g.drawLine(Math.round(top[0]), Math.round(top[1]), Math.round(bot[0]), Math.round(bot[1]));
				g.setStroke(new BasicStroke(1f));
			}
		}

		AffineTransform base = g.getTransform();
		for (int i = 0; i < names.length; i++)
		{
			float[][] anchor = anchors[CapeAnchors.indexOf(names[i])];
			PanelFace host = ordered[(int) anchor[0][0]];
			if (!host.visible)
			{
				continue;
			}
			float[] c0 = anchorPoint(ordered, anchor[0]);
			float[] r = anchorPoint(ordered, anchor[1]);
			float[] d = anchorPoint(ordered, anchor[2]);
			if (c0 == null || r == null || d == null)
			{
				continue;
			}
			// anchors are 5 model units apart; the sprite is 32px across
			float size = EMBLEM_SIZES[e][i];
			float k = size / 5f / 32f;
			float rx = (r[0] - c0[0]) * k, ry = (r[1] - c0[1]) * k;
			float dx = (d[0] - c0[0]) * k, dy = (d[1] - c0[1]) * k;
			AffineTransform at = new AffineTransform(rx, ry, dx, dy,
				c0[0] - 16 * rx - 16 * dx, c0[1] - 16 * ry - 16 * dy);
			g.setTransform(base);
			g.transform(at);
			// a soft glow behind the emblem that breathes with the veins
			float glowA = 0.35f;
			if (config.flowing())
			{
				StyleLook look = plugin.look(style);
				glowA = 0.25f + 0.55f * look.glowAlpha(client.getGameCycle() / 50f, host.hemness, i * 1.3f);
			}
			Composite oc = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, glowA)));
			g.drawImage(plugin.emblemGlow(style), -16, -16, 64, 64, null);
			g.setComposite(oc);
			g.drawImage(sprites[EMBLEM_SPRITE[e][i]], 0, 0, null);
		}
		g.setTransform(base);
	}

	private PanelFace[] orderedFaces(List<PanelFace> faces)
	{
		PanelFace[] out = faces.toArray(new PanelFace[0]);
		// faces were collected in model order, then sorted by depth for drawing; restore model order
		java.util.Arrays.sort(out, (x, y) -> Integer.compare(x.order, y.order));
		return out;
	}

	private float[] anchorPoint(PanelFace[] ordered, float[] spec)
	{
		int k = (int) spec[0];
		if (k < 0 || k >= ordered.length)
		{
			return null;
		}
		PanelFace f = ordered[k];
		if (sx[f.a] == Integer.MIN_VALUE || sx[f.b] == Integer.MIN_VALUE || sx[f.c] == Integer.MIN_VALUE)
		{
			return null;
		}
		return new float[]{
			spec[1] * sx[f.a] + spec[2] * sx[f.b] + spec[3] * sx[f.c],
			spec[1] * sy[f.a] + spec[2] * sy[f.b] + spec[3] * sy[f.c],
		};
	}

	// ---------------------------------------------------------------- occlusion

	/**
	 * Our drawing sits on top of the scene, so carve out anybody standing between the camera and
	 * this cape (other players, NPCs, pets) so they still appear in front of it.
	 */
	/**
	 * Cut anything that stands in front of the cape out of the drawing, so it covers the cape like
	 * it covers the rest of the player.
	 * <p>
	 * Depth is judged along the ground in the camera's viewing direction. A person or NPC is in front
	 * when their centre is nearer than the middle of the cape. Scenery is only treated as in front
	 * when its whole footprint is nearer than the nearest point of the cape. Objects beside or
	 * behind the player, or flat on the ground under them, never cut the cape, so it can't turn
	 * see-through.
	 *
	 * @param capeNear ground distance of the cape's nearest visible point
	 * @param capeMid  average ground distance of the cape's visible points
	 */
	private Shape clipAroundBlockers(WorldView wv, Player owner, float capeNear, float capeMid, Camera cam,
		Shape current, Rectangle capeBounds)
	{
		LocalPoint ol = owner.getLocalLocation();
		Area area = null;
		for (Player other : wv.players())
		{
			area = carveActor(area, other, owner, ol, capeMid, cam, current, capeBounds);
		}
		for (NPC npc : wv.npcs())
		{
			area = carveActor(area, npc, owner, ol, capeMid, cam, current, capeBounds);
		}
		area = carveScenery(area, wv, ol, capeNear, cam, current, capeBounds);
		return area == null ? current : area;
	}

	/** How many tiles around the player to look for scenery standing in front of the cape. */
	private static final int SCENERY_RANGE = 6;

	/** Scenery has to be at least this much nearer than the cape (local units) to count as in front. */
	private static final float SCENERY_MARGIN = 8f;

	private Area carveScenery(Area area, WorldView wv, LocalPoint ol, float capeNear, Camera cam, Shape current,
		Rectangle capeBounds)
	{
		Scene scene = wv.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = wv.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return area;
		}
		int ox = ol.getSceneX(), oy = ol.getSceneY();
		for (int x = ox - SCENERY_RANGE; x <= ox + SCENERY_RANGE; x++)
		{
			for (int y = oy - SCENERY_RANGE; y <= oy + SCENERY_RANGE; y++)
			{
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length)
				{
					continue;
				}
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}
				GameObject[] objects = tile.getGameObjects();
				if (objects != null)
				{
					for (GameObject o : objects)
					{
						if (o == null || o.getRenderable() instanceof Actor || o.getSceneMinLocation() == null
							|| o.getSceneMinLocation().getX() != x || o.getSceneMinLocation().getY() != y)
						{
							continue; // players and NPCs are handled separately; big objects once, from their corner tile
						}
						Point max = o.getSceneMaxLocation();
						int x1 = max != null ? max.getX() : x, y1 = max != null ? max.getY() : y;
						if (footprintInFront(x, y, x1, y1, capeNear, cam))
						{
							area = cut(area, o.getConvexHull(), current, capeBounds);
						}
					}
				}
				if (!footprintInFront(x, y, x, y, capeNear, cam))
				{
					continue;
				}
				WallObject wall = tile.getWallObject();
				if (wall != null)
				{
					area = cut(area, wall.getConvexHull(), current, capeBounds);
					area = cut(area, wall.getConvexHull2(), current, capeBounds);
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null)
				{
					area = cut(area, deco.getConvexHull(), current, capeBounds);
					area = cut(area, deco.getConvexHull2(), current, capeBounds);
				}
			}
		}
		return area;
	}

	/** True when every corner of the scene-tile footprint (inclusive) is nearer than the cape. */
	private static boolean footprintInFront(int x0, int y0, int x1, int y1, float capeNear, Camera cam)
	{
		float far = -Float.MAX_VALUE;
		for (int cx : new int[]{x0 * 128, (x1 + 1) * 128})
		{
			for (int cy : new int[]{y0 * 128, (y1 + 1) * 128})
			{
				far = Math.max(far, cam.ground(cx, cy));
			}
		}
		return far < capeNear - SCENERY_MARGIN;
	}

	private Area carveActor(Area area, Actor a, Player owner, LocalPoint ol, float capeMid, Camera cam, Shape current,
		Rectangle capeBounds)
	{
		if (a == null || a == owner)
		{
			return area;
		}
		LocalPoint al = a.getLocalLocation();
		if (al == null || Math.abs(al.getX() - ol.getX()) > 640 || Math.abs(al.getY() - ol.getY()) > 640)
		{
			return area;
		}
		if (cam.ground(al.getX(), al.getY()) >= capeMid)
		{
			return area; // beside or behind the cape
		}
		return cut(area, a.getConvexHull(), current, capeBounds);
	}

	private Area cut(Area area, Shape hull, Shape current, Rectangle capeBounds)
	{
		if (hull == null || !hull.intersects(capeBounds))
		{
			return area;
		}
		if (area == null)
		{
			area = new Area(startBounds(current));
		}
		area.subtract(new Area(hull));
		return area;
	}

	private Rectangle startBounds(Shape current)
	{
		return current != null ? current.getBounds()
			: new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight());
	}

	// ---------------------------------------------------------------- utils

	private void ensure(int n)
	{
		if (sx.length < n)
		{
			sx = new int[n];
			sy = new int[n];
			lx = new float[n];
			ly = new float[n];
			lh = new float[n];
			depth = new float[n];
		}
	}

	/** Camera-space depth, matching the client's projection, used for sorting and occlusion. */
	private static final class Camera
	{
		final float cx, cy, cz, ys, yc, ps, pc;

		Camera(Client client)
		{
			cx = client.getCameraX();
			cy = client.getCameraY();
			cz = client.getCameraZ();
			double yaw = client.getCameraYaw() * Math.PI / 1024.0;
			double pitch = client.getCameraPitch() * Math.PI / 1024.0;
			ys = (float) Math.sin(yaw);
			yc = (float) Math.cos(yaw);
			ps = (float) Math.sin(pitch);
			pc = (float) Math.cos(pitch);
		}

		/**
		 * Distance along the ground in the direction the camera faces (local units). Larger is
		 * farther from the camera. Height is left out, so a tall object and a cape at a different
		 * height compare fairly.
		 */
		float ground(float x, float y)
		{
			return (y - cy) * yc - (x - cx) * ys;
		}

		float depth(float x, float y, float h)
		{
			x -= cx;
			y -= cy;
			h -= cz;
			float y1 = y * yc - x * ys;
			return y1 * pc + h * ps;
		}
	}
}
