package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service class for handling lead deduplication
 */
public class LeadDeduplicationService {
    private final ObjectMapper objectMapper;
    private static final Logger logger = Logger.getLogger(LeadDeduplicationService.class.getName());
    
    static {
        // Configure logging to use single-line format
        setupSingleLineLogging();
    }
    
    public LeadDeduplicationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Sets up single-line logging format
     */
    private static void setupSingleLineLogging() {
        // Remove default handlers
        Logger rootLogger = Logger.getLogger("");
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        
        // Create custom formatter for single-line output
        Formatter singleLineFormatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                String level = record.getLevel().getName();
                String message = record.getMessage();
                return String.format("[%s] %s%n", level, message);
            }
        };
        
        // Create console handler with custom formatter
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(singleLineFormatter);
        
        // Add handler to root logger
        rootLogger.addHandler(consoleHandler);
        rootLogger.setLevel(Level.INFO);
    }
    
    /**
     * Reads leads from a JSON file
     */
    public LeadsData readLeadsFromFile(String filePath) throws IOException {
        logger.info("Reading leads from file: " + filePath);
        File file = new File(filePath);
        LeadsData data = objectMapper.readValue(file, LeadsData.class);
        logger.info("Successfully read " + (data.getLeads() != null ? data.getLeads().size() : 0) + " leads from file");
        return data;
    }
    
    /**
     * Writes leads to a JSON file
     */
    public void writeLeadsToFile(LeadsData leadsData, String filePath) throws IOException {
        logger.info("Writing " + (leadsData.getLeads() != null ? leadsData.getLeads().size() : 0) + " leads to file: " + filePath);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), leadsData);
        logger.info("Successfully wrote leads to file: " + filePath);
    }
    
    /**
     * Deduplicates leads based on the following rules:
     * 1. The data from the newest date should be preferred
     * 2. Duplicate IDs count as dups. Duplicate emails count as dups. Both must be unique
     * 3. If the dates are identical, the data from the record provided last in the list should be preferred
     */
    public List<Lead> deduplicateLeads(List<Lead> leads) {
        logger.info("Starting deduplication process for " + (leads != null ? leads.size() : 0) + " leads");
        
        if (leads == null || leads.isEmpty()) {
            logger.info("No leads to deduplicate, returning empty list");
            return new ArrayList<>();
        }

        // First pass: deduplicate by ID
        Map<String, Lead> leadsById = new HashMap<>();
        for (int i = 0; i < leads.size(); i++) {
            Lead lead = leads.get(i);
            lead.setIndex(i);

            if (leadsById.containsKey(lead.getId())) {
                Lead existingLead = leadsById.get(lead.getId());
                logger.info("** Duplicate ID detected: " + lead.getId() + " - Existing: ID=" + existingLead.getId() + ", Email=" + existingLead.getEmail() + ", EntryDate=" + existingLead.getEntryDate() + ", Index=" + existingLead.getIndex() + " | Current: ID=" + lead.getId() + ", Email=" + lead.getEmail() + ", EntryDate=" + lead.getEntryDate() + ", Index=" + lead.getIndex());
                if (lead.getEntryDate().isAfter(existingLead.getEntryDate())) {
                    logger.info("  Replacing existing lead by ID: " + lead.getId() + " - Reason: existing lead has older date,  existing: " + existingLead.getEntryDate() + " vs current: " + lead.getEntryDate());
                    leadsById.put(lead.getId(), lead);
                } else if (lead.getEntryDate().isEqual(existingLead.getEntryDate())) {
                    // current lead is later in the list when the dates are equal
                    logger.info("  Replacing existing lead by ID: " + lead.getId() + " - Reason: existing lead has same date, current is later in list, existing: " + existingLead.getIndex() + " vs current: " + lead.getIndex());
                    leadsById.put(lead.getId(), lead);
                } else {
                    logger.info("  Keeping existing lead by ID: " + lead.getId() + " - Reason: existing is newer date or later index, existing: " + existingLead.getEntryDate() + " vs current: " + lead.getEntryDate() + ", existing index: " + existingLead.getIndex() + " vs current index: " + lead.getIndex());
                }
            } else {
                leadsById.put(lead.getId(), lead);
            }
        }

        // Now iterate through the leads by email using leadsById hashmap
        Map<String, Lead> finalLeads = new HashMap<>();
        for (Lead lead : leadsById.values()) {
            if (finalLeads.containsKey(lead.getEmail())) {
                Lead existingLead = finalLeads.get(lead.getEmail());
                logger.info("** Duplicate Email detected: " + lead.getEmail() + " - Existing: ID=" + existingLead.getId() + ", Email=" + existingLead.getEmail() + ", EntryDate=" + existingLead.getEntryDate() + ", Index=" + existingLead.getIndex() + " | Current: ID=" + lead.getId() + ", Email=" + lead.getEmail() + ", EntryDate=" + lead.getEntryDate() + ", Index=" + lead.getIndex());
                if (lead.getEntryDate().isAfter(existingLead.getEntryDate())) {
                    logger.info("  Replacing existing lead by Email: " + lead.getEmail() + " - Reason: existing lead has older date,  existing: " + existingLead.getEntryDate() + " vs current: " + lead.getEntryDate());
                    finalLeads.put(lead.getEmail(), lead);
                } else if (lead.getEntryDate().isEqual(existingLead.getEntryDate()) && lead.getIndex() > existingLead.getIndex()) {
                    // Only if current lead is later in the list when the dates are equal
                    logger.info("  Replacing existing lead by Email: " + lead.getEmail() + " - Reason: existing lead has same date, current is later in list, existing: " + existingLead.getIndex() + " vs current: " + lead.getIndex());
                    finalLeads.put(lead.getEmail(), lead);
                } else {
                    logger.info("  Keeping existing lead by Eamil:" + lead.getEmail() + " - Reason: existing is newer date or later index, existing: " + existingLead.getEntryDate() + " vs current: " + lead.getEntryDate() + ", existing index: " + existingLead.getIndex() + " vs current index: " + lead.getIndex());
                }

            } else {
                finalLeads.put(lead.getEmail(), lead);
            }
        }

        // Convert to list and sort by entry date for consistent output
        List<Lead> result = finalLeads.values().stream()
                .sorted(Comparator.comparing(Lead::getEntryDate))
                .collect(Collectors.toList());
        
        logger.info("Deduplication complete. Input: " + leads.size() + " leads, Output: " + result.size() + " leads, Removed: " + (leads.size() - result.size()) + " duplicates");
        
        return result;
    }
} 