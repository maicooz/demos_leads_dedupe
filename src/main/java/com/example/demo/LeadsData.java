package com.example.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wrapper class for the JSON structure containing leads
 */
public class LeadsData {
    @JsonProperty("leads")
    private List<Lead> leads;
    
    // Default constructor for Jackson
    public LeadsData() {}
    
    public LeadsData(List<Lead> leads) {
        this.leads = leads;
    }
    
    public List<Lead> getLeads() {
        return leads;
    }
    
    public void setLeads(List<Lead> leads) {
        this.leads = leads;
    }
} 