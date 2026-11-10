package com.edfinal.structures;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxHeapTest {

    // Elemento de prueba: la prioridad manda y el id ubica el elemento
    record Item(int id, int p) implements Comparable<Item>, IIdentificable {

        @Override
        public int compareTo(Item o) {

            return Integer.compare(p, o.p);

        }

        @Override
        public String getId() {

            return String.valueOf(id);

        }

    }

    private static Tarea tarea(String id, int prioridad, String fecha) {

        return new Tarea(id, "T-" + id, 1, "Materia", prioridad, LocalDateTime.parse(fecha), EstadoTarea.PENDIENTE);

    }

    @Test
    @DisplayName("heap vacío: peekMax y extractMax devuelven null")
    void heapVacio_peekYExtractDevuelvenNull() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();

        // Assert
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        assertEquals(null, heap.peekMax());
        assertEquals(null, heap.extractMax());

    }

    @Test
    @DisplayName("un solo elemento")
    void unSoloElemento() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        Item unico = new Item(1, 7);

        // Act
        heap.insert(unico);

        // Assert
        assertEquals(unico, heap.peekMax());
        assertEquals(unico, heap.extractMax());
        assertTrue(heap.isEmpty());

    }

    @Test
    @DisplayName("insertar null lanza NullPointerException")
    void insertarNull_lanzaExcepcion() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();

        // Act
        Throwable ex = capturar(() -> heap.insert(null));

        // Assert
        assertTrue(ex instanceof NullPointerException);

    }

    @Test
    @DisplayName("inserción desordenada, extracción descendente")
    void insercionDesordenada_extraccionDescendente() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        int[] entrada = {5, 1, 9, 3, 7, 2, 8, 6, 4, 10};

        // Act
        for (int n : entrada) {

            heap.insert(new Item(n, n));
            assertTrue(heap.isValidHeap());

        }
        List<Integer> salida = new ArrayList<>();
        while (!heap.isEmpty()) {

            salida.add(heap.extractMax().p());
            assertTrue(heap.isValidHeap());

        }

        // Assert
        assertEquals(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), salida);

    }

    @Test
    @DisplayName("prioridades empatadas: se extraen todas")
    void prioridadesEmpatadas_seExtraenTodas() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        for (int i = 0; i < 5; i++) {

            heap.insert(new Item(i, 3));

        }

        // Act y Assert
        for (int i = 0; i < 5; i++) {

            assertEquals(3, heap.extractMax().p());

        }
        assertTrue(heap.isEmpty());

    }

    @Test
    @DisplayName("crece más allá de 1000 elementos y conserva el orden")
    void crecimiento_variosElementos() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();

        // Act
        for (int i = 0; i < 1000; i++) {

            heap.insert(new Item(i, i));

        }

        // Assert
        assertEquals(1000, heap.size());
        assertTrue(heap.isValidHeap());
        assertEquals(999, heap.peekMax().p());
        for (int esperado = 999; esperado >= 0; esperado--) {

            assertEquals(esperado, heap.extractMax().p());

        }

    }

    @Test
    @DisplayName("remove: raíz, medio, hoja y id inexistente")
    void remove_raiz_medio_ultimo_yClaveInexistente() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        for (int i = 1; i <= 10; i++) {

            heap.insert(new Item(i, i * 10));

        }

        // Act y Assert
        assertEquals(10, heap.remove("10").id());
        assertEquals(5, heap.remove("5").id());
        assertEquals(1, heap.remove("1").id());
        assertEquals(null, heap.remove("999"));
        assertEquals(7, heap.size());
        assertTrue(heap.isValidHeap());
        assertFalse(heap.contains("5"));
        assertEquals(9, heap.peekMax().id());

    }

    @Test
    @DisplayName("remove de varios ids conserva la propiedad y el orden")
    void remove_variosIds_conservaPropiedadYOrden() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        for (int i = 0; i < 200; i++) {

            heap.insert(new Item(i, (i * 37) % 50));

        }

        // Act
        for (int k = 0; k < 200; k += 3) {

            assertEquals(k, heap.remove(String.valueOf(k)).id());
            assertTrue(heap.isValidHeap());

        }

        // Assert
        int anterior = Integer.MAX_VALUE;
        while (!heap.isEmpty()) {

            int p = heap.extractMax().p();
            assertTrue(p <= anterior);
            anterior = p;

        }

    }

    @Test
    @DisplayName("insertar un id duplicado lanza IllegalArgumentException")
    void insertarClaveDuplicada_lanzaExcepcion() {

        // Arrange
        MaxHeap<Item> heap = new MaxHeap<>();
        heap.insert(new Item(1, 5));

        // Act
        Throwable ex = capturar(() -> heap.insert(new Item(1, 9)));

        // Assert
        assertTrue(ex instanceof IllegalArgumentException);

    }

    @Test
    @DisplayName("tareas: gana la mayor prioridad y, en empate, la fecha más cercana")
    void tareas_mayorPrioridadGana_yEnEmpateLaFechaMasCercana() {

        // Arrange
        MaxHeap<Tarea> heap = new MaxHeap<>();
        heap.insert(tarea("a", 3, "2026-12-01T09:00"));
        heap.insert(tarea("b", 5, "2026-12-20T09:00"));
        heap.insert(tarea("c", 5, "2026-12-10T09:00"));

        // Act y Assert
        assertEquals("c", heap.extractMax().id());
        assertEquals("b", heap.extractMax().id());
        assertEquals("a", heap.extractMax().id());

    }

}
