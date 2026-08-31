package com.putzwirk.mapmakermusic.network;

public final class MusicRemotes {

	private static MusicRemote remote;

	private MusicRemotes() {
	}

	public static void setRemote(MusicRemote remote) {
		MusicRemotes.remote = remote;
	}

	public static MusicRemote getRemote() {
		return remote == null ? MusicRemote.NOOP : remote;
	}
}