package com.atex.desk.api.auth;

import java.util.Date;
import java.util.List;

public record DecodedToken(
    String subject,
    String impersonating,
    List<String> permissions,
    List<String> targets,
    Date expiration
)
{
}
