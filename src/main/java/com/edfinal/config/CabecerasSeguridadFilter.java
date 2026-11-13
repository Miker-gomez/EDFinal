package com.edfinal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Agrega las cabeceras de seguridad básicas a todas las respuestas
@Component
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    private static final String POLITICA_CONTENIDO = "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", POLITICA_CONTENIDO);
        chain.doFilter(request, response);

    }

}
