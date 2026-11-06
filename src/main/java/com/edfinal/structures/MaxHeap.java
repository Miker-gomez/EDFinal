package com.edfinal.structures;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Max-Heap sobre arreglo dinámico (implementación propia, sin dependencias de Spring).
 * Padre de i = (i-1)/2; hijos = 2i+1 y 2i+2.
 *
 * Con keyExtractor se mantiene un mapa clave -> índice, lo que permite
 * remove(key) en O(log n) en vez de O(n).
 *
 * Complejidad: insert O(log n), extractMax O(log n), peekMax O(1), remove(key) O(log n).
 * No es thread-safe: la sincronización vive en TareaService.
 */
public class MaxHeap<T extends Comparable<T>> {

    private static final int CAPACIDAD_INICIAL = 16;

    private Object[] data = new Object[CAPACIDAD_INICIAL];
    private int size = 0;

    private final Function<? super T, ?> keyExtractor; // null si no hay indexado
    private final Map<Object, Integer> index;          // null si no hay indexado

    public MaxHeap() {
        this.keyExtractor = null;
        this.index = null;
    }

    public MaxHeap(Function<? super T, ?> keyExtractor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor);
        this.index = new HashMap<>();
    }

    // ---------- API pública ----------

    public void insert(T elem) {
        Objects.requireNonNull(elem, "elem no puede ser null");
        if (index != null && index.containsKey(keyExtractor.apply(elem))) {
            throw new IllegalArgumentException("Clave duplicada: " + keyExtractor.apply(elem));
        }
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2); // redimensionamiento automático
        }
        data[size] = elem;
        track(elem, size);
        size++;
        heapifyUp(size - 1);
    }

    public Optional<T> peekMax() {
        return isEmpty() ? Optional.empty() : Optional.of(at(0));
    }

    public Optional<T> extractMax() {
        if (isEmpty()) {
            return Optional.empty();
        }
        T max = at(0);
        removeAt(0);
        return Optional.of(max);
    }

    /** Remueve por clave en O(log n). Vacío si la clave no existe. */
    public Optional<T> remove(Object key) {
        if (index == null) {
            throw new UnsupportedOperationException("MaxHeap creado sin keyExtractor");
        }
        Integer i = index.get(key);
        if (i == null) {
            return Optional.empty();
        }
        T removed = at(i);
        removeAt(i);
        return Optional.of(removed);
    }

    public boolean contains(Object key) {
        if (index == null) {
            throw new UnsupportedOperationException("MaxHeap creado sin keyExtractor");
        }
        return index.containsKey(key);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ---------- Núcleo ----------

    private void removeAt(int i) {
        T removed = at(i);
        int last = --size;
        untrack(removed);
        if (i != last) {
            T moved = at(last);
            data[i] = moved;
            track(moved, i);
            data[last] = null;
            heapifyDown(i);
            heapifyUp(i); // el elemento movido pudo ser mayor que el padre de i
        } else {
            data[last] = null;
        }
    }

    private void heapifyUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (at(i).compareTo(at(parent)) <= 0) {
                break;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void heapifyDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int largest = i;
            if (left < size && at(left).compareTo(at(largest)) > 0) {
                largest = left;
            }
            if (right < size && at(right).compareTo(at(largest)) > 0) {
                largest = right;
            }
            if (largest == i) {
                break;
            }
            swap(i, largest);
            i = largest;
        }
    }

    private void swap(int i, int j) {
        Object tmp = data[i];
        data[i] = data[j];
        data[j] = tmp;
        track(at(i), i);
        track(at(j), j);
    }

    @SuppressWarnings("unchecked")
    private T at(int i) {
        return (T) data[i];
    }

    private void track(T elem, int pos) {
        if (index != null) {
            index.put(keyExtractor.apply(elem), pos);
        }
    }

    private void untrack(T elem) {
        if (index != null) {
            index.remove(keyExtractor.apply(elem));
        }
    }

    /** Solo para pruebas: verifica la propiedad de montículo y la coherencia del índice. */
    boolean isValidHeap() {
        for (int i = 1; i < size; i++) {
            if (at(i).compareTo(at((i - 1) / 2)) > 0) {
                return false;
            }
        }
        if (index != null) {
            if (index.size() != size) {
                return false;
            }
            for (int i = 0; i < size; i++) {
                if (!Integer.valueOf(i).equals(index.get(keyExtractor.apply(at(i))))) {
                    return false;
                }
            }
        }
        return true;
    }
}