package ai.wolf.autopilot;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline text extraction for explicitly selected PDF and DOCX.
 * Never renders or executes code; scanned/image PDFs have no text OCR.
 * Strict input, page, ZIP and output limits to bound mobile resource use.
 */
public final class DocumentExtractor {
    public static final int MAX_PDF_BYTES = 4_000_000;
    public static final int MAX_DOCX_BYTES = 4_000_000;
    public static final int MAX_TEXT_BYTES = 1_500_000;
    public static final int MAX_PDF_PAGES = 45;
    private DocumentExtractor() {}

    public static String extract(Context context, String name, byte[] file)
            throws Exception {
        if (name == null || file == null)
            throw new IllegalArgumentException("Пустой документ.");
        String lower = name.toLowerCase(Locale.ROOT);
        String plain;
        if (lower.endsWith(".pdf")) plain = pdf(context, file);
        else if (lower.endsWith(".docx")) plain = docx(file);
        else throw new IllegalArgumentException("Поддерживается только PDF или DOCX.");
        if (plain.trim().length() < 20)
            throw new IllegalArgumentException("Из документа не удалось извлечь текст. " +
                    "Сканированным страницам требуется OCR; он пока недоступен.");
        byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES)
            throw new IllegalArgumentException("Извлечённый текст превышает 1,5 МБ. " +
                    "Раздели книгу на несколько документов.");
        return plain;
    }

    private static String pdf(Context context, byte[] data) throws Exception {
        if (data.length < 30 || data.length > MAX_PDF_BYTES ||
                data[0] != '%' || data[1] != 'P' ||
                data[2] != 'D' || data[3] != 'F')
            throw new IllegalArgumentException("Нужен настоящий PDF до 4 МБ.");
        PDFBoxResourceLoader.init(context.getApplicationContext());
        try (PDDocument document = PDDocument.load(data)) {
            if (document.isEncrypted())
                throw new IllegalArgumentException("Зашифрованный PDF не поддерживается.");
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PDF_PAGES)
                throw new IllegalArgumentException("PDF: до 45 страниц за импорт. " +
                        "Раздели длинную книгу на части.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES)
                throw new IllegalArgumentException("В PDF слишком много текста (лимит 1,5 МБ).");
            return text;
        }
    }

    private static String docx(byte[] data) throws Exception {
        if (data.length < 30 || data.length > MAX_DOCX_BYTES ||
                data[0] != 'P' || data[1] != 'K')
            throw new IllegalArgumentException("Нужен настоящий DOCX до 4 МБ.");
        int entries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entries > 180)
                    throw new IllegalArgumentException("В DOCX слишком много ZIP-компонентов.");
                String entryName = entry.getName();
                // Read *only* the main document XML, not relationships,
                // embedded media, macros or external content.
                if (!"word/document.xml".equals(entryName)) {
                    // Limit decompressed bytes BEFORE the target document so
                    // malicious ZIP bombs cannot allocate unbounded memory.
                    byte[] skip = new byte[8192];
                    long skipped = 0;
                    int length;
                    while ((length = archive.read(skip)) != -1) {
                        skipped += length;
                        if (skipped > 2_000_000)
                            throw new IllegalArgumentException(
                                    "Непропорционально большой компонент DOCX.");
                    }
                    archive.closeEntry();
                    continue;
                }
                ByteArrayOutputStream xml = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = archive.read(buffer)) != -1) {
                    if (xml.size() + n > MAX_TEXT_BYTES * 3)
                        throw new IllegalArgumentException("XML внутри DOCX слишком большой.");
                    xml.write(buffer, 0, n);
                }
                archive.closeEntry();
                byte[] content = xml.toByteArray();
                String raw = new String(content, StandardCharsets.UTF_8);
                if (raw.contains("<!DOCTYPE") || raw.contains("<!ENTITY"))
                    throw new IllegalArgumentException("DOCX с DTD запрещён.");
                return extractWordText(content);
            }
        }
        throw new IllegalArgumentException("DOCX не содержит word/document.xml.");
    }

    private static String extractWordText(byte[] xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder text = new StringBuilder();
        boolean insideWordText = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("t".equals(name) && parser.getNamespace() != null &&
                        parser.getNamespace().contains("wordprocessingml"))
                    insideWordText = true;
                else if ("tab".equals(name)) text.append('\t');
                else if ("br".equals(name) || "cr".equals(name)) text.append('\n');
            } else if (event == XmlPullParser.TEXT && insideWordText) {
                text.append(parser.getText());
                if (text.length() > MAX_TEXT_BYTES)
                    throw new IllegalArgumentException("DOCX содержит слишком много текста.");
            } else if (event == XmlPullParser.END_TAG) {
                if ("t".equals(parser.getName())) insideWordText = false;
                else if ("p".equals(parser.getName())) text.append('\n');
            }
            event = parser.next();
        }
        return text.toString();
    }
}
