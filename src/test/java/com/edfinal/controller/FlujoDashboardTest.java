package com.edfinal.controller;

import com.edfinal.service.TareaService;
import com.edfinal.support.PostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Secuencias de la matriz de verificación (F02, F04, F05 y F09) tal como las ejecuta el dashboard:
// cada acción va seguida de la consulta del widget crítico y de la lista
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FlujoDashboardTest extends PostgresTestBase {

    private final MockMvc mockMvc;
    private final TareaService tareaService;
    private final ObjectMapper mapper = new ObjectMapper();

    private int materiaId;

    public FlujoDashboardTest(MockMvc mockMvc, TareaService tareaService) {

        this.mockMvc = mockMvc;
        this.tareaService = tareaService;

    }

    @BeforeEach
    void prepararEstructuras() {

        // Arrange
        tareaService.recargarDesdeBaseDeDatos();
        materiaId = crearMateria("Estructuras de Datos");

    }

    @Test
    @DisplayName("F02: una tarea de mayor prioridad pasa a ser la crítica")
    void registrarMasPrioritaria_cambiaElWidget() throws Exception {

        // Arrange
        crearTarea("Normal", 3, 5);

        // Act
        String criticaAntes = tituloDeLaCritica();
        crearTarea("Urgente", 5, 5);
        String criticaDespues = tituloDeLaCritica();

        // Assert
        assertEquals("Normal", criticaAntes);
        assertEquals("Urgente", criticaDespues);

    }

    @Test
    @DisplayName("F02: con igual prioridad gana la fecha límite más cercana")
    void igualPrioridad_ganaLaFechaMasCercana() throws Exception {

        // Arrange
        crearTarea("Lejana", 4, 10);
        crearTarea("Cercana", 4, 2);

        // Act
        String critica = tituloDeLaCritica();
        JsonNode lista = mapper.readTree(enviar("GET", "/api/tareas", null).getContentAsString());

        // Assert
        assertEquals("Cercana", critica);
        assertEquals("Cercana", lista.get(0).get("titulo").asText());
        assertEquals("Lejana", lista.get(1).get("titulo").asText());

    }

    @Test
    @DisplayName("F04 y F05: completar la crítica muestra la siguiente y deshacer devuelve la anterior")
    void completarLaCritica_yDeshacer() throws Exception {

        // Arrange
        String idCritica = crearTarea("Crítica", 5, 5);
        crearTarea("Siguiente", 2, 5);

        // Act
        enviar("PUT", "/api/tareas/" + idCritica + "/completar", null);
        String despuesDeCompletar = tituloDeLaCritica();
        MockHttpServletResponse buscada = enviar("GET", "/api/tareas/" + idCritica, null);
        enviar("POST", "/api/tareas/deshacer", null);
        String despuesDeDeshacer = tituloDeLaCritica();

        // Assert
        assertEquals("Siguiente", despuesDeCompletar);
        assertEquals(404, buscada.getStatus());
        assertEquals("Crítica", despuesDeDeshacer);

    }

    @Test
    @DisplayName("F04 y F05: completar una tarea que no es la raíz conserva el orden de la lista")
    void completarUnaIntermedia_conservaElOrden() throws Exception {

        // Arrange
        crearTarea("Alta", 5, 5);
        String idMedia = crearTarea("Media", 3, 5);
        crearTarea("Baja", 1, 5);

        // Act
        enviar("PUT", "/api/tareas/" + idMedia + "/completar", null);
        JsonNode lista = mapper.readTree(enviar("GET", "/api/tareas", null).getContentAsString());

        // Assert
        assertEquals(2, lista.size());
        assertEquals("Alta", lista.get(0).get("titulo").asText());
        assertEquals("Baja", lista.get(1).get("titulo").asText());

    }

    @Test
    @DisplayName("F05: completar una tarea VENCIDA y deshacer la devuelve como VENCIDA")
    void completarVencida_yDeshacer_vuelveVencida() throws Exception {

        // Arrange
        String id = crearTarea("Atrasada", 3, 5);
        forzarPlazoExpirado(id);
        enviar("POST", "/api/tareas/vencidas", null);

        // Act
        enviar("PUT", "/api/tareas/" + id + "/completar", null);
        MockHttpServletResponse restaurada = enviar("POST", "/api/tareas/deshacer", null);

        // Assert
        assertEquals(200, restaurada.getStatus());
        assertEquals("VENCIDA", mapper.readTree(restaurada.getContentAsString()).get("estado").asText());
        assertEquals("VENCIDA", mapper.readTree(enviar("GET", "/api/tareas/" + id, null).getContentAsString()).get("estado").asText());

    }

    @Test
    @DisplayName("F09: bajar la prioridad de la crítica hace que la siguiente pase a ser la crítica")
    void editarLaCritica_cambiaElWidget() throws Exception {

        // Arrange
        String idCritica = crearTarea("Crítica", 5, 5);
        crearTarea("Otra", 3, 5);

        // Act
        enviar("PUT", "/api/tareas/" + idCritica, cuerpoTarea("Crítica", materiaId, 1, 5));
        String critica = tituloDeLaCritica();
        JsonNode lista = mapper.readTree(enviar("GET", "/api/tareas", null).getContentAsString());

        // Assert
        assertEquals("Otra", critica);
        assertEquals("Otra", lista.get(0).get("titulo").asText());
        assertEquals("Crítica", lista.get(1).get("titulo").asText());

    }

    @Test
    @DisplayName("F09: editar una VENCIDA con fecha futura la devuelve a PENDIENTE")
    void editarVencida_conFechaFutura_pasaAPendiente() throws Exception {

        // Arrange
        String id = crearTarea("Atrasada", 3, 5);
        forzarPlazoExpirado(id);
        enviar("POST", "/api/tareas/vencidas", null);

        // Act
        MockHttpServletResponse editada = enviar("PUT", "/api/tareas/" + id, cuerpoTarea("Atrasada", materiaId, 3, 7));

        // Assert
        assertEquals(200, editada.getStatus());
        assertEquals("PENDIENTE", mapper.readTree(editada.getContentAsString()).get("estado").asText());
        assertTrue(contarAuditoria(id) >= 2);

    }

    // ---------- apoyo ----------

    private String tituloDeLaCritica() throws Exception {

        // Consulta el widget de la crítica y devuelve su título
        MockHttpServletResponse respuesta = enviar("GET", "/api/tareas/critica", null);
        return mapper.readTree(respuesta.getContentAsString()).get("titulo").asText();

    }

    private MockHttpServletResponse enviar(String metodo, String ruta, String cuerpo) throws Exception {

        // Arma la petición, con cuerpo JSON si lo hay, y devuelve la respuesta
        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), ruta);
        if (cuerpo != null) {
            peticion = peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(peticion).andReturn().getResponse();

    }

    private String crearTarea(String titulo, int prioridad, int dias) throws Exception {

        // Registra la tarea por la API y devuelve su id
        MockHttpServletResponse respuesta = enviar("POST", "/api/tareas", cuerpoTarea(titulo, materiaId, prioridad, dias));
        return mapper.readTree(respuesta.getContentAsString()).get("id").asText();

    }

    private String cuerpoTarea(String titulo, int materia, int prioridad, int dias) {

        // JSON de entrada de una tarea con fecha límite dentro de los días indicados
        return "{\"titulo\":\"" + titulo + "\",\"materiaId\":" + materia + ",\"prioridad\":" + prioridad
                + ",\"fechaLimite\":\"" + LocalDateTime.now().plusDays(dias).truncatedTo(ChronoUnit.SECONDS) + "\"}";

    }

}
