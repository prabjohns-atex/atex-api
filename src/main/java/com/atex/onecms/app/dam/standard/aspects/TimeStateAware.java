package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.types.TimeState;

public interface TimeStateAware {
    TimeState getTimeState();
    void setTimeState(final TimeState timeState);
}

