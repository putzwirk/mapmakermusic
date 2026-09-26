package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.network.MusicRemotes;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MusicBlockTicker {

	private static final int REEVALUATE_INTERVAL_TICKS = 20;

	private enum Source {
		AREA,
		REDSTONE
	}

	private static final class ActiveState {
		long areaKey;
		int queueIndex;
		int playlistIndex;
		int ticksSinceEval;
		Source source = Source.AREA;
	}

	private static final Map<Long, Set<UUID>> PLAYERS_IN_AREA = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveState> ACTIVE = new ConcurrentHashMap<>();

	public static void tick(Level level, BlockPos pos, BlockState state, MusicBlockEntity musicBe) {
		if (musicBe.getActivationType() == MusicBlockEntity.ActivationType.AREA) {
			tickAreaActivation(level, musicBe);
		}
	}

	public static void triggerRedstoneActivation(Level level, MusicBlockEntity musicBe) {
		if (!boxHasTracks(musicBe)) {
			return;
		}

		for (ServerPlayer player : getTargetPlayers(level, musicBe)) {
			MusicQueue queue = musicBe.findMatchingQueue(player);
			if (queue == null) {
				continue;
			}
			int queueIndex = musicBe.getQueues().indexOf(queue);
			playQueue(musicBe, player, queueIndex, 0, true, false, Source.REDSTONE);
		}
	}

	private static void tickAreaActivation(Level level, MusicBlockEntity musicBe) {
		AABB area = areaOf(musicBe);
		long beKey = musicBe.getBlockPos().asLong();
		Set<UUID> insideSet = PLAYERS_IN_AREA.computeIfAbsent(beKey, k -> ConcurrentHashMap.newKeySet());

		for (ServerPlayer player : serverPlayers(level)) {
			boolean inside = area.contains(player.getX(), player.getY(), player.getZ());
			UUID uuid = player.getUUID();
			boolean wasInside = insideSet.contains(uuid);

			if (inside) {
				boolean newly = !wasInside;
				if (newly) {
					insideSet.add(uuid);
				}
				ActiveState state = ACTIVE.get(uuid);
				if (state == null || newly) {
					activateArea(level, musicBe, player);
				} else if (state.source == Source.AREA && state.areaKey == beKey) {
					reevaluateActive(musicBe, beKey, player);
				}
			} else if (wasInside) {
				insideSet.remove(uuid);
				onPlayerLeftArea(level, musicBe, beKey, player);
			}
		}
	}

	private static void activateArea(Level level, MusicBlockEntity entered, ServerPlayer player) {
		MusicBlockEntity best = bestAreaFor(level, player);
		MusicBlockEntity target = best != null ? best : entered;

		MusicQueue queue = target.findMatchingQueue(player);
		if (queue == null && target != entered) {
			target = entered;
			queue = entered.findMatchingQueue(player);
		}
		if (queue == null) {
			return;
		}

		int queueIndex = target.getQueues().indexOf(queue);
		playQueue(target, player, queueIndex, 0, true, true, Source.AREA);
	}

	private static void reevaluateActive(MusicBlockEntity musicBe, long beKey, ServerPlayer player) {
		ActiveState state = ACTIVE.get(player.getUUID());
		if (state == null || state.source != Source.AREA || state.areaKey != beKey) {
			return;
		}

		state.ticksSinceEval++;
		if (state.ticksSinceEval < REEVALUATE_INTERVAL_TICKS) {
			return;
		}
		state.ticksSinceEval = 0;

		MusicQueue queue = musicBe.findMatchingQueue(player);
		if (queue == null) {
			stopPlayer(player, musicBe);
			return;
		}

		int queueIndex = musicBe.getQueues().indexOf(queue);
		if (queueIndex != state.queueIndex) {
			playQueue(musicBe, player, queueIndex, 0, true, true, Source.AREA);
		}
	}

	private static void onPlayerLeftArea(Level level, MusicBlockEntity left, long leftKey, ServerPlayer player) {
		ActiveState state = ACTIVE.get(player.getUUID());
		if (state == null || state.source != Source.AREA || state.areaKey != leftKey) {
			return;
		}

		MusicBlockEntity best = bestAreaFor(level, player);
		if (best != null && best.getBlockPos().asLong() != leftKey) {
			MusicQueue queue = best.findMatchingQueue(player);
			if (queue != null) {
				playQueue(best, player, best.getQueues().indexOf(queue), 0, true, true, Source.AREA);
				return;
			}
		}

		stopPlayer(player, left);
	}

	private static void playQueue(MusicBlockEntity musicBe, ServerPlayer player, int queueIndex, int playlistIndex, boolean restart, boolean forceGlobal, Source source) {
		List<MusicQueue> queues = musicBe.getQueues();
		if (queueIndex < 0 || queueIndex >= queues.size()) {
			return;
		}

		MusicQueue queue = queues.get(queueIndex);
		if (queue.getTracks().isEmpty()) {
			return;
		}
		if (playlistIndex < 0 || playlistIndex >= queue.getTracks().size()) {
			playlistIndex = 0;
		}
		MusicQueue.PlaylistItem item = queue.getTracks().get(playlistIndex);
		String track = item.getTrack();

		int volume = item.getVolume() != null ? item.getVolume() : musicBe.getVolume();
		float pitch = item.getPitch() != null ? item.getPitch() : musicBe.getPitch();
		boolean fadeIn = queue.isFadeIn();
		boolean fadeOut = queue.isFadeOut();
		boolean loop = queue.isLoop() && !queue.isPlaylist();

		ActiveState state = ACTIVE.computeIfAbsent(player.getUUID(), k -> new ActiveState());
		state.areaKey = musicBe.getBlockPos().asLong();
		state.queueIndex = queueIndex;
		state.playlistIndex = playlistIndex;
		state.ticksSinceEval = 0;
		state.source = source;

		if (queue.getChannel() == MusicQueue.Channel.SOUND) {
			playSoundForPlayer(musicBe, player, track, volume, pitch, forceGlobal);
			return;
		}

		boolean positional = !forceGlobal && musicBe.getPlaybackMode() == MusicBlockEntity.PlaybackMode.POSITIONAL;
		if (positional) {
			BlockPos pPos = musicBe.getPlaybackPos();
			Vec3 vecPos = new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5);
			MusicRemotes.getRemote().playMusic(player, track, volume, pitch, fadeIn, fadeOut, vecPos, musicBe.getRadius(), restart, loop);
		} else {
			MusicRemotes.getRemote().playMusic(player, track, volume, pitch, fadeIn, fadeOut, null, musicBe.getRadius(), restart, loop);
		}
	}

	private static void playSoundForPlayer(MusicBlockEntity musicBe, ServerPlayer player, String track, int volume, float pitch, boolean forceGlobal) {
		boolean positional = !forceGlobal && musicBe.getPlaybackMode() == MusicBlockEntity.PlaybackMode.POSITIONAL;
		if (positional) {
			BlockPos pPos = musicBe.getPlaybackPos();
			Vec3 vecPos = new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5);
			MusicRemotes.getRemote().playSound(player, track, volume, pitch, vecPos, musicBe.getRadius());
		} else {
			MusicRemotes.getRemote().playSound(player, track, volume, pitch, null, musicBe.getRadius());
		}
	}

	public static void onTrackFinished(ServerPlayer player) {
		UUID uuid = player.getUUID();
		ActiveState state = ACTIVE.get(uuid);
		if (state == null) {
			return;
		}

		BlockEntity be = player.level().getBlockEntity(BlockPos.of(state.areaKey));
		if (!(be instanceof MusicBlockEntity musicBe)) {
			ACTIVE.remove(uuid);
			return;
		}

		List<MusicQueue> queues = musicBe.getQueues();
		if (state.queueIndex < 0 || state.queueIndex >= queues.size()) {
			ACTIVE.remove(uuid);
			return;
		}

		if (queues.get(state.queueIndex).getChannel() == MusicQueue.Channel.SOUND) {
			return;
		}

		MusicQueue desired = musicBe.findMatchingQueue(player);
		if (desired == null) {
			stopPlayer(player, musicBe);
			return;
		}

		int desiredIndex = queues.indexOf(desired);
		if (desiredIndex != state.queueIndex) {
			playQueue(musicBe, player, desiredIndex, 0, true, true, state.source);
			return;
		}

		MusicQueue queue = queues.get(state.queueIndex);
		if (!queue.isPlaylist()) {
			stopPlayer(player, musicBe);
			return;
		}

		int next = nextPlaylistIndex(queue, state.playlistIndex);
		if (next < 0) {
			stopPlayer(player, musicBe);
			return;
		}
		playQueue(musicBe, player, state.queueIndex, next, true, true, state.source);
	}

	private static int nextPlaylistIndex(MusicQueue queue, int current) {
		int size = queue.getTracks().size();
		if (size <= 1) {
			return 0;
		}
		if (queue.isLoop()) {
			return (current + 1) % size;
		}
		return current + 1 < size ? current + 1 : -1;
	}

	private static void stopPlayer(ServerPlayer player, MusicBlockEntity musicBe) {
		ACTIVE.remove(player.getUUID());
		MusicRemotes.getRemote().stopMusic(player, musicBe.isFadeOut());
	}

	public static void forgetPlayer(UUID uuid) {
		ACTIVE.remove(uuid);
		for (Set<UUID> set : PLAYERS_IN_AREA.values()) {
			set.remove(uuid);
		}
	}

	public static void invalidateArea(long areaKey) {
		ACTIVE.entrySet().removeIf(entry -> entry.getValue().areaKey == areaKey);
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

	private static MusicBlockEntity bestAreaFor(Level level, ServerPlayer player) {
		MusicBlockEntity best = null;
		long bestVolume = Long.MAX_VALUE;
		long bestKey = Long.MAX_VALUE;

		Iterator<Map.Entry<Long, Set<UUID>>> it = PLAYERS_IN_AREA.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Set<UUID>> entry = it.next();
			long key = entry.getKey();
			BlockEntity be = level.getBlockEntity(BlockPos.of(key));
			if (!(be instanceof MusicBlockEntity musicBe)
					|| musicBe.getActivationType() != MusicBlockEntity.ActivationType.AREA
					|| !boxHasTracks(musicBe)) {
				it.remove();
				continue;
			}
			if (!areaOf(musicBe).contains(player.getX(), player.getY(), player.getZ())) {
				continue;
			}
			long volume = areaVolume(musicBe);
			if (volume < bestVolume || (volume == bestVolume && key < bestKey)) {
				bestVolume = volume;
				bestKey = key;
				best = musicBe;
			}
		}
		return best;
	}

	private static List<ServerPlayer> serverPlayers(Level level) {
		return level.players().stream()
				.filter(p -> p instanceof ServerPlayer)
				.map(p -> (ServerPlayer) p)
				.toList();
	}

	private static List<ServerPlayer> getTargetPlayers(Level level, MusicBlockEntity musicBe) {
		String selector = musicBe.getListenerSelector();
		if (selector == null || selector.isEmpty() || selector.equals("@a")) {
			return serverPlayers(level);
		}
		if (selector.equals("@p")) {
			ServerPlayer nearest = null;
			double nearestDist = Double.MAX_VALUE;
			Vec3 blockVec = Vec3.atCenterOf(musicBe.getBlockPos());
			for (net.minecraft.world.entity.player.Player p : level.players()) {
				if (p instanceof ServerPlayer sp) {
					double dist = sp.distanceToSqr(blockVec);
					if (dist < nearestDist) {
						nearestDist = dist;
						nearest = sp;
					}
				}
			}
			return nearest != null ? List.of(nearest) : List.of();
		}
		return serverPlayers(level);
	}
}
