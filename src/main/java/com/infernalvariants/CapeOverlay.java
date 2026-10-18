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
import net.runelite.api.Renderable;
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
			if (style == null || plugin.onPetMount(p))
			{
				continue; // not ours to paint, or up on a Pet Mounts mount (see onPetMount)
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
		// where the cape is on screen
		int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, by1 = Integer.MIN_VALUE;
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
				}
			}
		}
		Rectangle capeBounds = new Rectangle(bx0 - 2, by0 - 2, bx1 - bx0 + 4, by1 - by0 + 4);
		Rectangle view = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight());
		capeBounds = capeBounds.intersection(view);
		if (capeBounds.isEmpty() || style.isOriginal())
		{
			return;
		}

		// what's in front of the cape, pixel by pixel
		BufferedImage mask = buildMask(wv, p, model, faces, capeBounds, cam);
		if (mask == null)
		{
			drawDesign(g, p, style, faces);
			return;
		}

		// draw the design off-screen, cut out whatever is in front of it, then put it on screen
		int w = capeBounds.width, h = capeBounds.height;
		if (offscreen == null || offscreen.getWidth() < w || offscreen.getHeight() < h)
		{
			offscreen = new BufferedImage(Math.max(w, offscreen == null ? 0 : offscreen.getWidth()),
				Math.max(h, offscreen == null ? 0 : offscreen.getHeight()), BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D og = offscreen.createGraphics();
		og.setComposite(AlphaComposite.Clear);
		og.fillRect(0, 0, w, h);
		og.setComposite(AlphaComposite.SrcOver);
		og.setRenderingHints(g.getRenderingHints());
		og.translate(-capeBounds.x, -capeBounds.y);
		drawDesign(og, p, style, faces);
		og.setTransform(new AffineTransform());
		og.setComposite(AlphaComposite.DstIn);
		og.drawImage(mask, 0, 0, w, h, 0, 0, w, h, null);
		og.dispose();
		g.drawImage(offscreen, capeBounds.x, capeBounds.y, capeBounds.x + w, capeBounds.y + h, 0, 0, w, h, null);
	}

	private void drawDesign(Graphics2D g, Player p, CapeStyle style, List<PanelFace> faces)
	{
		drawPanel(g, style, faces);
		if (config.emblems() && style.emblem() != CapeStyle.Emblem.NONE && faces.size() == PANEL_FACES_STANDARD)
		{
			drawEmblems(g, p, style, faces);
		}
	}

	// ---------------------------------------------------------------- occlusion (per pixel)

	/** An occluder this much nearer (local units) than the cape hides it; keeps the cape's own trim from nicking it. */
	private static final float OWN_BIAS = 4f;

	private final DepthMask depthMask = new DepthMask();
	private BufferedImage offscreen;
	private final float[] one = new float[1], oneY = new float[1];
	private final int[] onePx = new int[1], onePy = new int[1];
	private int[] ox = new int[0], oy = new int[0];
	private float[] oz = new float[0];

	/**
	 * Rasterise the cape and everything that could be in front of it into a small depth buffer, and
	 * return a mask of where the cape is actually visible (null when nothing covers it).
	 */
	private BufferedImage buildMask(WorldView wv, Player owner, Model ownModel, List<PanelFace> faces,
		Rectangle box, Camera cam)
	{
		depthMask.begin(box);

		// the cape itself (sx/sy/depth were filled in for the owner's model by drawCape)
		float capeFar = -Float.MAX_VALUE;
		for (PanelFace pf : faces)
		{
			if (!pf.visible)
			{
				continue;
			}
			depthMask.cape(sx[pf.a], sy[pf.a], depth[pf.a], sx[pf.b], sy[pf.b], depth[pf.b], sx[pf.c], sy[pf.c], depth[pf.c]);
			capeFar = Math.max(capeFar, Math.max(cam.ground(lx[pf.a], ly[pf.a]), Math.max(cam.ground(lx[pf.b], ly[pf.b]),
				cam.ground(lx[pf.c], ly[pf.c]))));
		}

		// the player's own body, arms and weapon (anything except the cape panel)
		rasterOwn(ownModel);

		// other players, NPCs and pets
		LocalPoint ol = owner.getLocalLocation();
		for (Player other : wv.players())
		{
			if (other != null && other != owner)
			{
				rasterActor(wv, other, box, ol, capeFar, cam);
			}
		}
		for (NPC npc : wv.npcs())
		{
			if (npc != null)
			{
				rasterActor(wv, npc, box, ol, capeFar, cam);
			}
		}

		// scenery between the camera and the cape
		rasterScenery(wv, ol, capeFar, box, cam);

		return depthMask.build(OWN_BIAS);
	}

	private void rasterOwn(Model model)
	{
		short[] tex = model.getFaceTextures();
		int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();
		int[] c3 = model.getFaceColors3();
		byte[] alpha = model.getFaceTransparencies();
		for (int f = 0; f < model.getFaceCount(); f++)
		{
			if (tex != null && tex[f] == InfernalVariantsPlugin.INFERNAL_TEXTURE)
			{
				continue;
			}
			if (skipFace(c3, alpha, f))
			{
				continue;
			}
			int a = f1[f], b = f2[f], c = f3[f];
			if (!frontFacing(sx, sy, a, b, c) || !depthMask.touches(sx[a], sy[a], sx[b], sy[b], sx[c], sy[c]))
			{
				continue;
			}
			depthMask.occluder(sx[a], sy[a], depth[a], sx[b], sy[b], depth[b], sx[c], sy[c], depth[c]);
		}
	}

	private void rasterActor(WorldView wv, Actor a, Rectangle box, LocalPoint ol, float capeFar, Camera cam)
	{
		LocalPoint al = a.getLocalLocation();
		if (al == null || Math.abs(al.getX() - ol.getX()) > SCENERY_MAX_TILES * 128
			|| Math.abs(al.getY() - ol.getY()) > SCENERY_MAX_TILES * 128)
		{
			return;
		}
		// anything standing more than a tile and a half past the cape can't be in front of it
		if (cam.ground(al.getX(), al.getY()) > capeFar + 192)
		{
			return;
		}
		int z = Perspective.getFootprintTileHeight(client, al, wv.getPlane(), a.getFootprintSize()) - a.getAnimationHeightOffset();
		// cheap screen test before projecting the whole model: a generous circle around the actor
		one[0] = 0;
		oneY[0] = -120;
		Perspective.modelToCanvas(client, wv, 1, al.getX(), al.getY(), z, 0, one, one, oneY, onePx, onePy);
		float d = cam.depth(al.getX(), al.getY(), z - 120);
		if (onePx[0] == Integer.MIN_VALUE || d <= 1f)
		{
			return;
		}
		float r = (192 + a.getFootprintSize() * 96) * client.getScale() / d;
		if (onePx[0] + r < box.x || onePx[0] - r > box.x + box.width || onePy[0] + r < box.y || onePy[0] - r > box.y + box.height)
		{
			return;
		}
		Model m = a.getModel();
		if (m == null)
		{
			return;
		}
		rasterModel(wv, m, al.getX(), al.getY(), z, a.getCurrentOrientation(), cam);
	}

	private void rasterScenery(WorldView wv, LocalPoint ol, float capeFar, Rectangle box, Camera cam)
	{
		Scene scene = wv.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = wv.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return;
		}
		java.util.Set<GameObject> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		int ox0 = ol.getSceneX(), oy0 = ol.getSceneY();
		int camX = clamp((int) Math.floor(cam.cx) >> 7, ox0 - SCENERY_MAX_TILES, ox0 + SCENERY_MAX_TILES);
		int camY = clamp((int) Math.floor(cam.cy) >> 7, oy0 - SCENERY_MAX_TILES, oy0 + SCENERY_MAX_TILES);
		int minX = Math.max(0, Math.min(ox0, camX) - SCENERY_MARGIN_TILES);
		int maxX = Math.min(tiles[plane].length - 1, Math.max(ox0, camX) + SCENERY_MARGIN_TILES);
		for (int x = minX; x <= maxX; x++)
		{
			int minY = Math.max(0, Math.min(oy0, camY) - SCENERY_MARGIN_TILES);
			int maxY = Math.min(tiles[plane][x].length - 1, Math.max(oy0, camY) + SCENERY_MARGIN_TILES);
			for (int y = minY; y <= maxY; y++)
			{
				// a tile that is entirely farther away than the cape can't hold anything in front of it
				// (big objects are visited from every tile they cover, so they're still found)
				if (nearestCorner(x, y, cam) > capeFar)
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
						if (o == null || o.getRenderable() instanceof Actor)
						{
							continue; // players and NPCs are done above
						}
						if (!seen.add(o))
						{
							continue; // big objects cover several tiles: once is enough
						}
						Shape hull = o.getConvexHull();
						if (hull == null || !hull.intersects(box))
						{
							continue;
						}
						Renderable r = o.getRenderable();
						rasterRenderable(wv, r, o.getX(), o.getY(), o.getZ(), o.getModelOrientation(), cam);
					}
				}
				WallObject wall = tile.getWallObject();
				if (wall != null && hullTouches(wall.getConvexHull(), wall.getConvexHull2(), box))
				{
					rasterRenderable(wv, wall.getRenderable1(), wall.getX(), wall.getY(), wall.getZ(), 0, cam);
					rasterRenderable(wv, wall.getRenderable2(), wall.getX(), wall.getY(), wall.getZ(), 0, cam);
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null && hullTouches(deco.getConvexHull(), deco.getConvexHull2(), box))
				{
					rasterRenderable(wv, deco.getRenderable(), deco.getX() + deco.getXOffset(), deco.getY() + deco.getYOffset(),
						deco.getZ(), 0, cam);
					rasterRenderable(wv, deco.getRenderable2(), deco.getX() + deco.getXOffset2(), deco.getY() + deco.getYOffset2(),
						deco.getZ(), 0, cam);
				}
			}
		}
	}

	private static boolean hullTouches(Shape a, Shape b, Rectangle box)
	{
		return (a != null && a.intersects(box)) || (b != null && b.intersects(box));
	}

	private void rasterRenderable(WorldView wv, Renderable r, int x, int y, int z, int orientation, Camera cam)
	{
		if (r == null)
		{
			return;
		}
		Model m = r instanceof Model ? (Model) r : r.getModel();
		if (m == null)
		{
			return;
		}
		rasterModel(wv, m, x, y, z - r.getAnimationHeightOffset(), orientation, cam);
	}

	/** Project a model placed in the scene and add its front-facing triangles as occluders. */
	private void rasterModel(WorldView wv, Model m, int x, int y, int z, int orientation, Camera cam)
	{
		int n = m.getVerticesCount();
		if (n == 0)
		{
			return;
		}
		if (ox.length < n)
		{
			ox = new int[n];
			oy = new int[n];
			oz = new float[n];
		}
		float[] vx = m.getVerticesX(), vy = m.getVerticesY(), vz = m.getVerticesZ();
		Perspective.modelToCanvas(client, wv, n, x, y, z, orientation, vx, vz, vy, ox, oy);
		double ang = orientation * Math.PI / 1024.0;
		float rs = (float) Math.sin(ang), rc = (float) Math.cos(ang);
		for (int i = 0; i < n; i++)
		{
			float px = vx[i] * rc + vz[i] * rs + x;
			float py = vz[i] * rc - vx[i] * rs + y;
			oz[i] = cam.depth(px, py, vy[i] + z);
		}
		int[] f1 = m.getFaceIndices1(), f2 = m.getFaceIndices2(), f3 = m.getFaceIndices3();
		int[] c3 = m.getFaceColors3();
		byte[] alpha = m.getFaceTransparencies();
		for (int f = 0; f < m.getFaceCount(); f++)
		{
			if (skipFace(c3, alpha, f))
			{
				continue;
			}
			int a = f1[f], b = f2[f], c = f3[f];
			if (ox[a] == Integer.MIN_VALUE || ox[b] == Integer.MIN_VALUE || ox[c] == Integer.MIN_VALUE)
			{
				continue;
			}
			if (!frontFacing(ox, oy, a, b, c) || !depthMask.touches(ox[a], oy[a], ox[b], oy[b], ox[c], oy[c]))
			{
				continue;
			}
			depthMask.occluder(ox[a], oy[a], oz[a], ox[b], oy[b], oz[b], ox[c], oy[c], oz[c]);
		}
	}

	/** Faces the game doesn't draw (hidden) or that are nearly see-through don't hide the cape. */
	private static boolean skipFace(int[] c3, byte[] alpha, int f)
	{
		if (c3 != null && f < c3.length && c3[f] == -2)
		{
			return true;
		}
		return alpha != null && f < alpha.length && (alpha[f] & 0xFF) > 200;
	}

	/** The client's own back-face test: only triangles wound this way are drawn. */
	private static boolean frontFacing(int[] x, int[] y, int a, int b, int c)
	{
		if (x[a] == Integer.MIN_VALUE || x[b] == Integer.MIN_VALUE || x[c] == Integer.MIN_VALUE)
		{
			return false;
		}
		long cross = (long) (x[a] - x[b]) * (y[c] - y[b]) - (long) (y[a] - y[b]) * (x[c] - x[b]);
		return cross > 0;
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
	/** Extra tiles searched around the player and camera for scenery that could cover the cape. */
	private static final int SCENERY_MARGIN_TILES = 3;
	/** Never search further than this many tiles from the player (keeps zoomed-out views cheap). */
	private static final int SCENERY_MAX_TILES = 40;

	private static int clamp(int v, int lo, int hi)
	{
		return v < lo ? lo : (v > hi ? hi : v);
	}

	/** Ground distance of a scene tile's corner nearest the camera. */
	private static float nearestCorner(int x, int y, Camera cam)
	{
		float near = Float.MAX_VALUE;
		for (int cx : new int[]{x * 128, (x + 1) * 128})
		{
			for (int cy : new int[]{y * 128, (y + 1) * 128})
			{
				near = Math.min(near, cam.ground(cx, cy));
			}
		}
		return near;
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
			double yaw, pitch;
			if (client.isGpu())
			{
				cx = client.getCameraFpX();
				cy = client.getCameraFpY();
				cz = client.getCameraFpZ();
				yaw = client.getCameraFpYaw();
				pitch = client.getCameraFpPitch();
			}
			else
			{
				cx = client.getCameraX();
				cy = client.getCameraY();
				cz = client.getCameraZ();
				// 14-bit Jagex units: 16384 per full turn
				yaw = client.getCameraYaw() * Math.PI / 8192.0;
				pitch = client.getCameraPitch() * Math.PI / 8192.0;
			}
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
