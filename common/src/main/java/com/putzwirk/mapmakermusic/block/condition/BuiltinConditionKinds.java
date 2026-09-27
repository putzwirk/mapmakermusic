package com.putzwirk.mapmakermusic.block.condition;

public final class BuiltinConditionKinds {

	private BuiltinConditionKinds() {
	}

	public static void registerAll() {
		ConditionKindRegistry.register(new CommandConditionKind());
	}
}
