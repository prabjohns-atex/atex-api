package com.atex.onecms.app.dam.standard.aspects;

public interface TranscribeSupport {
    String getTranscriptionText();
    void setTranscriptionText(final String transcriptionText);
    Boolean isTranscribeFlag();
    void setTranscribeFlag(final Boolean transcribeFlag);
}

