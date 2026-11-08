# Student Hub – Dashboard de Gestión Académica

Dashboard web para que estudiantes universitarios con alta carga académica registren sus entregas, talleres y exámenes y vean siempre cuál es la tarea más urgente. Proyecto de Estructuras de Datos y Algoritmos.

Integrantes: Juan Vélez, Miker Gómez y Edward Molina.

## Stack

- Java 21 (Gradle toolchain y `--release 21`) con Spring Boot 3.5.6 y Spring JDBC (sin JPA/Hibernate).
- PostgreSQL 16 con rutinas PL/pgSQL (procedimientos almacenados y triggers).
- Frontend estático: HTML5, JavaScript (Fetch API) y Bootstrap 5.3.8 como WebJar; sin Node.js.
- Docker y Docker Compose para la base de datos y la aplicación.
- Pruebas: JUnit 5, AssertJ y Testcontainers (PostgreSQL 16).

## Arquitectura

```text
cliente estático → controller → service → estructuras en memoria + repository → PostgreSQL
```

PostgreSQL es la fuente de verdad. Las tareas activas (PENDIENTE y VENCIDA) también viven en memoria en tres estructuras:

- **Max-Heap propio** (`MaxHeap`): tarea más urgente en O(1); insertar y remover en O(log n).
- **HashMap**: búsqueda por ID en O(1).
- **Pila (`ArrayDeque`)**: deshacer la última acción de completar o eliminar.

## Estructura del repositorio

```text
src/main/java/com/edfinal/
  config/       configuración de beans
  controller/   controladores REST
  domain/       entidades y objetos de dominio
  repository/   acceso a PostgreSQL con JdbcTemplate
  service/      reglas de negocio y sincronización memoria–BD
  structures/   MaxHeap (implementación propia)
src/main/resources/
  application.properties
  static/       HTML, JavaScript y estilos del cliente
src/test/       pruebas unitarias e de integración
db/init/        01_schema.sql, 02_seed.sql, 03_routines.sql
Dockerfile
docker-compose.yml
.env.example
```

## Cómo ejecutar

1. Crea el archivo de entorno y ajusta la contraseña:

   ```powershell
   Copy-Item .env.example .env
   ```

   `.env` no se versiona. Guárdalo en UTF-8 sin BOM y sin comandos dentro.

2. Levanta todo con Docker Compose:

   ```powershell
   docker compose up --build
   ```

   El dashboard queda en <http://localhost:8080>. La primera construcción tarda varios minutos (imágenes base y dependencias de Gradle); las siguientes usan caché. Tras `up`, espera unos segundos antes de abrirlo.

Los scripts de `db/init/` solo se ejecutan cuando el volumen está vacío. Para reaplicarlos tras cambiar el esquema:

```powershell
docker compose down -v
```

Un `docker compose down` sin `-v` conserva los datos.

### Desarrollo desde el IDE

```powershell
docker compose up -d --wait db
Get-Content .env | Where-Object { $_ -match '^\s*[^#\s]\S*=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2
  Set-Item "env:$($k.Trim())" $v.Trim()
}
.\gradlew.bat bootRun
```

Las variables viven solo en esa sesión de PowerShell.

##