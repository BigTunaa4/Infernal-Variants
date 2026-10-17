package com.infernalvariants;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Smaller copies of each cape texture (half, quarter, eighth size), each pixel the average of the four
 * below it. When the cape is small on screen (zoomed out, or far away) the matching copy is drawn, so
 * the design keeps its look instead of breaking up into flickering, jagged pixels.
 */
final class Mips
{
	/** Smallest copy kept, in pixels across. */
	private static final int MIN_SIZE = 8;

	private final Map<BufferedImage, BufferedImage[]> cache = new WeakHashMap<>();

	/** The texture and its smaller copies, largest first. */
	BufferedImage[] of(BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}
		return cache.computeIfAbsent(image, Mips::build);
	}

	void clear()
	{
		cache.clear();
	}

	/**
	 * Which copy to draw for a triangle covering this many texture pixels and this many screen pixels:
	 * about one texture pixel per screen pixel.
	 */
	static int level(float texArea, float screenArea, int levels)
	{
		if (screenArea <= 0 || texArea <= 0)
		{
			return levels - 1;
		}
		double texelsPerPixel = Math.sqrt(texArea / screenArea);
		int level = texelsPerPixel <= 1 ? 0 : (int) Math.floor(Math.log(texelsPerPixel) / Math.log(2));
		return Math.max(0, Math.min(levels - 1, level));
	}

	static BufferedImage[] build(BufferedImage image)
	{
		java.util.List<BufferedImage> out = new java.util.ArrayList<>();
		out.add(image);
		BufferedImage cur = image;
		while (cur.getWidth() / 2 >= MIN_SIZE && cur.getHeight() / 2 >= MIN_SIZE)
		{
			cur = half(cur);
			out.add(cur);
		}
		return out.toArray(new BufferedImage[0]);
	}

	/** Half the size: each pixel averages a 2x2 block, weighted by alpha so glows don't darken at the edges. */
	static BufferedImage half(BufferedImage src)
	{
		int w = src.getWidth() / 2, h = src.getHeight() / 2;
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int a = 0, r = 0, g = 0, b = 0;
				for (int dy = 0; dy < 2; dy++)
				{
					for (int dx = 0; dx < 2; dx++)
					{
						int p = src.getRGB(2 * x + dx, 2 * y + dy);
						int pa = p >>> 24;
						a += pa;
						r += ((p >> 16) & 0xFF) * pa;
						g += ((p >> 8) & 0xFF) * pa;
						b += (p & 0xFF) * pa;
					}
				}
				int argb = 0;
				if (a > 0)
				{
					argb = ((a / 4) << 24) | ((r / a) << 16) | ((g / a) << 8) | (b / a);
				}
				dst.setRGB(x, y, argb);
			}
		}
		return dst;
	}
}
