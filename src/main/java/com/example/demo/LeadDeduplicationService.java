package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

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
        
        // Create index mapping for O(1) lookup instead of O(n) linear search
        Map<String, Integer> leadToIndex = new HashMap<>();
        for (int i = 0; i < leads.size(); i++) {
            Lead lead = leads.get(i);
            if (lead.getId() != null && lead.getEmail() != null) {
                // Create a unique key for each lead based on ID, email, and entry date
                String leadKey = createLeadKey(lead);
                leadToIndex.put(leadKey, i);
            }
        }
        logger.info("Created index mapping for " + leadToIndex.size() + " valid leads");
        
        // Track the best lead for each unique ID and email
        Map<String, Lead> bestLeadById = new HashMap<>();
        Map<String, Lead> bestLeadByEmail = new HashMap<>();
        
        // Process leads in order to handle "last in list" preference for identical dates
        for (int i = 0; i < leads.size(); i++) {
            Lead currentLead = leads.get(i);
            if (currentLead.getId() == null || currentLead.getEmail() == null) {
                logger.warning("Skipping lead at index " + i + " due to null ID or email - ID: " + currentLead.getId() + ", Email: " + currentLead.getEmail());
                continue; // Skip leads with null ID or email
            }
            
            logger.fine("Processing lead at index " + i + ": ID=" + currentLead.getId() + ", Email=" + currentLead.getEmail() + ", EntryDate=" + currentLead.getEntryDate());
            
            // Check for ID duplicates
            Lead existingById = bestLeadById.get(currentLead.getId());
            if (existingById != null) {
                logger.info("Duplicate ID detected: " + currentLead.getId() + " - Existing: ID=" + existingById.getId() + ", Email=" + existingById.getEmail() + ", EntryDate=" + existingById.getEntryDate() + ", Index=" + getIndexOfLead(leadToIndex, existingById) + " | Current: ID=" + currentLead.getId() + ", Email=" + currentLead.getEmail() + ", EntryDate=" + currentLead.getEntryDate() + ", Index=" + i);
            }
            
            if (shouldReplaceLead(existingById, getIndexOfLead(leadToIndex, existingById), currentLead, i)) {
                if (existingById != null) {
                    logger.info("Replacing existing lead by ID: " + currentLead.getId() + " - Reason: " + getReplacementReason(existingById, currentLead, i, getIndexOfLead(leadToIndex, existingById)));
                }
                bestLeadById.put(currentLead.getId(), currentLead);
            }
            
            // Check for email duplicates
            Lead existingByEmail = bestLeadByEmail.get(currentLead.getEmail());
            if (existingByEmail != null) {
                logger.info("Duplicate email detected: " + currentLead.getEmail() + " - Existing: ID=" + existingByEmail.getId() + ", Email=" + existingByEmail.getEmail() + ", EntryDate=" + existingByEmail.getEntryDate() + ", Index=" + getIndexOfLead(leadToIndex, existingByEmail) + " | Current: ID=" + currentLead.getId() + ", Email=" + currentLead.getEmail() + ", EntryDate=" + currentLead.getEntryDate() + ", Index=" + i);
            }
            
            if (shouldReplaceLead(existingByEmail, getIndexOfLead(leadToIndex, existingByEmail), currentLead, i)) {
                if (existingByEmail != null) {
                    logger.info("Replacing existing lead by email: " + currentLead.getEmail() + " - Reason: " + getReplacementReason(existingByEmail, currentLead, i, getIndexOfLead(leadToIndex, existingByEmail)));
                }
                bestLeadByEmail.put(currentLead.getEmail(), currentLead);
            }
        }
        
        logger.info("After initial deduplication - Best leads by ID: " + bestLeadById.size() + ", Best leads by email: " + bestLeadByEmail.size());
        
        // Combine the results, ensuring no conflicts between ID and email constraints
        Set<Lead> finalLeads = new HashSet<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedEmails = new HashSet<>();
        
        // Combine candidates from both ID-based and email-based best leads
        Set<Lead> allCandidates = new HashSet<>();
        allCandidates.addAll(bestLeadById.values());
        allCandidates.addAll(bestLeadByEmail.values());
        
        logger.info("Total unique candidates to process: " + allCandidates.size());
        
        // First pass: add leads that don't conflict
        for (Lead lead : allCandidates) {
            if (!usedIds.contains(lead.getId()) && !usedEmails.contains(lead.getEmail())) {
                finalLeads.add(lead);
                usedIds.add(lead.getId());
                usedEmails.add(lead.getEmail());
                logger.fine("Added non-conflicting lead: ID=" + lead.getId() + ", Email=" + lead.getEmail());
            }
        }
        
        logger.info("After first pass (non-conflicting leads): " + finalLeads.size() + " leads added");
        
        // Second pass: handle conflicts by choosing the lead with the newer date
        for (Lead candidate : allCandidates) {
            if (!finalLeads.contains(candidate)) {
                Lead conflictingLead = findConflictingLead(finalLeads, candidate);
                if (conflictingLead != null) {
                    logger.info("Conflict resolution needed - Candidate: ID=" + candidate.getId() + ", Email=" + candidate.getEmail() + ", EntryDate=" + candidate.getEntryDate() + ", Index=" + getIndexOfLead(leadToIndex, candidate) + " | Conflicting: ID=" + conflictingLead.getId() + ", Email=" + conflictingLead.getEmail() + ", EntryDate=" + conflictingLead.getEntryDate() + ", Index=" + getIndexOfLead(leadToIndex, conflictingLead));
                    
                    int currentIndex = getIndexOfLead(leadToIndex, candidate);
                    int conflictingIndex = getIndexOfLead(leadToIndex, conflictingLead);
                    
                    if (shouldReplaceLead(conflictingLead, conflictingIndex, candidate, currentIndex)) {
                        logger.info("Replacing conflicting lead with candidate - Reason: " + getReplacementReason(conflictingLead, candidate, currentIndex, conflictingIndex));
                        finalLeads.remove(conflictingLead);
                        usedIds.remove(conflictingLead.getId());
                        usedEmails.remove(conflictingLead.getEmail());
                        
                        finalLeads.add(candidate);
                        usedIds.add(candidate.getId());
                        usedEmails.add(candidate.getEmail());
                    } else {
                        logger.info("Keeping conflicting lead - Reason: " + getReplacementReason(candidate, conflictingLead, currentIndex, conflictingIndex));
                    }
                }
            }
        }
        
        logger.info("After conflict resolution: " + finalLeads.size() + " leads in final result");
        
        // Convert to list and sort by entry date for consistent output
        List<Lead> result = finalLeads.stream()
                .sorted(Comparator.comparing(Lead::getEntryDate))
                .collect(Collectors.toList());
        
        logger.info("Deduplication complete. Input: " + leads.size() + " leads, Output: " + result.size() + " leads, Removed: " + (leads.size() - result.size()) + " duplicates");
        
        return result;
    }
    
    /**
     * Determines if a current lead should replace an existing lead based on deduplication rules
     */
    private boolean shouldReplaceLead(Lead existing, int existingIndex, Lead current, int currentIndex) {
        if (existing == null) {
            return true; // No existing lead, so current should be used
        }
        
        OffsetDateTime existingDate = existing.getEntryDate();
        OffsetDateTime currentDate = current.getEntryDate();
        
        // Rule 1: Prefer newer date
        if (currentDate.isAfter(existingDate)) {
            return true;
        } else if (existingDate.isAfter(currentDate)) {
            return false;
        } else {
            // Rule 3: If dates are identical, prefer the one that appeared last in the list
            return currentIndex > existingIndex;
        }
    }
    
    /**
     * Gets a human-readable reason for why a lead was or wasn't replaced
     */
    private String getReplacementReason(Lead existing, Lead current, int currentIndex, int existingIndex) {
        if (existing == null) {
            return "No existing lead to compare against";
        }
        
        OffsetDateTime existingDate = existing.getEntryDate();
        OffsetDateTime currentDate = current.getEntryDate();
        
        if (currentDate.isAfter(existingDate)) {
            return "Current lead has newer date: " + currentDate + " vs " + existingDate;
        } else if (existingDate.isAfter(currentDate)) {
            return "Existing lead has newer date: " + existingDate + " vs " + currentDate;
        } else {
            if (currentIndex > existingIndex) {
                return "Dates are identical, current lead appears later in list (index " + currentIndex + " vs " + existingIndex + ")";
            } else {
                return "Dates are identical, existing lead appears later in list (index " + existingIndex + " vs " + currentIndex + ")";
            }
        }
    }
    
    /**
     * Creates a unique key for a lead based on ID, email, and entry date
     */
    private String createLeadKey(Lead lead) {
        return lead.getId() + "|" + lead.getEmail() + "|" + lead.getEntryDate().toString();
    }
    
    /**
     * Gets the index of a lead using HashMap lookup - O(1) instead of O(n)
     */
    private int getIndexOfLead(Map<String, Integer> leadToIndex, Lead targetLead) {
        if (targetLead == null) return -1;
        
        String leadKey = createLeadKey(targetLead);
        return leadToIndex.getOrDefault(leadKey, -1);
    }
    
    /**
     * Finds a lead in the final set that conflicts with the given lead (same ID or email)
     */
    private Lead findConflictingLead(Set<Lead> finalLeads, Lead targetLead) {
        for (Lead lead : finalLeads) {
            if (Objects.equals(lead.getId(), targetLead.getId()) || 
                Objects.equals(lead.getEmail(), targetLead.getEmail())) {
                return lead;
            }
        }
        return null;
    }
} 