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

	private static final class PlaybackState {
		int queueIndex;
		int trackIndex;
		long startedTick;
		boolean sound;
		boolean stopped;
	}

	private static final Map<BoxKey, Map<UUID, PlaybackState>> STATES = new ConcurrentHashMap<>();
	private static final Map<String, Set<Long>> CHAIN_BOXES = new ConcurrentHashMap<>();
	private static final Map<UUID, BoxKey> MUSIC_CLAIM = new ConcurrentHashMap<>();

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
			startQueue(serverLevel, key, musicBe, player, musicBe.getQueues().indexOf(queue), 0, 0f, true, true);
		}
	}

	public static void onTrackFinished(ServerPlayer player, String trackKey) {
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
			advanceTrack(serverLevel, entry.getKey(), musicBe, player, uuid, state, true);
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
	}

	public static void invalidateBox(Level level, BlockPos pos) {
		if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BoxKey key = keyOf(serverLevel, pos);
		STATES.remove(key);
		MUSIC_CLAIM.entrySet().removeIf(entry -> entry.getValue().equals(key));
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
		MusicQueue desired = musicBe.findMatchingQueue(player);
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
				startQueue(level, key, musicBe, player, queueIndex, 0, 0f, true, false);
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
		STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(uuid, state);
		advanceClock(level, key, musicBe, player, uuid, state, false);
		return true;
	}

	private static void startQueue(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, int queueIndex, int trackIndex, float offsetSeconds, boolean fresh, boolean stealClaim) {
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

		if (sound) {
			MusicRemotes.getRemote().playSound(player, item.getTrack(), volume, pitch, playbackAt(musicBe), musicBe.getRadius(), queue.isFadeIn());
		} else {
			boolean fadeIn = fresh && queue.isFadeIn();
			MusicRemotes.getRemote().playMusic(player, item.getTrack(), volume, pitch, fadeIn, queue.isFadeOut(), playbackAt(musicBe), musicBe.getRadius(), fresh, queue.isLoop(), offsetSeconds);
		}

		PlaybackState state = new PlaybackState();
		state.queueIndex = queueIndex;
		state.trackIndex = trackIndex;
		state.startedTick = now - (long) (offsetSeconds * 20f);
		state.sound = sound;
		STATES.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(uuid, state);
		PlaybackSaveData.get(level.getServer()).put(PlaybackSaveData.boxId(key.dimension(), key.pos()), uuid,
				new PlaybackSaveData.Entry(queueIndex, trackIndex, state.startedTick));
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
		float duration = trackDuration(queue.getTracks().get(state.trackIndex).getTrack());
		if (duration < 0f) {
			return;
		}
		float elapsed = (level.getGameTime() - state.startedTick) / 20f;
		if (elapsed < duration) {
			return;
		}
		advanceTrack(level, key, musicBe, player, uuid, state, allowFadeIn);
	}

	private static void advanceTrack(ServerLevel level, BoxKey key, MusicBlockEntity musicBe, ServerPlayer player, UUID uuid, PlaybackState state, boolean allowFadeIn) {
		MusicQueue queue = musicBe.getQueues().get(state.queueIndex);
		int next = state.trackIndex + 1;
		if (next >= queue.getTracks().size()) {
			if (queue.isLoop()) {
				startQueue(level, key, musicBe, player, state.queueIndex, 0, 0f, true, false);
			} else {
				settle(level, key, uuid, state.queueIndex);
			}
			return;
		}
		startQueue(level, key, musicBe, player, state.queueIndex, next, 0f, true, false);
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
			MusicQueue match = other.findMatchingQueue(player);
			if (match == null || match.getChannel() != MusicQueue.Channel.MUSIC) {
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
			if (other.findMatchingQueue(player) == null) {
				continue;
			}
			long otherVolume = areaVolume(other);
			if (otherVolume < myVolume || (otherVolume == myVolume && otherPos < key.pos())) {
				return true;
			}
		}
		return false;
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
