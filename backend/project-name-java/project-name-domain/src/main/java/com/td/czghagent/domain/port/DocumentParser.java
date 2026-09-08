// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.model.StoredFile;

public interface DocumentParser {

    ParsedDocument parse(String documentId, StoredFile file);
}
