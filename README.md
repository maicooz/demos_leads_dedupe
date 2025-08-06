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

## Building the Project

### Compile the project
```bash
mvn clean compile
```

### Run tests
```bash
mvn test
```

### Package into JAR
```bash
mvn clean package
```

This creates an executable JAR in the `target/` directory.

## Running the Application

### Using Maven exec plugin (recommended for development)
```bash
# Show help
mvn exec:java -Dexec.args="help"

# Show version
mvn exec:java -Dexec.args="version"

# Deduplicate leads (output to deduplicated_leads.json)
mvn exec:java -Dexec.args="dedupe leads.json"

# Deduplicate leads with custom output file
mvn exec:java -Dexec.args="dedupe leads.json clean_leads.json"
```

### Using the compiled JAR
```bash
# First package the application
mvn clean package

# Then run the JAR
java -jar target/demo-leads-1.0.0.jar help
java -jar target/demo-leads-1.0.0.jar version
java -jar target/demo-leads-1.0.0.jar dedupe leads.json
java -jar target/demo-leads-1.0.0.jar dedupe leads.json output.json
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

### Example Usage

```bash
# Process the sample leads file
mvn exec:java -Dexec.args="dedupe leads.json"

# Output will show:
# Reading leads from: leads.json
# Found 15 leads
# Deduplicating leads...
# Writing 10 deduplicated leads to: deduplicated_leads.json
# Deduplication completed successfully!
# Removed 5 duplicate(s)
```

## Development

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