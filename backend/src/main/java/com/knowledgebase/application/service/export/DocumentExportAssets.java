package com.knowledgebase.application.service.export;

import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Per-export allowlist: a URL never causes an HTTP request or a lookup of a foreign attachment. */
public final class DocumentExportAssets {
    public static final int MAX_FILES = 100;
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 12_000_000;
    private static final long MAX_TOTAL_PIXELS = 32_000_000;
    private static final Pattern DOWNLOAD = Pattern.compile("^/api/attachments/([1-9][0-9]*)/download$");
    private final Map<Long, Attachment> attachments = new LinkedHashMap<>();
    private final Map<Long, byte[]> files = new HashMap<>();
    private final Map<Long, Optional<ExportImage>> images = new HashMap<>();
    private final AttachmentFileStorageRepository storage;
    private long bytesRead;
    private long normalizedBytes;
    private long pixelsRead;
    private long embeddedBytes;
    private int imageOccurrences;

    public DocumentExportAssets(Long documentId, List<Attachment> metadata, AttachmentFileStorageRepository storage) {
        this.storage = storage;
        if (metadata.size() > MAX_FILES) throw limit();
        for (Attachment attachment : metadata) {
            if (!documentId.equals(attachment.getDocumentId())) continue;
            String path = attachment.getStoragePath();
            if (path == null || !path.startsWith("attachments/document-" + documentId + "/")
                    || path.contains("\\") || Arrays.asList(path.split("/")).contains("..")) {
                throw new ExportException(ExportException.Code.ATTACHMENT_UNAVAILABLE);
            }
            if (attachment.getSizeBytes() < 0 || attachment.getSizeBytes() > MAX_FILE_BYTES) throw limit();
            attachments.put(attachment.getId(), attachment);
        }
    }

    public Collection<Attachment> attachments() { return List.copyOf(attachments.values()); }
    public Attachment find(String url) {
        if (url == null) return null;
        var matcher = DOWNLOAD.matcher(url);
        if (!matcher.matches()) return null;
        try { return attachments.get(Long.parseLong(matcher.group(1))); }
        catch (NumberFormatException ex) { return null; }
    }
    public static boolean isAttachmentUrl(String url) {
        return url != null && (url.startsWith("/api/attachments/") || url.startsWith("api/attachments/"));
    }
    public static String url(Attachment attachment) { return "/api/attachments/" + attachment.getId() + "/download"; }

    public byte[] file(Attachment attachment) {
        if (files.containsKey(attachment.getId())) return files.get(attachment.getId());
        try (var input = storage.open(attachment.getStoragePath())) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES || bytesRead + bytes.length > MAX_TOTAL_BYTES) throw limit();
            if (bytes.length != attachment.getSizeBytes()) throw new ExportException(ExportException.Code.ATTACHMENT_UNAVAILABLE);
            bytesRead += bytes.length;
            files.put(attachment.getId(), bytes);
            return bytes;
        } catch (IOException ex) { throw new ExportException(ExportException.Code.ATTACHMENT_UNAVAILABLE); }
    }

    public ExportImage image(String url) {
        if (++imageOccurrences > 100) throw limit();
        Attachment attachment = find(url);
        if (attachment == null) return null;
        Optional<ExportImage> image = images.computeIfAbsent(attachment.getId(), ignored -> decode(attachment));
        if (image.isPresent()) accountEmbedding(image.get().bytes().length);
        return image.orElse(null);
    }

    public void accountEmbedding(int size) {
        embeddedBytes += size;
        if (embeddedBytes > MAX_TOTAL_BYTES) throw limit();
    }

    private Optional<ExportImage> decode(Attachment attachment) {
        byte[] raw;
        try { raw = file(attachment); }
        catch (ExportException ex) {
            if (ex.getCode() == ExportException.Code.LIMIT_EXCEEDED) throw ex;
            return Optional.empty();
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return Optional.empty();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("png", "jpeg", "jpg").contains(format)) return Optional.empty();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width < 1 || height < 1 || width > 6000 || height > 6000 || pixels > MAX_IMAGE_PIXELS
                        || pixelsRead + pixels > MAX_TOTAL_PIXELS) throw limit();
                pixelsRead += pixels;
                BufferedImage decoded = reader.read(0);
                try {
                    String outputFormat = format.equals("png") ? "png" : "jpeg";
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(decoded, outputFormat, output)) return Optional.empty();
                    byte[] normalized = output.toByteArray();
                    if (normalized.length > MAX_FILE_BYTES || normalizedBytes + normalized.length > MAX_TOTAL_BYTES) throw limit();
                    normalizedBytes += normalized.length;
                    return Optional.of(new ExportImage(normalized, "image/" + outputFormat, width, height));
                } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { return Optional.empty(); }
    }

    public String archivePath(Attachment attachment) {
        String name = attachment.getFilename() == null ? "file" : attachment.getFilename();
        name = name.replaceAll("[^\\p{L}\\p{N}._-]", "_").replaceAll("^[.]+", "_");
        if (name.isBlank()) name = "file";
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String extension = dot >= 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 100) + extension;
        }
        return "attachments/" + attachment.getId() + "-" + name;
    }

    private static ExportException limit() { return new ExportException(ExportException.Code.LIMIT_EXCEEDED); }
}
