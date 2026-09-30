package com.demandhub.platform.document.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

/** Detecção de tipo pelo conteúdo e extração de texto (PDF, Office, texto) via Apache Tika. */
@Service
public class TextExtractionService {

    private static final int MAX_CHARS = 1_000_000;

    private final Tika tika = new Tika();

    public String detectMimeType(byte[] content, String fileName) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        try (InputStream in = new ByteArrayInputStream(content)) {
            return tika.getDetector().detect(org.apache.tika.io.TikaInputStream.get(in), metadata).toString();
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    public String extract(byte[] content) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        try (InputStream in = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(in, handler, new Metadata(), new ParseContext());
        } catch (org.xml.sax.SAXException e) {
            // Limite de caracteres atingido: mantém o texto parcial.
            if (!e.getClass().getSimpleName().contains("WriteLimit")) {
                throw e;
            }
        }
        return handler.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
