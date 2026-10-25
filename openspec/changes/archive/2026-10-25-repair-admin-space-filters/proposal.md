## Why
Admin-space status and owner filters do not change the request after options are replaced. Seven browser scenarios reproduce the defect. Searchable selects also close on the click that focuses them.

Feature ID: `admin-space-filter-controls`.
Input: [project backlog](https://github.com/C23-501-SoftDev/Docs/blob/main/documents/backlog.json), administration of spaces and owners.

## What Changes
- Bind the status selector to its actual form value.
- Keep dynamically loaded options interactive and preserve expected focus behavior.
- Clear obsolete owners before loading choices for a new status.

## Capabilities
### New Capabilities
- `admin-space-filter-controls`: functional status/owner controls after reload.
### Modified Capabilities
None.

## Impact
Frontend `custom-select.js` and `pages/admin-spaces.html`. Controller/service/repository/database and API contracts are unchanged.
