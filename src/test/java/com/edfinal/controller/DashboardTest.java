package com.edfinal.controller;

import com.edfinal.service.TareaService;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Comprueba que la aplicación sirve el dashboard completo: la página, sus scripts, su hoja de estilos y Bootstrap
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DashboardTest extends PostgresTestBase {

    private static final String[] ELEMENTOS_DE_LA_PAGINA = {
            "id=\"widgetCritica\"", "id=\"criticaContenido\"", "id=\"formTarea\"", "id=\"tareaMateria\"",
            "id=\"listaTareas\"", "id=\"formBuscar\"", "id=\"buscarId\"", "id=\"btnDeshacer\"",
            "id=\"btnVencidas\"", "id=\"formMateria\"", "id=\"listaMaterias\"", "id=\"formProfesor\"",
            "id=\"listaProfesores\"", "id=\"listaEstadisticas\"", "id=\"modalEditar\"", "id=\"formEditar\"",
            "id=\"alertas\"", "id=\"tablaTareas\""
    };

    private final MockMvc mockMvc;
    private final TareaService tareaService;

    public DashboardTest(MockMvc mockMvc, TareaService tareaService) {

        this.mockMvc = mockMvc;
        this.tareaService = tareaService;

    }

    @BeforeEach
    void vaciarEstructuras() {

        // Arrange
        tareaService.recargarDesdeBaseDeDatos();

    }

    @Test
    @DisplayName("GET / lleva a index.html")
    void raiz_llevaALaPaginaPrincipal() throws Exception {

        // Arrange
        String ruta = "/";

        // Act
        MockHttpServletResponse respuesta = pedir(ruta);

        // Assert
        assertEquals(200, respuesta.getStatus());
        assertEquals("index.html", respuesta.getForwardedUrl());

    }

    @Test
    @DisplayName("index.html tiene todos los elementos que usa el script")
    void paginaPrincipal_tieneLosElementos() throws Exception {

        // Arrange
        String ruta = "/index.html";

        // Act
        MockHttpServletResponse respuesta = pedir(ruta);
        String html = respuesta.getContentAsString();

        // Assert
        assertEquals(200, respuesta.getStatus());
        assertTrue(respuesta.getContentType().startsWith("text/html"));
        for (String elemento : ELEMENTOS_DE_LA_PAGINA) {

            assertTrue(html.contains(elemento), "Falta " + elemento);

        }

    }

    @Test
    @DisplayName("El dashboard enlaza sus scripts, su hoja de estilos y Bootstrap")
    void paginaPrincipal_enlazaLosRecursos() throws Exception {

        // Arrange
        String ruta = "/index.html";

        // Act
        String html = pedir(ruta).getContentAsString();

        // Assert
        assertTrue(html.contains("/webjars/bootstrap/5.3.8/css/bootstrap.min.css"));
        assertTrue(html.contains("/webjars/bootstrap/5.3.8/js/bootstrap.bundle.min.js"));
        assertTrue(html.contains("/css/estilos.css"));
        assertTrue(html.contains("/js/api.js"));
        assertTrue(html.contains("/js/app.js"));

    }

    @Test
    @DisplayName("El dashboard declara un favicon vacío para no pedir /favicon.ico")
    void paginaPrincipalDeclaraFavicon() throws Exception {

        // Arrange
        String ruta = "/index.html";

        // Act
        String html = pedir(ruta).getContentAsString();

        // Assert
        assertTrue(html.contains("<link rel=\"icon\" href=\"data:,\">"));

    }

    @Test
    @DisplayName("En pantallas angostas la hoja de estilos oculta la columna de la materia")
    void paginaPrincipalOcultaLaMateriaEnPantallasAngostas() throws Exception {

        // Arrange
        String ruta = "/index.html";

        // Act
        String html = pedir(ruta).getContentAsString(StandardCharsets.UTF_8);
        String css = pedir("/css/estilos.css").getContentAsString();
        String script = pedir("/js/app.js").getContentAsString();

        // Assert
        assertTrue(html.contains("<th class=\"columna-materia\">Materia</th>"));
        assertTrue(script.contains("crear('td', tarea.materia, 'columna-materia')"));
        assertTrue(css.contains("@media (max-width: 575.98px) {\n  #tablaTareas .columna-materia {\n    display: none;"));

    }

    @Test
    @DisplayName("Los scripts, la hoja de estilos y Bootstrap se sirven con 200")
    void recursosEstaticos_responden200() throws Exception {

        // Arrange
        String rutaBootstrap = "/webjars/bootstrap/5.3.8";

        // Act
        MockHttpServletResponse api = pedir("/js/api.js");
        MockHttpServletResponse app = pedir("/js/app.js");
        MockHttpServletResponse estilos = pedir("/css/estilos.css");
        MockHttpServletResponse bootstrapCss = pedir(rutaBootstrap + "/css/bootstrap.min.css");
        MockHttpServletResponse bootstrapJs = pedir(rutaBootstrap + "/js/bootstrap.bundle.min.js");

        // Assert
        assertEquals(200, api.getStatus());
        assertEquals(200, app.getStatus());
        assertEquals(200, estilos.getStatus());
        assertEquals(200, bootstrapCss.getStatus());
        assertEquals(200, bootstrapJs.getStatus());
        assertTrue(api.getContentAsString().contains("function pedir"));
        assertTrue(app.getContentAsString().contains("function refrescar"));

    }

    @Test
    @DisplayName("Todas las rutas de la API que usa el script existen")
    void rutasQueUsaElScript_existen() throws Exception {

        // Arrange
        String codigo = pedir("/js/app.js").getContentAsString();

        // Act
        MockHttpServletResponse critica = pedir("/api/tareas/critica");
        MockHttpServletResponse tareas = pedir("/api/tareas");
        MockHttpServletResponse estadisticas = pedir("/api/estadisticas");
        MockHttpServletResponse materias = pedir("/api/materias");
        MockHttpServletResponse profesores = pedir("/api/profesores");

        // Assert
        assertTrue(codigo.contains("/api/tareas/critica"));
        assertTrue(codigo.contains("/api/estadisticas"));
        assertEquals(204, critica.getStatus());
        assertEquals(200, tareas.getStatus());
        assertEquals(200, estadisticas.getStatus());
        assertEquals(200, materias.getStatus());
        assertEquals(200, profesores.getStatus());

    }

    // ---------- apoyo ----------

    private MockHttpServletResponse pedir(String ruta) throws Exception {

        // Envía un GET a la ruta y devuelve la respuesta
        return mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.GET, ruta)).andReturn().getResponse();

    }

}
