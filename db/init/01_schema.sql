-- Student Hub: esquema relacional (PostgreSQL 16)

CREATE TABLE estado (
    id     SMALLINT    PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE tipos_prioridad (
    id     SMALLINT    PRIMARY KEY CHECK (id BETWEEN 1 AND 5),
    nombre VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE profesores (
    id     INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(120) NOT NULL UNIQUE CHECK (correo LIKE '%@%')
);

CREATE TABLE materias (
    id          INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    profesor_id INTEGER      NOT NULL REFERENCES profesores(id) ON DELETE RESTRICT
);

-- El nombre es único por profesor sin distinguir mayúsculas ("Cálculo" y "cálculo" son la misma materia)
CREATE UNIQUE INDEX uq_materias_nombre_profesor ON materias (lower(nombre), profesor_id);

CREATE TABLE tareas (
    id           UUID         PRIMARY KEY,
    titulo       VARCHAR(150) NOT NULL CHECK (btrim(titulo) <> ''),
    materia_id   INTEGER      NOT NULL REFERENCES materias(id) ON DELETE RESTRICT,
    estado_id    SMALLINT     NOT NULL DEFAULT 1 REFERENCES estado(id),
    prioridad_id SMALLINT     NOT NULL REFERENCES tipos_prioridad(id),
    fecha_limite TIMESTAMP    NOT NULL
);

CREATE TABLE auditoria_tareas (
    id                 BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tarea_id           UUID      NOT NULL REFERENCES tareas(id),
    estado_anterior_id SMALLINT  NOT NULL REFERENCES estado(id),
    estado_nuevo_id    SMALLINT  NOT NULL REFERENCES estado(id),
    fecha_cambio       TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

CREATE INDEX idx_tareas_fecha_limite ON tareas(fecha_limite);
CREATE INDEX idx_tareas_estado_id    ON tareas(estado_id);