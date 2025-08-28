# Demo Leads CLI

A command line application built with Maven and Java 17 for deduplicating sales leads data.

## Use Case

This application helps sales teams clean up their lead databases by removing duplicate entries. Sales leads often come from multiple sources (web forms, trade shows, partner systems) and can result in duplicate records with the same person having multiple IDs or the same email appearing multiple times with different information.

The deduplication process ensures:
- **Data Quality**: Eliminates duplicate contacts to improve CRM accuracy
- **Cost Efficiency**: Reduces marketing costs by avoiding duplicate outreach
- **Compliance**: Helps maintain clean contact lists for GDPR and other privacy regulations
- **Sales Efficiency**: Prevents multiple sales reps from contacting the same lead

## Prerequisites

- Java 17 or higher
- Maven 3.6+ 

## Quick Start

### 1. Clone and Navigate
```bash
cd /path/to/your/project
```

### 2. Run the Application
```bash
# Deduplicate the sample leads file
mvn exec:java -Dexec.args="dedupe leads.json"
```

That's it! The application will process your leads and create a `deduplicated_leads.json` file.

## Project Structure

```
demo-leads/
├── pom.xml                     # Maven configuration
├── leads.json                  # Sample input data
├── deduplicated_leads.json     # Example output data
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/example/demo/
│   │           ├── App.java                    # Main application class
│   │           ├── Lead.java                   # Lead data model
│   │           ├── LeadDeduplicationService.java # Core deduplication logic
│   │           └── LeadsData.java              # JSON wrapper for leads array
│   └── test/
│       ├── java/
│       │   └── com/example/demo/
│       │       └── AppTest.java # Test class
│       └── resources/
│           ├── empty_leads.json
│           └── test_leads.json
└── README.md                   # This file
```

## Running the Application

### Simple Command (Recommended)
```bash
mvn exec:java -Dexec.args="dedupe leads.json"
```

### Other Commands
```bash
# Show help
mvn exec:java -Dexec.args="help"

# Show version
mvn exec:java -Dexec.args="version"

# Custom output file
mvn exec:java -Dexec.args="dedupe leads.json my_clean_leads.json"
```

### Using the Compiled JAR
```bash
# First package the application
mvn clean package

# Then run the JAR
java -jar target/demo-leads-1.0.0.jar dedupe leads.json
```

## Available Commands

- `help`, `-h`, `--help` - Show usage information
- `version`, `-v`, `--version` - Show version and Java information  
- `dedupe <input-file> [output-file]` - Deduplicate leads from JSON file

## Deduplication Rules

The application applies the following rules when processing leads:

1. **Unique Identifiers**: Both ID (`_id`) and email must be unique across all leads
2. **Date Preference**: When duplicates are found, data from the newest `entryDate` is preferred
3. **Order Preference**: For identical dates, the record appearing last in the input list is preferred
4. **Data Preservation**: All other fields (firstName, lastName, address) are preserved from the selected record

### Input Format

The input JSON file must contain a `leads` array with objects having these fields:
- `_id` (string): Unique identifier for the lead
- `email` (string): Email address (must be unique)
- `firstName` (string): Contact's first name
- `lastName` (string): Contact's last name  
- `address` (string): Contact's address
- `entryDate` (string): ISO 8601 formatted date with timezone (e.g., "2014-05-07T17:30:20+00:00")

## Example Output and Logging

When you run the deduplication, you'll see detailed logging showing exactly what's happening:

### Sample Run
```bash
mvn exec:java -Dexec.args="dedupe leads.json"
```

### Console Output
```
Demo Leads CLI Application
==========================
Reading leads from: leads.json
Found 10 leads
Deduplicating leads...
Writing 5 deduplicated leads to: deduplicated_leads.json
Deduplication completed successfully!
Removed 5 duplicate(s)
```

### Detailed Logging (INFO level)
The application provides comprehensive logging showing the deduplication process:

```
[INFO] Reading leads from file: leads.json
[INFO] Successfully read 10 leads from file
[INFO] Starting deduplication process for 10 leads
[INFO] Created index mapping for 10 valid leads

[INFO] Duplicate ID detected: jkj238238jdsnfsj23
[INFO] Replacing existing lead by ID: jkj238238jdsnfsj23 - Reason: Current lead has newer date: 2014-05-07T17:32:20Z vs 2014-05-07T17:30:20Z

[INFO] Duplicate email detected: foo@bar.com
[INFO] Replacing existing lead by email: foo@bar.com - Reason: Current lead has newer date: 2014-05-07T17:32:20Z vs 2014-05-07T17:30:20Z

[INFO] After initial deduplication - Best leads by ID: 8, Best leads by email: 6
[INFO] Total unique candidates to process: 9
[INFO] After first pass (non-conflicting leads): 5 leads added
[INFO] Conflict resolution needed - Candidate: ID=jkj238238jdsnfsj23, Email=coo@bar.com, EntryDate=2014-05-07T17:32:20Z, Index=3 | Conflicting: ID=jkj238238jdsnfsj23, Email=bill@bar.com, EntryDate=2014-05-07T17:33:20Z, Index=9
[INFO] Keeping conflicting lead - Reason: Current lead has newer date: 2014-05-07T17:33:20Z vs 2014-05-07T17:32:20Z

[INFO] After conflict resolution: 5 leads in final result
[INFO] Deduplication complete. Input: 10 leads, Output: 5 leads, Removed: 5 duplicates
[INFO] Writing 5 leads to file: deduplicated_leads.json
[INFO] Successfully wrote leads to file: deduplicated_leads.json
```

### What the Logging Shows
- **Input/Output Summary**: Total leads processed and results
- **Duplicate Detection**: Each duplicate found with details about why it was replaced
- **Decision Logic**: Clear reasoning for each deduplication decision
- **Conflict Resolution**: How conflicts between ID and email duplicates are resolved
- **File Operations**: Confirmation of reading and writing operations

### Sample Results
**Input**: `leads.json` with 10 leads containing duplicates
**Output**: `deduplicated_leads.json` with 5 unique leads
**Removed**: 5 duplicate entries based on deduplication rules

## Development

### Building the Project
```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package into JAR
mvn clean package
```

### Adding New Commands

1. Add a new case to the switch statement in `App.java`
2. Create a private method to handle the command logic
3. Update the `printUsage()` method to document the new command
4. Add tests in `AppTest.java`

### Running Tests
```bash
mvn test
```

### IDE Setup

The project can be imported into any Java IDE that supports Maven:
- IntelliJ IDEA
- Eclipse 
- VS Code with Java extensions

## Maven Plugins Used

- **maven-compiler-plugin** - Compiles Java source code
- **maven-surefire-plugin** - Runs unit tests
- **exec-maven-plugin** - Runs the application via Maven
- **maven-shade-plugin** - Creates executable JAR with dependencies