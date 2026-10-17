package com.infernalvariants;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.TextureProvider;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Infernal Variants",
	description = "Custom infernal cape designs (Holy, Ice, Dragonfire, Rune, Void and more) with matching cape effects",
	tags = {"infernal", "cape", "cosmetic", "recolor", "recolour", "fire", "inferno", "effects"}
)
public class InfernalVariantsPlugin extends Plugin
{
	/** The game's own infernal cape lava texture. */
	static final int INFERNAL_TEXTURE = 59;

	private static final int[] CAPE_IDS = {
		ItemID.INFERNAL_CAPE,
		ItemID.BR_INFERNAL_CAPE,
		ItemID.INFERNAL_CAPE_TROUVER,
		ItemID.INFERNAL_CAPE_TROUVER_BROKEN,
		ItemID.INFERNAL_CAPE_TROUVER_MANGLED,
	};

	private static final int MAX_CAPE_ID = ItemID.SKILLCAPE_MAX_INFERNALCAPE;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CapeOverlay overlay;

	@Inject
	private InfernalVariantsConfig config;

	private float[] heat;
	private final Map<CapeStyle, BufferedImage> veinTextures = new EnumMap<>(CapeStyle.class);
	private final Map<CapeStyle, BufferedImage> hemTextures = new EnumMap<>(CapeStyle.class);
	private final Map<CapeStyle, BufferedImage[]> emblemSprites = new EnumMap<>(CapeStyle.class);

	/** Players whose cape we've put a colour override on, and the style it carries. */
	private final Map<Player, CapeStyle> applied = new WeakHashMap<>();

	@Provides
	InfernalVariantsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InfernalVariantsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			// re-load the cape item definitions so our recolour hook sees them
			client.getItemCompositionCache().reset();
			loadHeat();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlay.reset();
		clientThread.invoke(() ->
		{
			for (Map.Entry<Player, CapeStyle> e : applied.entrySet())
			{
				removeOverride(e.getKey());
			}
			applied.clear();
			client.getItemCompositionCache().reset();
		});
		veinTextures.clear();
		hemTextures.clear();
		emblemSprites.clear();
		heat = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!InfernalVariantsConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		// styles are re-applied on the next client tick; clear particles so a style switch is instant
		overlay.reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			applied.clear();
			overlay.reset();
		}
	}

	@Subscribe
	public void onPostItemComposition(PostItemComposition e)
	{
		ItemComposition item = e.getItemComposition();
		if (!isInfernal(item.getId()))
		{
			return;
		}
		// Register the cape's model colours as recolourable (mapped to themselves), so a per-player
		// colour override can repaint them. On its own this changes nothing visually.
		item.setColorToReplace(InfernalColors.ALL.clone());
		item.setColorToReplaceWith(InfernalColors.ALL.clone());
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		if (heat == null)
		{
			loadHeat();
		}

		for (Player p : client.getTopLevelWorldView().players())
		{
			if (p == null)
			{
				continue;
			}
			CapeStyle want = styleFor(p);
			CapeStyle have = applied.get(p);
			if (want == null || want.isOriginal())
			{
				if (have != null)
				{
					removeOverride(p);
					applied.remove(p);
				}
				continue;
			}
			applyOverride(p, want);
		}
	}

	private void applyOverride(Player p, CapeStyle style)
	{
		PlayerComposition pc = p.getPlayerComposition();
		if (pc == null)
		{
			return;
		}
		int itemId = pc.getEquipmentId(KitType.CAPE);
		ColorTextureOverride ov = pc.getColorTextureOverride(KitType.CAPE);
		if (ov == null)
		{
			ov = pc.createColorTextureOverride(KitType.CAPE, itemId);
		}
		if (ov == null)
		{
			return;
		}
		short[] with = ov.getColorToReplaceWith();
		if (with == null || with.length != InfernalColors.ALL.length)
		{
			// item definition was cached before our hook ran; it will be reloaded shortly
			return;
		}
		boolean changed = false;
		for (int i = 0; i < with.length; i++)
		{
			short c = CapeTextures.mapColor(style, InfernalColors.ALL[i]);
			if (with[i] != c)
			{
				with[i] = c;
				changed = true;
			}
		}
		if (changed)
		{
			pc.setHash();
		}
		applied.put(p, style);
	}

	private void removeOverride(Player p)
	{
		PlayerComposition pc = p.getPlayerComposition();
		if (pc != null && pc.getColorTextureOverride(KitType.CAPE) != null)
		{
			pc.removeColorTextureOverride(KitType.CAPE);
			pc.setHash();
		}
	}

	/** The style a player's cape should show, or null if they aren't wearing an infernal cape we handle. */
	CapeStyle styleFor(Player p)
	{
		PlayerComposition pc = p.getPlayerComposition();
		if (pc == null)
		{
			return null;
		}
		int itemId = pc.getEquipmentId(KitType.CAPE);
		if (!isInfernal(itemId) || (itemId == MAX_CAPE_ID && !config.maxCape()))
		{
			return null;
		}
		if (p == client.getLocalPlayer())
		{
			return config.style();
		}
		switch (config.others())
		{
			case SAME:
				return config.style();
			case RANDOM:
			{
				String name = p.getName();
				CapeStyle[] all = CapeStyle.values();
				int h = name == null ? 0 : (name.hashCode() & 0x7FFFFFFF);
				return all[1 + h % (all.length - 1)];
			}
			default:
				return null;
		}
	}

	static boolean isInfernal(int itemId)
	{
		if (itemId == MAX_CAPE_ID)
		{
			return true;
		}
		for (int id : CAPE_IDS)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		return false;
	}

	boolean isMaxCape(Player p)
	{
		PlayerComposition pc = p.getPlayerComposition();
		return pc != null && pc.getEquipmentId(KitType.CAPE) == MAX_CAPE_ID;
	}

	private void loadHeat()
	{
		TextureProvider tp = client.getTextureProvider();
		if (tp == null)
		{
			return;
		}
		int[] pixels = tp.load(INFERNAL_TEXTURE);
		if (pixels == null || pixels.length < CapeTextures.SIZE * CapeTextures.SIZE)
		{
			return;
		}
		heat = CapeTextures.heatMap(pixels, tp.getBrightness());
		veinTextures.clear();
		hemTextures.clear();
		emblemSprites.clear();
	}

	boolean texturesReady()
	{
		return heat != null;
	}

	BufferedImage veinTexture(CapeStyle style)
	{
		if (heat == null || style.isOriginal())
		{
			return null;
		}
		return veinTextures.computeIfAbsent(style, s -> CapeTextures.veinTexture(s, heat));
	}

	BufferedImage hemTexture(CapeStyle style)
	{
		if (heat == null || style.special != CapeStyle.Special.DRAGON)
		{
			return null;
		}
		return hemTextures.computeIfAbsent(style, s -> CapeTextures.hemTexture(s, heat));
	}

	BufferedImage[] emblems(CapeStyle style)
	{
		return emblemSprites.computeIfAbsent(style, s ->
		{
			BufferedImage[] arr = new BufferedImage[4];
			for (int i = 0; i < arr.length; i++)
			{
				arr[i] = CapeTextures.emblemSprite(s.emblem, i, s);
			}
			return arr;
		});
	}

	InfernalVariantsConfig getConfig()
	{
		return config;
	}
}
