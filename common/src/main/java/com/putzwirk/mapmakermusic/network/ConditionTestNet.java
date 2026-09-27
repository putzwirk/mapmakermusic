package com.putzwirk.mapmakermusic.network;

import java.util.function.Consumer;

public final class ConditionTestNet {

	private static Consumer<String> sender = command -> {
	};

	private static Consumer<String> suggestionSender = command -> {
	};

	private ConditionTestNet() {
	}

	public static void setSender(Consumer<String> sender) {
		ConditionTestNet.sender = sender == null ? command -> {
		} : sender;
	}

	public static void requestTest(String command) {
		sender.accept(command);
	}

	public static void setSuggestionSender(Consumer<String> sender) {
		ConditionTestNet.suggestionSender = sender == null ? command -> {
		} : sender;
	}

	public static void requestSuggestions(String command) {
		suggestionSender.accept(command);
	}
}
