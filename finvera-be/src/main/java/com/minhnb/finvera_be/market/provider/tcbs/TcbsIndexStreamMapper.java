package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.provider.MarketDataProvider.ProviderObservation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Maps the documented TCBS `rt` stream without inventing timestamp or revision semantics. */
public final class TcbsIndexStreamMapper {
    public ProviderObservation map(IndexStreamMessage message) {
        IndexCode code = switch (message.indexNumber()) {
            case 1 -> IndexCode.VN_INDEX;
            case 2 -> IndexCode.VN30;
            case 3 -> IndexCode.HNX_INDEX;
            case 5 -> IndexCode.UPCOM_INDEX;
            default -> throw new IllegalArgumentException("TCBS_INDEX_NOT_ALLOWLISTED");
        };
        BigDecimal level = decimal(message.index());
        BigDecimal change = decimal(message.change());
        BigDecimal reference = level.subtract(change).setScale(6, RoundingMode.UNNECESSARY);
        return new ProviderObservation(code, level, reference, change, decimal(message.changePercent()),
                nonNegativeLong(message.volume()), decimal(message.value()),
                List.of("TCBS_STREAM_ORDERING_UNAVAILABLE", "TCBS_STREAM_TIMESTAMP_UNAVAILABLE"));
    }

    private static BigDecimal decimal(String value) {
        if (value == null || !value.matches("-?[0-9]+(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("TCBS_DECIMAL_INVALID");
        }
        return new BigDecimal(value).setScale(6, RoundingMode.UNNECESSARY);
    }

    private static Long nonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new IllegalArgumentException("TCBS_VOLUME_INVALID");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TCBS_VOLUME_INVALID", exception);
        }
    }

    public record IndexStreamMessage(int indexNumber, String index, String change, String changePercent,
            String volume, String value, String session) { }
}
