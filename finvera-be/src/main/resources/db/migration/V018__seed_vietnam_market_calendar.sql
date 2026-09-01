-- V018: Seed Vietnam Stock Exchange (HOSE, HNX, UPCOM) official holiday calendar (2024-2027)
-- Covers: New Year, Lunar New Year (Tet), Hung Kings Commemoration, Victory Day (30/4),
-- International Labor Day (1/5), National Day (2/9), and designated compensatory/swap days off.

DO $$
DECLARE
    v_venues text[] := ARRAY['HOSE', 'HNX', 'UPCOM'];
    v_venue text;
    v_policy text := 'vn-exchange-calendar-v1';
    v_source text := 'UBCKNN / VNX Official Holiday Schedule';
    v_now timestamptz := '2024-01-01 00:00:00+07';
BEGIN
    FOREACH v_venue IN ARRAY v_venues LOOP
        -- ==========================================
        -- NĂM 2024
        -- ==========================================
        -- Tết Dương lịch
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2024-01-01', false, v_policy, v_source, 'HOLIDAY_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Tết Nguyên đán Giáp Thìn (08/02/2024 - 14/02/2024)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2024-02-08', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2024-02-09', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2024-02-12', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2024-02-13', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2024-02-14', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Giỗ tổ Hùng Vương (10/3 Âm lịch)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2024-04-18', false, v_policy, v_source, 'HOLIDAY_HUNG_KINGS', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- 30/4 & 1/5 (kèm ngày hoán đổi 29/04)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2024-04-29', false, v_policy, v_source, 'HOLIDAY_BRIDGE_DAY', v_now),
            (gen_random_uuid(), v_venue, '2024-04-30', false, v_policy, v_source, 'HOLIDAY_VICTORY_DAY', v_now),
            (gen_random_uuid(), v_venue, '2024-05-01', false, v_policy, v_source, 'HOLIDAY_LABOR_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Quốc khánh 2/9
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2024-09-02', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now),
            (gen_random_uuid(), v_venue, '2024-09-03', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- ==========================================
        -- NĂM 2025
        -- ==========================================
        -- Tết Dương lịch
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2025-01-01', false, v_policy, v_source, 'HOLIDAY_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Tết Nguyên đán Ất Tỵ (27/01/2025 - 31/01/2025)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2025-01-27', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2025-01-28', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2025-01-29', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2025-01-30', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2025-01-31', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Giỗ tổ Hùng Vương (10/3 Âm lịch)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2025-04-07', false, v_policy, v_source, 'HOLIDAY_HUNG_KINGS', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- 30/4 & 1/5 (kèm ngày hoán đổi 02/05)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2025-04-30', false, v_policy, v_source, 'HOLIDAY_VICTORY_DAY', v_now),
            (gen_random_uuid(), v_venue, '2025-05-01', false, v_policy, v_source, 'HOLIDAY_LABOR_DAY', v_now),
            (gen_random_uuid(), v_venue, '2025-05-02', false, v_policy, v_source, 'HOLIDAY_BRIDGE_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Quốc khánh 2/9
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2025-09-01', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now),
            (gen_random_uuid(), v_venue, '2025-09-02', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- ==========================================
        -- NĂM 2026
        -- ==========================================
        -- Tết Dương lịch (kèm hoán đổi/nghỉ 02/01)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2026-01-01', false, v_policy, v_source, 'HOLIDAY_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2026-01-02', false, v_policy, v_source, 'HOLIDAY_BRIDGE_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Tết Nguyên đán Bính Ngọ (16/02/2026 - 20/02/2026)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2026-02-16', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2026-02-17', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2026-02-18', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2026-02-19', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2026-02-20', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Giỗ tổ Hùng Vương (10/3 Âm lịch rơi vào 26/04 Chủ Nhật -> nghỉ bù 27/04 Thứ 2)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2026-04-27', false, v_policy, v_source, 'HOLIDAY_HUNG_KINGS_COMPENSATORY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- 30/4 & 1/5
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2026-04-30', false, v_policy, v_source, 'HOLIDAY_VICTORY_DAY', v_now),
            (gen_random_uuid(), v_venue, '2026-05-01', false, v_policy, v_source, 'HOLIDAY_LABOR_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Quốc khánh 2/9 (01/09 & 02/09/2026)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2026-09-01', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now),
            (gen_random_uuid(), v_venue, '2026-09-02', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- ==========================================
        -- NĂM 2027
        -- ==========================================
        -- Tết Dương lịch
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2027-01-01', false, v_policy, v_source, 'HOLIDAY_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Tết Nguyên đán Đinh Mùi (04/02/2027 - 10/02/2027)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2027-02-04', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2027-02-05', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2027-02-08', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2027-02-09', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now),
            (gen_random_uuid(), v_venue, '2027-02-10', false, v_policy, v_source, 'HOLIDAY_LUNAR_NEW_YEAR', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Giỗ tổ Hùng Vương (16/04/2027)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at)
        VALUES (gen_random_uuid(), v_venue, '2027-04-16', false, v_policy, v_source, 'HOLIDAY_HUNG_KINGS', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- 30/4 & 1/5 (01/05 Thứ 7 -> nghỉ bù 03/05 Thứ 2)
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2027-04-30', false, v_policy, v_source, 'HOLIDAY_VICTORY_DAY', v_now),
            (gen_random_uuid(), v_venue, '2027-05-03', false, v_policy, v_source, 'HOLIDAY_LABOR_DAY_COMPENSATORY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;

        -- Quốc khánh 2/9
        INSERT INTO market_calendar_day (id, venue, trading_date, is_trading_day, policy_version, source_reference, reason_code, accepted_at) VALUES
            (gen_random_uuid(), v_venue, '2027-09-02', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now),
            (gen_random_uuid(), v_venue, '2027-09-03', false, v_policy, v_source, 'HOLIDAY_NATIONAL_DAY', v_now)
        ON CONFLICT (venue, trading_date, policy_version) DO NOTHING;
    END LOOP;
END $$;
