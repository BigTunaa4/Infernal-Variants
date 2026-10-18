package com.infernalvariants;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * A small software depth buffer covering just the cape's area of the screen.
 * <p>
 * The cape design is drawn on top of the finished frame, so on its own it would cover anything
 * standing in front of the cape. To avoid that, the cape's own triangles and the triangles of
 * everything that could be in front of it (the player's own arms and weapon, pets, other players,
 * NPCs, scenery) are rasterised here with their real camera depth. Wherever something is nearer
 * than the cape, the design is masked out, so the game's own drawing of that thing shows through,
 * exactly like the real cape would be hidden.
 */
final class DepthMask
{
	private int x0, y0, w, h;
	private float[] cape = new float[0];
	private float[] occ = new float[0];
	private BufferedImage mask;

	/** Start a new frame for the given screen box. */
	void begin(Rectangle box)
	{
		x0 = box.x;
		y0 = box.y;
		w = Math.max(1, box.width);
		h = Math.max(1, box.height);
		int n = w * h;
		if (cape.length < n)
		{
			cape = new float[n];
			occ = new float[n];
		}
		Arrays.fill(cape, 0, n, Float.POSITIVE_INFINITY);
		Arrays.fill(occ, 0, n, Float.POSITIVE_INFINITY);
	}

	int x()
	{
		return x0;
	}

	int y()
	{
		return y0;
	}

	int width()
	{
		return w;
	}

	int height()
	{
		return h;
	}

	/** True if the screen-space bounding box of a triangle touches this mask. */
	boolean touches(int ax, int ay, int bx, int by, int cx, int cy)
	{
		int minX = Math.min(ax, Math.min(bx, cx)), maxX = Math.max(ax, Math.max(bx, cx));
		int minY = Math.min(ay, Math.min(by, cy)), maxY = Math.max(ay, Math.max(by, cy));
		return maxX >= x0 && minX < x0 + w && maxY >= y0 && minY < y0 + h;
	}

	void cape(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
	{
		raster(cape, ax, ay, az, bx, by, bz, cx, cy, cz);
	}

	void occluder(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
	{
		raster(occ, ax, ay, az, bx, by, bz, cx, cy, cz);
	}

	/**
	 * Rasterise one triangle, keeping the nearest depth per pixel. Depth is interpolated as 1/z,
	 * which is linear in screen space, so it matches a real perspective depth buffer.
	 */
	private void raster(float[] buf, int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
	{
		if (az <= 1f || bz <= 1f || cz <= 1f)
		{
			return;
		}
		int minX = Math.max(x0, Math.min(ax, Math.min(bx, cx)));
		int maxX = Math.min(x0 + w - 1, Math.max(ax, Math.max(bx, cx)));
		int minY = Math.max(y0, Math.min(ay, Math.min(by, cy)));
		int maxY = Math.min(y0 + h - 1, Math.max(ay, Math.max(by, cy)));
		if (minX > maxX || minY > maxY)
		{
			return;
		}
		float area = (float) (bx - ax) * (cy - ay) - (float) (by - ay) * (cx - ax);
		if (Math.abs(area) < 0.5f)
		{
			return;
		}
		float inv = 1f / area;
		float iwa = 1f / az, iwb = 1f / bz, iwc = 1f / cz;
		for (int py = minY; py <= maxY; py++)
		{
			float fy = py + 0.5f;
			int row = (py - y0) * w;
			for (int px = minX; px <= maxX; px++)
			{
				float fx = px + 0.5f;
				float wb = ((fx - ax) * (cy - ay) - (fy - ay) * (cx - ax)) * inv;
				float wc = ((bx - ax) * (fy - ay) - (by - ay) * (fx - ax)) * inv;
				float wa = 1f - wb - wc;
				if (wa < -0.01f || wb < -0.01f || wc < -0.01f)
				{
					continue;
				}
				float z = 1f / (wa * iwa + wb * iwb + wc * iwc);
				int i = row + (px - x0);
				if (z < buf[i])
				{
					buf[i] = z;
				}
			}
		}
	}

	/**
	 * Build the mask: opaque where the cape is the nearest thing, clear where something is in front.
	 *
	 * @param bias how much nearer (in local units) an occluder must be to hide the cape
	 * @return the mask, or null if nothing covers the cape this frame
	 */
	BufferedImage build(float bias)
	{
		boolean hidden = false;
		int n = w * h;
		for (int i = 0; i < n; i++)
		{
			if (occ[i] < cape[i] - bias)
			{
				hidden = true;
				break;
			}
		}
		if (!hidden)
		{
			return null;
		}
		if (mask == null || mask.getWidth() < w || mask.getHeight() < h)
		{
			mask = new BufferedImage(Math.max(w, mask == null ? 0 : mask.getWidth()),
				Math.max(h, mask == null ? 0 : mask.getHeight()), BufferedImage.TYPE_INT_ARGB);
		}
		int[] px = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
		int stride = mask.getWidth();
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int i = y * w + x;
				px[y * stride + x] = occ[i] < cape[i] - bias ? 0 : 0xFF000000;
			}
		}
		return mask;
	}
}
