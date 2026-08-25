-- Add catalog entries for newly confirmed fundamental metrics (BVPS, TRAILING_EPS, DIVIDEND_YIELD)
insert into fundamental_metric_catalog
    (metric_code, catalog_version, statement_type, unit_type, scale, polarity, display_name_vi, display_name_en)
values
    ('BVPS', 'fundamental-metric-catalog-v1', 'PER_SHARE', 'VND', 2, 'ANY', 'Giá trị sổ sách trên cổ phiếu', 'Book value per share'),
    ('TRAILING_EPS', 'fundamental-metric-catalog-v1', 'PER_SHARE', 'VND', 2, 'ANY', 'Lãi cơ bản trên cổ phiếu 4 quý', 'Trailing earnings per share'),
    ('DIVIDEND_YIELD', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Tỷ suất cổ tức', 'Dividend yield')
on conflict (metric_code) do nothing;
