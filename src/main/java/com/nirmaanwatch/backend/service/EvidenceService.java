package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.config.AppProperties;
import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.EvidenceFile;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.EvidenceFileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "application/pdf");
    private final EvidenceFileRepository evidence;
    private final AppProperties properties;

    public EvidenceService(EvidenceFileRepository evidence, AppProperties properties) {
        this.evidence = evidence;
        this.properties = properties;
    }

    public EvidenceFile store(CitizenReport report, MultipartFile file, UserAccount uploader) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No evidence file was provided.");
        }
        if (file.getSize() > properties.getEvidence().getMaxBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file exceeds the maximum allowed size of 5 MB.");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(declared)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPG, PNG, and PDF evidence files are accepted.");
        }
        try {
            byte[] bytes = file.getBytes();
            String detected = detectType(bytes);
            if (!ALLOWED.contains(detected) || !detected.equals(declared) && !(declared.equals("image/jpeg") && detected.equals("image/jpeg"))) {
                if (!detected.equals(declared)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "The file contents do not match the declared type.");
                }
            }
            String extension = switch (detected) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                default -> ".pdf";
            };
            String storedName = UUID.randomUUID() + extension;
            Path root = Path.of(properties.getEvidence().getDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(storedName);
            Files.write(target, bytes);

            EvidenceFile record = new EvidenceFile();
            record.setReport(report);
            record.setOriginalFilename(safeOriginalName(file.getOriginalFilename()));
            record.setStoredFilename(storedName);
            record.setContentType(detected);
            record.setSizeBytes(bytes.length);
            record.setChecksumSha256(sha256(bytes));
            record.setPublicVisible(false);
            record.setUploadedBy(uploader);
            return evidence.save(record);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The evidence file could not be stored.");
        }
    }

    public Path resolveFile(EvidenceFile file) {
        Path root = Path.of(properties.getEvidence().getDir()).toAbsolutePath().normalize();
        Path target = root.resolve(file.getStoredFilename()).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Evidence file was not found.");
        }
        return target;
    }

    public boolean canAccess(EvidenceFile file, UserAccount user) {
        if (file.isPublicVisible()) {
            return true;
        }
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.OFFICER) {
            return true;
        }
        return file.getReport().getReporter() != null && file.getReport().getReporter().getId().equals(user.getId());
    }

    private String safeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "evidence";
        }
        return Path.of(name).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String detectType(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 5 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return "application/pdf";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "The file type is not an allowed JPG, PNG, or PDF.");
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to checksum the uploaded file.");
        }
    }

    public EvidenceFile getById(Long id) {
        return evidence.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Evidence was not found."));
    }
}
