package com.putzwirk.mapmakermusic.block.condition;

public final class BuiltinConditionKinds {

	private BuiltinConditionKinds() {
	}

	public static void registerAll() {
		ConditionKindRegistry.register(new TimeConditionKind());
		ConditionKindRegistry.register(new WeatherConditionKind());
		ConditionKindRegistry.register(new ScoreboardConditionKind());
		ConditionKindRegistry.register(new PlayerConditionKind());
		ConditionKindRegistry.register(new PlayerHealthConditionKind());
		ConditionKindRegistry.register(new PlayerHungerConditionKind());
		ConditionKindRegistry.register(new EntityAliveConditionKind());
		ConditionKindRegistry.register(new InBiomeConditionKind());
		ConditionKindRegistry.register(new CoordinatesConditionKind());
		ConditionKindRegistry.register(new CommandConditionKind());
	}
}
