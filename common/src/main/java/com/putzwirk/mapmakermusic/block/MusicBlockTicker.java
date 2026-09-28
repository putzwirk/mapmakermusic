package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.library.MusicLibrary;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicBlockTicker {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Ticker");

	private record BoxKey(String dimension, long pos) {
	}

	private record MatchKey(BoxKey box, UUID player) {
	}

	private static final class MatchMemo {
		long tick;
		MusicQueue queue;
	}

	private static final class PlaybackState {
		int queueIndex;
		int trackIndex;
		long startedTick;
		boolean sound;
		boolean stopped;
		boolean fadingGap;
		int nextTrack;
		List<Integer> order;
		int orderPos;
	}

	private static float fadeOutSeconds() {
		return com.putzwirk.mapmakermusic.client.audio.MusicPlayer.FADE_TICKS / 20f;
	}

	private static boolean fadeApplies(String track) {
		float duration = trackDuration(track);
		return duration < 0f || duration >= fadeOutSeconds();
	}

	private static final Map<BoxKey, Map<UUID, PlaybackState>> STATES = new ConcurrentHashMap<>();
	private static final Map<String, Set<Long>> CHAIN_BOXES = new ConcurrentHashMap<>();
	private static final Map<UUID, BoxKey> MUSIC_CLAIM = new ConcurrentHashMap<>();
	private static final Map<MatchKey, MatchMemo> MATCH_MEMO = new ConcurrentHashMap<>();

	private static MusicQueue matchMemo(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player) {
		MatchKey matchKey = new MatchKey(key, player.getUUID());
		long now = level.getGameTime();
		MatchMemo memo = MATCH_MEMO.get(matchKey);
		if (memo != null && memo.tick == now) {
			return memo.queue;
		}
		MusicQueue match = musicBe.findMatchingQueue(player);
		MatchMemo next = new MatchMemo();
		next.tick = now;
		next.queue = match;
		MATCH_MEMO.put(matchKey, next);
		return match;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MusicBlockEntity musicBe) {
		if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (musicBe.getTriggerMode() != MusicBlockEntity.TriggerMode.CHAIN) {
			return;
		}
		BoxKey key = keyOf(serverLevel, pos);
		CHAIN_BOXES.computeIfAbsent(key.dimension(), k -> ConcurrentHashMap.newKeySet()).add(key.pos());

		List<ServerPlayer> targets = musicBe.isAreaGate() ? playersInside(serverLevel, musicBe) : allPlayers(serverLevel);
		Set<UUID> targeted = ConcurrentHashMap.newKeySet();
		for (ServerPlayer player : targets) {
			targeted.add(player.getUUID());
			stepPlayer(serverLevel, key, musicBe, player);
		}

		Map<UUID, PlaybackState> states = STATES.get(key);
		if (states != null) {
			for (UUID uuid : new ArrayList<>(states.keySet())) {
				if (!targeted.contains(uuid)) {
					ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
					stopState(serverLevel, key, musicBe, uuid, player);
				}
			}
			if (states.isEmpty()) {
				STATES.remove(key);
			}
		}
	}

	public static void triggerRedstoneActivation(Level level, MusicBlockEntity musicBe) {
		if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!boxHasTracks(musicBe)) {
			return;
		}
		BoxKey key = keyOf(serverLevel, musicBe.getBlockPos());
		for (ServerPlayer player : resolveTargets(serverLevel, musicBe)) {
			MusicQueue queue = musicBe.findMatchingQueue(player);
			if (queue == null) {
				continue;
			}
			startFresh(serverLevel, key, musicBe, player, musicBe.getQueues().indexOf(queue), true);
		}
	}

	public static void onTrackFinished(ServerPlayer player, String trackKey) {
		if (MusicDebug.ENABLED) {
			LOGGER.info("[dbg] finished player={} track={}", player.getScoreboardName(), trackKey);
		}
		UUID uuid = player.getUUID();
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		String wanted = normalizeTrack(trackKey);
		for (Map.Entry<BoxKey, Map<UUID, PlaybackState>> entry : STATES.entrySet()) {
			PlaybackState state = entry.getValue().get(uuid);
			if (state == null || state.stopped || state.sound) {
				continue;
			}
			BlockEntity be = serverLevel.getBlockEntity(BlockPos.of(entry.getKey().pos()));
			if (!(be instanceof MusicBlockEntity musicBe)) {
				continue;
			}
			List<MusicQueue> queues = musicBe.getQueues();
			if (state.queueIndex < 0 || state.queueIndex >= queues.size()) {
				continue;
			}
			MusicQueue queue = queues.get(state.queueIndex);
			if (state.trackIndex < 0 || state.trackIndex >= queue.getTracks().size()) {
				continue;
			}
			String current = normalizeTrack(queue.getTracks().get(state.trackIndex).getTrack());
			if (!current.equals(wanted)) {
				continue;
			}
			if (trackDuration(queue.getTracks().get(state.trackIndex).getTrack()) >= 0f) {
				continue;
			}
			advanceTrack(serverLevel, entry.getKey(), musicBe, player, uuid, state, true, false);
		}
	}

	private static String normalizeTrack(String track) {
		String key = track == null ? "" : track.trim().toLowerCase(Locale.ROOT);
		if (key.endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}
		return key;
	}

	public static void onPlayerLogin(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		String dimension = serverLevel.dimension().location().toString();
		for (long pos : new ArrayList<>(chainBoxesIn(dimension))) {
			BlockEntity be = serverLevel.getBlockEntity(BlockPos.of(pos));
			if (be instanceof MusicBlockEntity musicBe && musicBe.getTriggerMode() == MusicBlockEntity.TriggerMode.CHAIN) {
				stepPlayer(serverLevel, keyOf(serverLevel, BlockPos.of(pos)), musicBe, player);
			}
		}
	}

	public static void forgetPlayer(UUID uuid) {
		for (Map<UUID, PlaybackState> states : STATES.values()) {
			states.remove(uuid);
		}
		MUSIC_CLAIM.entrySet().removeIf(entry -> entry.getKey().equals(uuid));
		MATCH_MEMO.keySet().removeIf(matchKey -> matchKey.player().equals(uuid));
	}

	public static void invalidateBox(Level level, BlockPos pos) {
		if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BoxKey key = keyOf(serverLevel, pos);
		STATES.remove(key);
		MUSIC_CLAIM.entrySet().removeIf(entry -> entry.getValue().equals(key));
		MATCH_MEMO.keySet().removeIf(matchKey -> matchKey.box().equals(key));
		PlaybackSaveData.get(serverLevel.getServer()).removeBox(PlaybackSaveData.boxId(key.dimension(), key.pos()));
		Set<Long> boxes = CHAIN_BOXES.get(key.dimension());
		if (boxes != null) {
			boxes.remove(key.pos());
		}
	}

	public static void removeBox(Level level, BlockPos pos) {
		if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BoxKey key = keyOf(serverLevel, pos);
		MinecraftServer server = serverLevel.getServer();
		Map<UUID, PlaybackState> states = STATES.get(key);
		if (states != null) {
			BlockEntity be = serverLevel.getBlockEntity(pos);
			boolean fade = be instanceof MusicBlockEntity musicBe && musicBe.isFadeOut();
			for (UUID uuid : new ArrayList<>(states.keySet())) {
				PlaybackState state = states.get(uuid);
				if (state == null || state.stopped) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(uuid);
				if (player == null) {
					continue;
				}
				if (state.sound) {
					MusicRemotes.getRemote().stopSound(player, fade);
				} else {
					MusicRemotes.getRemote().stopMusic(player, fade);
				}
			}
		}
		invalidateBox(level, pos);
	}

	private static void stepPlayer(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player) {
		UUID uuid = player.getUUID();
		MusicQueue desired = matchMemo(level, key, musicBe, player);
		if (desired != null && !audibleBox(musicBe, player)) {
			desired = null;
		}
		Map<UUID, PlaybackState> states = STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
		PlaybackState state = states.get(uuid);

		if (desired == null) {
			if (state != null && !state.stopped) {
				stopState(level, key, musicBe, uuid, player);
			}
			return;
		}

		int queueIndex = musicBe.getQueues().indexOf(desired);
		if (state != null && state.stopped && state.trackIndex < 0 && state.queueIndex == queueIndex) {
			return;
		}
		if (desired.getChannel() == MusicQueue.Channel.MUSIC && beatenBy(level, key, musicBe, player)) {
			if (state != null && !state.stopped) {
				stopState(level, key, musicBe, uuid, player);
			}
			return;
		}
		if (state == null || state.stopped || state.queueIndex != queueIndex) {
			if (!adoptSaved(level, key, musicBe, player, uuid, queueIndex)) {
				startFresh(level, key, musicBe, player, queueIndex, false);
			}
			return;
		}

		advanceClock(level, key, musicBe, player, uuid, state, true);
	}

	private static boolean adoptSaved(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, UUID uuid, int queueIndex) {
		PlaybackSaveData.Entry saved = PlaybackSaveData.get(level.getServer()).get(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid);
		if (saved == null || saved.queueIndex != queueIndex || !validEntry(musicBe, saved)) {
			return false;
		}
		PlaybackState state = new PlaybackState();
		state.queueIndex = saved.queueIndex;
		state.trackIndex = saved.trackIndex;
		state.startedTick = saved.startedTick;
		state.sound = musicBe.getQueues().get(saved.queueIndex).getChannel() == MusicQueue.Channel.SOUND;
		if (saved.order != null && saved.order.length == musicBe.getQueues().get(saved.queueIndex).getTracks().size()
				&& saved.orderPos >= 0 && saved.orderPos < saved.order.length
				&& saved.order[saved.orderPos] == saved.trackIndex) {
			state.order = new ArrayList<>(saved.order.length);
			for (int slot : saved.order) {
				state.order.add(slot);
			}
			state.orderPos = saved.orderPos;
		}
		STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(uuid, state);
		if (!catchUpClock(level, musicBe, player, uuid, state)) {
			return false;
		}
		float offset = Math.max(0f, (level.getGameTime() - state.startedTick) / 20f);
		startQueue(level, key, musicBe, player, state.queueIndex, state.trackIndex, offset, false, false, state.order, state.orderPos);
		return true;
	}

	private static boolean catchUpClock(ServerLevel level, MusicBlockEntity musicBe, ServerPlayer player, UUID uuid, PlaybackState state) {
		MusicQueue queue = musicBe.getQueues().get(state.queueIndex);
		if (state.fadingGap) {
			state.trackIndex = state.nextTrack;
			state.fadingGap = false;
			if (state.trackIndex < 0 || state.trackIndex >= queue.getTracks().size()) {
				settle(level, keyOf(level, musicBe.getBlockPos()), uuid, state.queueIndex);
				return false;
			}
		}
		for (int i = 0; i < 4096; i++) {
			float duration = trackDuration(queue.getTracks().get(state.trackIndex).getTrack());
			if (duration <= 0f) {
				state.startedTick = level.getGameTime();
				return true;
			}
			float elapsed = (level.getGameTime() - state.startedTick) / 20f;
			if (elapsed < duration) {
				return true;
			}
			int next;
			List<Integer> order = state.order;
			if (order != null) {
				int pos = state.orderPos + 1;
				if (pos >= order.size()) {
					if (!queue.isLoop()) {
						settle(level, keyOf(level, musicBe.getBlockPos()), uuid, state.queueIndex);
						return false;
					}
					order = MusicQueue.shuffledOrder(queue.getTracks().size());
					pos = 0;
				}
				state.order = new ArrayList<>(order);
				state.orderPos = pos;
				next = order.get(pos);
			} else {
				next = state.trackIndex + 1;
				if (next >= queue.getTracks().size()) {
					if (!queue.isLoop()) {
						settle(level, keyOf(level, musicBe.getBlockPos()), uuid, state.queueIndex);
						return false;
					}
					next = 0;
				}
			}
			if (queue.getTracks().get(next).isStop()) {
				settle(level, keyOf(level, musicBe.getBlockPos()), uuid, state.queueIndex);
				return false;
			}
			state.trackIndex = next;
			state.startedTick += (long) (duration * 20f);
		}
		return true;
	}

	private static void startQueue(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, int queueIndex, int trackIndex, float offsetSeconds, boolean fresh, boolean stealClaim, List<Integer> order, int orderPos) {
		List<MusicQueue> queues = musicBe.getQueues();
		if (queueIndex < 0 || queueIndex >= queues.size()) {
			return;
		}
		MusicQueue queue = queues.get(queueIndex);
		if (queue.getTracks().isEmpty()) {
			return;
		}
		if (trackIndex < 0 || trackIndex >= queue.getTracks().size()) {
			trackIndex = 0;
		}
		MusicQueue.PlaylistItem item = queue.getTracks().get(trackIndex);
		UUID uuid = player.getUUID();
		boolean sound = queue.getChannel() == MusicQueue.Channel.SOUND;

		if (item.isStop()) {
			if (sound) {
				MusicRemotes.getRemote().stopSound(player, queue.isFadeOut());
			} else {
				MusicRemotes.getRemote().stopMusic(player, queue.isFadeOut());
				MUSIC_CLAIM.remove(uuid, key);
			}
			settle(level, key, uuid, queueIndex);
			return;
		}

		if (!sound) {
			BoxKey claim = MUSIC_CLAIM.get(uuid);
			if (claim != null && !claim.equals(key) && !stealClaim) {
				return;
			}
			if (musicBe.isAreaGate() && blockedBySmaller(level, key, musicBe, player)) {
				return;
			}
			MUSIC_CLAIM.put(uuid, key);
		}

		int volume = item.getVolume() != null ? item.getVolume() : musicBe.getVolume();
		float pitch = item.getPitch() != null ? item.getPitch() : musicBe.getPitch();
		long now = level.getGameTime();

		if (MusicDebug.ENABLED) {
			LOGGER.info("[dbg] start box={} player={} qi={} ti={} track={} offset={} fadeIn={} loop={} sound={} dur={}",
					key.pos(), player.getScoreboardName(), queueIndex, trackIndex, item.getTrack(), offsetSeconds,
					fresh && queue.isFadeIn(), queue.isLoop(), sound, trackDuration(item.getTrack()));
		}

		if (sound) {
			MusicRemotes.getRemote().playSound(player, item.getTrack(), volume, pitch, playbackAt(musicBe), musicBe.getRadius(),
					queue.isFadeIn() && fadeApplies(item.getTrack()));
		} else {
			boolean fadeIn = fresh && queue.isFadeIn() && fadeApplies(item.getTrack());
			MusicRemotes.getRemote().playMusic(player, item.getTrack(), volume, pitch, fadeIn, queue.isFadeOut(), playbackAt(musicBe), musicBe.getRadius(), fresh, queue.isLoop(), offsetSeconds);
		}

		PlaybackState state = new PlaybackState();
		state.queueIndex = queueIndex;
		state.trackIndex = trackIndex;
		state.startedTick = now - (long) (offsetSeconds * 20f);
		state.sound = sound;
		state.order = order == null ? null : new ArrayList<>(order);
		state.orderPos = orderPos;
		STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(uuid, state);
		PlaybackSaveData.get(level.getServer()).put(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid,
				new PlaybackSaveData.Entry(queueIndex, trackIndex, state.startedTick, toOrderArray(state.order), state.orderPos));
	}

	private static int[] toOrderArray(List<Integer> order) {
		if (order == null) {
			return null;
		}
		int[] array = new int[order.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = order.get(i);
		}
		return array;
	}

	private static void startFresh(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, int queueIndex, boolean stealClaim) {
		MusicQueue queue = musicBe.getQueues().get(queueIndex);
		List<Integer> order = null;
		int first = 0;
		if (queue.isShuffle() && queue.getTracks().size() > 1) {
			order = MusicQueue.shuffledOrder(queue.getTracks().size());
			first = order.get(0);
		}
		startQueue(level, key, musicBe, player, queueIndex, first, 0f, true, stealClaim, order, 0);
	}

	private static void advanceClock(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, UUID uuid, PlaybackState state, boolean allowFadeIn) {
		List<MusicQueue> queues = musicBe.getQueues();
		if (state.queueIndex < 0 || state.queueIndex >= queues.size()) {
			stopState(level, key, musicBe, uuid, player);
			return;
		}
		MusicQueue queue = queues.get(state.queueIndex);
		if (state.trackIndex < 0 || state.trackIndex >= queue.getTracks().size()) {
			stopState(level, key, musicBe, uuid, player);
			return;
		}
		if (!state.sound && queue.getTracks().size() == 1 && queue.isLoop()) {
			return;
		}
		if (state.fadingGap) {
			float gapElapsed = (level.getGameTime() - state.startedTick) / 20f;
			if (gapElapsed >= fadeOutSeconds()) {
				state.fadingGap = false;
				startQueue(level, key, musicBe, player, state.queueIndex, state.nextTrack, 0f, true, false, state.order, state.orderPos);
			}
			return;
		}
		float duration = trackDuration(queue.getTracks().get(state.trackIndex).getTrack());
		if (duration < 0f) {
			return;
		}
		float elapsed = (level.getGameTime() - state.startedTick) / 20f;
		if (elapsed < duration) {
			return;
		}
		advanceTrack(level, key, musicBe, player, uuid, state, allowFadeIn, true);
	}

	private static void advanceTrack(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, UUID uuid, PlaybackState state, boolean allowFadeIn, boolean allowGap) {
		MusicQueue queue = musicBe.getQueues().get(state.queueIndex);
		int size = queue.getTracks().size();
		List<Integer> order = state.order;
		int orderPos = state.orderPos;
		int next;
		if (order != null) {
			if (orderPos + 1 >= order.size()) {
				if (!queue.isLoop()) {
					settle(level, key, uuid, state.queueIndex);
					return;
				}
				order = MusicQueue.shuffledOrder(size);
				orderPos = 0;
			} else {
				orderPos = orderPos + 1;
			}
			next = order.get(orderPos);
		} else {
			next = state.trackIndex + 1;
			if (next >= size) {
				if (!queue.isLoop()) {
					settle(level, key, uuid, state.queueIndex);
					return;
				}
				next = 0;
			}
		}
		float fade = fadeOutSeconds();
		float currentDur = trackDuration(queue.getTracks().get(state.trackIndex).getTrack());
		float nextDur = trackDuration(queue.getTracks().get(next).getTrack());
		boolean longTransition = currentDur >= fade && nextDur >= fade;
		if (allowGap && !state.sound && queue.isFadeOut() && longTransition) {
			if (MusicDebug.ENABLED) {
				LOGGER.info("[dbg] gap box={} player={} qi={} {}->{} fireIn={}s",
						key.pos(), player.getScoreboardName(), state.queueIndex, state.trackIndex, next, fadeOutSeconds());
			}
			MusicRemotes.getRemote().stopMusic(player, true);
			state.fadingGap = true;
			state.nextTrack = next;
			state.orderPos = orderPos;
			if (order != null && state.order != order) {
				state.order = new ArrayList<>(order);
			}
			state.startedTick = level.getGameTime();
			PlaybackSaveData.get(level.getServer()).put(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid,
					new PlaybackSaveData.Entry(state.queueIndex, state.trackIndex, state.startedTick, toOrderArray(state.order), state.orderPos));
			return;
		}
		if (MusicDebug.ENABLED) {
			LOGGER.info("[dbg] advance box={} player={} qi={} {}->{} gap=false", key.pos(), player.getScoreboardName(), state.queueIndex, state.trackIndex, next);
		}
		if (!state.sound && queue.isFadeOut() && !longTransition) {
			MusicRemotes.getRemote().stopMusic(player, false);
		}
		startQueue(level, key, musicBe, player, state.queueIndex, next, 0f, true, false, order, orderPos);
	}

	private static void stopState(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, UUID uuid, ServerPlayer player) {
		Map<UUID, PlaybackState> states = STATES.get(key);
		PlaybackState state = states == null ? null : states.get(uuid);
		if (state == null) {
			return;
		}
		if (state.stopped) {
			if (state.trackIndex < 0) {
				states.remove(uuid);
			}
			return;
		}
		if (player != null) {
			if (state.sound) {
				MusicRemotes.getRemote().stopSound(player, musicBe.isFadeOut());
			} else {
				MusicRemotes.getRemote().stopMusic(player, musicBe.isFadeOut());
				MUSIC_CLAIM.remove(uuid, key);
			}
		} else if (!state.sound) {
			MUSIC_CLAIM.remove(uuid, key);
		}
		state.stopped = true;
		PlaybackSaveData.get(level.getServer()).remove(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid);
		if (MusicDebug.ENABLED) {
			LOGGER.info("[dbg] stop box={} player={} qi={} ti={} sound={}",
					key.pos(), uuid, state.queueIndex, state.trackIndex, state.sound);
		}
	}

	private static void settle(ServerLevel level, BoxKey key, UUID uuid, int queueIndex) {
		PlaybackState state = new PlaybackState();
		state.queueIndex = queueIndex;
		state.trackIndex = -1;
		state.startedTick = level.getGameTime();
		state.stopped = true;
		STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(uuid, state);
		MUSIC_CLAIM.remove(uuid, key);
		PlaybackSaveData.get(level.getServer()).remove(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid);
	}

	private static boolean beatenBy(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player) {
		boolean iAmGate = musicBe.isAreaGate() && areaOf(musicBe).contains(player.getX(), player.getY(), player.getZ());
		int myPriority = musicBe.getPriority();
		long myVolume = iAmGate ? areaVolume(musicBe) : 0L;
		for (long otherPos : chainBoxesIn(key.dimension())) {
			if (otherPos == key.pos()) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(BlockPos.of(otherPos));
			if (!(be instanceof MusicBlockEntity other)
					|| other.getTriggerMode() != MusicBlockEntity.TriggerMode.CHAIN || !boxHasTracks(other)) {
				continue;
			}
		MusicQueue match = matchMemo(level, new BoxKey(key.dimension(), otherPos), other, player);
		if (match == null || match.getChannel() != MusicQueue.Channel.MUSIC) {
			continue;
		}
		if (!audibleBox(other, player)) {
			continue;
		}
		boolean otherGate = other.isAreaGate() && areaOf(other).contains(player.getX(), player.getY(), player.getZ());
			if (other.isAreaGate() && !otherGate) {
				continue;
			}
			if (other.getPriority() != myPriority) {
				return other.getPriority() > myPriority;
			}
			if (!iAmGate && !otherGate) {
				continue;
			}
			if (!iAmGate) {
				return true;
			}
			if (!otherGate) {
				continue;
			}
			long otherVolume = areaVolume(other);
			if (otherVolume < myVolume || (otherVolume == myVolume && otherPos < key.pos())) {
				return true;
			}
		}
		return false;
	}

	private static boolean blockedBySmaller(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player) {
		long myVolume = areaVolume(musicBe);
		for (long otherPos : chainBoxesIn(key.dimension())) {
			if (otherPos == key.pos()) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(BlockPos.of(otherPos));
			if (!(be instanceof MusicBlockEntity other) || !other.isAreaGate()
					|| other.getTriggerMode() != MusicBlockEntity.TriggerMode.CHAIN || !boxHasTracks(other)) {
				continue;
			}
			if (!areaOf(other).contains(player.getX(), player.getY(), player.getZ())) {
				continue;
			}
			if (matchMemo(level, new BoxKey(key.dimension(), otherPos), other, player) == null) {
				continue;
			}
			if (!audibleBox(other, player)) {
				continue;
			}
			if (isBlockedBy(musicBe.getPriority(), myVolume, key.pos(), other.getPriority(), areaVolume(other), otherPos)) {
				return true;
			}
		}
		return false;
	}

	static boolean isBlockedBy(int myPriority, long myVolume, long myPos, int otherPriority, long otherVolume, long otherPos) {
		if (otherPriority < myPriority) {
			return false;
		}
		return otherVolume < myVolume || (otherVolume == myVolume && otherPos < myPos);
	}

	static boolean audible(boolean positional, BlockPos playbackPos, int radius, double x, double y, double z) {
		if (!positional) {
			return true;
		}
		double dx = x - (playbackPos.getX() + 0.5);
		double dy = y - (playbackPos.getY() + 0.5);
		double dz = z - (playbackPos.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
	}

	private static boolean audibleBox(MusicBlockEntity musicBe, ServerPlayer player) {
		return audible(musicBe.getPlaybackMode() == MusicBlockEntity.PlaybackMode.POSITIONAL,
				musicBe.getPlaybackPos(), musicBe.getRadius(), player.getX(), player.getY(), player.getZ());
	}

	private static Set<Long> chainBoxesIn(String dimension) {
		Set<Long> boxes = CHAIN_BOXES.get(dimension);
		return boxes == null ? Set.of() : boxes;
	}

	private static boolean validEntry(MusicBlockEntity musicBe, PlaybackSaveData.Entry saved) {
		List<MusicQueue> queues = musicBe.getQueues();
		if (saved.queueIndex < 0 || saved.queueIndex >= queues.size()) {
			return false;
		}
		MusicQueue queue = queues.get(saved.queueIndex);
		return !queue.getTracks().isEmpty() && saved.trackIndex >= 0 && saved.trackIndex < queue.getTracks().size()
				&& !queue.getTracks().get(saved.trackIndex).isStop();
	}

	private static float trackDuration(String track) {
		if (MusicQueue.PlaylistItem.isStop(track)) {
			return 0f;
		}
		String key = track.trim().toLowerCase(Locale.ROOT);
		if (key.endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}
		Path path = MusicLibrary.scanTracks().get(key);
		if (path == null) {
			return -1f;
		}
		return TrackDurations.seconds(path);
	}

	private static Vec3 playbackAt(MusicBlockEntity musicBe) {
		if (musicBe.getPlaybackMode() != MusicBlockEntity.PlaybackMode.POSITIONAL) {
			return null;
		}
		BlockPos pPos = musicBe.getPlaybackPos();
		return new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5);
	}

	private static List<ServerPlayer> allPlayers(ServerLevel level) {
		return level.players().stream().filter(p -> p instanceof ServerPlayer).map(p -> (ServerPlayer) p).toList();
	}

	private static List<ServerPlayer> playersInside(ServerLevel level, MusicBlockEntity musicBe) {
		AABB area = areaOf(musicBe);
		List<ServerPlayer> inside = new ArrayList<>();
		for (ServerPlayer player : level.players().stream().filter(p -> p instanceof ServerPlayer).map(p -> (ServerPlayer) p).toList()) {
			if (area.contains(player.getX(), player.getY(), player.getZ())) {
				inside.add(player);
			}
		}
		return inside;
	}

	private static List<ServerPlayer> resolveTargets(ServerLevel level, MusicBlockEntity musicBe) {
		String selector = musicBe.getListenerSelector();
		List<ServerPlayer> players = level.players().stream().filter(p -> p instanceof ServerPlayer).map(p -> (ServerPlayer) p).toList();
		if (selector == null || selector.isBlank() || selector.equals("@a")) {
			return players;
		}
		if (selector.equals("@p")) {
			ServerPlayer nearest = null;
			double nearestDist = Double.MAX_VALUE;
			Vec3 blockVec = Vec3.atCenterOf(musicBe.getBlockPos());
			for (ServerPlayer player : players) {
				double dist = player.distanceToSqr(blockVec);
				if (dist < nearestDist) {
					nearestDist = dist;
					nearest = player;
				}
			}
			return nearest != null ? List.of(nearest) : List.of();
		}
		if (selector.equals("@r")) {
			if (players.isEmpty()) {
				return List.of();
			}
			return List.of(players.get(level.getRandom().nextInt(players.size())));
		}
		if (!selector.startsWith("@")) {
			for (ServerPlayer player : players) {
				if (player.getScoreboardName().equalsIgnoreCase(selector) || player.getUUID().toString().equalsIgnoreCase(selector)) {
					return List.of(player);
				}
			}
		}
		LOGGER.warn("Unsupported listener selector '{}', no players targeted", selector);
		return List.of();
	}

	private static BoxKey keyOf(ServerLevel level, BlockPos pos) {
		return new BoxKey(level.dimension().location().toString(), pos.asLong());
	}

	private static boolean boxHasTracks(MusicBlockEntity musicBe) {
		for (MusicQueue queue : musicBe.getQueues()) {
			if (!queue.getTracks().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public static AABB areaOf(MusicBlockEntity musicBe) {
		BlockPos p1 = musicBe.getPos1();
		BlockPos p2 = musicBe.getPos2();
		return new AABB(
				Math.min(p1.getX(), p2.getX()),
				Math.min(p1.getY(), p2.getY()),
				Math.min(p1.getZ(), p2.getZ()),
				Math.max(p1.getX(), p2.getX()) + 1.0,
				Math.max(p1.getY(), p2.getY()) + 1.0,
				Math.max(p1.getZ(), p2.getZ()) + 1.0
		);
	}

	private static long areaVolume(MusicBlockEntity musicBe) {
		BlockPos p1 = musicBe.getPos1();
		BlockPos p2 = musicBe.getPos2();
		long dx = Math.abs((long) p1.getX() - p2.getX()) + 1L;
		long dy = Math.abs((long) p1.getY() - p2.getY()) + 1L;
		long dz = Math.abs((long) p1.getZ() - p2.getZ()) + 1L;
		return dx * dy * dz;
	}
}
