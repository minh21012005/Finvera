package com.minhnb.finvera_be.analyst.domain;

import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PriorTurnDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationContextWindowPolicy {

    public static final String RULE_VERSION = "context-window-v1";
    public static final int DEFAULT_CANDIDATES = 10;
    public static final int DEFAULT_INCLUDED = 5;
    public static final int DEFAULT_CHARACTER_BUDGET = 12_000;

    private ConversationContextWindowPolicy() {
    }

    public static Selection selectNewestFirst(List<PriorTurnDto> newestFirst) {
        return selectNewestFirst(newestFirst, DEFAULT_INCLUDED, DEFAULT_CHARACTER_BUDGET);
    }

    public static Selection selectNewestFirst(List<PriorTurnDto> newestFirst, int maximumPairs, int characterBudget) {
        if (maximumPairs < 0 || maximumPairs > DEFAULT_INCLUDED || characterBudget < 0) {
            throw new IllegalArgumentException("Invalid conversation context limits");
        }
        List<PriorTurnDto> candidates = newestFirst == null
                ? List.of()
                : newestFirst.stream().limit(DEFAULT_CANDIDATES).toList();
        List<PriorTurnDto> selected = new ArrayList<>();
        int used = 0;
        for (PriorTurnDto candidate : candidates) {
            if (selected.size() >= maximumPairs) {
                break;
            }
            int size = codePoints(candidate.question()) + codePoints(candidate.answer());
            if (used + size > characterBudget) {
                break;
            }
            selected.add(candidate);
            used += size;
        }
        Collections.reverse(selected);
        return new Selection(List.copyOf(selected), candidates.size() - selected.size(), used);
    }

    private static int codePoints(String value) {
        if (value == null) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    public record Selection(List<PriorTurnDto> turns, int omittedExchanges, int characterCount) {
        public int includedExchanges() {
            return turns.size();
        }
    }
}
