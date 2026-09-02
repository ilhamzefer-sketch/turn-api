package az.turn.api;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

@Component
public class SecurePdfValidator {
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_END = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final Set<COSName> UNSAFE_KEYS = Set.of(
            COSName.OPEN_ACTION,
            COSName.AA,
            COSName.JAVA_SCRIPT,
            COSName.getPDFName("JS"),
            COSName.EMBEDDED_FILE,
            COSName.EMBEDDED_FILES,
            COSName.AF,
            COSName.getPDFName("RichMediaContent"),
            COSName.getPDFName("RichMediaSettings")
    );
    private static final Set<COSName> UNSAFE_ACTIONS = Set.of(
            COSName.JAVA_SCRIPT,
            COSName.getPDFName("Launch"),
            COSName.getPDFName("RichMedia")
    );
    private static final int MAX_INSPECTED_OBJECTS = 100_000;

    public boolean hasPdfSignature(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_HEADER.length) return false;
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (bytes[index] != PDF_HEADER[index]) return false;
        }
        return true;
    }

    public NormalizedAttachment validate(SecureUploadSource source, String filename) {
        validateDeclaredType(source.declaredMediaType());
        validateExtension(filename);
        validateEndMarker(source.bytes());
        inspectDocument(source.bytes());
        return new NormalizedAttachment(
                filename,
                source.bytes(),
                "application/pdf",
                "pdf",
                0,
                0,
                sha256(source.bytes())
        );
    }

    private void inspectDocument(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw invalid("Parolla qorunan PDF çeki qəbul edilmir.");
            }
            if (document.getNumberOfPages() < 1) {
                throw invalid("PDF çekində ən azı bir səhifə olmalıdır.");
            }
            if (containsUnsafeContent(document)) {
                throw invalid("Aktiv məzmun və ya əlavə fayl daşıyan PDF qəbul edilmir.");
            }
        } catch (InvalidPasswordException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_FILE,
                    "Parolla qorunan PDF çeki qəbul edilmir.",
                    exception
            );
        } catch (SecureUploadException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_FILE,
                    "PDF faylı zədəlidir və ya təhlükəsiz oxuna bilmir.",
                    exception
            );
        }
    }

    private boolean containsUnsafeContent(PDDocument document) {
        ArrayDeque<COSBase> pending = new ArrayDeque<>();
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(document.getDocument().getTrailer());
        for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
            pending.add(document.getDocument().getObjectFromPool(key));
        }
        while (!pending.isEmpty()) {
            COSBase value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (visited.size() > MAX_INSPECTED_OBJECTS) {
                throw invalid("PDF faylının strukturu təhlükəsizlik limitini keçir.");
            }
            if (value instanceof COSObject object) {
                addIfPresent(pending, object.getObject());
            } else if (value instanceof COSDictionary dictionary) {
                if (isUnsafeDictionary(dictionary)) return true;
                dictionary.getValues().forEach(item -> addIfPresent(pending, item));
            } else if (value instanceof COSArray array) {
                array.forEach(item -> addIfPresent(pending, item));
            }
        }
        return false;
    }

    private void addIfPresent(ArrayDeque<COSBase> pending, COSBase value) {
        if (value != null) pending.addLast(value);
    }

    private boolean isUnsafeDictionary(COSDictionary dictionary) {
        if (dictionary == null) return false;
        if (dictionary.keySet().stream().anyMatch(UNSAFE_KEYS::contains)) return true;
        COSName action = dictionary.getCOSName(COSName.S);
        COSName subtype = dictionary.getCOSName(COSName.SUBTYPE);
        return (action != null && UNSAFE_ACTIONS.contains(action))
                || (subtype != null && UNSAFE_ACTIONS.contains(subtype));
    }

    private void validateDeclaredType(String declaredMediaType) {
        if (declaredMediaType == null) {
            throw unsupported("PDF faylının media tipi yoxdur.");
        }
        String normalized = declaredMediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("application/pdf")) {
            throw unsupported("PDF faylının media tipi real formatla uyğun deyil.");
        }
    }

    private void validateExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1);
        if (!extension.equalsIgnoreCase("pdf")) {
            throw unsupported("PDF faylının uzantısı real formatla uyğun deyil.");
        }
    }

    private void validateEndMarker(byte[] bytes) {
        int marker = lastIndexOf(bytes, PDF_END);
        if (marker < 0) throw invalid("PDF faylı tam deyil.");
        for (int index = marker + PDF_END.length; index < bytes.length; index++) {
            if (!isPdfWhitespace(bytes[index])) {
                throw invalid("PDF faylının sonunda gözlənilməyən məlumat var.");
            }
        }
    }

    private int lastIndexOf(byte[] content, byte[] target) {
        for (int start = content.length - target.length; start >= 0; start--) {
            boolean matches = true;
            for (int index = 0; index < target.length; index++) {
                if (content[start + index] != target[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return start;
        }
        return -1;
    }

    private boolean isPdfWhitespace(byte value) {
        return value == 0 || value == 9 || value == 10 || value == 12 || value == 13 || value == 32;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 dəstəklənmir.", exception);
        }
    }

    private SecureUploadException invalid(String message) {
        return new SecureUploadException(SecureUploadFailure.INVALID_FILE, message);
    }

    private SecureUploadException unsupported(String message) {
        return new SecureUploadException(SecureUploadFailure.UNSUPPORTED_FILE_TYPE, message);
    }
}
