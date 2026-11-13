-- Datos de catálogo (los ids son parte del contrato: prioridad_id = nivel 1..5)

INSERT INTO estado (id, nombre) VALUES
    (1, 'PENDIENTE'),
    (2, 'COMPLETADA'),
    (3, 'VENCIDA'),
    (4, 'ELIMINADA');

INSERT INTO tipos_prioridad (id, nombre) VALUES
    (1, 'Muy baja'),
    (2, 'Baja'),
    (3, 'Media'),
    (4, 'Alta'),
    (5, 'Crítica');