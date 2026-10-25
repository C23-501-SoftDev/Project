## ADDED Requirements

### Requirement: Admin filters reflect selected values
The space administration page SHALL submit the chosen status and owner, including after clearing filters or replacing owner options.

#### Scenario: Deleted spaces with selected owner
- **WHEN** an administrator selects deleted status and then an owner from the refreshed list
- **THEN** the list request contains both values and shows only matching deleted spaces.

#### Scenario: Updated choices remain selectable
- **WHEN** the owner options are replaced after a status change or reset
- **THEN** selecting an option updates the owner value and the next list request.

### Requirement: Searchable selects remain open on focus
A searchable custom select MUST remain usable when the same pointer action focuses and clicks it.

#### Scenario: Choose a space owner
- **WHEN** an administrator focuses the searchable owner select
- **THEN** visible matching options can be selected without a second toggle click.
