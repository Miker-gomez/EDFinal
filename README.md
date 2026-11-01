# EDFinal

Base arquitectónica preparada para Java 21, Spring Boot, SQLite y una interfaz
estática HTML/JavaScript con Bootstrap. Este repositorio contiene únicamente el
esqueleto y la configuración: todavía no implementa funcionalidades, endpoints,
esquema de base de datos ni interfaz.

## Compatibilidad fijada

- Java 21 mediante Gradle toolchain y `--release 21`.
- Spring Boot 3.5.6 y dependencias gestionadas por su BOM.
- Spring MVC para futuros controladores REST, Bean Validation para validar
  entradas y Spring JDBC para la capa de repositorios.
- SQLite JDBC 3.50.3.0; la URL local configurada apunta a `data/edfinal.db`.
- Bootstrap 5.3.8 como WebJar, disponible en el classpath sin Node.js ni CDN.
- Cliente estático en `src/main/resources/static`; no se requiere Node.js.

Se requiere tener disponible un JDK 21 para compilar con Gradle. El wrapper
permite usar la versión de Gradle del proyecto sin instalar Gradle globalmente.

## Estructura

```text
src/main/java/com/edfinal/
  config/       configuración transversal
  controller/   controladores REST y capa HTTP
  domain/       modelos y reglas de negocio
  repository/   persistencia SQLite mediante JDBC
  service/      casos de uso y lógica de negocio
  EdFinalApplication.java
src/main/resources/
  application.properties
  static/       HTML, JavaScript y estilos del cliente
data/           ubicación local del archivo SQLite (no versionado)
```

La dirección prevista del flujo es `cliente estático → controller → service →
repository → SQLite`. Las estructuras de datos en memoria y las reglas de
negocio se incorporarán en `domain`/`service`; esta base no presupone su diseño.

## Verificación de configuración

```powershell
.\gradlew.bat --version
.\gradlew.bat compileJava
```

La configuración solicita Java 21 explícitamente: un JDK distinto no debe
considerarse evidencia de compatibilidad con Java 21.
