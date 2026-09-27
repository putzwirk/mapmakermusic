package com.putzwirk.mapmakermusic.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ConditionTestNet {

	private static Consumer<String> sender = command -> {
	};

	private static BiConsumer<String, Integer> suggestionSender = (command, cursor) -> {
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

	public static void setSuggestionSender(BiConsumer<String, Integer> sender) {
		ConditionTestNet.suggestionSender = sender == null ? (command, cursor) -> {
		} : sender;
	}

	public static void requestSuggestions(String command, int cursor) {
		suggestionSender.accept(command, cursor);
	}
}
