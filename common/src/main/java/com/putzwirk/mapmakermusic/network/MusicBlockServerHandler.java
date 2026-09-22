package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MusicBlockServerHandler {

	public static void handleUpdate(ServerPlayer player, UpdateMusicBlockPacket packet) {
		if (player == null || player.level() == null) return;
		
		// Optional: permission check if needed (e.g., operator or creative)
		if (!player.hasPermissions(2)) {
			return;
		}

		BlockEntity be = player.level().getBlockEntity(packet.pos);
		if (be instanceof MusicBlockEntity musicBe) {
			musicBe.setActivationType(packet.activationType == 1 ? MusicBlockEntity.ActivationType.AREA : MusicBlockEntity.ActivationType.REDSTONE);
			musicBe.setAudioType(packet.audioType == 1 ? MusicBlockEntity.AudioType.SOUND : MusicBlockEntity.AudioType.MUSIC);
			musicBe.setPos1(packet.pos1);
			musicBe.setPos2(packet.pos2);
			musicBe.setAudioTrack(packet.audioTrack);
			musicBe.setVolume(packet.volume);
			musicBe.setPitch(packet.pitch);
			musicBe.setLoop(packet.loop);
			musicBe.setPersistent(packet.persistent);
			musicBe.setFadeIn(packet.fadeIn);
			musicBe.setFadeOut(packet.fadeOut);
			musicBe.setPlaybackMode(packet.playbackMode == 1 ? MusicBlockEntity.PlaybackMode.POSITIONAL : MusicBlockEntity.PlaybackMode.GLOBAL);
			musicBe.setListenerSelector(packet.listenerSelector);
			musicBe.setPlaybackPos(packet.playbackPos);
			musicBe.setRadius(packet.radius);

			musicBe.setChanged();
			player.level().sendBlockUpdated(packet.pos, musicBe.getBlockState(), musicBe.getBlockState(), 3);
		}
	}
}
