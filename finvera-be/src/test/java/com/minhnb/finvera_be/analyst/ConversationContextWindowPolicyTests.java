package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.analyst.domain.ConversationContextWindowPolicy;
import com.minhnb.finvera_be.analyst.dto.AskAnalystDto.PriorTurnDto;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConversationContextWindowPolicyTests {
    @Test
    void selectsNewestFiveAndRestoresChronologicalOrder() {
        List<PriorTurnDto> newestFirst=IntStream.rangeClosed(1, 6)
                .mapToObj(i -> new PriorTurnDto("q" + (7-i), "a" + (7-i))).toList();
        var result=ConversationContextWindowPolicy.selectNewestFirst(newestFirst);
        assertThat(result.turns()).extracting(PriorTurnDto::question)
                .containsExactly("q2", "q3", "q4", "q5", "q6");
        assertThat(result.omittedExchanges()).isEqualTo(1);
    }

    @Test
    void stopsAtFirstOversizedRecentPairInsteadOfSkippingBackward() {
        var result=ConversationContextWindowPolicy.selectNewestFirst(List.of(
                new PriorTurnDto("q", "😀".repeat(12_000)),
                new PriorTurnDto("older", "small")));
        assertThat(result.turns()).isEmpty();
        assertThat(result.omittedExchanges()).isEqualTo(2);
    }

    @Test
    void countsUnicodeCodePointsAndAcceptsExactBudget() {
        var result=ConversationContextWindowPolicy.selectNewestFirst(
                List.of(new PriorTurnDto("😀", "a".repeat(11_999))));
        assertThat(result.includedExchanges()).isOne();
        assertThat(result.characterCount()).isEqualTo(12_000);
    }
}
