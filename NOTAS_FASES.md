# Bitácora de Desarrollo por Fases – Student Hub

**Equipo de desarrollo:** Juan Vélez, Miker Gómez, Edward Molina.  
**Rama actual:** `feature/fase-3-servicio-memoria`  
**Objetivo de la rama:** Pull Request hacia `main` tras completar la Capa de Servicios y Sincronización en Memoria.

---

## Resumen del Pull Request: Fase 3 – Capa de Servicio y Sincronización en Memoria

### 1. ¿Qué se agregó en esta fase?

1. **`AccionDeshacer` (`com.edfinal.service`):**
   - Record inmutable que almacena el tipo de acción (`COMPLETAR`, `MODIFICAR`) y la instantánea previa (`Tarea tareaPrevia`) antes de la mutación.
   - Es el elemento que alimenta la pila `ArrayDeque` para permitir reversiones atómicas.

2. **`TareaService` (`com.edfinal.service`):**
   - Orquestador del núcleo del proyecto. Conecta PostgreSQL con las 3 estructuras de datos en memoria:
     - **`MaxHeap<Tarea>` (propio):** Consulta de la tarea más urgente en $O(1)$ (`peekMax()`) y remoción/inserción en $O(\log n)$.
     - **`Map<String, Tarea>` (`HashMap`):** Búsqueda instantánea de tareas activas por UUID en $O(1)$.
     - **`Deque<AccionDeshacer>` (`ArrayDeque`):** Pila de deshacer con comportamiento LIFO en $O(1)$.
   - **Inicialización:** Carga automática al arrancar (`@PostConstruct`) de tareas activas (`PENDIENTE` y `VENCIDA`) desde PostgreSQL.
   - **Concurrencia:** Protección de todas las mutaciones y consultas en memoria mediante `ReentrantReadWriteLock`.
   - **Reglas de negocio integradas:**
     - Al completar una tarea: se actualiza en BD a `COMPLETADA`, se remueve del heap y del hashmap, y se apila en `ArrayDeque`.
     - Al deshacer: se desapila la acción, se restaura en PostgreSQL al estado previo y se reinserta en heap y hashmap.
     - Al modificar plazo (F09): si una tarea estaba `VENCIDA` y se le asigna fecha futura, pasa automáticamente a `PENDIENTE`.
     - Sincronización de vencidas: invoca el procedimiento `sp_marcar_vencidas` y actualiza las instancias en memoria.

3. **`ProfesorService` y `MateriaService` (`com.edfinal.service`):**
   - Lógica de negocio y validaciones de formato/integridad para los catálogos de profesores y materias.

4. **Pruebas Unitarias:**
   - `TareaServiceTest`: 10 pruebas unitarias con Mockito y AssertJ cubriendo inicialización, urgencia, validación de fechas pasadas, completar, deshacer, modificación con cambio de estado y sincronización de vencidas.
   - `CatalogoServicesTest`: Pruebas de inserción y validación de relaciones profesor-materia.

---

## 2. Historial de Commits en la Rama

```text
a835265 feat(service): TareaService con sincronizacion de MaxHeap, HashMap y Pila de deshacer
c497717 feat(service): servicios de catalogo ProfesorService y MateriaService con pruebas
5f5d5c6 feat(service): modelo AccionDeshacer para pila LIFO de reversion
```

---

## 3. Próximos Pasos (Fase 4 – API REST)

Una vez fusionado este Pull Request en `main`, el siguiente bloque de trabajo comprenderá:
- `TareaController`, `MateriaController`, `ProfesorController`.
- DTOs (`CrearTareaRequest`, `ActualizarTareaRequest`) con Bean Validation (`@NotBlank`, `@Min`, `@FutureOrPresent`).
- `GlobalExceptionHandler` con `@RestControllerAdvice` para capturar violaciones de integridad y excepciones de negocio con códigos HTTP semánticos (400, 404, 409).
