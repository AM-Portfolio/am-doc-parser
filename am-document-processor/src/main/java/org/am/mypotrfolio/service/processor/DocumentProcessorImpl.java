package org.am.mypotrfolio.service.processor;

import java.util.List;

import org.am.mypotrfolio.domain.common.DocumentRequest;
import org.am.mypotrfolio.model.DocumentProcessResponse;
import org.am.mypotrfolio.model.trade.TradeModel;
import org.am.mypotrfolio.service.MessagingEventService;
import org.am.mypotrfolio.service.NseService;
import org.am.mypotrfolio.service.PortfolioService;
import org.am.mypotrfolio.service.TradeService;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.security.SecurityModel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentProcessorImpl implements DocumentProcessor {
    @org.springframework.beans.factory.annotation.Qualifier("documentProcessorPortfolioService")
    private final PortfolioService portfolioService;
    private final NseService nseService;
    private final MessagingEventService messagingEventService;
    private final TradeService tradeService;

    @Override
    public DocumentProcessResponse processDocument(DocumentRequest documentRequest, String portfolioId, String userId) {
        processPortfolio(documentRequest, portfolioId, userId);
        DocumentProcessResponse response = new DocumentProcessResponse();
        response.setDocumentType(documentRequest.getDocumentType().name());
        response.setFileName(documentRequest.getFile().getOriginalFilename());
        response.setProcessId(documentRequest.getRequestId());
        return response;
    }

    private void processPortfolio(DocumentRequest documentRequest, String portfolioId, String userId) {
        if(documentRequest.getDocumentType().isStockPortfolio()) {
            processEquityPortfolio(documentRequest, portfolioId, userId);
        } else if(documentRequest.getDocumentType().isMutualFund()) {
            processMutualFundsPortfolio(documentRequest, portfolioId, userId);
        } else if(documentRequest.getDocumentType().isNseIndices()) {
            processNseIndices(documentRequest, portfolioId);
        } else if(documentRequest.getDocumentType().isTradeFno()) {
            processTradeFno(documentRequest, portfolioId, userId);
        } else if(documentRequest.getDocumentType().isTradeEq()) {
            processTradeEq(documentRequest, portfolioId, userId);
        }
    }

    private void processEquityPortfolio(DocumentRequest documentRequest, String portfolioId, String userId) {
        List<EquityModel> assets = portfolioService.processEquityFile(documentRequest);
        messagingEventService.sendStockPortfolioMessage(assets, documentRequest.getRequestId(), documentRequest.getBrokerType(), portfolioId, userId);
    }

    private void processNseIndices(DocumentRequest documentRequest, String portfolioId) {
        List<SecurityModel> assets = nseService.processNseSecurity(documentRequest);
        //messagingEventService.sendNseIndicesMessage(assets, documentRequest.getRequestId(), documentRequest.getBrokerType());
    }

    private void processMutualFundsPortfolio(DocumentRequest documentRequest, String portfolioId, String userId) {
        List<MutualFundModel> mutualFunds = portfolioService.processMutualFundFile(documentRequest);
        messagingEventService.sendMutualFundPortfolioMessage(mutualFunds, documentRequest.getRequestId(), documentRequest.getBrokerType(), portfolioId, userId);
    }

    private void processTradeFno(DocumentRequest documentRequest, String portfolioId, String userId) {
        List<TradeModel> trades = tradeService.processTradeFile(documentRequest);
        messagingEventService.sendTradeFnoMessage(trades, documentRequest.getRequestId(), documentRequest.getBrokerType(), portfolioId, userId);
    }

    private void processTradeEq(DocumentRequest documentRequest, String portfolioId, String userId) {
        List<TradeModel> trades = tradeService.processTradeFile(documentRequest);
        messagingEventService.sendTradeEqMessage(trades, documentRequest.getRequestId(), documentRequest.getBrokerType(), portfolioId, userId);
    }

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.DHAN;
    }
}
