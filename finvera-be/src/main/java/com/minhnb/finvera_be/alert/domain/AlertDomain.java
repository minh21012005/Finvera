package com.minhnb.finvera_be.alert.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Set;

public final class AlertDomain {
    private AlertDomain() {}
    public static final String RULE_VERSION="alert-condition-v1";
    public enum Type { PRICE_ABOVE,PRICE_BELOW,RSI_ABOVE,RSI_BELOW,MACD_BULLISH_CROSS,MACD_BEARISH_CROSS,
        MA_BULLISH_CROSS,MA_BEARISH_CROSS,VOLUME_SPIKE,BREAKOUT,BREAKDOWN,MARKET_REGIME_CHANGE,
        STRATEGY_SIGNAL,PORTFOLIO_CONCENTRATION_ABOVE,NEW_DOCUMENT }
    public enum Outcome { FALSE,TRUE,WITHHELD,FAILED }
    public enum EpisodeState { UNKNOWN,FALSE,TRUE }
    public record Decision(Outcome outcome,String reasonCode,JsonNode evidence,String factKey,
            java.time.Instant factAt,java.time.Instant acceptedAt,String eventKey) {}
    public static Type validate(JsonNode c) {
        if(c==null||!c.isObject())throw new IllegalArgumentException("CONDITION_INVALID");
        Type type; try{type=Type.valueOf(text(c,"type"));}catch(Exception e){throw new IllegalArgumentException("CONDITION_TYPE_UNSUPPORTED");}
        switch(type){
            case PRICE_ABOVE,PRICE_BELOW -> {only(c,"type","symbol","threshold","adjustmentBasis");symbol(c);positive(c,"threshold");requiredBasis(c);}
            case RSI_ABOVE,RSI_BELOW -> {only(c,"type","symbol","threshold");symbol(c);range(c,"threshold",BigDecimal.ZERO,new BigDecimal("100"));}
            case MACD_BULLISH_CROSS,MACD_BEARISH_CROSS -> {only(c,"type","symbol");symbol(c);}
            case MA_BULLISH_CROSS,MA_BEARISH_CROSS -> {only(c,"type","symbol","shortWindow","longWindow");symbol(c);int s=window(c,"shortWindow"),l=window(c,"longWindow");if(s>=l)bad();}
            case VOLUME_SPIKE -> {only(c,"type","symbol","threshold");symbol(c);positive(c,"threshold");}
            case BREAKOUT,BREAKDOWN -> {only(c,"type","symbol","adjustmentBasis");symbol(c);requiredBasis(c);}
            case MARKET_REGIME_CHANGE -> {only(c,"type","targetLabel");allowed(c,"targetLabel",Set.of("BULL","EARLY_BULL","SIDEWAYS","EARLY_BEAR","BEAR"));}
            case STRATEGY_SIGNAL -> {only(c,"type","symbol","strategyCode");symbol(c);allowed(c,"strategyCode",Set.of("TREND_FOLLOWING","MOMENTUM","BREAKOUT","PULLBACK","MEAN_REVERSION","MA_CROSSOVER","MACD_BASED","RSI_BASED"));}
            case PORTFOLIO_CONCENTRATION_ABOVE -> {only(c,"type","portfolioId","thresholdPercent");uuid(c,"portfolioId");rangeExclusiveZero(c,"thresholdPercent",new BigDecimal("100"));}
            case NEW_DOCUMENT -> {only(c,"type","symbol","documentType");symbol(c);if(c.hasNonNull("documentType"))allowed(c,"documentType",Set.of("ANNUAL_REPORT","QUARTERLY_REPORT","FINANCIAL_REPORT","ECONOMIC_REPORT","INVESTOR_PRESENTATION","CORPORATE_DISCLOSURE","OTHER"));}
        }
        return type;
    }
    public static Type normalize(JsonNode c) {
        Type type=validate(c);
        ObjectNode normalized=(ObjectNode)c;
        if(c.hasNonNull("symbol"))normalized.put("symbol",symbol(c));
        if(c.hasNonNull("threshold"))normalized.put("threshold",canonicalDecimal(c,"threshold"));
        if(c.hasNonNull("thresholdPercent"))normalized.put("thresholdPercent",canonicalDecimal(c,"thresholdPercent"));
        return type;
    }
    public static String summary(JsonNode c){Type t=validate(c);return switch(t){
        case PRICE_ABOVE -> symbol(c)+" giá đóng cửa ≥ "+text(c,"threshold")+" VND";
        case PRICE_BELOW -> symbol(c)+" giá đóng cửa ≤ "+text(c,"threshold")+" VND";
        case RSI_ABOVE -> symbol(c)+" RSI14 ≥ "+text(c,"threshold");
        case RSI_BELOW -> symbol(c)+" RSI14 ≤ "+text(c,"threshold");
        case MACD_BULLISH_CROSS -> symbol(c)+" MACD cắt lên Signal";
        case MACD_BEARISH_CROSS -> symbol(c)+" MACD cắt xuống Signal";
        case MA_BULLISH_CROSS -> symbol(c)+" MA"+c.get("shortWindow").asInt()+" cắt lên MA"+c.get("longWindow").asInt();
        case MA_BEARISH_CROSS -> symbol(c)+" MA"+c.get("shortWindow").asInt()+" cắt xuống MA"+c.get("longWindow").asInt();
        case VOLUME_SPIKE -> symbol(c)+" khối lượng ≥ "+text(c,"threshold")+"× TB20";
        case BREAKOUT -> symbol(c)+" đóng cửa vượt đỉnh 20 phiên"; case BREAKDOWN -> symbol(c)+" đóng cửa thủng đáy 20 phiên";
        case MARKET_REGIME_CHANGE -> "Thị trường chuyển sang "+text(c,"targetLabel");
        case STRATEGY_SIGNAL -> symbol(c)+" có tín hiệu "+text(c,"strategyCode");
        case PORTFOLIO_CONCENTRATION_ABOVE -> "Tỷ trọng lớn nhất ≥ "+text(c,"thresholdPercent")+"%";
        case NEW_DOCUMENT -> "Tài liệu mới"+(c.hasNonNull("symbol")?" cho "+symbol(c):"")+(c.hasNonNull("documentType")?" loại "+text(c,"documentType"):"");};}
    public static String symbol(JsonNode c){String s=text(c,"symbol").trim().toUpperCase();if(!s.matches("[A-Z0-9]{1,10}"))bad();return s;}
    public static BigDecimal decimal(JsonNode c,String f){try{BigDecimal value=new BigDecimal(text(c,f)).stripTrailingZeros();if(Math.max(0,value.scale())>12)bad();return value;}catch(Exception e){throw new IllegalArgumentException("CONDITION_INVALID");}}
    private static String canonicalDecimal(JsonNode c,String f){return decimal(c,f).toPlainString();}
    private static void positive(JsonNode c,String f){if(decimal(c,f).signum()<=0)bad();}
    private static void range(JsonNode c,String f,BigDecimal min,BigDecimal max){BigDecimal v=decimal(c,f);if(v.compareTo(min)<0||v.compareTo(max)>0)bad();}
    private static void rangeExclusiveZero(JsonNode c,String f,BigDecimal max){BigDecimal v=decimal(c,f);if(v.signum()<=0||v.compareTo(max)>0)bad();}
    private static int window(JsonNode c,String f){int v=c.path(f).asInt(-1);if(!Set.of(20,50,200).contains(v))bad();return v;}
    private static void requiredBasis(JsonNode c){allowed(c,"adjustmentBasis",Set.of("RAW","PROVIDER_ADJUSTED"));}
    private static void allowed(JsonNode c,String f,Set<String> a){if(!a.contains(text(c,f)))bad();}
    private static void uuid(JsonNode c,String f){try{java.util.UUID.fromString(text(c,f));}catch(Exception e){bad();}}
    private static void only(JsonNode c,String... allowed){if(!Set.of(allowed).containsAll(c.propertyNames()))bad();}
    private static String text(JsonNode c,String f){if(!c.hasNonNull(f)||c.get(f).asText().isBlank())bad();return c.get(f).asText();}
    private static void bad(){throw new IllegalArgumentException("CONDITION_INVALID");}
}
