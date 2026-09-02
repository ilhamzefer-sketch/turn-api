package az.turn.api;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class QrPosterPdfRenderer {
    private static final Color CANVAS = new Color(248, 247, 242);
    private static final Color WHITE = Color.WHITE;
    private static final Color BRAND = new Color(0, 79, 69);
    private static final Color BRAND_SOFT = new Color(226, 241, 236);
    private static final Color MUTED = new Color(91, 111, 104);
    private static final Color LINE = new Color(218, 226, 221);
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    public byte[] render(QrPosterSpecification data) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont regular = loadFont(document, "pdf/Roboto-Regular.ttf");
            PDFont bold = loadFont(document, "pdf/Roboto-Bold.ttf");
            PDImageXObject logo = loadLogo(document);
            setMetadata(document, data.posterTitle());
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawPage(content, data, regular, bold, logo);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException exception) {
            throw new IllegalStateException("QR afişası yaradıla bilmədi.", exception);
        }
    }

    private void drawPage(
            PDPageContentStream content,
            QrPosterSpecification data,
            PDFont regular,
            PDFont bold,
            PDImageXObject logo
    ) throws IOException, WriterException {
        content.setNonStrokingColor(CANVAS);
        content.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        content.fill();
        content.setNonStrokingColor(WHITE);
        addRoundedRectangle(content, 28, 26, PAGE_WIDTH - 56, PAGE_HEIGHT - 52, 22);
        content.fill();
        content.setStrokingColor(LINE);
        content.setLineWidth(1);
        addRoundedRectangle(content, 28, 26, PAGE_WIDTH - 56, PAGE_HEIGHT - 52, 22);
        content.stroke();

        drawText(content, bold, 24, "NövbəTime", 58, 762, BRAND);
        drawText(content, regular, 11, "Onlayn növbə və qəbul sistemi", 58, 741, MUTED);
        content.drawImage(logo, 482, 731, 54, 54);

        drawCenteredText(content, regular, 13, "QR ilə qoşulun", 695, MUTED);
        drawCenteredTitle(content, bold, data.posterTitle(), 654);
        drawQr(content, data.publicUrl(), logo, 135, 305, 325);
        drawDetails(content, data, regular, bold);

        content.setStrokingColor(LINE);
        content.moveTo(58, 104);
        content.lineTo(PAGE_WIDTH - 58, 104);
        content.stroke();
        drawText(content, regular, 11, "Kameranızla skan edin", 58, 76, MUTED);
        drawRightAlignedText(content, bold, 12, "novbetime.az", PAGE_WIDTH - 58, 76, BRAND);
    }

    private void drawCenteredTitle(PDPageContentStream content, PDFont font, String title, float topY)
            throws IOException {
        List<String> lines = wrap(title, font, 28, PAGE_WIDTH - 120, 2);
        float y = topY;
        for (String line : lines) {
            drawCenteredText(content, font, 28, line, y, BRAND);
            y -= 34;
        }
    }

    private void drawQr(
            PDPageContentStream content,
            String publicUrl,
            PDImageXObject logo,
            float x,
            float y,
            float size
    ) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 4);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new QRCodeWriter().encode(publicUrl, BarcodeFormat.QR_CODE, 1, 1, hints);
        float module = size / matrix.getWidth();
        float gap = module * 0.08f;
        content.setNonStrokingColor(BRAND);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int column = 0; column < matrix.getWidth(); column++) {
                if (!matrix.get(column, row)) continue;
                float moduleX = x + column * module;
                float moduleY = y + (matrix.getHeight() - row - 1) * module;
                if (isFinderModule(column, row, matrix.getWidth())) {
                    content.addRect(moduleX, moduleY, module, module);
                } else {
                    addRoundedRectangle(
                            content,
                            moduleX + gap,
                            moduleY + gap,
                            module - 2 * gap,
                            module - 2 * gap,
                            module * 0.18f
                    );
                }
            }
        }
        content.fill();

        float patchSize = 70;
        float patchX = x + (size - patchSize) / 2;
        float patchY = y + (size - patchSize) / 2;
        content.setNonStrokingColor(WHITE);
        addRoundedRectangle(content, patchX, patchY, patchSize, patchSize, 12);
        content.fill();
        content.drawImage(logo, patchX + 10, patchY + 10, patchSize - 20, patchSize - 20);
    }

    private boolean isFinderModule(int column, int row, int dimension) {
        int start = 4;
        int end = start + 7;
        boolean left = column >= start && column < end;
        boolean right = column >= dimension - end && column < dimension - start;
        boolean top = row >= start && row < end;
        boolean bottom = row >= dimension - end && row < dimension - start;
        return (left && top) || (right && top) || (left && bottom);
    }

    private void drawDetails(PDPageContentStream content, QrPosterSpecification data, PDFont regular, PDFont bold)
            throws IOException {
        content.setNonStrokingColor(BRAND_SOFT);
        addRoundedRectangle(content, 92, 156, PAGE_WIDTH - 184, 105, 14);
        content.fill();
        String mode = data.reservationMode() == ReservationMode.LIVE_QUEUE ? "Canlı növbə" : "Planlı qəbul";
        drawCenteredText(content, bold, 15, mode, 226, BRAND);
        String detail = data.roomCode() == null || data.roomCode().isBlank()
                ? data.durationMinutes() + " dəqiqəlik qəbul"
                : "Otaq kodu: " + data.roomCode() + " · " + data.durationMinutes() + " dəqiqəlik qəbul";
        drawCenteredText(content, regular, 11, fit(detail, regular, 11, PAGE_WIDTH - 220), 201, MUTED);
        if (data.description() != null && !data.description().isBlank()) {
            String description = fit(data.description().trim().replaceAll("\\s+", " "), regular, 10, PAGE_WIDTH - 220);
            drawCenteredText(content, regular, 10, description, 178, MUTED);
        }
    }

    private PDFont loadFont(PDDocument document, String path) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return PDType0Font.load(document, input, true);
        }
    }

    private PDImageXObject loadLogo(PDDocument document) throws IOException {
        try (InputStream input = new ClassPathResource("pdf/novbetime-logo.png").getInputStream()) {
            BufferedImage source = ImageIO.read(input);
            return LosslessFactory.createFromImage(document, cropLogo(source));
        }
    }

    private BufferedImage cropLogo(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = 0;
        int maxY = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y), true);
                int maximum = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
                int minimum = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
                if (maximum - minimum < 25 || maximum > 235) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (minX > maxX || minY > maxY) return source;
        int padding = 10;
        int x = Math.max(0, minX - padding);
        int y = Math.max(0, minY - padding);
        int width = Math.min(source.getWidth() - x, maxX - minX + 1 + padding * 2);
        int height = Math.min(source.getHeight() - y, maxY - minY + 1 + padding * 2);
        return source.getSubimage(x, y, width, height);
    }

    private void setMetadata(PDDocument document, String title) {
        PDDocumentInformation information = document.getDocumentInformation();
        information.setTitle(title + " - NövbəTime QR kodu");
        information.setAuthor("NövbəTime");
        information.setSubject("Çap üçün QR afişası");
        information.setCreator("NövbəTime");
    }

    private List<String> wrap(String value, PDFont font, float size, float maxWidth, int maxLines)
            throws IOException {
        String[] words = value.trim().replaceAll("\\s+", " ").split(" ");
        List<String> lines = new ArrayList<>();
        String current = "";
        for (int index = 0; index < words.length; index++) {
            String candidate = current.isEmpty() ? words[index] : current + " " + words[index];
            if (textWidth(font, size, candidate) <= maxWidth) {
                current = candidate;
                continue;
            }
            if (!current.isEmpty()) lines.add(current);
            current = words[index];
            if (lines.size() == maxLines - 1) {
                StringBuilder remainder = new StringBuilder(current);
                for (int remaining = index + 1; remaining < words.length; remaining++) {
                    remainder.append(' ').append(words[remaining]);
                }
                current = fit(remainder.toString(), font, size, maxWidth);
                break;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) lines.add(fit(current, font, size, maxWidth));
        return lines.isEmpty() ? List.of("NövbəTime") : lines;
    }

    private String fit(String value, PDFont font, float size, float maxWidth) throws IOException {
        if (textWidth(font, size, value) <= maxWidth) return value;
        String suffix = "...";
        int length = value.length();
        while (length > 0 && textWidth(font, size, value.substring(0, length).trim() + suffix) > maxWidth) {
            length--;
        }
        return value.substring(0, Math.max(0, length)).trim() + suffix;
    }

    private void drawCenteredText(
            PDPageContentStream content,
            PDFont font,
            float size,
            String value,
            float y,
            Color color
    ) throws IOException {
        float x = (PAGE_WIDTH - textWidth(font, size, value)) / 2;
        drawText(content, font, size, value, x, y, color);
    }

    private void drawRightAlignedText(
            PDPageContentStream content,
            PDFont font,
            float size,
            String value,
            float right,
            float y,
            Color color
    ) throws IOException {
        drawText(content, font, size, value, right - textWidth(font, size, value), y, color);
    }

    private void drawText(
            PDPageContentStream content,
            PDFont font,
            float size,
            String value,
            float x,
            float y,
            Color color
    ) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }

    private float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    private void addRoundedRectangle(
            PDPageContentStream content,
            float x,
            float y,
            float width,
            float height,
            float radius
    ) throws IOException {
        float control = radius * 0.55228475f;
        content.moveTo(x + radius, y);
        content.lineTo(x + width - radius, y);
        content.curveTo(x + width - radius + control, y, x + width, y + radius - control, x + width, y + radius);
        content.lineTo(x + width, y + height - radius);
        content.curveTo(x + width, y + height - radius + control, x + width - radius + control, y + height, x + width - radius, y + height);
        content.lineTo(x + radius, y + height);
        content.curveTo(x + radius - control, y + height, x, y + height - radius + control, x, y + height - radius);
        content.lineTo(x, y + radius);
        content.curveTo(x, y + radius - control, x + radius - control, y, x + radius, y);
        content.closePath();
    }
}
