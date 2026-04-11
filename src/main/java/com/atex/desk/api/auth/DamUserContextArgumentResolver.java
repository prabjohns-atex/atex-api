package com.atex.desk.api.auth;

import com.atex.onecms.app.dam.ws.DamUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring MVC argument resolver that injects {@link DamUserContext} into
 * controller method parameters. Matches the Polopoly {@code @AuthUser} pattern:
 * declare the parameter, call {@code assertLoggedIn()} inside the method
 * to enforce authentication.
 *
 * <p>Example:
 * <pre>
 *   {@literal @}GetMapping("/stuff")
 *   public ResponseEntity{@literal <}?{@literal >} doStuff(DamUserContext user) {
 *       user.assertLoggedIn();
 *       ...
 *   }
 * </pre>
 *
 * <p>Works with both built-in and plugin controllers, since Spring MVC invokes
 * argument resolvers for all handler methods regardless of URL pattern.
 */
public class DamUserContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return DamUserContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return DamUserContext.from(request);
    }
}
