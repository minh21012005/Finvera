import { useEffect, useState } from "react";
import { getMarketOverview, type MarketRegime } from "../../market-overview/api/market-overview";
import { Activity, TrendingUp, ShieldAlert, ArrowRight } from "lucide-react";
import { navigate } from "../../../router";

export function MarketRegimeBanner() {
  const [regime, setRegime] = useState<MarketRegime | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    getMarketOverview()
      .then((ov) => {
        if (active) {
          setRegime(ov.regime);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  if (loading || !regime || !regime.label) return null;

  const isBull = regime.label === "BULL" || regime.label === "EARLY_BULL";
  const isBear = regime.label === "BEAR" || regime.label === "EARLY_BEAR";

  const config = isBull
    ? {
        borderClass: "border-emerald-500/40 bg-emerald-950/20",
        badgeClass: "bg-emerald-500/20 text-emerald-300 border-emerald-500/50",
        icon: <TrendingUp className="text-emerald-400" size={18} />,
        title: "Bối Cảnh Thị Trường: TĂNG TRƯỞNG (BULL MARKET)",
        allocation: "80% – 100% Vốn",
        recommendation:
          "Môi trường thị trường thuận lợi cho vị thế Mua mới. Ưu tiên chiến lược Bám theo xu hướng (Trend Following) hoặc Bắt nhịp điều chỉnh (Pullback) ở cổ phiếu nhóm dẫn dắt.",
      }
    : isBear
    ? {
        borderClass: "border-rose-500/40 bg-rose-950/20",
        badgeClass: "bg-rose-500/20 text-rose-300 border-rose-500/50",
        icon: <ShieldAlert className="text-rose-400" size={18} />,
        title: "Bối Cảnh Thị Trường: XU HƯỚNG GIẢM (BEAR MARKET)",
        allocation: "0% – 20% Vốn (Ưu tiên tiền mặt)",
        recommendation:
          "Rủi ro thị trường chung ở mức cao, xác suất thất bại của các vị thế Mua tăng vọt. Khuyến nghị hạ tỷ trọng cổ phiếu, giữ tỷ trọng tiền mặt lớn và tuân thủ nghiêm ngặt mức Dừng lỗ (Stop Loss).",
      }
    : {
        borderClass: "border-amber-500/40 bg-amber-950/20",
        badgeClass: "bg-amber-500/20 text-amber-300 border-amber-500/50",
        icon: <Activity className="text-amber-400" size={18} />,
        title: "Bối Cảnh Thị Trường: ĐI NGANG TÍCH LŨY (SIDEWAYS)",
        allocation: "40% – 60% Vốn",
        recommendation:
          "Thị trường phân hóa mạnh và thiếu xu hướng dẫn dắt. Thận trọng với các pha Breakout giả; ưu tiên chiến lược Pullback hoặc Mean Reversion tại các vùng hỗ trợ cứng.",
      };

  return (
    <aside
      aria-label="Định hướng chiến lược theo bối cảnh thị trường"
      className={`mb-6 p-4 rounded-xl border ${config.borderClass} backdrop-blur-sm transition-all`}
    >
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="p-2 rounded-lg bg-slate-900/80 border border-slate-800 shrink-0 mt-0.5">
            {config.icon}
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap mb-1">
              <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${config.badgeClass}`}>
                {regime.label} · {regime.score !== null ? `${regime.score}/100 điểm` : ""}
              </span>
              <span className="text-xs text-slate-400">
                Tỷ trọng danh mục khuyến nghị: <strong className="text-slate-200">{config.allocation}</strong>
              </span>
            </div>
            <h2 className="text-sm font-bold text-slate-100 mb-1">{config.title}</h2>
            <p className="text-xs text-slate-300 leading-relaxed max-w-4xl">{config.recommendation}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => navigate("/market-overview")}
          className="self-start md:self-center shrink-0 flex items-center gap-1.5 text-xs text-cyan-400 hover:text-cyan-300 transition-colors font-medium px-3 py-1.5 rounded-lg bg-slate-900/60 border border-slate-700/60 hover:border-cyan-500/50"
        >
          Xem Tổng quan Thị trường <ArrowRight size={14} />
        </button>
      </div>
    </aside>
  );
}
