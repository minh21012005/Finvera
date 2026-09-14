export interface ToolSampleQuery {
  label: string;
  query: string;
  symbol?: string;
}

export interface SupportedTool {
  id: string;
  name: string;
  badge: string;
  category: 'MARKET' | 'TECHNICAL' | 'FUNDAMENTAL' | 'QUANT' | 'RESEARCH' | 'PORTFOLIO';
  categoryLabel: string;
  summary: string;
  description: string;
  sampleQueries: ToolSampleQuery[];
}

export const SUPPORTED_TOOLS: SupportedTool[] = [
  {
    id: 'MARKET',
    name: 'Tổng quan Thị trường',
    badge: 'MARKET',
    category: 'MARKET',
    categoryLabel: 'Thị trường & Vĩ mô',
    summary: 'Tra cứu trạng thái chỉ số VN-Index, HNX, độ rộng thị trường, thanh khoản và dòng tiền nhóm ngành.',
    description: 'Truy vấn dữ liệu vĩ mô và phiên giao dịch: Điểm số các chỉ số chính, thanh khoản khớp lệnh, số mã tăng/giảm/tham chiếu, dòng tiền vào các nhóm ngành (Ngân hàng, Bất động sản, Thép, Chứng khoán...).',
    sampleQueries: [
      { label: 'Diễn biến VN-Index', query: 'Tổng quan diễn biến thị trường và thanh khoản VN-Index hôm nay' },
      { label: 'Độ rộng thị trường', query: 'Độ rộng thị trường hôm nay thế nào, dòng tiền đang tập trung vào nhóm ngành nào?' },
    ],
  },
  {
    id: 'STOCK',
    name: 'Hồ sơ & Thị giá Cổ phiếu',
    badge: 'STOCK',
    category: 'FUNDAMENTAL',
    categoryLabel: 'Cơ bản Doanh nghiệp',
    summary: 'Tra cứu hồ sơ niêm yết, vốn hóa, khối lượng lưu hành, giá hiện tại và biên độ phiên.',
    description: 'Cung cấp thông tin tổng quan doanh nghiệp: Mã chứng khoán, tên công ty, sàn niêm yết (HSX, HNX, UPCOM), vốn hóa thị trường, KL lưu hành, giá mở cửa, cao nhất, thấp nhất và giá khớp lệnh mới nhất.',
    sampleQueries: [
      { label: 'Hồ sơ FPT', query: 'Thông tin cơ bản và quy mô vốn hóa thị trường của FPT', symbol: 'FPT' },
      { label: 'Hồ sơ VCB', query: 'Quy mô vốn hóa và thông tin niêm yết của Vietcombank', symbol: 'VCB' },
    ],
  },
  {
    id: 'TECHNICAL',
    name: 'Phân tích Kỹ thuật Định lượng',
    badge: 'TECHNICAL',
    category: 'TECHNICAL',
    categoryLabel: 'Kỹ thuật & Xu hướng',
    summary: 'Tính toán chỉ báo định lượng: RSI(14), MACD, Bollinger Bands, các đường MA20/50/200 và ATR.',
    description: 'Thực thi các thuật toán phân tích kỹ thuật chuẩn mực: Xác định vùng quá mua/quá bán (RSI), tín hiệu cắt nhau của đường MACD/Signal, độ biến động Bollinger Bands, hỗ trợ/kháng cự động qua các đường trung bình MA20, MA50, MA200.',
    sampleQueries: [
      { label: 'Kỹ thuật FPT', query: 'Phân tích kỹ thuật các chỉ báo RSI, MACD và các đường MA của FPT', symbol: 'FPT' },
      { label: 'Kỹ thuật HPG', query: 'Đánh giá xu hướng kỹ thuật và các vùng hỗ trợ/kháng cự của HPG', symbol: 'HPG' },
    ],
  },
  {
    id: 'FUNDAMENTAL',
    name: 'Báo cáo Tài chính & Chỉ số',
    badge: 'FUNDAMENTAL',
    category: 'FUNDAMENTAL',
    categoryLabel: 'Tài chính Doanh nghiệp',
    summary: 'Trích xuất BCTC: Doanh thu, Lợi nhuận sau thuế, EPS, P/E, P/B, ROE, ROA qua các quý gần nhất.',
    description: 'Truy vấn dữ liệu tài chính chính thống từ BCTC kiểm toán: Tăng trưởng doanh thu, tăng trưởng lợi nhuận ròng, biên lợi nhuận gộp/ròng, tỷ suất sinh lời trên vốn chủ sở hữu (ROE), ROA và đòn bẩy nợ.',
    sampleQueries: [
      { label: 'BCTC Vietcombank', query: 'Tình hình doanh thu, tăng trưởng lợi nhuận và ROE của VCB qua các quý gần nhất', symbol: 'VCB' },
      { label: 'Biên lợi nhuận HPG', query: 'Tình hình tài chính và biên lợi nhuận gộp của HPG trong các quý qua', symbol: 'HPG' },
    ],
  },
  {
    id: 'VALUATION',
    name: 'Mô hình Định giá So sánh',
    badge: 'VALUATION',
    category: 'FUNDAMENTAL',
    categoryLabel: 'Định giá Cổ phiếu',
    summary: 'Định giá so sánh lịch sử qua P/E Band, P/B Band nhiều năm và đối chiếu trung vị ngành.',
    description: 'Đánh giá mức độ đắt/rẻ của cổ phiếu dựa trên phân vị lịch sử 3-5 năm của P/E và P/B, định giá tương đối so với nhóm ngành cùng quy mô và phân tích biên an toàn đầu tư.',
    sampleQueries: [
      { label: 'Định giá MBB', query: 'Định giá cổ phiếu MBB theo P/E và P/B lịch sử 5 năm', symbol: 'MBB' },
      { label: 'Định giá VHM', query: 'P/B hiện tại của VHM so với mức trung bình trong quá khứ thế nào?', symbol: 'VHM' },
    ],
  },
  {
    id: 'STRATEGY_SCAN',
    name: 'Quét Chiến lược Quant Engine',
    badge: 'STRATEGY_SCAN',
    category: 'QUANT',
    categoryLabel: 'Chiến lược Định lượng',
    summary: 'Phát hiện tín hiệu tự động theo 8 mô hình quant: Breakout, Pullback, Momentum, Crossover...',
    description: 'Chạy engine định lượng để quét toàn bộ thị trường tìm kiếm các cổ phiếu kích hoạt tín hiệu giao dịch theo các chiến lược đã kiểm thử: Breakout đỉnh, Kéo ngược (Pullback), Động lượng (Momentum), MA Crossover, RSI Reversal, Mean Reversion.',
    sampleQueries: [
      { label: 'Quét Breakout', query: 'Quét các cổ phiếu đang có tín hiệu Breakout vượt nền giá hôm nay' },
      { label: 'Quét Pullback', query: 'Có mã cổ phiếu nào đang trong pha Pullback về vùng hỗ trợ lành mạnh?' },
    ],
  },
  {
    id: 'SCREENING',
    name: 'Bộ lọc Cổ phiếu Đa tiêu chí',
    badge: 'SCREENING',
    category: 'QUANT',
    categoryLabel: 'Sàng lọc Cơ hội',
    summary: 'Sàng lọc cổ phiếu kết hợp tiêu chuẩn cơ bản (ROE, P/E, vốn hóa) và thanh khoản kỹ thuật.',
    description: 'Lọc hàng trăm mã cổ phiếu trên 3 sàn theo các bộ tiêu chuẩn khắt khe: Vốn hóa lớn/vừa, ROE cao (>15%), P/E hấp dẫn, thanh khoản trung bình 20 phiên đáp ứng quy mô đầu tư.',
    sampleQueries: [
      { label: 'Lọc ROE cao, P/E thấp', query: 'Lọc các mã vốn hóa trên 10.000 tỷ có ROE > 18% và P/E < 15' },
      { label: 'Lọc tăng trưởng lợi nhuận', query: 'Tìm các cổ phiếu có tăng trưởng lợi nhuận quý gần nhất trên 25%' },
    ],
  },
  {
    id: 'COMPARE',
    name: 'So sánh Đa Cổ phiếu',
    badge: 'COMPARE',
    category: 'FUNDAMENTAL',
    categoryLabel: 'Phân tích Đối chiếu',
    summary: 'Đối chiếu trực diện các chỉ số tài chính, định giá và sức mạnh giá giữa nhiều cổ phiếu.',
    description: 'So sánh ma trận đa chiều giữa 2 hoặc nhiều mã cùng ngành (ví dụ Thép: HPG vs NKG vs HSG; Ngân hàng: VCB vs TCB vs MBB) về định giá P/E, P/B, ROE, biên lợi nhuận và sức mạnh giá RS.',
    sampleQueries: [
      { label: 'So sánh ngành Thép', query: 'So sánh các chỉ số tài chính và định giá giữa HPG, NKG và HSG' },
      { label: 'So sánh Ngân hàng', query: 'So sánh ROE, P/B và chất lượng tài sản giữa VCB, TCB và MBB' },
    ],
  },
  {
    id: 'RESEARCH_RAG',
    name: 'Hybrid RAG Nghiên cứu Tài liệu',
    badge: 'RESEARCH_RAG',
    category: 'RESEARCH',
    categoryLabel: 'Kho Tri thức & RAG',
    summary: 'Truy vấn ngữ nghĩa kết hợp BM25 trên kho tài liệu cáo bạch, BCTC kiểm toán và báo cáo phân tích.',
    description: 'Công nghệ Hybrid RAG (Dense Vector + BM25 Sparse Search) trích xuất chính xác các đoạn văn bản quan trọng kèm nguồn dẫn chứng (trang, tài liệu) từ kho báo cáo thường niên, tài liệu ĐHCĐ và nghiên cứu ngành.',
    sampleQueries: [
      { label: 'Kế hoạch MWG', query: 'Kế hoạch mở rộng chuỗi và định hướng kinh doanh của MWG trong tài liệu ĐHCĐ', symbol: 'MWG' },
      { label: 'Triển vọng ngành Thép', query: 'Các yếu tố tác động đến chu kỳ xuất khẩu và tiêu thụ thép Việt Nam' },
    ],
  },
  {
    id: 'NEWS',
    name: 'Tin tức & Sự kiện Doanh nghiệp',
    badge: 'NEWS',
    category: 'MARKET',
    categoryLabel: 'Tin tức & Sự kiện',
    summary: 'Cập nhật tin tức tài chính, sự kiện trả cổ tức, ĐHCĐ và thông tin vĩ mô ảnh hưởng cổ phiếu.',
    description: 'Tổng hợp và trích xuất các thông cáo báo chí, thông tin giao dịch cổ đông lớn, lịch trả cổ tức tiền mặt/cổ phiếu và các tin tức thời sự ảnh hưởng trực tiếp đến diễn biến giá cổ phiếu.',
    sampleQueries: [
      { label: 'Tin tức & Cổ tức FPT', query: 'Tin tức mới nhất và sự kiện chia cổ tức của FPT gần đây', symbol: 'FPT' },
      { label: 'Sự kiện thị trường tuần này', query: 'Điểm tin tức tài chính nổi bật tác động đến tâm lý thị trường tuần này' },
    ],
  },
  {
    id: 'PORTFOLIO',
    name: 'Phân tích Danh mục Cá nhân',
    badge: 'PORTFOLIO',
    category: 'PORTFOLIO',
    categoryLabel: 'Danh mục Đầu tư',
    summary: 'Tra cứu danh mục tài khoản của bạn, phân tích tỷ trọng tiền/cổ phiếu, lãi lỗ và rủi ro vị thế.',
    description: 'Truy cập dữ liệu danh mục đầu tư nội bộ được quản lý trong tài khoản của bạn: Tính toán tỷ trọng tiền mặt, cơ cấu phân bổ từng mã, giá vốn bình quân, lãi/lỗ chưa thực hiện và cảnh báo độ tập trung.',
    sampleQueries: [
      { label: 'Xem danh mục của tôi', query: 'Tổng hợp trạng thái danh mục hiện tại và tỷ trọng các mã của tôi' },
      { label: 'Đánh giá rủi ro danh mục', query: 'Phân tích mức độ phân bổ và rủi ro trong danh mục đầu tư của tôi' },
    ],
  },
];
