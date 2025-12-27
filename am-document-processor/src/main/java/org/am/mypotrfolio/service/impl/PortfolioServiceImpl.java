package org.am.mypotrfolio.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import org.am.mypotrfolio.domain.common.MutualFundAsset;
import org.am.mypotrfolio.domain.common.DocumentRequest;
import org.am.mypotrfolio.domain.common.StockAsset;
import org.am.mypotrfolio.processor.FileProcessorFactory;
import org.am.mypotrfolio.service.PortfolioService;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.SecurityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service("documentProcessorPortfolioService")
@Slf4j
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {
    private final FileProcessorFactory fileProcessorFactory;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;


    @Override
    public List<EquityModel> processEquityFile(DocumentRequest portfolioRequest) {
        
        log.info("[ProcessId: {}] Starting to process stokcs portfolio file: {}", portfolioRequest.getRequestId(), portfolioRequest.getFile().getOriginalFilename());
        try {
            // Process the file using appropriate processor
            log.debug("[ProcessId: {}] Getting file processor for file type", portfolioRequest.getRequestId());
            List<Map<String, String>> fileData = fileProcessorFactory.getProcessor(portfolioRequest.getFile())
                    .processFile(portfolioRequest.getFile(), portfolioRequest);
            log.debug("[ProcessId: {}] Successfully processed file data, converting to StockPortfolio objects", portfolioRequest.getRequestId());
            return processPortfolioFileAndGetAssets(fileData, portfolioRequest.getBrokerType(), portfolioRequest.getRequestId());
        } catch (Exception e) {
            log.error("[ProcessId: {}] Error processing portfolio file: {}", portfolioRequest.getRequestId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<MutualFundModel> processMutualFundFile(DocumentRequest portfolioRequest) {
        log.info("[ProcessId: {}] Starting to process mutual funds portfolio file: {}", portfolioRequest.getRequestId(), portfolioRequest.getFile().getOriginalFilename());
        try {
            // Process the file using appropriate processor
            log.debug("[ProcessId: {}] Getting file processor for file type", portfolioRequest.getRequestId());
            List<Map<String, String>> fileData = fileProcessorFactory.getProcessor(portfolioRequest.getFile())
                    .processFile(portfolioRequest.getFile(), portfolioRequest);
            log.debug("[ProcessId: {}] Successfully processed file data, converting to StockPortfolio objects", portfolioRequest.getRequestId());
            return processMutualFundsPortfolioFileAndGetAssets(fileData, portfolioRequest.getBrokerType(), portfolioRequest.getRequestId());
        } catch (Exception e) {
            log.error("[ProcessId: {}] Error processing portfolio file: {}", portfolioRequest.getRequestId(), e.getMessage(), e);
            throw e;
        }
    }

    @SneakyThrows
    public List<MutualFundModel> processMutualFundsPortfolioFileAndGetAssets(List<Map<String, String>> fileData, BrokerType brokerType, UUID processId) {
       // Convert the data to StockPortfolio objects
       String payload = objectMapper.writeValueAsString(fileData);
       List<MutualFundAsset> portfolios = objectMapper.readValue(payload, new TypeReference<List<MutualFundAsset>>() {});
        // Convert to AssetModels
            List<MutualFundModel> portfolioAssets = new ArrayList<>();
            for (MutualFundAsset mutualFund : portfolios) {
                log.debug("[ProcessId: {}] Processing mutual funds: {}", processId, mutualFund.getSchemeName());
                // Try to find NSE security by name or other identifiers
                var assetModel = getMutualFundModel(mutualFund, brokerType);
                portfolioAssets.add(assetModel);
            }
       log.info("[ProcessId: {}] Successfully processed {} portfolio entries", processId, portfolioAssets.size());
       return portfolioAssets;
    }

    @SuppressWarnings("rawtypes")
    private MutualFundModel getMutualFundModel(MutualFundAsset mutualFund, BrokerType brokerType) {
        var quantity = getDouble(mutualFund.getUnits());
        var investedValue = mutualFund.getInvestedValue() != null ? getDouble(mutualFund.getInvestedValue()) : 0.0;
        var currentValue = mutualFund.getCurrentValue() != null ? getDouble(mutualFund.getCurrentValue()) : 0.0;
        MutualFundModel.MutualFundModelBuilder assetBuilder = MutualFundModel.builder()
                .assetType(AssetType.MUTUAL_FUND)
                .name(mutualFund.getSchemeName())
                .fundHouse(mutualFund.getAmc())
                .category(mutualFund.getCategory())
                .subCategory(mutualFund.getSubCategory())
                .quantity(quantity)
                .investmentValue(investedValue)
                .currentValue(currentValue);
        return assetBuilder.build();
    }

    @SneakyThrows
    public List<EquityModel> processPortfolioFileAndGetAssets(List<Map<String, String>> fileData, BrokerType brokerType, UUID processId) {
       // Convert the data to StockPortfolio objects
       String payload = objectMapper.writeValueAsString(fileData);
       List<StockAsset> portfolios = objectMapper.readValue(payload, new TypeReference<List<StockAsset>>() {});
        // Convert to AssetModels
            List<EquityModel> portfolioAssets = new ArrayList<>();
            for (StockAsset stock : portfolios) {
                log.debug("[ProcessId: {}] Processing stock: {}", processId, stock.getSymbol());
                // Try to find NSE security by name or other identifiers
                var assetModel = getAssetModel(stock, brokerType);
                portfolioAssets.add(assetModel);
            }
       log.info("[ProcessId: {}] Successfully processed {} portfolio entries", processId, portfolioAssets.size());
       return portfolioAssets;
    }

    @SuppressWarnings("rawtypes")
    private EquityModel getAssetModel(StockAsset stock, BrokerType brokerType) {
        var quantity = getDouble(stock.getQuantity());
        var avgBuyingPrice = stock.getAvgPrice() != null ? getDouble(stock.getAvgPrice()) : 0.0;
        var investedValue = stock.getInvestmentValue() != null ? getDouble(stock.getInvestmentValue())
                : quantity * avgBuyingPrice;
        EquityModel.EquityModelBuilder assetBuilder = EquityModel.builder()
                .assetType(AssetType.EQUITY)
                .isin(stock.getIsin())
                .symbol(stock.getSymbol())
                .avgBuyingPrice(avgBuyingPrice)
                .quantity(quantity)
                .investmentValue(investedValue)
                .name(stock.getName());

        if(brokerType != null && (brokerType.isDhan() || brokerType.isMStock())){
            Optional<SecurityModel> nseSecurity = findBestMatchBySearchParam(brokerType.isDhan() ? stock.getName() : stock.getSymbol());
            if (nseSecurity.isPresent()) {
                SecurityModel security = nseSecurity.get();
                stock.setIsin(security.getKey().getIsin());
            }
        }

        //if (stock.getIsin() == null || stock.getIsin().isEmpty()) {
            Optional<SecurityModel> nseSecurity = securityService.findByKey(stock.getIsin());
            // Enhance asset with NSE security information if available
            if (nseSecurity.isPresent()) {
                SecurityModel security = nseSecurity.get();
                assetBuilder.isin(security.getKey().getIsin());
                assetBuilder.symbol(security.getKey().getSymbol());
                assetBuilder.name(security.getMetadata().getSecurityName());
                assetBuilder.industry(security.getMetadata().getIndustry());
                assetBuilder.sector(security.getMetadata().getSector());
                assetBuilder.marketCap(security.getMetadata().getMarketCapType().getName());
            }
        //}
        return assetBuilder.build();
    }

    Optional<SecurityModel> findBestMatchBySearchParam(String searchParam) {
        if (searchParam == null || searchParam.trim().isEmpty()) {
            return Optional.empty();
        }
        List<SecurityModel> matches = securityService.findSecurityBySearchParam(searchParam);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
}
