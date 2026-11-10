// Envoltorio de fetch para toda la API: devuelve el JSON, null si la respuesta es 204 y lanza Error con el mensaje del servidor si falla
async function pedir(metodo, ruta, cuerpo) {

  const opciones = { method: metodo, headers: {} };

  if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json';
    opciones.body = JSON.stringify(cuerpo);
  }

  const respuesta = await fetch(ruta, opciones);

  if (respuesta.status === 204) {
    return null;
  }

  // Los errores del servidor llegan como { status, mensaje }
  let datos = null;
  try {
    datos = await respuesta.json();
  } catch (error) {
    datos = null;
  }

  if (!respuesta.ok) {
    const mensaje = datos && datos.mensaje ? datos.mensaje : 'Error inesperado (' + respuesta.status + ')';
    throw new Error(mensaje);
  }

  return datos;

}
