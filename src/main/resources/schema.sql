CREATE TABLE IF NOT EXISTS tareas (
    id           TEXT    PRIMARY KEY,
    materia      TEXT    NOT NULL,
    titulo       TEXT    NOT NULL,
    prioridad    INTEGER NOT NULL CHECK (prioridad BETWEEN 1 AND 5),
    fecha_limite TEXT    NOT NULL,
    estado       TEXT    NOT NULL CHECK (estado IN ('PENDIENTE', 'COMPLETADA'))
);

CREATE INDEX IF NOT EXISTS idx_tareas_estado ON tareas(estado);