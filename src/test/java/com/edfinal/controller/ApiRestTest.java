package com.edfinal.controller;

import com.edfinal.service.TareaService;
import com.edfinal.support.PostgresTestBase;
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

// Contexto nuevo por prueba: la pila de deshacer vive en TareaService y no debe pasar de una prueba a otra
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ApiRestTest extends PostgresTestBase {

    private final MockMvc mockMvc;
    private final TareaService tareaService;
    private final ObjectMapper mapper = new ObjectMapper();

    private int materiaId;

    public ApiRestTest(MockMvc mockMvc, TareaService tareaService) {

        this.mockMvc = mockMvc;
        this.tareaService = tareaService;

    }

    @BeforeEach
    void prepararEstructuras() {

        // Arrange
        tareaService.recargarDesdeBaseDeDatos();
        materiaId = crearMateria("Estructuras de Datos");

    }

    // ---------- tareas ----------

    @Test
    @DisplayName("POST /api/tareas crea la tarea y la lista queda de la más urgente a la menos urgente")
    void crearTarea_yListarEnOrden() throws Exception {

        // Act
        MockHttpServletResponse baja = enviar("POST", "/api/tareas", cuerpoTarea("Baja", materiaId, 1, 5));
        MockHttpServletResponse alta = enviar("POST", "/api/tareas", cuerpoTarea("Alta", materiaId, 5, 5));
        MockHttpServletResponse lista = enviar("GET", "/api/tareas", null);

        // Assert
        assertEquals(201, baja.getStatus());
        assertEquals(201, alta.getStatus());
        assertTrue(alta.getContentAsString().contains("\"materia\":\"Estructuras de Datos\""));
        assertTrue(alta.getContentAsString().contains("\"estado\":\"PENDIENTE\""));
        assertEquals(200, lista.getStatus());
        assertEquals("Alta", mapper.readTree(lista.getContentAsString()).get(0).get("titulo").asText());
        assertEquals("Baja", mapper.readTree(lista.getContentAsString()).get(1).get("titulo").asText());

    }

    @Test
    @DisplayName("POST /api/tareas rechaza datos inválidos con 400 y el formato { status, mensaje }")
    void crearTarea_datosInvalidos_400() throws Exception {

        // Act
        MockHttpServletResponse prioridadSeis = enviar("POST", "/api/tareas", cuerpoTarea("Taller", materiaId, 6, 5));
        MockHttpServletResponse prioridadCero = enviar("POST", "/api/tareas", cuerpoTarea("Taller", materiaId, 0, 5));
        MockHttpServletResponse tituloVacio = enviar("POST", "/api/tareas", cuerpoTarea("  ", materiaId, 3, 5));
        MockHttpServletResponse fechaPasada = enviar("POST", "/api/tareas", cuerpoTarea("Taller", materiaId, 3, -1));
        MockHttpServletResponse fechaMalFormada = enviar("POST", "/api/tareas",
                "{\"titulo\":\"Taller\",\"materiaId\":" + materiaId + ",\"prioridad\":3,\"fechaLimite\":\"mañana\"}");
        MockHttpServletResponse sinMateria = enviar("POST", "/api/tareas",
                "{\"titulo\":\"Taller\",\"prioridad\":3,\"fechaLimite\":\"" + fecha(5) + "\"}");

        // Assert
        assertEquals(400, prioridadSeis.getStatus());
        assertEquals("La prioridad debe estar entre 1 y 5", mapper.readTree(prioridadSeis.getContentAsString()).get("mensaje").asText());
        assertEquals(400, mapper.readTree(prioridadSeis.getContentAsString()).get("status").asInt());
        assertEquals(400, prioridadCero.getStatus());
        assertEquals(400, tituloVacio.getStatus());
        assertEquals(400, fechaPasada.getStatus());
        assertEquals(400, fechaMalFormada.getStatus());
        assertEquals(400, sinMateria.getStatus());
        assertTrue(tareaService.listarActivas().isEmpty());

    }

    @Test
    @DisplayName("POST /api/tareas con una materia inexistente responde 404")
    void crearTarea_materiaInexistente_404() throws Exception {

        // Act
        MockHttpServletResponse respuesta = enviar("POST", "/api/tareas", cuerpoTarea("Taller", 99999, 3, 5));

        // Assert
        assertEquals(404, respuesta.getStatus());
        assertTrue(tareaService.listarActivas().isEmpty());

    }

    @Test
    @DisplayName("GET /api/tareas/critica responde 204 sin tareas y la raíz del heap con tareas")
    void consultarCritica() throws Exception {

        // Act
        MockHttpServletResponse vacia = enviar("GET", "/api/tareas/critica", null);
        enviar("POST", "/api/tareas", cuerpoTarea("Media", materiaId, 3, 5));
        enviar("POST", "/api/tareas", cuerpoTarea("Alta", materiaId, 5, 5));
        MockHttpServletResponse critica = enviar("GET", "/api/tareas/critica", null);

        // Assert
        assertEquals(204, vacia.getStatus());
        assertEquals(200, critica.getStatus());
        assertEquals("Alta", mapper.readTree(critica.getContentAsString()).get("titulo").asText());

    }

    @Test
    @DisplayName("GET /api/tareas/{id} responde 200 si está activa y 404 si no existe")
    void buscarPorId() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);

        // Act
        MockHttpServletResponse encontrada = enviar("GET", "/api/tareas/" + id, null);
        MockHttpServletResponse inexistente = enviar("GET", "/api/tareas/no-existe", null);

        // Assert
        assertEquals(200, encontrada.getStatus());
        assertEquals(id, mapper.readTree(encontrada.getContentAsString()).get("id").asText());
        assertEquals(404, inexistente.getStatus());
        assertEquals("Tarea no encontrada", mapper.readTree(inexistente.getContentAsString()).get("mensaje").asText());

    }

    @Test
    @DisplayName("PUT /api/tareas/{id} edita la tarea activa y reordena el heap")
    void editarTarea() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 2, 5);
        crearTarea("Parcial", 4, 5);

        // Act
        MockHttpServletResponse editada = enviar("PUT", "/api/tareas/" + id, cuerpoTarea("Taller final", materiaId, 5, 6));
        MockHttpServletResponse critica = enviar("GET", "/api/tareas/critica", null);

        // Assert
        assertEquals(200, editada.getStatus());
        assertEquals("Taller final", mapper.readTree(editada.getContentAsString()).get("titulo").asText());
        assertEquals(id, mapper.readTree(critica.getContentAsString()).get("id").asText());

    }

    @Test
    @DisplayName("PUT /api/tareas/{id} responde 404 si no está activa y 400 si la fecha es pasada")
    void editarTarea_errores() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 2, 5);

        // Act
        MockHttpServletResponse inexistente = enviar("PUT", "/api/tareas/no-existe", cuerpoTarea("Taller", materiaId, 2, 5));
        MockHttpServletResponse fechaPasada = enviar("PUT", "/api/tareas/" + id, cuerpoTarea("Taller", materiaId, 2, -1));
        MockHttpServletResponse materiaInexistente = enviar("PUT", "/api/tareas/" + id, cuerpoTarea("Taller", 99999, 2, 5));
        enviar("PUT", "/api/tareas/" + id + "/completar", null);
        MockHttpServletResponse completada = enviar("PUT", "/api/tareas/" + id, cuerpoTarea("Taller", materiaId, 2, 5));

        // Assert
        assertEquals(404, inexistente.getStatus());
        assertEquals(400, fechaPasada.getStatus());
        assertEquals(404, materiaInexistente.getStatus());
        assertEquals(404, completada.getStatus());

    }

    @Test
    @DisplayName("PUT /api/tareas/{id}/completar saca la tarea del flujo activo")
    void completarTarea() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);

        // Act
        MockHttpServletResponse completada = enviar("PUT", "/api/tareas/" + id + "/completar", null);
        MockHttpServletResponse busqueda = enviar("GET", "/api/tareas/" + id, null);
        MockHttpServletResponse otraVez = enviar("PUT", "/api/tareas/" + id + "/completar", null);

        // Assert
        assertEquals(200, completada.getStatus());
        assertEquals("COMPLETADA", mapper.readTree(completada.getContentAsString()).get("estado").asText());
        assertEquals(404, busqueda.getStatus());
        assertEquals(404, otraVez.getStatus());

    }

    @Test
    @DisplayName("DELETE /api/tareas/{id} responde 204 y luego 404")
    void eliminarTarea() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);

        // Act
        MockHttpServletResponse eliminada = enviar("DELETE", "/api/tareas/" + id, null);
        MockHttpServletResponse otraVez = enviar("DELETE", "/api/tareas/" + id, null);

        // Assert
        assertEquals(204, eliminada.getStatus());
        assertEquals(404, otraVez.getStatus());

    }

    @Test
    @DisplayName("POST /api/tareas/deshacer restaura la última acción y responde 409 con la pila vacía")
    void deshacerTarea() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);
        enviar("DELETE", "/api/tareas/" + id, null);

        // Act
        MockHttpServletResponse restaurada = enviar("POST", "/api/tareas/deshacer", null);
        MockHttpServletResponse sinAcciones = enviar("POST", "/api/tareas/deshacer", null);

        // Assert
        assertEquals(200, restaurada.getStatus());
        assertEquals(id, mapper.readTree(restaurada.getContentAsString()).get("id").asText());
        assertEquals("PENDIENTE", mapper.readTree(restaurada.getContentAsString()).get("estado").asText());
        assertEquals(409, sinAcciones.getStatus());
        assertEquals("No hay acciones para deshacer", mapper.readTree(sinAcciones.getContentAsString()).get("mensaje").asText());

    }

    @Test
    @DisplayName("POST /api/tareas/vencidas marca las tareas expiradas y devuelve la cantidad")
    void marcarVencidas() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);
        forzarPlazoExpirado(id);

        // Act
        MockHttpServletResponse respuesta = enviar("POST", "/api/tareas/vencidas", null);
        MockHttpServletResponse tarea = enviar("GET", "/api/tareas/" + id, null);

        // Assert
        assertEquals(200, respuesta.getStatus());
        assertEquals(1, mapper.readTree(respuesta.getContentAsString()).get("marcadas").asInt());
        assertEquals("VENCIDA", mapper.readTree(tarea.getContentAsString()).get("estado").asText());

    }

    @Test
    @DisplayName("GET /api/estadisticas devuelve el conteo por materia")
    void estadisticas() throws Exception {

        // Arrange
        crearTarea("Taller", 3, 5);
        String id = crearTarea("Parcial", 4, 5);
        enviar("PUT", "/api/tareas/" + id + "/completar", null);

        // Act
        MockHttpServletResponse respuesta = enviar("GET", "/api/estadisticas", null);

        // Assert
        assertEquals(200, respuesta.getStatus());
        assertEquals(2, mapper.readTree(respuesta.getContentAsString()).get(0).get("total").asInt());
        assertEquals(1, mapper.readTree(respuesta.getContentAsString()).get(0).get("pendientes").asInt());
        assertEquals(1, mapper.readTree(respuesta.getContentAsString()).get(0).get("completadas").asInt());

    }

    // ---------- materias ----------

    @Test
    @DisplayName("Materias: crear, listar, consultar, editar y eliminar")
    void materias_crud() throws Exception {

        // Arrange
        int profesorId = crearProfesor("materias@test.com");

        // Act
        MockHttpServletResponse creada = enviar("POST", "/api/materias", cuerpoMateria("Algoritmos", profesorId));
        int id = mapper.readTree(creada.getContentAsString()).get("id").asInt();
        MockHttpServletResponse lista = enviar("GET", "/api/materias", null);
        MockHttpServletResponse consultada = enviar("GET", "/api/materias/" + id, null);
        MockHttpServletResponse editada = enviar("PUT", "/api/materias/" + id, cuerpoMateria("Algoritmos Avanzados", profesorId));
        MockHttpServletResponse eliminada = enviar("DELETE", "/api/materias/" + id, null);
        MockHttpServletResponse inexistente = enviar("GET", "/api/materias/" + id, null);

        // Assert
        assertEquals(201, creada.getStatus());
        assertEquals(200, lista.getStatus());
        assertEquals(2, mapper.readTree(lista.getContentAsString()).size());
        assertEquals(200, consultada.getStatus());
        assertEquals(200, editada.getStatus());
        assertEquals("Algoritmos Avanzados", mapper.readTree(editada.getContentAsString()).get("nombre").asText());
        assertEquals(204, eliminada.getStatus());
        assertEquals(404, inexistente.getStatus());

    }

    @Test
    @DisplayName("Materias: errores 400, 404 y 409")
    void materias_errores() throws Exception {

        // Arrange
        int profesorId = crearProfesor("errores@test.com");
        enviar("POST", "/api/materias", cuerpoMateria("Algoritmos", profesorId));

        // Act
        MockHttpServletResponse nombreVacio = enviar("POST", "/api/materias", cuerpoMateria("", profesorId));
        MockHttpServletResponse profesorInexistente = enviar("POST", "/api/materias", cuerpoMateria("Redes", 99999));
        MockHttpServletResponse duplicada = enviar("POST", "/api/materias", cuerpoMateria("Algoritmos", profesorId));
        MockHttpServletResponse editarInexistente = enviar("PUT", "/api/materias/99999", cuerpoMateria("Redes", profesorId));
        MockHttpServletResponse eliminarInexistente = enviar("DELETE", "/api/materias/99999", null);

        // Assert
        assertEquals(400, nombreVacio.getStatus());
        assertEquals(404, profesorInexistente.getStatus());
        assertEquals(409, duplicada.getStatus());
        assertEquals(404, editarInexistente.getStatus());
        assertEquals(404, eliminarInexistente.getStatus());

    }

    @Test
    @DisplayName("Materias: eliminar una materia con tareas asociadas responde 409")
    void eliminarMateria_conTareas_409() throws Exception {

        // Arrange
        crearTarea("Taller", 3, 5);

        // Act
        MockHttpServletResponse respuesta = enviar("DELETE", "/api/materias/" + materiaId, null);

        // Assert
        assertEquals(409, respuesta.getStatus());

    }

    @Test
    @DisplayName("Materias: editar el nombre se refleja en las tareas activas")
    void editarMateria_actualizaTareas() throws Exception {

        // Arrange
        String id = crearTarea("Taller", 3, 5);
        int profesorId = jdbc.queryForObject("SELECT profesor_id FROM materias WHERE id = ?", Integer.class, materiaId);

        // Act
        enviar("PUT", "/api/materias/" + materiaId, cuerpoMateria("Estructuras II", profesorId));
        MockHttpServletResponse tarea = enviar("GET", "/api/tareas/" + id, null);

        // Assert
        assertEquals("Estructuras II", mapper.readTree(tarea.getContentAsString()).get("materia").asText());

    }

    // ---------- profesores ----------

    @Test
    @DisplayName("Profesores: crear y listar")
    void profesores_crearYListar() throws Exception {

        // Act
        MockHttpServletResponse creado = enviar("POST", "/api/profesores",
                "{\"nombre\":\"Alan Turing\",\"correo\":\"alan@universidad.edu\"}");
        MockHttpServletResponse lista = enviar("GET", "/api/profesores", null);

        // Assert
        assertEquals(201, creado.getStatus());
        assertEquals(200, lista.getStatus());
        assertTrue(lista.getContentAsString().contains("alan@universidad.edu"));

    }

    @Test
    @DisplayName("Profesores: correo inválido responde 400 y correo repetido responde 409")
    void profesores_errores() throws Exception {

        // Arrange
        enviar("POST", "/api/profesores", "{\"nombre\":\"Alan Turing\",\"correo\":\"alan@universidad.edu\"}");

        // Act
        MockHttpServletResponse correoInvalido = enviar("POST", "/api/profesores",
                "{\"nombre\":\"Ada Lovelace\",\"correo\":\"correo-invalido\"}");
        MockHttpServletResponse nombreVacio = enviar("POST", "/api/profesores",
                "{\"nombre\":\"\",\"correo\":\"ada@universidad.edu\"}");
        MockHttpServletResponse repetido = enviar("POST", "/api/profesores",
                "{\"nombre\":\"Otro Alan\",\"correo\":\"alan@universidad.edu\"}");

        // Assert
        assertEquals(400, correoInvalido.getStatus());
        assertEquals(400, nombreVacio.getStatus());
        assertEquals(409, repetido.getStatus());

    }

    // ---------- entradas hostiles (revisión adversarial de la Fase 6) ----------

    @Test
    @DisplayName("Nombres demasiado largos, fechas absurdas, NUL en el título y Content-Type erróneo no producen 500")
    void entradasHostiles_responden4xx_yNoGuardanNada() throws Exception {

        // Arrange
        String largo = "a".repeat(101);
        String correoLargo = "a".repeat(115) + "@u.edu";
        int profesorId = crearProfesor("hostil@test.com");

        // Act
        MockHttpServletResponse profesorLargo = enviar("POST", "/api/profesores",
                "{\"nombre\":\"" + largo + "\",\"correo\":\"largo@u.edu\"}");
        MockHttpServletResponse correoMuyLargo = enviar("POST", "/api/profesores",
                "{\"nombre\":\"Ada\",\"correo\":\"" + correoLargo + "\"}");
        MockHttpServletResponse materiaLarga = enviar("POST", "/api/materias", cuerpoMateria(largo, profesorId));
        MockHttpServletResponse fechaAbsurda = enviar("POST", "/api/tareas",
                "{\"titulo\":\"Taller\",\"materiaId\":" + materiaId + ",\"prioridad\":3,\"fechaLimite\":\"+999999999-01-01T00:00:00\"}");
        MockHttpServletResponse tituloConNul = enviar("POST", "/api/tareas",
                "{\"titulo\":\"Ta\\u0000ller\",\"materiaId\":" + materiaId + ",\"prioridad\":3,\"fechaLimite\":\"" + fecha(5) + "\"}");
        MockHttpServletResponse nombreConNul = enviar("POST", "/api/materias",
                "{\"nombre\":\"Al\\u0000go\",\"profesorId\":" + profesorId + "}");
        MockHttpServletResponse textoPlano = mockMvc.perform(MockMvcRequestBuilders.post("/api/tareas")
                .contentType(MediaType.TEXT_PLAIN).content(cuerpoTarea("Taller", materiaId, 3, 5))).andReturn().getResponse();

        // Assert
        assertEquals(400, profesorLargo.getStatus());
        assertEquals(400, correoMuyLargo.getStatus());
        assertEquals(400, materiaLarga.getStatus());
        assertEquals(400, fechaAbsurda.getStatus());
        assertEquals(400, tituloConNul.getStatus());
        assertEquals(400, nombreConNul.getStatus());
        assertEquals(415, textoPlano.getStatus());
        assertEquals(415, mapper.readTree(textoPlano.getContentAsString()).get("status").asInt());
        assertTrue(tareaService.listarActivas().isEmpty());

    }

    // ---------- rutas desconocidas ----------

    @Test
    @DisplayName("Una ruta inexistente responde 404 y un método no permitido responde 405")
    void rutasDesconocidas() throws Exception {

        // Act
        MockHttpServletResponse inexistente = enviar("GET", "/api/no-existe", null);
        MockHttpServletResponse noPermitido = enviar("DELETE", "/api/estadisticas", null);

        // Assert
        assertEquals(404, inexistente.getStatus());
        assertEquals(405, noPermitido.getStatus());

    }

    // ---------- apoyo ----------

    private MockHttpServletResponse enviar(String metodo, String ruta, String cuerpo) throws Exception {

        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), ruta);
        if (cuerpo != null) {
            peticion = peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(peticion).andReturn().getResponse();

    }

    private String crearTarea(String titulo, int prioridad, int dias) throws Exception {

        MockHttpServletResponse respuesta = enviar("POST", "/api/tareas", cuerpoTarea(titulo, materiaId, prioridad, dias));
        return mapper.readTree(respuesta.getContentAsString()).get("id").asText();

    }

    private int crearProfesor(String correo) {

        return jdbc.queryForObject("INSERT INTO profesores (nombre, correo) VALUES (?, ?) RETURNING id",
                Integer.class, "Profesor de prueba", correo);

    }

    private String cuerpoTarea(String titulo, int materia, int prioridad, int dias) {

        return "{\"titulo\":\"" + titulo + "\",\"materiaId\":" + materia + ",\"prioridad\":" + prioridad
                + ",\"fechaLimite\":\"" + fecha(dias) + "\"}";

    }

    private String cuerpoMateria(String nombre, int profesorId) {

        return "{\"nombre\":\"" + nombre + "\",\"profesorId\":" + profesorId + "}";

    }

    private String fecha(int dias) {

        return LocalDateTime.now().plusDays(dias).truncatedTo(ChronoUnit.SECONDS).toString();

    }

}
