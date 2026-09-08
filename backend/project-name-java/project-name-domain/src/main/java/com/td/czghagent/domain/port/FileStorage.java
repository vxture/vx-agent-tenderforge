// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.StoredFile;

public interface FileStorage {

    StoredFile store(String namespace, String fileName, String mediaType, byte[] content);

    StoredFile read(String objectKey, String fileName, String mediaType);

    void delete(String objectKey);
}
