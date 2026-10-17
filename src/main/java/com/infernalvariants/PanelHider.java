package com.infernalvariants;

import net.runelite.api.Model;

/**
 * Hides the real lava panel of a cape for one frame, so only the repainted design shows.
 * <p>
 * The design is painted over the scene after it is drawn. Painting over the lava panel left it
 * showing through wherever the two didn't line up exactly (a frame of animation apart, or a
 * triangle the paint skipped). Instead, just before each frame is drawn the panel's faces are
 * marked hidden (the game skips any face whose third colour is -2, in both the normal and the GPU
 * renderer), and they are put back as soon as the frame is done. The player's model in the game's
 * cache is never left changed: if anything goes wrong, the next frame restores it first.
 */
final class PanelHider
{
	private static final int HIDDEN = -2;

	/** Every colour array changed this frame, with the face and the colour it had. */
	private int[][] arrays = new int[16][];
	private int[] faces = new int[16];
	private int[] colors = new int[16];
	private int count;

	/** Hides every lava-textured face of this model until {@link #restore()}. */
	void hide(Model model)
	{
		if (model == null)
		{
			return;
		}
		short[] textures = model.getFaceTextures();
		int[] colors3 = model.getFaceColors3();
		if (textures == null || colors3 == null)
		{
			return;
		}
		int n = Math.min(model.getFaceCount(), Math.min(textures.length, colors3.length));
		for (int f = 0; f < n; f++)
		{
			if (textures[f] == InfernalVariantsPlugin.INFERNAL_TEXTURE && colors3[f] != HIDDEN)
			{
				remember(colors3, f);
				colors3[f] = HIDDEN;
			}
		}
	}

	/** Puts back every face hidden since the last restore. */
	void restore()
	{
		for (int i = count - 1; i >= 0; i--)
		{
			int[] a = arrays[i];
			if (faces[i] < a.length && a[faces[i]] == HIDDEN)
			{
				a[faces[i]] = colors[i];
			}
			arrays[i] = null;
		}
		count = 0;
	}

	boolean isHiding()
	{
		return count > 0;
	}

	private void remember(int[] array, int face)
	{
		if (count == arrays.length)
		{
			arrays = java.util.Arrays.copyOf(arrays, count * 2);
			faces = java.util.Arrays.copyOf(faces, count * 2);
			colors = java.util.Arrays.copyOf(colors, count * 2);
		}
		arrays[count] = array;
		faces[count] = face;
		colors[count] = array[face];
		count++;
	}
}
