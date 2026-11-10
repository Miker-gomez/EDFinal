// Estado local: solo las listas de catálogos, el resto se pide a la API cada vez
let materias = [];
let profesores = [];

// Crea un elemento con texto plano (textContent evita inyectar HTML)
function crear(etiqueta, texto, clases) {

  const elemento = document.createElement(etiqueta);
  if (texto !== undefined && texto !== null) {
    elemento.textContent = texto;
  }
  if (clases) {
    elemento.className = clases;
  }
  return elemento;

}

// Muestra un aviso de Bootstrap en la zona de alertas, reemplazando el anterior
function mostrarAlerta(mensaje, tipo) {

  const alerta = crear('div', mensaje, 'alert alert-' + tipo + ' alert-dismissible');
  const cerrar = crear('button', null, 'btn-close');
  cerrar.type = 'button';
  cerrar.setAttribute('data-bs-dismiss', 'alert');
  cerrar.setAttribute('aria-label', 'Cerrar');
  alerta.appendChild(cerrar);

  const zona = document.getElementById('alertas');
  zona.innerHTML = '';
  zona.appendChild(alerta);

}

// Muestra en rojo el mensaje de error que devolvió la API
function mostrarError(error) {

  mostrarAlerta(error.message, 'danger');

}

// "2026-11-20T23:59:00" -> "2026-11-20 23:59"
function formatearFecha(fecha) {

  return fecha.replace('T', ' ').slice(0, 16);

}

// El input datetime-local entrega "yyyy-MM-ddTHH:mm" y la API espera segundos
function aFechaApi(valor) {

  if (!valor) {
    return null;
  }
  return valor.length === 16 ? valor + ':00' : valor;

}

// Número del input, o null si está vacío (0 sigue siendo 0 para que la API lo rechace con su mensaje)
function aNumero(valor) {

  return valor === '' ? null : Number(valor);

}

// Llena un select con las opciones y conserva el valor anterior si todavía existe
function llenarSelect(select, opciones, valorActual) {

  select.innerHTML = '';
  opciones.forEach(function (opcion) {
    const item = crear('option', opcion.texto);
    item.value = opcion.valor;
    select.appendChild(item);
  });
  // Solo se restaura el valor anterior si todavía existe entre las opciones
  if (valorActual !== undefined && valorActual !== null && valorActual !== '') {
    const existe = opciones.some(function (opcion) { return String(opcion.valor) === String(valorActual); });
    if (existe) {
      select.value = String(valorActual);
    }
  }

}

// Número de la última recarga pedida: una respuesta vieja no debe pintarse sobre una nueva
let ultimaRecarga = 0;

// Recarga todo lo visible; se llama después de cada acción
async function refrescar() {

  const numero = ++ultimaRecarga;

  try {
    const resultados = await Promise.all([
      pedir('GET', '/api/tareas/critica'),
      pedir('GET', '/api/tareas'),
      pedir('GET', '/api/estadisticas'),
      pedir('GET', '/api/materias'),
      pedir('GET', '/api/profesores')
    ]);
    if (numero !== ultimaRecarga) {
      return;
    }
    materias = resultados[3];
    profesores = resultados[4];
    pintarCritica(resultados[0]);
    pintarTareas(resultados[1]);
    pintarEstadisticas(resultados[2]);
    pintarMaterias();
    pintarProfesores();
  } catch (error) {
    mostrarError(error);
  }

}

// Dibuja el widget de la próxima entrega crítica (null cuando la API respondió 204)
function pintarCritica(tarea) {

  const contenido = document.getElementById('criticaContenido');
  contenido.innerHTML = '';

  if (tarea === null) {
    contenido.textContent = 'Sin tareas activas';
    return;
  }

  contenido.appendChild(crear('div', tarea.titulo, 'titulo-critica'));
  contenido.appendChild(crear('div', tarea.materia + ' · Prioridad ' + tarea.prioridad + ' · ' + formatearFecha(tarea.fechaLimite)));
  contenido.appendChild(crear('div', tarea.id, 'text-muted small'));

}

// Dibuja la tabla de tareas activas, de mayor a menor urgencia
function pintarTareas(tareas) {

  const cuerpo = document.getElementById('listaTareas');
  cuerpo.innerHTML = '';

  if (tareas.length === 0) {
    const fila = crear('tr');
    const celda = crear('td', 'No hay tareas activas', 'text-muted');
    celda.colSpan = 6;
    fila.appendChild(celda);
    cuerpo.appendChild(fila);
    return;
  }

  tareas.forEach(function (tarea) {
    cuerpo.appendChild(crearFilaTarea(tarea));
  });

}

// Arma la fila de una tarea con su distintivo de estado y sus botones
function crearFilaTarea(tarea) {

  const fila = crear('tr', null, tarea.estado === 'VENCIDA' ? 'fila-vencida' : '');
  fila.appendChild(crear('td', tarea.titulo));
  fila.appendChild(crear('td', tarea.materia));
  fila.appendChild(crear('td', tarea.prioridad));
  fila.appendChild(crear('td', formatearFecha(tarea.fechaLimite)));

  const estado = crear('td');
  estado.appendChild(crear('span', tarea.estado, tarea.estado === 'VENCIDA' ? 'badge text-bg-danger' : 'badge text-bg-secondary'));
  fila.appendChild(estado);

  const acciones = crear('td', null, 'acciones');
  acciones.appendChild(crearBoton('Completar', 'btn-success', function () { completar(tarea.id); }));
  acciones.appendChild(crearBoton('Editar', 'btn-outline-primary', function () { abrirEdicion(tarea); }));
  acciones.appendChild(crearBoton('Eliminar', 'btn-outline-danger', function () { eliminar(tarea.id); }));
  fila.appendChild(acciones);

  return fila;

}

// Crea un botón pequeño que ejecuta la acción al hacer clic
function crearBoton(texto, clase, accion) {

  const boton = crear('button', texto, 'btn btn-sm ' + clase + ' me-1');
  boton.type = 'button';
  boton.addEventListener('click', accion);
  return boton;

}

// Dibuja la tabla de estadísticas de entrega por materia
function pintarEstadisticas(estadisticas) {

  const cuerpo = document.getElementById('listaEstadisticas');
  cuerpo.innerHTML = '';

  if (estadisticas.length === 0) {
    const fila = crear('tr');
    const celda = crear('td', 'Sin datos', 'text-muted');
    celda.colSpan = 5;
    fila.appendChild(celda);
    cuerpo.appendChild(fila);
    return;
  }

  estadisticas.forEach(function (estadistica) {
    const fila = crear('tr');
    fila.appendChild(crear('td', estadistica.materia));
    fila.appendChild(crear('td', estadistica.total));
    fila.appendChild(crear('td', estadistica.pendientes));
    fila.appendChild(crear('td', estadistica.completadas));
    fila.appendChild(crear('td', estadistica.vencidas));
    cuerpo.appendChild(fila);
  });

}

// Llena el selector de materias del registro y la lista de materias con sus botones
function pintarMaterias() {

  const opciones = materias.map(function (materia) {
    return { valor: materia.id, texto: materia.nombre };
  });
  const selectRegistro = document.getElementById('tareaMateria');
  const anterior = selectRegistro.value;
  llenarSelect(selectRegistro, opciones, anterior);

  const lista = document.getElementById('listaMaterias');
  lista.innerHTML = '';
  materias.forEach(function (materia) {
    const item = crear('li', null, 'list-group-item d-flex justify-content-between align-items-center');
    item.appendChild(crear('span', materia.nombre + ' (' + materia.profesor + ')'));
    const acciones = crear('span', null, 'acciones');
    acciones.appendChild(crearBoton('Editar', 'btn-outline-primary', function () { editarMateria(materia); }));
    acciones.appendChild(crearBoton('Eliminar', 'btn-outline-danger', function () { eliminarMateria(materia.id); }));
    item.appendChild(acciones);
    lista.appendChild(item);
  });

}

// Llena el selector de profesores de la materia y la lista de profesores
function pintarProfesores() {

  const selectMateria = document.getElementById('materiaProfesor');
  const anterior = selectMateria.value;
  llenarSelect(selectMateria, profesores.map(function (profesor) {
    return { valor: profesor.id, texto: profesor.nombre };
  }), anterior);

  const lista = document.getElementById('listaProfesores');
  lista.innerHTML = '';
  profesores.forEach(function (profesor) {
    lista.appendChild(crear('li', profesor.nombre + ' · ' + profesor.correo, 'list-group-item'));
  });

}

// ---------- tareas ----------

// F01: envía el formulario de registro de tarea a la API
async function registrarTarea(evento) {

  evento.preventDefault();
  try {
    await pedir('POST', '/api/tareas', {
      titulo: document.getElementById('tareaTitulo').value,
      materiaId: aNumero(document.getElementById('tareaMateria').value),
      prioridad: aNumero(document.getElementById('tareaPrioridad').value),
      fechaLimite: aFechaApi(document.getElementById('tareaFecha').value)
    });
    document.getElementById('formTarea').reset();
    mostrarAlerta('Tarea registrada', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// F04: completa la tarea y refresca la vista, incluso si la API respondió con error
async function completar(id) {

  try {
    await pedir('PUT', '/api/tareas/' + id + '/completar');
    mostrarAlerta('Tarea completada', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
    await refrescar();
  }

}

// F04: elimina la tarea y refresca la vista, incluso si la API respondió con error
async function eliminar(id) {

  try {
    await pedir('DELETE', '/api/tareas/' + id);
    mostrarAlerta('Tarea eliminada', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
    await refrescar();
  }

}

// F05: revierte la última acción y refresca la vista, incluso si la API respondió con error
async function deshacer() {

  try {
    const restaurada = await pedir('POST', '/api/tareas/deshacer');
    mostrarAlerta('Se restauró la tarea «' + restaurada.titulo + '»', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
    await refrescar();
  }

}

// F07: pide a la API marcar las tareas vencidas y muestra cuántas fueron
async function marcarVencidas() {

  try {
    const respuesta = await pedir('POST', '/api/tareas/vencidas');
    mostrarAlerta('Tareas marcadas como vencidas: ' + respuesta.marcadas, 'info');
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// F03: busca una tarea activa por su ID
async function buscar(evento) {

  evento.preventDefault();
  const id = document.getElementById('buscarId').value.trim();
  const resultado = document.getElementById('resultadoBusqueda');
  resultado.innerHTML = '';

  if (id === '') {
    mostrarAlerta('Escribe un ID para buscar', 'warning');
    return;
  }

  // Un ID con caracteres que no son de un UUID no puede existir (y Tomcat rechaza las barras)
  if (!/^[0-9a-fA-F-]+$/.test(id)) {
    mostrarAlerta('Tarea no encontrada', 'danger');
    return;
  }

  try {
    const tarea = await pedir('GET', '/api/tareas/' + encodeURIComponent(id));
    const detalle = crear('div', null, 'alert alert-primary mb-0');
    detalle.appendChild(crear('strong', tarea.titulo));
    detalle.appendChild(crear('div', tarea.materia + ' · Prioridad ' + tarea.prioridad + ' · ' + formatearFecha(tarea.fechaLimite) + ' · ' + tarea.estado));
    resultado.appendChild(detalle);
  } catch (error) {
    mostrarError(error);
  }

}

// ---------- edición de tarea ----------

// F09: precarga el modal con los datos de la tarea y lo abre
function abrirEdicion(tarea) {

  document.getElementById('editarId').value = tarea.id;
  document.getElementById('editarTitulo').value = tarea.titulo;
  llenarSelect(document.getElementById('editarMateria'), materias.map(function (materia) {
    return { valor: materia.id, texto: materia.nombre };
  }), tarea.materiaId);
  document.getElementById('editarPrioridad').value = tarea.prioridad;
  document.getElementById('editarFecha').value = tarea.fechaLimite.slice(0, 16);
  document.getElementById('editarFechaOriginal').value = tarea.fechaLimite;
  bootstrap.Modal.getOrCreateInstance(document.getElementById('modalEditar')).show();

}

// Si el usuario no cambió la fecha se envía la original completa (con segundos), para que el servidor no la vea como un cambio
function fechaParaEditar() {

  const valor = document.getElementById('editarFecha').value;
  const original = document.getElementById('editarFechaOriginal').value;
  if (original !== '' && valor === original.slice(0, 16)) {
    return original;
  }
  return aFechaApi(valor);

}

// F09: envía los cambios de la tarea y cierra el modal
async function guardarEdicion(evento) {

  evento.preventDefault();
  const id = document.getElementById('editarId').value;
  try {
    await pedir('PUT', '/api/tareas/' + id, {
      titulo: document.getElementById('editarTitulo').value,
      materiaId: aNumero(document.getElementById('editarMateria').value),
      prioridad: aNumero(document.getElementById('editarPrioridad').value),
      fechaLimite: fechaParaEditar()
    });
    bootstrap.Modal.getOrCreateInstance(document.getElementById('modalEditar')).hide();
    mostrarAlerta('Tarea actualizada', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// ---------- materias y profesores ----------

// F06: pasa el formulario de materias a modo edición con los datos de la materia
function editarMateria(materia) {

  document.getElementById('materiaId').value = materia.id;
  document.getElementById('materiaNombre').value = materia.nombre;
  document.getElementById('materiaProfesor').value = String(materia.profesorId);
  document.getElementById('btnGuardarMateria').textContent = 'Guardar';
  document.getElementById('btnCancelarMateria').classList.remove('d-none');

}

// F06: devuelve el formulario de materias a modo creación
function cancelarMateria() {

  document.getElementById('formMateria').reset();
  document.getElementById('materiaId').value = '';
  document.getElementById('btnGuardarMateria').textContent = 'Crear';
  document.getElementById('btnCancelarMateria').classList.add('d-none');

}

// F06: crea o edita la materia según el formulario esté en modo creación o edición
async function guardarMateria(evento) {

  evento.preventDefault();
  const id = document.getElementById('materiaId').value;
  const cuerpo = {
    nombre: document.getElementById('materiaNombre').value,
    profesorId: aNumero(document.getElementById('materiaProfesor').value)
  };
  try {
    if (id === '') {
      await pedir('POST', '/api/materias', cuerpo);
      mostrarAlerta('Materia creada', 'success');
    } else {
      await pedir('PUT', '/api/materias/' + id, cuerpo);
      mostrarAlerta('Materia actualizada', 'success');
    }
    cancelarMateria();
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// F06: elimina la materia (la API responde 409 si tiene tareas)
async function eliminarMateria(id) {

  try {
    await pedir('DELETE', '/api/materias/' + id);
    mostrarAlerta('Materia eliminada', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// F06: registra un profesor nuevo
async function registrarProfesor(evento) {

  evento.preventDefault();
  try {
    await pedir('POST', '/api/profesores', {
      nombre: document.getElementById('profesorNombre').value,
      correo: document.getElementById('profesorCorreo').value
    });
    document.getElementById('formProfesor').reset();
    mostrarAlerta('Profesor registrado', 'success');
    await refrescar();
  } catch (error) {
    mostrarError(error);
  }

}

// ---------- arranque ----------

document.getElementById('formTarea').addEventListener('submit', registrarTarea);
document.getElementById('formEditar').addEventListener('submit', guardarEdicion);
document.getElementById('formBuscar').addEventListener('submit', buscar);
document.getElementById('formMateria').addEventListener('submit', guardarMateria);
document.getElementById('formProfesor').addEventListener('submit', registrarProfesor);
document.getElementById('btnCancelarMateria').addEventListener('click', cancelarMateria);
document.getElementById('btnDeshacer').addEventListener('click', deshacer);
document.getElementById('btnVencidas').addEventListener('click', marcarVencidas);

refrescar();
