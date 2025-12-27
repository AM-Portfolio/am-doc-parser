package org.am.mypotrfolio.service;

import org.am.mypotrfolio.domain.common.DocumentType;
import org.am.mypotrfolio.domain.common.DocumentRequest;
import org.am.mypotrfolio.model.DocumentProcessResponse;
import org.am.mypotrfolio.model.ProcessingStatus;
import org.am.mypotrfolio.service.processor.DocumentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.am.common.amcommondata.model.enums.BrokerType;

import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DocumentProcessorService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessorService.class);

    private final Map<UUID, ProcessingStatus> processStatusMap = new ConcurrentHashMap<>();
    private final DocumentProcessor documentProcessor;

    public DocumentProcessResponse processDocument(MultipartFile file, DocumentType documentType, String portfolioId, String userId) {
        var documentRequest = getDocumentRequest(file, documentType, portfolioId, userId);
        log.info("[ProcessId: {}] Starting document processing for type: {}", documentRequest.getRequestId(), documentType);
        processStatusMap.put(documentRequest.getRequestId(), ProcessingStatus.QUEUED);
        
        try {
                // Extract broker type from file name or content
                log.debug("[ProcessId: {}] Detecting broker type from file", documentRequest.getRequestId());
                log.info("[ProcessId: {}] Detected broker type: {}", documentRequest.getRequestId(), documentRequest.getBrokerType());
                
                processStatusMap.put(documentRequest.getRequestId(), ProcessingStatus.PROCESSING);
                log.info("[ProcessId: {}] Processing document with {} processor", documentRequest.getRequestId(), documentRequest.getBrokerType());
                DocumentProcessResponse response = documentProcessor.processDocument(documentRequest, portfolioId, userId);
                response.setProcessId(documentRequest.getRequestId());
                response.setStatus(ProcessingStatus.COMPLETED);
                processStatusMap.put(documentRequest.getRequestId(), ProcessingStatus.COMPLETED);
                
                log.info("[ProcessId: {}] Successfully completed document processing", documentRequest.getRequestId());
                return response;
        
        } catch (Exception e) {
            log.error("[ProcessId: {}] Failed to process document: {}", documentRequest.getRequestId(), e.getMessage(), e);
            processStatusMap.put(documentRequest.getRequestId(), ProcessingStatus.FAILED);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private DocumentRequest getDocumentRequest(MultipartFile file, DocumentType documentType, String portfolioId, String userId) {
        UUID processId = UUID.randomUUID();
        BrokerType brokerType = detectBrokerType(file);
        return DocumentRequest.builder().file(file).documentType(documentType).requestId(processId).brokerType(brokerType).portfolioId(portfolioId).userId(userId).build();
    }

    public List<DocumentProcessResponse> processBatchDocuments(List<MultipartFile> files,  DocumentType documentType, String portfolioId, String userId) {
        UUID batchId = UUID.randomUUID();
        log.info("[BatchId: {}] Starting batch processing of {} documents", batchId, files.size());
        List<DocumentProcessResponse> responses = new ArrayList<>();
        
        for (MultipartFile file : files) {
            responses.add(processDocument(file, documentType, portfolioId, userId));
        }
        
        log.info("[BatchId: {}] Completed batch processing", batchId);
        return responses;
    }

    public ProcessingStatus getProcessingStatus(UUID processId) {
        return processStatusMap.getOrDefault(processId, ProcessingStatus.FAILED);
    }

    public List<String> getSupportedDocumentTypes() {
        List<String> types = new ArrayList<>();
        types.add("BROKER_PORTFOLIO");
        types.add("MUTUAL_FUND");
        types.add("NPS_STATEMENT");
        types.add("COMPANY_FINANCIAL_REPORT");
        types.add("STOCK_PORTFOLIO");
        types.add("NSE_INDICES");
        return types;
    }

    private BrokerType detectBrokerType(MultipartFile file) {
        String filename = file.getOriginalFilename().toUpperCase();
        if (filename.contains("DHAN")) {
            return BrokerType.DHAN;
        } else if (filename.contains("ZERODHA")) {
            return BrokerType.ZERODHA;
        }
        else if (filename.contains("MSTOCK")) {
            return BrokerType.MSTOCK;
        }
        else if (filename.contains("GROWW")) {
            return BrokerType.GROW;
        }
        return null;
        //throw new IllegalArgumentException("Unable to detect broker type from file: " + filename);
    }

    private DocumentProcessResponse processOtherDocumentTypes(UUID processId, MultipartFile file, String documentType) {
        log.info("[ProcessId: {}] Processing other document type: {}", processId, documentType);
        DocumentProcessResponse response = new DocumentProcessResponse();
        response.setProcessId(processId);
        response.setDocumentType(documentType);
        response.setFileName(file.getOriginalFilename());
        response.setStatus(ProcessingStatus.COMPLETED);
        response.setMessage("Processed " + documentType);
        log.info("[ProcessId: {}] Completed processing other document type", processId);
        return response;
    }
}
