package org.stupormundi.chunk;

import java.io.IOException;

/** Sends a prompt to a language model. Replaced by a fake in tests. */
interface LlmClient {

    /** Returns the text of the model's reply to {@code prompt}. */
    String complete(String prompt) throws IOException, InterruptedException;
}
