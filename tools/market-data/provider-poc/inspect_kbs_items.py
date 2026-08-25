from vnstock import Finance
import pandas as pd

symbols = ['VNM', 'SSI', 'MBB', 'VIC']
for sym in symbols:
    print(f"\n=================== {sym} ===================")
    f = Finance(symbol=sym, source='KBS')
    for stmt in ['income_statement', 'ratio', 'cash_flow']:
        try:
            df = getattr(f, stmt)(period='quarter')
            if df is not None and 'item_id' in df.columns:
                print(f"--- {stmt} ({len(df)} items) ---")
                for _, row in df.iterrows():
                    item_id = row['item_id']
                    # Get recent values
                    cols = [c for c in df.columns if c not in ('item', 'item_id')]
                    recent_val = row[cols[0]] if cols else ''
                    print(f"  {item_id:45s} | val: {recent_val}")
        except Exception as e:
            print(f"Error {stmt}: {e}")
