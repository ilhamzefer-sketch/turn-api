package az.turn.api;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FileSystemPrivateAttachmentStorage implements PrivateAttachmentStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemPrivateAttachmentStorage.class);
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "^[a-f0-9]{2}/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)$"
    );
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );
    private final Path configuredRoot;
    private final int maxFileBytes;
    private Path storageRoot;

    public FileSystemPrivateAttachmentStorage(UploadProperties properties) {
        configuredRoot = properties.storageRoot().toAbsolutePath().normalize();
        maxFileBytes = properties.maxFileBytes();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(configuredRoot);
            if (Files.isSymbolicLink(configuredRoot)) {
                throw new IOException("Storage root cannot be a symbolic link.");
            }
            storageRoot = configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(storageRoot) || !Files.isReadable(storageRoot) || !Files.isWritable(storageRoot)) {
                throw new IOException("Storage root must be a readable and writable directory.");
            }
        } catch (IOException exception) {
            throw unavailable("Private fayl storage sahəsi hazırlana bilmədi.", exception);
        }
    }

    @Override
    public void store(String storageKey, byte[] content) {
        validateContent(content);
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            Path parent = target.getParent();
            Files.createDirectories(parent);
            setDirectoryPermissions(parent);
            requireInsideStorage(parent.toRealPath(LinkOption.NOFOLLOW_LINKS));
            temporary = Files.createTempFile(parent, ".upload-", ".tmp");
            setFilePermissions(temporary);
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            move(temporary, target);
        } catch (IOException exception) {
            deleteTemporary(temporary);
            throw unavailable("Fayl private storage sahəsinə yazıla bilmədi.", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        Path target = resolve(storageKey);
        try {
            if (Files.isSymbolicLink(target)) {
                throw new IOException("Stored file cannot be a symbolic link.");
            }
            byte[] content = Files.readAllBytes(target);
            validateContent(content);
            return content;
        } catch (IOException exception) {
            throw unavailable("Private fayl oxuna bilmədi.", exception);
        }
    }

    @Override
    public void deleteIfExists(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw unavailable("Private fayl silinə bilmədi.", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw unavailable("Private fayl açarı düzgün deyil.", null);
        }
        Path target = storageRoot.resolve(storageKey).normalize();
        requireInsideStorage(target);
        return target;
    }

    private void requireInsideStorage(Path path) {
        if (!path.startsWith(storageRoot)) {
            throw unavailable("Private fayl yolu storage sahəsindən kənara çıxa bilməz.", null);
        }
    }

    private void validateContent(byte[] content) {
        if (content == null || content.length == 0 || content.length > maxFileBytes) {
            throw unavailable("Private fayl ölçüsü düzgün deyil.", null);
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void setDirectoryPermissions(Path directory) throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS);
        }
    }

    private void setFilePermissions(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            logger.warn("Temporary upload file could not be removed", exception);
        }
    }

    private SecureUploadException unavailable(String message, Throwable cause) {
        return cause == null
                ? new SecureUploadException(SecureUploadFailure.STORAGE_UNAVAILABLE, message)
                : new SecureUploadException(SecureUploadFailure.STORAGE_UNAVAILABLE, message, cause);
    }
}
