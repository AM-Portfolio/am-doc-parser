package org.am.mypotrfolio.mapper;

import org.am.mypotrfolio.model.trade.*;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.enums.BrokerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mapper utility class for converting between Trade and TradeModel objects.
 */
@Component
public class TradeMapper {

    /**
     * Converts a Trade object to a TradeModel object.
     *
     * @param trade the Trade object to convert
     * @return the converted TradeModel object
     */
    public TradeModel toTradeModel(Trade trade, BrokerType brokerType) {
        if (trade == null) {
            return null;
        }

        return TradeModel.builder()
                .basicInfo(buildBasicInfo(trade, brokerType))
                .instrumentInfo(buildInstrumentInfo(trade))
                .executionInfo(buildExecutionInfo(trade))
                .build();
    }

    private TradeModel.BasicInfo buildBasicInfo(Trade trade, BrokerType brokerType) {
        return TradeModel.BasicInfo.builder()
                .tradeId(trade.getTradeId())
                .orderId(trade.getOrderId())
                .tradeDate(trade.getTradeDate())
                .orderExecutionTime(trade.getOrderExecutionTime())
                .brokerType(brokerType) // Set appropriate broker type if available
                .tradeType(TradeType.valueOf(trade.getTradeType().toUpperCase()))   // Set appropriate trade type if available
                .build();
    }


    private TradeModel.InstrumentInfo buildInstrumentInfo(Trade trade) {
        TradeModel.InstrumentInfo.InstrumentInfoBuilder builder = TradeModel.InstrumentInfo.builder()
                .symbol(trade.getSymbol())
                .isin(trade.getIsin())
                .exchange(trade.getExchange())
                .segment(trade.getSegment())
                .series(trade.getSeries());
                
        // If segment is F&O, add FnO info
        if (trade.getSegment() == Segment.FUTURES || 
            trade.getSegment() == Segment.OPTIONS || 
            trade.getSegment() == Segment.FNO ||
            "FO".equalsIgnoreCase(trade.getSegment().getValue())) {
            builder.fnoInfo(buildFnOInfo(trade));
        }
        
        return builder.build();
    }

    private TradeModel.ExecutionInfo buildExecutionInfo(Trade trade) {
        String symbol = trade.getSymbol();
        BigDecimal lotSize = determineLotSize(symbol, trade.getTradeDate());
        TradeModel.ExecutionInfo.ExecutionInfoBuilder executionInfoBuilder = TradeModel.ExecutionInfo.builder()
                .tradeType(TradeType.valueOf(trade.getTradeType().toUpperCase()))
                .auction(trade.getAuction())
                .quantity(trade.getQuantity().intValue())
                .price(trade.getPrice());

        if (lotSize != null) {
            executionInfoBuilder.lotSize(trade.getQuantity().intValue() / lotSize.intValue());
        }
        
        return executionInfoBuilder.build();
    }

    /**
     * Builds FnO information by parsing the trade symbol.
     * Examples:
     * - RELIANCE20AUGFUT -> Equity Future
     * - BANKNIFTY20AUG23000PE -> Index Option
     *
     * @param trade the trade object
     * @return FnOInfo object with parsed details
     */
    private TradeModel.FnOInfo buildFnOInfo(Trade trade) {
        String symbol = trade.getSymbol();
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        
        TradeModel.FnOInfo.FnOInfoBuilder builder = TradeModel.FnOInfo.builder();
        
        // Check if it's a future (ends with FUT)
        if (symbol.endsWith("FUT")) {
            // It's a future
            String baseSymbol = extractBaseSymbol(symbol, "FUT");
            LocalDate expiryDate = extractExpiryDate(symbol);
            
            // Determine if it's an index future or equity future
            FNOTradeType instrumentType = isIndex(baseSymbol) ? FNOTradeType.FUTIDX : FNOTradeType.FUTEQ;
            
            builder.instrumentType(instrumentType)
                   .expiryDate(expiryDate)
                   .optionType(OptionType.NONE);
                   
        } else if (symbol.endsWith("CE") || symbol.endsWith("PE")) {
            // It's an option
            OptionType optionType = symbol.endsWith("CE") ? OptionType.CALL : OptionType.PUT;
            String baseSymbol;
            BigDecimal strikePrice = null;
            
            // Extract strike price - it's the numeric part before CE/PE
            Pattern pattern = Pattern.compile("(\\d+)(CE|PE)$");
            Matcher matcher = pattern.matcher(symbol);
            if (matcher.find()) {
                strikePrice = new BigDecimal(matcher.group(1));
                baseSymbol = symbol.substring(0, symbol.length() - matcher.group().length());
            } else {
                baseSymbol = extractBaseSymbol(symbol, optionType.getValue());
            }
            
            LocalDate expiryDate = extractExpiryDate(symbol);
            
            // Determine if it's an index option or equity option
            FNOTradeType instrumentType = isIndex(baseSymbol) ? FNOTradeType.OPTIDX : FNOTradeType.OPTEQ;
            
            builder.instrumentType(instrumentType)
                   .expiryDate(expiryDate)
                   .strikePrice(strikePrice)
                   .optionType(optionType);
        }
        
        // Set lot size based on trade date
        builder.lotSize(determineLotSize(symbol, trade.getTradeDate()));
        
        return builder.build();
    }
    
    /**
     * Extracts the base symbol from the F&O symbol
     */
    private String extractBaseSymbol(String symbol, String suffix) {
        // Remove the suffix and any date/month information
        String baseSymbol = symbol.replace(suffix, "");
        
        // Find where the date/month part starts (usually after letters)
        Pattern pattern = Pattern.compile("^([A-Za-z&]+)");
        Matcher matcher = pattern.matcher(baseSymbol);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return baseSymbol;
    }
    
    /**
     * Extracts the expiry date from the symbol
     */
    private LocalDate extractExpiryDate(String symbol) {
        // This is a simplified implementation - actual implementation would need to handle
        // various date formats like 20AUG, 20AUG23, etc.
        try {
            // Try to find a date pattern like 20AUG or 20AUG23
            Pattern pattern = Pattern.compile("(\\d{2})([A-Za-z]{3})(\\d{0,2})");
            Matcher matcher = pattern.matcher(symbol);
            if (matcher.find()) {
                String day = matcher.group(1);
                String month = matcher.group(2);
                String year = matcher.group(3);
                
                if (year.isEmpty()) {
                    // If year is not specified, use current year
                    year = String.valueOf(LocalDate.now().getYear() % 100);
                }
                
                // Parse the date
                String dateStr = day + "-" + month + "-" + "20" + year;
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MMM-yy"));
            }
        } catch (DateTimeParseException e) {
            // Log error and return null or today's date
        }
        
        return null;
    }
    
    /**
     * Determines if the symbol is an index
     */
    private boolean isIndex(String symbol) {
        return IndexType.isIndex(symbol);
    }
    
    /**
     * Determines the lot size based on the symbol and trade date
     * 
     * @param symbol the trade symbol
     * @param tradeDate the date of the trade
     * @return the lot size applicable for the symbol on the given date
     */
    private BigDecimal determineLotSize(String symbol, LocalDate tradeDate) {
        String baseSymbol = extractBaseSymbol(symbol, "");
        return IndexType.getLotSizeForSymbol(baseSymbol, tradeDate);
    }

    /**
     * Determines the current lot size based on the symbol
     * 
     * @param symbol the trade symbol
     * @return the current lot size applicable for the symbol
     */
    private BigDecimal determineLotSize(String symbol) {
        return determineLotSize(symbol, LocalDate.now());
    }
}
