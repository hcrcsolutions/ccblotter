package com.fmr.ec3.oscc.state.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecordingNotFoundException extends RuntimeException {

    private final String filename;

    public RecordingNotFoundException(String filename) {
        super("Recording not found: " + filename);
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }
}
