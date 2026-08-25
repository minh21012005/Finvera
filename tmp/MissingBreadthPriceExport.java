import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

public class MissingBreadthPriceExport {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("tmp", "missing-breadth-price.csv");
        String sql = """
                select mi.symbol, mi.venue, max(edb.trading_date)::text as latest_daily_bar
                from breadth_snapshot_input input
                join market_instrument mi on mi.id = input.instrument_id
                left join equity_daily_bar edb on edb.instrument_id = mi.id and edb.is_current = true
                where input.breadth_snapshot_id = (
                    select id from breadth_snapshot
                    order by trading_date desc, as_of desc, calculated_at desc
                    limit 1
                )
                  and input.reason_code = 'MISSING_PRICE'
                group by mi.symbol, mi.venue
                order by mi.venue, mi.symbol
                """;
        StringBuilder csv = new StringBuilder("symbol,venue,latest_daily_bar\n");
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:5432/finvera",
                "postgres",
                System.getenv("PGPASSWORD"));
             var st = conn.createStatement();
             var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                csv.append(rs.getString("symbol")).append(',')
                        .append(rs.getString("venue")).append(',')
                        .append(rs.getString("latest_daily_bar") == null ? "" : rs.getString("latest_daily_bar"))
                        .append('\n');
            }
        }
        Files.writeString(output, csv.toString());
        System.out.println(output.toAbsolutePath());
    }
}
