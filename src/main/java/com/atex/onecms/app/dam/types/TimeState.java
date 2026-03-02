package com.atex.onecms.app.dam.types;

import java.util.Objects;
import java.util.StringJoiner;

public class TimeState {

    private Long ontime;
    private Long offtime;

    public TimeState(final Long ontime, final Long offtime) {
        this.ontime = ontime;
        this.offtime = offtime;
    }

    public TimeState() {}

    public Long getOntime() { return ontime; }
    public void setOntime(final Long ontime) { this.ontime = ontime; }
    public Long getOfftime() { return offtime; }
    public void setOfftime(final Long offtime) { this.offtime = offtime; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TimeState timeState = (TimeState) o;
        return Objects.equals(ontime, timeState.ontime) &&
            Objects.equals(offtime, timeState.offtime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontime, offtime);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TimeState.class.getSimpleName() + "[", "]")
            .add("ontime=" + ontime)
            .add("offtime=" + offtime)
            .toString();
    }
}

