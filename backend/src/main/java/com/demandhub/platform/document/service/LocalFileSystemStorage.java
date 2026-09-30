package com.demandhub.platform.document.service;

import com.demandhub.platform.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Armazena arquivos em disco com chave UUID (o nome original nunca compõe o caminho). */
@Component
public class LocalFileSystemStorage implements DocumentStorage {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{2}/[0-9a-f-]{36}$");

    private final Path root;

    public LocalFileSystemStorage(AppProperties props) {
        this.root = Path.of(props.storage().path()).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content) {
        String id = UUID.randomUUID().toString();
        String key = id.substring(0, 2) + "/" + id;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao armazenar arquivo", e);
        }
        return key;
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler arquivo", e);
        }
    }

    private Path resolve(String key) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Caminho fora do diretório de armazenamento");
        }
        return p;
    }
}
