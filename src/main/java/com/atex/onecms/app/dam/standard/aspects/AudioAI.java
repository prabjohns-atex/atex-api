package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.ContentId;

public class AudioAI {

    private Boolean enabled;
    private String toneId;
    private String voiceId;
    private ContentId audioId;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(final Boolean enabled) { this.enabled = enabled; }
    public String getToneId() { return toneId; }
    public void setToneId(final String toneId) { this.toneId = toneId; }
    public String getVoiceId() { return voiceId; }
    public void setVoiceId(final String voiceId) { this.voiceId = voiceId; }
    public ContentId getAudioId() { return audioId; }
    public void setAudioId(final ContentId audioId) { this.audioId = audioId; }
}

