package com.example.demo;

/**
 * Main application class for the Demo Leads CLI
 */
public class App {
    
    public static void main(String[] args) {
        System.out.println("Demo Leads CLI Application");
        System.out.println("==========================");
        
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "help":
            case "-h":
            case "--help":
                printUsage();
                break;
            case "version":
            case "-v":
            case "--version":
                printVersion();
                break;
            case "dedupe":
                handleDedupeCommand(args);
                break;
            default:
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar demo-leads.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  help, -h, --help     Show this help message");
        System.out.println("  version, -v, --version   Show version information");
        System.out.println("  dedupe <input-file> [output-file]   Deduplicate leads from JSON file");
        System.out.println();
        System.out.println("Deduplication Rules:");
        System.out.println("  - Duplicate IDs and emails are not allowed");
        System.out.println("  - Newer dates are preferred");
        System.out.println("  - For identical dates, last record in list is preferred");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar demo-leads.jar dedupe leads.json");
        System.out.println("  java -jar demo-leads.jar dedupe leads.json clean_leads.json");
        System.out.println("  mvn exec:java -Dexec.args=\"dedupe leads.json\"");
    }
    
    private static void printVersion() {
        System.out.println("Demo Leads CLI v1.0.0");
        System.out.println("Java version: " + System.getProperty("java.version"));
    }
    
    private static void handleDedupeCommand(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Input file path is required for dedupe command");
            System.err.println("Usage: java -jar demo-leads.jar dedupe <input-file> [output-file]");
            System.exit(1);
        }
        
        String inputFile = args[1];
        String outputFile = args.length > 2 ? args[2] : "deduplicated_leads.json";
        
        try {
            LeadDeduplicationService service = new LeadDeduplicationService();
            
            System.out.println("Reading leads from: " + inputFile);
            LeadsData inputData = service.readLeadsFromFile(inputFile);
            
            System.out.println("Found " + inputData.getLeads().size() + " leads");
            System.out.println("Deduplicating leads...");
            
            java.util.List<Lead> deduplicatedLeads = service.deduplicateLeads(inputData.getLeads());
            LeadsData outputData = new LeadsData(deduplicatedLeads);
            
            System.out.println("Writing " + deduplicatedLeads.size() + " deduplicated leads to: " + outputFile);
            service.writeLeadsToFile(outputData, outputFile);
            
            System.out.println("Deduplication completed successfully!");
            System.out.println("Removed " + (inputData.getLeads().size() - deduplicatedLeads.size()) + " duplicate(s)");
            
        } catch (java.io.IOException e) {
            System.err.println("Error processing leads: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 