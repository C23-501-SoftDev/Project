## Why
PDF output depends on Linux system font paths; Cyrillic becomes replacement characters on macOS/Windows. Feature ID: `pdf-portable-cyrillic-fonts` (US5.1.2).

## Inputs
[PDF export requirements](https://github.com/C23-501-SoftDev/Docs/tree/main/documents/backlog-descriptions/E5%20Экспорт%20и%20Отчетность%20(Export)/F5.1%20Экспорт%20документов/US5.1.2%20Конвертация%20в%20PDF).

## What Changes
- Package licensed Noto Sans/Mono fonts and register them from the classpath.
- Preserve Cyrillic in normal, bold, italic and code text on supported operating systems.
- Verify extracted text, font embedding and visual rendering of an exported sample.

## Capabilities
### New Capabilities
- `portable-cyrillic-pdf`: PDF font coverage independent of system font installation.
### Modified Capabilities
None.

## Impact
MarkdownService and font resources. No controller/service API, repository or database contract changes; JAR gains bundled font files.
