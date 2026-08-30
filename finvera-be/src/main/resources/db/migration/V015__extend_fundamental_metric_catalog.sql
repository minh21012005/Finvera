-- Feature 009 (specs/009-extended-fundamentals-screening, contract provider-ratio-facts-v1):
-- provider-reported ratio facts confirmed by the 2026-08-30 KBS probe, plus the DEBT_TO_EQUITY unit
-- correction (values are percent points; the catalog had declared a plain ratio).
insert into fundamental_metric_catalog
    (metric_code, catalog_version, category, unit_type, scale, sign_policy, display_name_vi, display_name_en)
values
    ('GROSS_MARGIN', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Biên lợi nhuận gộp', 'Gross margin'),
    ('NET_MARGIN', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Biên lợi nhuận ròng', 'Net margin'),
    ('ROE_TTM', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'ROE 4 quý gần nhất', 'ROE trailing'),
    ('ROA_TTM', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'ROA 4 quý gần nhất', 'ROA trailing'),
    ('ROCE', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Tỷ suất sinh lời trên vốn dài hạn (ROCE)', 'Return on capital employed'),
    ('CURRENT_RATIO', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Tỷ số thanh toán hiện hành', 'Current ratio'),
    ('QUICK_RATIO', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Tỷ số thanh toán nhanh', 'Quick ratio'),
    ('CASH_RATIO', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Tỷ số thanh toán bằng tiền mặt', 'Cash ratio'),
    ('INTEREST_COVERAGE', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'ANY', 'Khả năng thanh toán lãi vay', 'Interest coverage'),
    ('TOTAL_ASSET_TURNOVER', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Vòng quay tổng tài sản', 'Total asset turnover'),
    ('INVENTORY_TURNOVER', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Vòng quay hàng tồn kho', 'Inventory turnover'),
    ('RECEIVABLES_TURNOVER', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Vòng quay phải thu', 'Receivables turnover'),
    ('DEBT_TO_ASSETS', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Nợ vay trên tổng tài sản', 'Debt to assets'),
    ('LIABILITIES_TO_EQUITY', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Nợ phải trả trên vốn chủ sở hữu', 'Liabilities to equity'),
    ('EQUITY_TO_ASSETS', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Vốn chủ sở hữu trên tổng tài sản', 'Equity to assets'),
    ('BETA', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'ANY', 'Beta', 'Beta'),
    ('PS', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'P/S (Giá trên doanh thu)', 'Price to sales'),
    ('TOTAL_ASSETS_GROWTH_PERCENT', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Tăng trưởng tổng tài sản', 'Total assets growth'),
    ('EQUITY_GROWTH_PERCENT', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Tăng trưởng vốn chủ sở hữu', 'Equity growth'),
    ('NIM', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'ANY', 'Biên lãi thuần (NIM)', 'Net interest margin'),
    ('COST_INCOME_RATIO', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Tỷ lệ chi phí trên thu nhập (CIR)', 'Cost to income ratio'),
    ('LOAN_TO_DEPOSIT', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 4, 'NON_NEGATIVE', 'Dư nợ cho vay trên huy động (LDR)', 'Loan to deposit ratio')
on conflict (metric_code, catalog_version) do nothing;

update fundamental_metric_catalog set unit_type = 'PERCENT'
 where metric_code = 'DEBT_TO_EQUITY' and catalog_version = 'fundamental-metric-catalog-v1';
