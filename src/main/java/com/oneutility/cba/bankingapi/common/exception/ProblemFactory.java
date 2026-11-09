package com.oneutility.cba.bankingapi.common.exception;

import com.oneutility.cba.bankingapi.common.web.CorrelationId;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds the API's problem details body: {@code type}, {@code title}, {@code status}, {@code detail}, {@code code}, ... */
@Component
public class ProblemFactory {

    private final String typeBaseUri;

    public ProblemFactory(@Value("${banking-api.problems.type-base-uri}") String typeBaseUri) {
        this.typeBaseUri = typeBaseUri.endsWith("/") ? typeBaseUri : typeBaseUri + "/";
    }

    public ProblemDetail create(ErrorCode code, String detail, String path) {
        return create(code, detail, path, List.of());
    }

    public ProblemDetail create(ErrorCode code, String detail, String path, List<FieldViolation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(typeBaseUri + code.slug()));
        problem.setTitle(code.title());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", CorrelationId.current());
        if (!violations.isEmpty()) {
            problem.setProperty("errors", violations);
        }
        return problem;
    }
}
