from vnstock import Finance
import pandas as pd

symbols = [
    # Banks
    'VCB', 'TCB', 'MBB', 'CTG',
    # Securities
    'SSI', 'VND', 'VCI', 'HCM',
    # Real Estate
    'VIC', 'VHM', 'NVL', 'KDH',
    # Industrial / Materials
    'HPG', 'VNM', 'GMD', 'MSN',
    # Retail / Tech / Energy
    'MWG', 'FPT', 'GAS', 'PLX'
]

print(f"Probing {len(symbols)} symbols across all market sectors...")
results = []

for sym in symbols:
    try:
        f = Finance(symbol=sym, source='KBS')
        df_ratio = f.ratio(period='quarter')
        df_income = f.income_statement(period='quarter')
        
        ratio_items = set(df_ratio['item_id'].dropna().tolist()) if df_ratio is not None and 'item_id' in df_ratio.columns else set()
        income_items = set(df_income['item_id'].dropna().tolist()) if df_income is not None and 'item_id' in df_income.columns else set()
        
        # Check specific critical items
        has_bvps = 'book_value_per_share_bvps' in ratio_items
        has_trailing_eps = 'trailing_eps' in ratio_items
        has_is_eps = 'earnings_per_share_vnd' in income_items
        has_div_yield = 'dividend_yield' in ratio_items
        has_op_profit = 'operating_profit' in income_items
        has_revenue = 'revenue' in income_items
        has_roe = 'roe' in ratio_items
        has_roa = 'roa' in ratio_items
        
        # Get sample values for latest quarter
        cols_r = [c for c in df_ratio.columns if c not in ('item', 'item_id')] if df_ratio is not None else []
        latest_q = cols_r[0] if cols_r else 'N/A'
        
        bvps_val = df_ratio[df_ratio['item_id'] == 'book_value_per_share_bvps'][latest_q].values[0] if has_bvps and cols_r else None
        trailing_eps_val = df_ratio[df_ratio['item_id'] == 'trailing_eps'][latest_q].values[0] if has_trailing_eps and cols_r else None
        
        results.append({
            'symbol': sym,
            'latest_q': latest_q,
            'has_bvps': has_bvps,
            'bvps_val': bvps_val,
            'has_trailing_eps': has_trailing_eps,
            'trailing_eps_val': trailing_eps_val,
            'has_is_eps': has_is_eps,
            'has_div_yield': has_div_yield,
            'has_op_profit': has_op_profit,
            'has_revenue': has_revenue,
        })
    except Exception as e:
        results.append({'symbol': sym, 'error': str(e)})

df_res = pd.DataFrame(results)
print(df_res.to_string())
