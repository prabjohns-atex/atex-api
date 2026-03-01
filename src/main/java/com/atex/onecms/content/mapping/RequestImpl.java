package com.atex.onecms.content.mapping;
import com.atex.onecms.content.ContentManager.GetOption;
import com.atex.onecms.content.Subject;
import java.util.Collections;
import java.util.Map;
public class RequestImpl implements Request {
    private final Map<String, Object> requestParameters;
    private final Subject subject;
    private final GetOption[] options;
    public RequestImpl(Map<String, Object> requestParameters, Subject subject, GetOption... options) {
        this.requestParameters = requestParameters != null ? requestParameters : Collections.emptyMap();
        this.subject = subject;
        this.options = options;
    }
    public static RequestImpl of(Map<String, Object> params, Subject subject) {
        return new RequestImpl(params, subject);
    }
    @Override
    public Map<String, Object> getRequestParameters() {
        return requestParameters;
    }
    @Override
    public Subject getSubject() {
        return subject;
    }
    @Override
    public GetOption[] getOptions() {
        return options;
    }
}
