package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for handling lead deduplication
 */
public class LeadDeduplicationService {
    private final ObjectMapper objectMapper;
    
    public LeadDeduplicationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Reads leads from a JSON file
     */
    public LeadsData readLeadsFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        return objectMapper.readValue(file, LeadsData.class);
    }
    
    /**
     * Writes leads to a JSON file
     */
    public void writeLeadsToFile(LeadsData leadsData, String filePath) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), leadsData);
    }
    
    /**
     * Deduplicates leads based on the following rules:
     * 1. The data from the newest date should be preferred
     * 2. Duplicate IDs count as dups. Duplicate emails count as dups. Both must be unique
     * 3. If the dates are identical, the data from the record provided last in the list should be preferred
     */
    public List<Lead> deduplicateLeads(List<Lead> leads) {
        if (leads == null || leads.isEmpty()) {
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
        
        // Track the best lead for each unique ID and email
        Map<String, Lead> bestLeadById = new HashMap<>();
        Map<String, Lead> bestLeadByEmail = new HashMap<>();
        
        // Process leads in order to handle "last in list" preference for identical dates
        for (int i = 0; i < leads.size(); i++) {
            Lead currentLead = leads.get(i);
            if (currentLead.getId() == null || currentLead.getEmail() == null) {
                continue; // Skip leads with null ID or email
            }
            
            // Check for ID duplicates
            Lead existingById = bestLeadById.get(currentLead.getId());
            if (shouldReplaceLead(existingById, currentLead, i, getIndexOfLead(leadToIndex, existingById))) {
                bestLeadById.put(currentLead.getId(), currentLead);
            }
            
            // Check for email duplicates
            Lead existingByEmail = bestLeadByEmail.get(currentLead.getEmail());
            if (shouldReplaceLead(existingByEmail, currentLead, i, getIndexOfLead(leadToIndex, existingByEmail))) {
                bestLeadByEmail.put(currentLead.getEmail(), currentLead);
            }
        }
        
        // Combine the results, ensuring no conflicts between ID and email constraints
        Set<Lead> finalLeads = new HashSet<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedEmails = new HashSet<>();
        
        // Combine candidates from both ID-based and email-based best leads
        Set<Lead> allCandidates = new HashSet<>();
        allCandidates.addAll(bestLeadById.values());
        allCandidates.addAll(bestLeadByEmail.values());
        
        // First pass: add leads that don't conflict
        for (Lead lead : allCandidates) {
            if (!usedIds.contains(lead.getId()) && !usedEmails.contains(lead.getEmail())) {
                finalLeads.add(lead);
                usedIds.add(lead.getId());
                usedEmails.add(lead.getEmail());
            }
        }
        
        // Second pass: handle conflicts by choosing the lead with the newer date
        for (Lead candidate : allCandidates) {
            if (!finalLeads.contains(candidate)) {
                Lead conflictingLead = findConflictingLead(finalLeads, candidate);
                if (conflictingLead != null) {
                    int currentIndex = getIndexOfLead(leadToIndex, candidate);
                    int conflictingIndex = getIndexOfLead(leadToIndex, conflictingLead);
                    
                    if (shouldReplaceLead(conflictingLead, candidate, currentIndex, conflictingIndex)) {
                        finalLeads.remove(conflictingLead);
                        usedIds.remove(conflictingLead.getId());
                        usedEmails.remove(conflictingLead.getEmail());
                        
                        finalLeads.add(candidate);
                        usedIds.add(candidate.getId());
                        usedEmails.add(candidate.getEmail());
                    }
                }
            }
        }
        
        // Convert to list and sort by entry date for consistent output
        return finalLeads.stream()
                .sorted(Comparator.comparing(Lead::getEntryDate))
                .collect(Collectors.toList());
    }
    
    /**
     * Determines if a current lead should replace an existing lead based on deduplication rules
     */
    private boolean shouldReplaceLead(Lead existing, Lead current, int currentIndex, int existingIndex) {
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