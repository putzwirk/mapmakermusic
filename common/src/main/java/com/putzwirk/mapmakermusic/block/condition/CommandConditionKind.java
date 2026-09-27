package com.putzwirk.mapmakermusic.block.condition;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.network.ConditionTestNet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CommandConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "command");
	public static final String COMMAND_KEY = "command";
	public static final String TEST_ACTION = "test";
	private static final ConditionEvalCache CACHE = new ConditionEvalCache();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Command";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		ServerPlayer player = ctx.player();
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		String command = ConditionParams.str(condition.params(), COMMAND_KEY, "").trim();
		if (command.isEmpty()) {
			return false;
		}
		String key = player.getUUID() + "\n" + command;
		return CACHE.get(server, server.getTickCount(), key, () -> runNow(player, command));
	}

	@Override
	public String describe(MusicCondition condition) {
		String command = ConditionParams.str(condition.params(), COMMAND_KEY, "").trim();
		if (command.isEmpty()) {
			return "Command";
		}
		String shown = command.length() > 24 ? command.substring(0, 24) + "..." : command;
		return "Run " + shown;
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(COMMAND_KEY, "");
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(
				FieldSpec.longText(COMMAND_KEY, "Command", "command to test, without leading slash", "command"),
				FieldSpec.action(TEST_ACTION, "Test"),
				FieldSpec.presets());
	}

	@Override
	public int editorWidth() {
		return 420;
	}

	@Override
	public int editorHeight() {
		return 290;
	}

	@Override
	public List<String> editorNotes() {
		return List.of();
	}

	@Override
	public List<Preset> editorPresets() {
		return List.of(
				new Preset("Health", "execute if data entity @s {Health:20.0f}"),
				new Preset("Hunger", "execute if data entity @s {foodLevel:0}"),
				new Preset("Name", "execute if entity @s[name=Steve]"),
				new Preset("Score", "execute if score @s kills matches 10.."),
				new Preset("Day", "mmcheck time day"),
				new Preset("Night", "mmcheck time night"),
				new Preset("Weather", "mmcheck weather clear"),
				new Preset("Biome", "mmcheck biome minecraft:plains"),
				new Preset("Entities", "execute if entity @e[type=minecraft:cow,distance=..30]"),
				new Preset("Item", "clear @s minecraft:diamond 0"),
				new Preset("GameMode", "execute if entity @s[gamemode=creative]"),
				new Preset("Team", "execute if entity @s[team=Red]"),
				new Preset("Holding", "execute if data entity @s {SelectedItem:{id:\"minecraft:torch\"}}"));
	}

	@Override
	public String runAction(String actionId, MusicCondition condition, Minecraft minecraft) {
		if (!TEST_ACTION.equals(actionId)) {
			return null;
		}
		String command = ConditionParams.str(condition.params(), COMMAND_KEY, "").trim();
		if (command.isEmpty()) {
			return null;
		}
		ConditionTestNet.requestTest(command);
		return null;
	}

	public static String playerNameCommand(String name) {
		return "execute if entity @s[name=" + name + "]";
	}

	public static boolean runNow(ServerPlayer player, String command) {		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(2);
		return dispatch(server.getCommands().getDispatcher(), source, command);
	}

	static boolean dispatch(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command) {
		String input = command.startsWith("/") ? command.substring(1) : command;
		try {
			return dispatcher.execute(input, source) > 0;
		} catch (CommandSyntaxException e) {
			warnOnce(command, e.getMessage());
			return false;
		} catch (RuntimeException e) {
			warnOnce(command, e.toString());
			return false;
		}
	}

	private static void warnOnce(String command, String detail) {
		if (WARNED.add(command)) {
			Constants.LOG.warn("Condition command failed: '{}' ({})", command, detail);
		}
	}
}
