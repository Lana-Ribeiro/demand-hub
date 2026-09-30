package com.demandhub.platform.document.service;

/** Porta de armazenamento de arquivos (local no MVP; Azure Blob/S3 no futuro). */
public interface DocumentStorage {

    /** Armazena o conteúdo e retorna a chave opaca. */
    String store(byte[] content);

    byte[] load(String key);
}
