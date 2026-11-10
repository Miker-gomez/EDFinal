package com.edfinal.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Max-Heap propio sobre ArrayList. Padre de i = (i-1)/2, hijos = 2i+1 y 2i+2
// El mapa id -> posicion permite remover por id en O(log n)
// insert, extractMax y remove son O(log n); peekMax es O(1)
// No es thread-safe: la sincronizacion vive en TareaService
public class MaxHeap<T extends Comparable<T> & IIdentificable> {

    // Elementos del montículo
    private List<T> data = new ArrayList<>();
    // Posicion de cada id dentro del montículo
    private Map<String, Integer> index = new HashMap<>();

    public void insert(T elem) {

        Objects.requireNonNull(elem, "elem no puede ser null");
        if ( index.containsKey(elem.getId()) ){
            throw new IllegalArgumentException("Clave duplicada: " + elem.getId());
        }

        // Se agrega al final y se sube hasta su lugar
        data.add(elem);
        index.put(elem.getId(), data.size() - 1);
        heapifyUp(data.size() - 1);

    }

    // Devuelve null si el montículo está vacío
    public T peekMax() {

        if ( isEmpty() ){
            return null;
        }
        return data.get(0);

    }

    // Devuelve null si el montículo está vacío
    public T extractMax() {

        if ( isEmpty() ){
            return null;
        }
        T max = data.get(0);
        removeAt(0);
        return max;

    }

    // Remueve por id. Devuelve null si el id no existe
    public T remove(String id) {

        Integer i = index.get(id);
        if ( i == null ){
            return null;
        }
        T removed = data.get(i);
        removeAt(i);
        return removed;

    }

    public boolean contains(String id) {
        return index.containsKey(id);
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    ////////////////////////////////////////////////////////////////

    private void removeAt(int i) {

        T removed = data.get(i);
        int last = data.size() - 1;
        index.remove(removed.getId());

        if ( i != last ){
            // El último ocupa el hueco y se acomoda hacia arriba o hacia abajo
            T moved = data.get(last);
            data.set(i, moved);
            index.put(moved.getId(), i);
            data.remove(last);
            heapifyDown(i);
            heapifyUp(i);
        } else {
            data.remove(last);
        }

    }

    private void heapifyUp(int i) {

        while ( i > 0 ){
            int parent = (i - 1) / 2;
            if ( data.get(i).compareTo(data.get(parent)) <= 0 ){
                break;
            }
            swap(i, parent);
            i = parent;
        }

    }

    private void heapifyDown(int i) {

        while ( true ){
            int left = 2 * i + 1;
            int right = left + 1;
            int largest = i;
            if ( left < data.size() && data.get(left).compareTo(data.get(largest)) > 0 ){
                largest = left;
            }
            if ( right < data.size() && data.get(right).compareTo(data.get(largest)) > 0 ){
                largest = right;
            }
            if ( largest == i ){
                break;
            }
            swap(i, largest);
            i = largest;
        }

    }

    private void swap(int i, int j) {

        T tmp = data.get(i);
        data.set(i, data.get(j));
        data.set(j, tmp);
        index.put(data.get(i).getId(), i);
        index.put(data.get(j).getId(), j);

    }

    // Solo para pruebas: verifica la propiedad de montículo y la coherencia del mapa
    boolean isValidHeap() {

        for ( int i = 1; i < data.size(); i++ ){
            if ( data.get(i).compareTo(data.get((i - 1) / 2)) > 0 ){
                return false;
            }
        }
        if ( index.size() != data.size() ){
            return false;
        }
        for ( int i = 0; i < data.size(); i++ ){
            if ( !Integer.valueOf(i).equals(index.get(data.get(i).getId())) ){
                return false;
            }
        }
        return true;

    }

}
