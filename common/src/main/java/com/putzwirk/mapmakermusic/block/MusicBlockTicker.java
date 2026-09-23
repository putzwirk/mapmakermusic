package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.network.MusicRemotes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MusicBlockTicker {

	private static final Map<Long, Set<UUID>> PLAYERS_IN_AREA = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> ACTIVE_AREA = new ConcurrentHashMap<>();

	public static void tick(Level level, BlockPos pos, BlockState state, MusicBlockEntity musicBe) {
		if (musicBe.getActivationType() == MusicBlockEntity.ActivationType.AREA) {
			tickAreaActivation(level, musicBe);
		}
	}

	public static void triggerRedstoneActivation(Level level, MusicBlockEntity musicBe) {
		String track = musicBe.getAudioTrack();
		if (track == null || track.isEmpty()) {
			return;
		}

		for (ServerPlayer player : getTargetPlayers(level, musicBe)) {
			playBoxAudioForPlayer(musicBe, player, false);
		}
	}

	private static void playBoxAudioForPlayer(MusicBlockEntity musicBe, ServerPlayer player, boolean forceGlobal) {
		String track = musicBe.getAudioTrack();
		if (track == null || track.isEmpty()) {
			return;
		}

		int volume = musicBe.getVolume();
		float pitch = musicBe.getPitch();
		boolean positional = !forceGlobal && musicBe.getPlaybackMode() == MusicBlockEntity.PlaybackMode.POSITIONAL;

		if (musicBe.getAudioType() == MusicBlockEntity.AudioType.MUSIC) {
			if (!positional) {
				MusicRemotes.getRemote().playMusic(player, track, volume, pitch, musicBe.isFadeIn(), musicBe.isFadeOut(), null, musicBe.getRadius(), true);
			} else {
				BlockPos pPos = musicBe.getPlaybackPos();
				Vec3 vecPos = new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5);
				MusicRemotes.getRemote().playMusic(player, track, volume, pitch, musicBe.isFadeIn(), musicBe.isFadeOut(), vecPos, musicBe.getRadius(), true);
			}
		} else {
			if (!positional) {
				MusicRemotes.getRemote().playSound(player, track, volume, pitch, null, musicBe.getRadius());
			} else {
				BlockPos pPos = musicBe.getPlaybackPos();
				Vec3 vecPos = new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5);
				MusicRemotes.getRemote().playSound(player, track, volume, pitch, vecPos, musicBe.getRadius());
			}
		}
	}

	private static void tickAreaActivation(Level level, MusicBlockEntity musicBe) {
		String track = musicBe.getAudioTrack();
		if (track == null || track.isEmpty()) {
			return;
		}

		AABB area = areaOf(musicBe);
		long beKey = musicBe.getBlockPos().asLong();
		Set<UUID> insideSet = PLAYERS_IN_AREA.computeIfAbsent(beKey, k -> ConcurrentHashMap.newKeySet());

		for (ServerPlayer player : serverPlayers(level)) {
			boolean inside = area.contains(player.getX(), player.getY(), player.getZ());
			UUID uuid = player.getUUID();
			boolean wasInside = insideSet.contains(uuid);

			if (inside && !wasInside) {
				insideSet.add(uuid);
				onPlayerEnteredArea(level, musicBe, player);
			} else if (!inside && wasInside) {
				insideSet.remove(uuid);
				onPlayerLeftArea(level, musicBe, beKey, player);
			}
		}
	}

	private static void onPlayerEnteredArea(Level level, MusicBlockEntity entered, ServerPlayer player) {
		MusicBlockEntity best = bestAreaFor(level, player);
		MusicBlockEntity target = best != null ? best : entered;
		long targetKey = target.getBlockPos().asLong();

		Long current = ACTIVE_AREA.get(player.getUUID());
		if (current != null && current.longValue() == targetKey) {
			return;
		}

		ACTIVE_AREA.put(player.getUUID(), targetKey);
		playBoxAudioForPlayer(target, player, true);
	}

	private static void onPlayerLeftArea(Level level, MusicBlockEntity left, long leftKey, ServerPlayer player) {
		Long current = ACTIVE_AREA.get(player.getUUID());
		if (current == null || current.longValue() != leftKey) {
			return;
		}

		MusicBlockEntity best = bestAreaFor(level, player);
		if (best != null) {
			long bestKey = best.getBlockPos().asLong();
			if (bestKey != leftKey) {
				ACTIVE_AREA.put(player.getUUID(), bestKey);
				playBoxAudioForPlayer(best, player, true);
			}
			return;
		}

		ACTIVE_AREA.remove(player.getUUID());
		if (left.getAudioType() == MusicBlockEntity.AudioType.MUSIC) {
			MusicRemotes.getRemote().stopMusic(player, left.isFadeOut());
		}
	}

	public static void forgetPlayer(UUID uuid) {
		ACTIVE_AREA.remove(uuid);
		for (Set<UUID> set : PLAYERS_IN_AREA.values()) {
			set.remove(uuid);
		}
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
					|| musicBe.getAudioType() != MusicBlockEntity.AudioType.MUSIC
					|| musicBe.getAudioTrack() == null
					|| musicBe.getAudioTrack().isEmpty()) {
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
