package com.knowledgebase.application.service;

import com.knowledgebase.domain.repository.RequirementNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RequirementNumberService {

    private final RequirementNumberRepository requirementNumberRepository;

    public RequirementNumberService(RequirementNumberRepository requirementNumberRepository) {
        this.requirementNumberRepository = requirementNumberRepository;
    }

    public String numberRequirements(String content, Long spaceId, Long templateId) {
        if (content == null || content.isBlank() || spaceId == null || templateId == null) {
            return content;
        }

        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = content.split("\\R", -1);
        StringBuilder result = new StringBuilder(content.length() + 64);

        boolean insideRequirementTable = false;
        boolean tableSeparatorSeen = false;

        for (int index = 0; index < lines.length; index++) {
            String currentLine = lines[index];
            String processedLine = currentLine;

            if (!insideRequirementTable) {
                if (isRequirementTableHeader(currentLine)) {
                    insideRequirementTable = true;
                    tableSeparatorSeen = false;
                }
            } else {
                if (isMarkdownTableSeparator(currentLine)) {
                    tableSeparatorSeen = true;
                } else if (tableSeparatorSeen && isMarkdownTableRow(currentLine)) {
                    processedLine = assignRequirementNumber(currentLine, spaceId, templateId);
                } else {
                    insideRequirementTable = false;
                    tableSeparatorSeen = false;
                }
            }

            if (index > 0) {
                result.append(lineSeparator);
            }
            result.append(processedLine);
        }

        return result.toString();
    }

    private String assignRequirementNumber(String line, Long spaceId, Long templateId) {
        int nextNumber = requirementNumberRepository.allocateNextRequirementNumber(spaceId, templateId);
        return replaceFirstTableCell(line, formatRequirementNumber(nextNumber));
    }

    private String replaceFirstTableCell(String line, String value) {
        String[] cells = line.split("\\|", -1);
        if (cells.length < 3) {
            return line;
        }

        cells[1] = " " + value + " ";
        return String.join("|", cells);
    }

    private boolean isRequirementTableHeader(String line) {
        if (!isMarkdownTableRow(line)) {
            return false;
        }

        String[] cells = line.split("\\|", -1);
        if (cells.length < 3) {
            return false;
        }

        String firstCell = cells[1].trim();
        return "№".equals(firstCell) || "No".equalsIgnoreCase(firstCell) || "N".equalsIgnoreCase(firstCell);
    }

    private boolean isMarkdownTableSeparator(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.contains("-") && trimmed.endsWith("|");
    }

    private boolean isMarkdownTableRow(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1;
    }

    private String formatRequirementNumber(int number) {
        return String.format("REQ-%03d", number);
    }
}