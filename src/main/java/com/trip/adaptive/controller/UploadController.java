package com.trip.adaptive.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;

/** 图片本地上传：攻略封面等图片直接传到服务器，前端不再需要手填外链。 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {
  private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif");
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  @Value("${app.upload.dir:uploads}")
  private String uploadDir;

  @PostMapping("/images")
  public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new BusinessException("请选择要上传的图片");
    if (file.getSize() > MAX_BYTES) throw new BusinessException("图片不能超过 5MB");
    String extension = extensionOf(file.getOriginalFilename());
    if (!ALLOWED.contains(extension)) throw new BusinessException("只支持 JPG / PNG / WEBP / GIF 图片");
    Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
    Files.createDirectories(directory);
    String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
    file.transferTo(directory.resolve(name));
    return Map.of("url", "/api/uploads/images/" + name);
  }

  @GetMapping("/images/{name}")
  public ResponseEntity<Resource> read(@PathVariable String name) {
    Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path file = directory.resolve(name).normalize();
    if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
      throw new ResourceNotFoundException("图片不存在");
    }
    MediaType type =
        MediaTypeFactory.getMediaType(file.getFileName().toString())
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok().contentType(type).body(new FileSystemResource(file));
  }

  private static String extensionOf(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
