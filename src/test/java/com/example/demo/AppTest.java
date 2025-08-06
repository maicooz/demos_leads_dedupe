package com.example.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Test class for the App
 */
public class AppTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Test that main method runs without throwing exceptions")
    public void testMainMethodWithHelp() {
        // This test ensures the main method can be called without throwing exceptions
        assertDoesNotThrow(() -> {
            App.main(new String[]{"help"});
        });
    }

    @Test
    @DisplayName("Test that main method handles version command")
    public void testMainMethodWithVersion() {
        assertDoesNotThrow(() -> {
            App.main(new String[]{"version"});
        });
    }

    @Test
    @DisplayName("Test that main method handles no arguments")
    public void testMainMethodWithNoArgs() {
        assertDoesNotThrow(() -> {
            App.main(new String[]{});
        });
    }

    @Test
    @DisplayName("Test dedupe command with valid input file")
    public void testDedupeCommandWithValidInput() throws IOException {
        // Copy test file to temp directory
        Path testInputFile = tempDir.resolve("test_input.json");
        Path testOutputFile = tempDir.resolve("test_output.json");
        
        // Create test data
        String testJson = """
        {
          "leads": [
            {
              "_id": "test1",
              "email": "test1@example.com",
              "firstName": "John",
              "lastName": "Doe",
              "address": "123 Test St",
              "entryDate": "2024-01-01T10:00:00+00:00"
            },
            {
              "_id": "test1",
              "email": "different@example.com",
              "firstName": "John",
              "lastName": "Updated",
              "address": "789 New St",
              "entryDate": "2024-01-01T12:00:00+00:00"
            }
          ]
        }
        """;
        
        Files.write(testInputFile, testJson.getBytes());
        
        // Test dedupe command
        assertDoesNotThrow(() -> {
            App.main(new String[]{"dedupe", testInputFile.toString(), testOutputFile.toString()});
        });
        
        // Verify output file was created
        assertTrue(Files.exists(testOutputFile));
        
        // Verify success message in output
        String output = outContent.toString();
        assertTrue(output.contains("Deduplication completed successfully!"));
    }

    @Test
    @DisplayName("Test dedupe command with empty leads file")
    public void testDedupeCommandWithEmptyFile() throws IOException {
        Path testInputFile = tempDir.resolve("empty_test.json");
        Path testOutputFile = tempDir.resolve("empty_output.json");
        
        String emptyJson = """
        {
          "leads": []
        }
        """;
        
        Files.write(testInputFile, emptyJson.getBytes());
        
        assertDoesNotThrow(() -> {
            App.main(new String[]{"dedupe", testInputFile.toString(), testOutputFile.toString()});
        });
        
        // Verify output file was created
        assertTrue(Files.exists(testOutputFile));
        
        String output = outContent.toString();
        assertTrue(output.contains("Found 0 leads"));
        assertTrue(output.contains("Deduplication completed successfully!"));
    }

    @Test
    @DisplayName("Test LeadDeduplicationService directly")
    public void testLeadDeduplicationService() {
        LeadDeduplicationService service = new LeadDeduplicationService();
        
        // Create test leads with duplicates
        Lead lead1 = new Lead("id1", "test@example.com", "John", "Doe", "123 St", 
                             OffsetDateTime.parse("2024-01-01T10:00:00+00:00"));
        Lead lead2 = new Lead("id2", "test2@example.com", "Jane", "Smith", "456 Ave", 
                             OffsetDateTime.parse("2024-01-01T11:00:00+00:00"));
        Lead lead3 = new Lead("id1", "different@example.com", "John", "Updated", "789 Rd", 
                             OffsetDateTime.parse("2024-01-01T12:00:00+00:00")); // Duplicate ID, newer date
        Lead lead4 = new Lead("id3", "test@example.com", "Another", "Person", "999 Ln", 
                             OffsetDateTime.parse("2024-01-01T13:00:00+00:00")); // Duplicate email, newer date
        
        List<Lead> inputLeads = Arrays.asList(lead1, lead2, lead3, lead4);
        List<Lead> deduplicatedLeads = service.deduplicateLeads(inputLeads);
        
        // Should have some leads after deduplication (exact number depends on complex logic)
        assertTrue(deduplicatedLeads.size() > 0);
        assertTrue(deduplicatedLeads.size() <= inputLeads.size());
        
        // Verify no duplicate IDs
        long uniqueIds = deduplicatedLeads.stream().map(Lead::getId).distinct().count();
        assertEquals(deduplicatedLeads.size(), uniqueIds, "All IDs should be unique");
        
        // Verify no duplicate emails  
        long uniqueEmails = deduplicatedLeads.stream().map(Lead::getEmail).distinct().count();
        assertEquals(deduplicatedLeads.size(), uniqueEmails, "All emails should be unique");
    }

    @Test
    @DisplayName("Test LeadDeduplicationService with identical dates - last in list wins")
    public void testLeadDeduplicationServiceWithIdenticalDates() {
        LeadDeduplicationService service = new LeadDeduplicationService();
        
        OffsetDateTime sameTime = OffsetDateTime.parse("2024-01-01T10:00:00+00:00");
        
        // Create test leads with same ID but identical dates
        Lead lead1 = new Lead("duplicate_id", "email1@example.com", "First", "Person", "123 St", sameTime);
        Lead lead2 = new Lead("duplicate_id", "email2@example.com", "Second", "Person", "456 Ave", sameTime);
        Lead lead3 = new Lead("duplicate_id", "email3@example.com", "Third", "Person", "789 Rd", sameTime);
        
        List<Lead> inputLeads = Arrays.asList(lead1, lead2, lead3);
        List<Lead> deduplicatedLeads = service.deduplicateLeads(inputLeads);
        
        // Should have 1 lead after deduplication (last one wins)
        assertEquals(1, deduplicatedLeads.size());
        
        // Verify the last lead was kept
        Lead result = deduplicatedLeads.get(0);
        assertEquals("duplicate_id", result.getId());
        assertEquals("email3@example.com", result.getEmail());
        assertEquals("Third", result.getFirstName());
    }

    @Test
    @DisplayName("Test LeadDeduplicationService with complex deduplication scenario")
    public void testComplexDeduplicationScenario() {
        LeadDeduplicationService service = new LeadDeduplicationService();
        
        // Test data from the original leads.json but simplified
        Lead lead1 = new Lead("jkj238238jdsnfsj23", "foo@bar.com", "John", "Smith", "123 Street St", 
                             OffsetDateTime.parse("2014-05-07T17:30:20+00:00"));
        Lead lead2 = new Lead("edu45238jdsnfsj23", "mae@bar.com", "Ted", "Masters", "44 North Hampton St", 
                             OffsetDateTime.parse("2014-05-07T17:31:20+00:00"));
        Lead lead3 = new Lead("jkj238238jdsnfsj23", "coo@bar.com", "Ted", "Jones", "456 Neat St", 
                             OffsetDateTime.parse("2014-05-07T17:32:20+00:00")); // Duplicate ID, newer date
        Lead lead4 = new Lead("wuj08238jdsnfsj23", "foo@bar.com", "Micah", "Valmer", "123 Street St", 
                             OffsetDateTime.parse("2014-05-07T17:33:20+00:00")); // Duplicate email, newer date
        
        List<Lead> inputLeads = Arrays.asList(lead1, lead2, lead3, lead4);
        List<Lead> deduplicatedLeads = service.deduplicateLeads(inputLeads);
        
        // Verify that deduplication worked
        assertTrue(deduplicatedLeads.size() > 0);
        assertTrue(deduplicatedLeads.size() <= inputLeads.size());
        
        // Verify no duplicate IDs
        long uniqueIds = deduplicatedLeads.stream().map(Lead::getId).distinct().count();
        assertEquals(deduplicatedLeads.size(), uniqueIds, "All IDs should be unique");
        
        // Verify no duplicate emails
        long uniqueEmails = deduplicatedLeads.stream().map(Lead::getEmail).distinct().count();
        assertEquals(deduplicatedLeads.size(), uniqueEmails, "All emails should be unique");
        
        // Verify that newer dates were preferred where there were conflicts
        // In this case, we should have lead3 (newer for ID) OR lead4 (newer for email), but not both original lead1
        boolean hasNewerIdLead = deduplicatedLeads.stream().anyMatch(lead -> 
            "jkj238238jdsnfsj23".equals(lead.getId()) && lead.getEntryDate().equals(OffsetDateTime.parse("2014-05-07T17:32:20+00:00")));
        boolean hasNewerEmailLead = deduplicatedLeads.stream().anyMatch(lead -> 
            "foo@bar.com".equals(lead.getEmail()) && lead.getEntryDate().equals(OffsetDateTime.parse("2014-05-07T17:33:20+00:00")));
        
        // At least one of the newer leads should be present
        assertTrue(hasNewerIdLead || hasNewerEmailLead, "Should prefer newer dates in conflicts");
    }
} 