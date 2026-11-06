package com.edfinal.structures;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaxHeapTest {

    record Item(int id, int p) implements Comparable<Item> {
        @Override
        public int compareTo(Item o) {
            return Integer.compare(p, o.p);
        }
    }

    private static Tarea tarea(String id, int prioridad, String fecha) {
        return new Tarea(id, "Materia", "T-" + id, prioridad, LocalDate.parse(fecha), EstadoTarea.PENDIENTE);
    }

    // ---------- Casos límite ----------

    @Test
    void heapVacio_peekYExtractDevuelvenVacio() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        assertThat(heap.isEmpty()).isTrue();
        assertThat(heap.size()).isZero();
        assertThat(heap.peekMax()).isEmpty();
        assertThat(heap.extractMax()).isEmpty();
    }

    @Test
    void unSoloElemento() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        heap.insert(7);
        assertThat(heap.peekMax()).contains(7);
        assertThat(heap.extractMax()).contains(7);
        assertThat(heap.isEmpty()).isTrue();
    }

    @Test
    void insertarNull_lanzaExcepcion() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        assertThatThrownBy(() -> heap.insert(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------- Orden y propiedad de montículo ----------

    @Test
    void insercionDesordenada_extraccionDescendente() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        int[] entrada = {5, 1, 9, 3, 7, 2, 8, 6, 4, 10};
        for (int n : entrada) {
            heap.insert(n);
            assertThat(heap.isValidHeap()).isTrue();
        }
        List<Integer> salida = new ArrayList<>();
        while (!heap.isEmpty()) {
            salida.add(heap.extractMax().orElseThrow());
            assertThat(heap.isValidHeap()).isTrue();
        }
        assertThat(salida).containsExactly(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    void prioridadesEmpatadas_seExtraenTodas() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        for (int i = 0; i < 5; i++) {
            heap.insert(3);
        }
        for (int i = 0; i < 5; i++) {
            assertThat(heap.extractMax()).contains(3);
        }
        assertThat(heap.isEmpty()).isTrue();
    }

    @Test
    void redimensionamiento_superaCapacidadInicial() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        for (int i = 0; i < 1000; i++) {
            heap.insert(i);
        }
        assertThat(heap.size()).isEqualTo(1000);
        assertThat(heap.isValidHeap()).isTrue();
        assertThat(heap.peekMax()).contains(999);
        for (int esperado = 999; esperado >= 0; esperado--) {
            assertThat(heap.extractMax()).contains(esperado);
        }
    }

    // ---------- Remoción por clave ----------

    @Test
    void remove_raiz_medio_ultimo_yClaveInexistente() {
        MaxHeap<Item> heap = new MaxHeap<>(Item::id);
        for (int i = 1; i <= 10; i++) {
            heap.insert(new Item(i, i * 10));
        }
        assertThat(heap.remove(10)).isPresent();   // raíz
        assertThat(heap.remove(5)).isPresent();    // arbitraria
        assertThat(heap.remove(1)).isPresent();    // hoja
        assertThat(heap.remove(999)).isEmpty();    // no existe
        assertThat(heap.size()).isEqualTo(7);
        assertThat(heap.isValidHeap()).isTrue();
        assertThat(heap.contains(5)).isFalse();
        assertThat(heap.peekMax().orElseThrow().id()).isEqualTo(9);
    }

    @Test
    void remove_aleatorio_conservaPropiedadYOrden() {
        Random rnd = new Random(42);
        MaxHeap<Item> heap = new MaxHeap<>(Item::id);
        for (int i = 0; i < 200; i++) {
            heap.insert(new Item(i, rnd.nextInt(50)));
        }
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 200; i++) ids.add(i);
        java.util.Collections.shuffle(ids, rnd);

        for (int k = 0; k < 80; k++) {
            assertThat(heap.remove(ids.get(k))).isPresent();
            assertThat(heap.isValidHeap()).isTrue();
        }
        int anterior = Integer.MAX_VALUE;
        while (!heap.isEmpty()) {
            int p = heap.extractMax().orElseThrow().p();
            assertThat(p).isLessThanOrEqualTo(anterior);
            anterior = p;
        }
    }

    @Test
    void insertarClaveDuplicada_lanzaExcepcion() {
        MaxHeap<Item> heap = new MaxHeap<>(Item::id);
        heap.insert(new Item(1, 5));
        assertThatThrownBy(() -> heap.insert(new Item(1, 9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeSinKeyExtractor_lanzaExcepcion() {
        MaxHeap<Integer> heap = new MaxHeap<>();
        heap.insert(1);
        assertThatThrownBy(() -> heap.remove(1)).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- Integración con Tarea (criterio de F02) ----------

    @Test
    void tareas_mayorPrioridadGana_yEnEmpateLaFechaMasCercana() {
        MaxHeap<Tarea> heap = new MaxHeap<>(Tarea::id);
        heap.insert(tarea("a", 3, "2026-12-01"));
        heap.insert(tarea("b", 5, "2026-12-20"));
        heap.insert(tarea("c", 5, "2026-12-10")); // empate con b, fecha más cercana

        assertThat(heap.extractMax().orElseThrow().id()).isEqualTo("c");
        assertThat(heap.extractMax().orElseThrow().id()).isEqualTo("b");
        assertThat(heap.extractMax().orElseThrow().id()).isEqualTo("a");
    }
}