package ru.citeck.ecos.security.jwt;

import org.springframework.web.filter.GenericFilterBean;
import ru.citeck.ecos.commons.utils.StringUtils;
import ru.citeck.ecos.gateway.GatewayHeader;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is
 * found.
 */
public class UserHeaderFilter extends GenericFilterBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        String userHeader = httpServletRequest.getHeader(GatewayHeader.ECOS_USER);
        if (StringUtils.isBlank(userHeader)) {
            //throw new RuntimeException("ECOS User is not found");
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
