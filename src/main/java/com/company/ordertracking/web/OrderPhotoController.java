package com.company.ordertracking.web;

import com.company.ordertracking.entity.OrderPhoto;
import com.company.ordertracking.entity.SalesOrder;
import com.company.ordertracking.audit.AuditLogService;
import com.company.ordertracking.repo.OrderPhotoRepository;
import com.company.ordertracking.repo.SalesOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OrderPhotoController {

    private final OrderPhotoRepository photoRepo;
    private final SalesOrderRepository orderRepo;
    private final AuditLogService auditLogService;

    private final Path uploadDir;

    public OrderPhotoController(OrderPhotoRepository photoRepo,
                               SalesOrderRepository orderRepo,
                               AuditLogService auditLogService,
                               @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.photoRepo = photoRepo;
        this.orderRepo = orderRepo;
        this.auditLogService = auditLogService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public record PhotoDto(Long id, String originalFilename, String contentType, long size,
                           Instant createdAt, String url) {}

    @GetMapping("/orders/{orderId}/photos")
    public List<PhotoDto> list(@PathVariable Long orderId) {
        // Ensure order exists for nicer errors
        if (!orderRepo.existsById(orderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return photoRepo.findByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(p -> new PhotoDto(
                        p.getId(),
                        p.getOriginalFilename(),
                        p.getContentType(),
                        p.getFileSize(),
                        p.getCreatedAt(),
                        "/api/photos/" + p.getId() + "/content"
                ))
                .toList();
    }

    @PostMapping(value = "/orders/{orderId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoDto upload(@PathVariable Long orderId,
                           @RequestParam("file") MultipartFile file,
                           jakarta.servlet.http.HttpServletRequest httpReq) throws IOException {
        SalesOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
        }

        // Basic type guard (UI also filters to images)
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are allowed");
        }

        // 10MB guard (you can raise this later)
        long max = 10L * 1024 * 1024;
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image too large (>10MB)");
        }

        Files.createDirectories(uploadDir);

        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
        original = Paths.get(original).getFileName().toString(); // prevent path traversal
        String ext = StringUtils.getFilenameExtension(original);
        String stored = orderId + "_" + UUID.randomUUID() + (ext != null && !ext.isBlank() ? "." + ext : "");
        Path target = uploadDir.resolve(stored).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        OrderPhoto photo = new OrderPhoto();
        photo.setOrder(order);
        photo.setStoredFilename(stored);
        photo.setOriginalFilename(original);
        photo.setContentType(contentType);
        photo.setFileSize(file.getSize());

        photoRepo.save(photo);

        auditLogService.log(httpReq, "UPLOAD_PHOTO", "orderId=" + orderId, AuditLogService.Status.SUCCESS,
                "photoId=" + photo.getId() + ", name=" + photo.getOriginalFilename());

        return new PhotoDto(photo.getId(), photo.getOriginalFilename(), photo.getContentType(), photo.getFileSize(),
                photo.getCreatedAt(), "/api/photos/" + photo.getId() + "/content");
    }

    @GetMapping("/photos/{photoId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long photoId) throws IOException {
        OrderPhoto photo = photoRepo.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        Path file = uploadDir.resolve(photo.getStoredFilename()).normalize();
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File missing on disk");
        }

        Resource res = new UrlResource(file.toUri());
        MediaType mt;
        try {
            mt = MediaType.parseMediaType(Optional.ofNullable(photo.getContentType()).orElse("application/octet-stream"));
        } catch (InvalidMediaTypeException ignored) {
            mt = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(res);
    }

    @DeleteMapping("/photos/{photoId}")
    public Map<String, Object> delete(@PathVariable Long photoId,
                                      jakarta.servlet.http.HttpServletRequest httpReq) throws IOException {
        OrderPhoto photo = photoRepo.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        Long orderId = photo.getOrder() == null ? null : photo.getOrder().getId();
        String name = photo.getOriginalFilename();
        photoRepo.delete(photo);

        // Best-effort delete from disk
        try {
            Files.deleteIfExists(uploadDir.resolve(photo.getStoredFilename()).normalize());
        } catch (IOException ignored) {}

        auditLogService.log(httpReq, "DELETE_PHOTO", "orderId=" + orderId, AuditLogService.Status.SUCCESS,
                "photoId=" + photoId + ", name=" + name);

        return Map.of("ok", true);
    }
}
