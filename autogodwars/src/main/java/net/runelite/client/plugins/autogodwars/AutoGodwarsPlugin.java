/*
 * Copyright (c) 2019, Ganom <https://github.com/Ganom>
 * Copyright (c) 2019, Xkylee <https://github.com/xKylee>
 * HUGE ThANK YOU TO THE BOTH OF THEM
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.autogodwars;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsEnum.RestorePrayer.PRAYER_POTION;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsEnum.RestorePrayer.SANFEW_SERUM;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsEnum.RestorePrayer.SUPER_RESTORE;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.ARMA_REGION;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.Armadyl_Altar;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.Bandos_Altar;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.GENERAL_REGION;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.SARA_REGION;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.Saradomin_Altar;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.ZAMMY_REGION;
import static net.runelite.client.plugins.autogodwars.AutoGodwarsVariable.Zamorak_Altar;
import static net.runelite.client.plugins.autogodwars.NPCContainer.BossMonsters.GENERAL_GRAARDOR;
import static net.runelite.client.plugins.autogodwars.NPCContainer.BossMonsters.KRIL_TSUTSAROTH;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.PrayerUtils;
import net.runelite.client.plugins.iutils.game.Game;
import net.runelite.client.plugins.iutils.iUtils;
import org.pf4j.Extension;

@Extension
@Slf4j
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "Auto Godwars",
	description = "Auto Godwars",
	tags = {"Auto", "Godwars", "Willemmmo"}
)
public class AutoGodwarsPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private AutoGodwarsConfig config;
	@Inject
	private AutoGodwarsHotkeyListener hotkeyListener;
	@Inject
	private AutoGodwarsFunction autoGodwarsFunction;
	@Inject
	private getStates getStates;
	@Inject
	private KeyManager keyManager;
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPCContainer> npcContainers = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<AutoGodwarsEnum.Stamina> staminaContainer = new HashSet<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<GameObjectContainer> gameObjectContainers = new HashSet<>();
	protected boolean enableAutoPrayers = false;
	private boolean validRegion;
	@Getter(AccessLevel.PACKAGE)
	private long lastTickTime;
	@Inject
	private iUtils utils;
	@Inject
	private Game game;
	@Inject
	private PrayerUtils prayerUtils;
	@Inject
	private InventoryUtils inventoryUtils;


	@Provides
	AutoGodwarsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AutoGodwarsConfig.class);
	}

	@Override
	public void startUp()
	{
		keyManager.registerKeyListener(hotkeyListener);
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		autoGodwarsFunction.regionCheck();
		if (autoGodwarsFunction.regionCheck())
		{
			npcContainers.clear();
			for (NPC npc : client.getNpcs())
			{
				addNpc(npc);
			}
			validRegion = true;
		}
		else if (!autoGodwarsFunction.regionCheck())
		{
			validRegion = false;
			npcContainers.clear();
			gameObjectContainers.clear();
		}
	}

	@Override
	public void shutDown()
	{
		npcContainers.clear();
		gameObjectContainers.clear();
		validRegion = false;
		keyManager.unregisterKeyListener(hotkeyListener);
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		staminaContainer.clear();
		if (autoGodwarsFunction.regionCheck())
		{
			npcContainers.clear();
			for (NPC npc : client.getNpcs())
			{
				addNpc(npc);
			}
			validRegion = true;
		}
		else if (!autoGodwarsFunction.regionCheck())
		{
			validRegion = false;
			npcContainers.clear();
		}
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event)
	{
		if (!validRegion)
		{
			return;
		}
		addNpc(event.getNpc());
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			log.info("Inventory did change");
		}
		ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
		staminaContainer.clear();
		for (Item item : itemContainer.getItems())
		{
			AutoGodwarsEnum.Stamina stamina = AutoGodwarsEnum.Stamina.of(item.getId());
			if (stamina != null)
			{
				log.info("Adding to container : " + item.getId());
				staminaContainer.add(stamina);
			}
		}
		if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
			{
				for (Item item : container.getItems())
				{
					ItemComposition composition = client.getItemComposition(item.getId());
				}
			}
		}
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned event)
	{
		if (!validRegion)
		{
			return;
		}
		removeNpc(event.getNpc());
	}

	@Subscribe
	private void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!validRegion)
		{
			return;
		}
		addGameObject(event.getGameObject());
	}

	@Subscribe
	private void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!validRegion)
		{
			return;
		}
		removeGameObject(event.getGameObject());
	}

	@Subscribe
	private void onChatMessage(ChatMessage event)
	{
		if (event == null)
		{
			return;
		}
		autoGodwarsFunction.handleChatEvent(event);
	}


	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (event == null)
		{
			log.info("TickEvent is Empty");
		}
		lastTickTime = System.currentTimeMillis();
		if (config.allowWorldHop() && npcContainers.isEmpty())
		{
			autoGodwarsFunction.checkForWorldHop();
		}
		if (config.debug())
		{
			doDebugFunction();
		}
		if (!validRegion)
		{
			return;
		}
		loadBossesIn();
		//::todo
		if (config.enableAutoEat() || config.restorePrayer())
		{
			checkPlayerState();
		}
		switch (client.getLocalPlayer().getWorldLocation().getRegionID())
		{
			case ARMA_REGION:
				if (config.enableAutoPrayArma() && config.enableArma())
				{
					checkForPrayers();
				}
				break;
			case ZAMMY_REGION:
				if (config.enableAutoPrayZammy() && config.enableZammy())
				{
					checkForPrayers();
				}
				break;
			case SARA_REGION:
				if (config.enableAutoPraySara() && config.enableSara())
				{
					checkForPrayers();
				}
				break;
			case GENERAL_REGION:
				if (config.enableAutoPrayBandos() && config.enableBandos())
				{
					checkForPrayers();
				}
				break;
		}
	}

	private void doDebugFunction()
	{
		AutoGodwarsEnum.Stamina stamina = AutoGodwarsEnum.Stamina.of(ItemID.STAMINA_POTION1);
		if (stamina == null)
		{
			log.info("notfound stamina");
		}
		if (npcContainers.isEmpty() && validRegion)
		{
			log.info("No monster found... to execute this function walk to any godwars region");
		}
		if (!npcContainers.isEmpty())
		{
			for (NPCContainer npc : getNpcContainers())
			{
				if (npc.getNpc() == null)
				{
					continue;
				}
				int ticksLeft = npc.getTicksUntilAttack();
				NPCContainer.AttackStyle attackStyle = npc.getAttackStyle();
				if (ticksLeft <= 0)
				{
					continue;
				}
				if (config.ignoreNonAttacking() && npc.getNpcInteracting() != client.getLocalPlayer() && npc.getMonsterType() != GENERAL_GRAARDOR && npc.getMonsterType() != KRIL_TSUTSAROTH)
				{
					continue;
				}
				if (npc.getMonsterType() == KRIL_TSUTSAROTH && npc.getNpcInteracting() != client.getLocalPlayer() && config.prioritiseMage())
				{
					attackStyle = NPCContainer.AttackStyle.MAGE;
				}
				if (npc.getMonsterType() == GENERAL_GRAARDOR && npc.getNpcInteracting() != client.getLocalPlayer() && config.prioritiseRange())
				{
					attackStyle = NPCContainer.AttackStyle.RANGE;
				}
				if (ticksLeft == 1)//this function switches {
				{
					if (attackStyle.getName().equals("Melee") && config.debug())
					{
						log.info("Melee hit incoming from " + npc.getNpcName());
					}
					if (attackStyle.getName().equals("Range") && config.debug())
					{
						log.info("Range Hit incoming from " + npc.getNpcName());
					}
					if (attackStyle.getName().equals("Mage") && config.debug())
					{
						log.info("Magic Hit incoming from " + npc.getNpcName());
					}
				}
			}
		}
		if (config.enableAutoEat())
		{
			handleEatFood();
			if (inventoryUtils.containsItem(ItemID.COOKED_KARAMBWAN))
			{
				checkPlayerState();
			}
			if (!inventoryUtils.containsItem(ItemID.COOKED_KARAMBWAN))
			{
				log.info("To test the eat Function, grab a Cooked Karambwan else it doesnt work");
			}
		}
		if (config.cureVenomPoison())
		{
			HandlePoison();
		}
		if (config.restorePrayer())
		{
			handleDrinkPrayer();
		}

	}

	private void checkPlayerState()
	{
		PlayerStates playerStates = getStates.getPlayerStates();
		switch (playerStates)
		{
			case EAT_FOOD:
				if (config.debug())
				{
					inventoryUtils.interactWithItem(ItemID.COOKED_KARAMBWAN, game.sleepDelay(), "Eat");
				}
				handleEatFood();
				break;
			case DRINK_PRAYER:
				if (config.debug())
				{
					handleDrinkPrayer();
				}
				handleDrinkPrayer();
				break;
			case FUNCTION_FOUND:
				break;
			case ERROR:
				log.info("There is an error");
				break;
			case POISON:
				HandlePoison();
				break;
		}
	}

	private void handleEatFood()
	{
		for (AutoGodwarsEnum.Food food : AutoGodwarsEnum.Food.values())
		{
			int[] list = AutoGodwarsEnum.Food.getIDs(food.getItemID()).clone();
			log.info(String.valueOf(list));
		}
	}

	private void HandlePoison()
	{
		int[] sanfewID = AutoGodwarsEnum.Antivenom.SANFEW_SERUM.getIds();
		for (AutoGodwarsEnum.Antivenom antivenom : AutoGodwarsEnum.Antivenom.values())
		{
			if (inventoryCheck(sanfewID) && config.poisonCheckPrayer())
			{
				double statRestore = (client.getRealSkillLevel(Skill.PRAYER) * 0.3) + 4;
				if (antivenom.name().equals(AutoGodwarsEnum.Antivenom.SANFEW_SERUM.name()) && client.getBoostedSkillLevel(Skill.PRAYER) < client.getRealSkillLevel(Skill.PRAYER) - Math.floor(statRestore))
				{
					for (int doseID : antivenom.getIds())
					{
						if (inventoryUtils.containsItem(doseID))
						{
							inventoryUtils.interactWithItem(doseID, game.tickDelay(), "Drink");
							return;
						}
					}
				}
			}
			if (!config.poisonCheckPrayer() || !inventoryCheck(sanfewID))
			{
				for (int doseID : antivenom.getIds())
				{
					if (inventoryUtils.containsItem(doseID))
					{
						inventoryUtils.interactWithItem(doseID, game.tickDelay(), "Drink");
						return;
					}
				}
			}
		}
	}

	public boolean inventoryCheck(int[] array)
	{
		for (int i : array)
		{
			if (inventoryUtils.containsItem(i))
			{
				return true;
			}
		}
		return false;
	}


	private void handleDrinkPrayer()
	{
		for (AutoGodwarsEnum.RestorePrayer restorePrayer : AutoGodwarsEnum.RestorePrayer.values())
		{
			if (inventoryCheck(restorePrayer.getIds()))
			{
				log.info(restorePrayer.name());
				double statRestore = 0;
				if (restorePrayer.name().equals(SANFEW_SERUM.name()))
				{
					statRestore = (client.getRealSkillLevel(Skill.PRAYER) * 0.3) + 4;
				}
				if (restorePrayer.name().equals(SUPER_RESTORE.name()))
				{
					statRestore = (client.getRealSkillLevel(Skill.PRAYER) * 0.27) + 8;
				}
				if (restorePrayer.name().equals(PRAYER_POTION.name()))
				{
					statRestore = (client.getRealSkillLevel(Skill.PRAYER) * 0.25) + 7;
				}
				if (client.getBoostedSkillLevel(Skill.PRAYER) < client.getRealSkillLevel(Skill.PRAYER) - Math.floor(statRestore) && statRestore > 0)
				{
					for (int doseID : restorePrayer.getIds())
					{
						if (inventoryUtils.containsItem(doseID))
						{
							inventoryUtils.interactWithItem(doseID, game.sleepDelay(), "Drink");
							return;
						}
					}
				}
			}
		}
	}

	private void loadBossesIn()
	{
		for (NPCContainer npc : getNpcContainers())
		{
			npc.setNpcInteracting(npc.getNpc().getInteracting());
			if (npc.getTicksUntilAttack() >= 0)
			{
				npc.setTicksUntilAttack(npc.getTicksUntilAttack() - 1);
			}
			for (int animation : npc.getAnimations())
			{
				if (animation == npc.getNpc().getAnimation() && npc.getTicksUntilAttack() < 1)
				{
					npc.setTicksUntilAttack(npc.getAttackSpeed());
				}
			}
		}
	}

	private boolean isFocusAlive(String Name)
	{
		AutoGodwarsEnum.Focus focus = AutoGodwarsEnum.Focus.valueOf(Name);
		for (NPCContainer npcContainer : getNpcContainers())
		{
			if (npcContainer.getID() == focus.getId())
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	private NPC fetchFirstNPC()
	{
		if (!getNpcContainers().isEmpty())
		{
			return Objects.requireNonNull(getNpcContainers().stream().findFirst().orElse(null)).getNpc();
		}
		return null;
	}

	@Nullable
	private NPC fetchNPC(String Name)
	{
		AutoGodwarsEnum.Focus focus = AutoGodwarsEnum.Focus.valueOf(Name);
		for (NPCContainer npcContainer : getNpcContainers())
		{
			if (npcContainer.getID() == focus.getId())
			{
				return npcContainer.getNpc();
			}
		}
		return null;
	}

	private void SendAttackOrder(NPC npc)
	{
		Player player = client.getLocalPlayer();
		if (npc == null)
		{
			return;
		}
		log.info("Setting kill order");
		if (player.getInteracting() == null || !Objects.equals(player.getInteracting().getName(), npc.getName()))
		{
			utils.doNpcActionMsTime(npc, MenuAction.NPC_SECOND_OPTION.getId(), game.sleepDelay());
		}
	}

	@Nullable
	private WorldPoint findAltar(int Altar_ID)
	{
		WorldPoint worldPoint = null;

		for (GameObjectContainer obj : getGameObjectContainers())
		{
			if (obj.getGameObjectID() == Altar_ID)
			{
				GameObject altar = obj.getGameObject();
				if (altar == null)
				{
					return null;
				}
				worldPoint = altar.getWorldLocation();
			}
		}
		return worldPoint;
	}

	private void defaultTasks()
	{
		//Poison
		int PoisonValue = game.varp(VarPlayer.IS_POISONED.getId()); //<-40 Antivenom is still running //0 = nothing running //1000000 just got it//44+43 = 9 splat 11:50 left is 40
		//int PoisonVarp = game.varp(VarPlayer.POISON.getId()); // dit is1 op 1 de zelfde value
		if (PoisonValue > 0)
		{
			//AutoGodwarsEnum.Food.ANGLERFISH.getId();
			inventoryUtils.interactWithItem(391, game.sleepDelay(), "eat");
		}

	}

	private void addNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		if (config.debug())
		{
			NPCContainer.BossMonsters monster = NPCContainer.BossMonsters.of(npc.getId());
			if (monster == null)
			{
				return;
			}
			log.info("Adding this npc to the list caused by debug function : " + npc.getName());
			npcContainers.add(new NPCContainer(npc));
		}
		switch (npc.getId())
		{
			//adding selected npc's to the list depending on config
			case NpcID.FLIGHT_KILISA:
			case NpcID.FLOCKLEADER_GEERIN:
			case NpcID.KREEARRA:
			case NpcID.WINGMAN_SKREE:
				if (config.enableArma())
				{
					npcContainers.add(new NPCContainer(npc));
				}
			case NpcID.GENERAL_GRAARDOR:
			case NpcID.SERGEANT_GRIMSPIKE:
			case NpcID.SERGEANT_STEELWILL:
			case NpcID.SERGEANT_STRONGSTACK:
				if (config.enableBandos())
				{
					npcContainers.add(new NPCContainer(npc));
				}
			case NpcID.BREE:
			case NpcID.COMMANDER_ZILYANA:
			case NpcID.GROWLER:
			case NpcID.STARLIGHT:
				if (config.enableSara())
				{
					npcContainers.add(new NPCContainer(npc));
				}
			case NpcID.BALFRUG_KREEYATH:
			case NpcID.KRIL_TSUTSAROTH:
			case NpcID.TSTANON_KARLAK:
			case NpcID.ZAKLN_GRITCH:
				if (config.enableZammy())
				{
					npcContainers.add(new NPCContainer(npc));
				}
				break;
		}
	}

	private void addGameObject(GameObject gameObject)
	{
		if (gameObject == null)
		{
			return;
		}
		switch (gameObject.getId())
		{
			case Armadyl_Altar:
				if (config.enableArma())
				{
					gameObjectContainers.add(new GameObjectContainer(gameObject));
				}
			case Bandos_Altar:
				if (config.enableArma())
				{
					gameObjectContainers.add(new GameObjectContainer(gameObject));
				}
			case Saradomin_Altar:
				if (config.enableArma())
				{
					gameObjectContainers.add(new GameObjectContainer(gameObject));
				}
			case Zamorak_Altar:
				if (config.enableArma())
				{
					gameObjectContainers.add(new GameObjectContainer(gameObject));
				}
				break;
		}
	}

	private void removeNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		switch (npc.getId())
		{
			case NpcID.SERGEANT_STRONGSTACK:
			case NpcID.SERGEANT_STEELWILL:
			case NpcID.SERGEANT_GRIMSPIKE:
			case NpcID.GENERAL_GRAARDOR:
			case NpcID.TSTANON_KARLAK:
			case NpcID.BALFRUG_KREEYATH:
			case NpcID.ZAKLN_GRITCH:
			case NpcID.KRIL_TSUTSAROTH:
			case NpcID.STARLIGHT:
			case NpcID.BREE:
			case NpcID.GROWLER:
			case NpcID.COMMANDER_ZILYANA:
			case NpcID.FLIGHT_KILISA:
			case NpcID.FLOCKLEADER_GEERIN:
			case NpcID.WINGMAN_SKREE:
			case NpcID.KREEARRA:
				npcContainers.removeIf(c -> c.getNpc() == npc);
				break;
		}
	}

	private void removeGameObject(GameObject gameobject)
	{
		if (gameobject == null)
		{
			return;
		}
		switch (gameobject.getId())
		{
			case Armadyl_Altar:
			case Bandos_Altar:
			case Saradomin_Altar:
			case Zamorak_Altar:
				gameObjectContainers.removeIf(c -> c.getGameObject() == gameobject);
				break;
		}
	}

	private void infoMessage(String Message)
	{
		if (!Objects.equals(Message, ""))
		{
			log.info(Message);
		}
	}
	private void checkForPrayers()
	{
		PrayerEnum state = getStates.shouldSwitchPrayers(getNpcContainers());
		switch (state)
		{
			case NONE:
				break;
			case PROTECT_FROM_MISSILES:
				setPrayerActive(Prayer.PROTECT_FROM_MISSILES);
				break;
			case PROTECT_FROM_MELEE:
				setPrayerActive(Prayer.PROTECT_FROM_MELEE);
				break;
			case PROTECT_FROM_MAGIC:
				setPrayerActive(Prayer.PROTECT_FROM_MAGIC);
				break;
			case DISABLE:
				autoGodwarsFunction.disableAllPrayer();
				break;
		}
	}
	private void setPrayerActive(Prayer prayer)
	{
		if (prayerUtils.isActive(prayer) || !enableAutoPrayers)
		{
			return;
		}
		if (enableAutoPrayers && !prayerUtils.isActive(prayer))
		{
			prayerUtils.toggle(prayer, game.sleepDelay());
		}
	}
	//THIS FUNCTION IS TO FIND ANY NPC WITHIN THE NPC CONTAINER
	//          List<NPCContainer> found = npcContainers.stream().filter(x -> x.getID() == GENERAL_GRAARDOR.getNpcID()).collect(Collectors.toList());
	//			Set<NPCContainer> BandosFound = npcContainers.stream().filter(x -> x.getID() == NpcID.GENERAL_GRAARDOR).collect(Collectors.toSet());
	//			log.info("First Result" + BandosFound.stream().findFirst().get().getNpcName());
	//			NPCContainer.BossMonsters npc = NPCContainer.BossMonsters.of(GENERAL_GRAARDOR.getNpcID());
	//			if (npc != null)
	//			{
	//				log.info("id:" + npc.getNpcID());
	//				log.info("Name:" + npc.name());
	//				log.info("AttackStyle:" + npc.getAttackStyle());
	//				npc.getAnimations().forEach(x -> log.info(x.toString()));
	//			}
	//			if (npcContainers.stream().anyMatch(x -> x.getID() == GENERAL_GRAARDOR.getNpcID()))
	//			{
	//				log.info("we have found bandos");
	//			}
}
