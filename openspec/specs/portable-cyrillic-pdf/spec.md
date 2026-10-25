# portable-cyrillic-pdf Specification

## Purpose
Preserve Cyrillic and Latin text in exported PDF documents by embedding licensed, versioned fonts independently of the host operating system (US5.1.2).

## Requirements
### Requirement: Portable Cyrillic PDF fonts
The system SHALL embed bundled, licensed fonts for Cyrillic and Latin text independently of operating-system font installation.

#### Scenario: Export on a host without Linux fonts
- **WHEN** a document contains Russian title, body, bold, italic, bold italic and code text
- **THEN** the PDF displays and exposes the original text for extraction using embedded fonts

#### Scenario: Readable structure
- **WHEN** a document contains Russian headings, lists and tables
- **THEN** the PDF preserves readable text, table borders and page margins

### Requirement: Auditable font distribution
Bundled fonts MUST include their redistribution license, pinned upstream revision and SHA-256 checksums.

#### Scenario: Distribution in the application JAR
- **WHEN** the application is packaged
- **THEN** fonts and their provenance are available as classpath resources without downloading external resources

