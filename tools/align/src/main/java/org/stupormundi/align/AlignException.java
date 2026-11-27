package org.stupormundi.align;

/** A problem with the input that the user should be told about; the CLI prints the message and exits with 1. */
class AlignException extends RuntimeException {
    AlignException(String message) {
        super(message);
    }
}
