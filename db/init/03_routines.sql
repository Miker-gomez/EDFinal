-- Procedimientos almacenados y triggers (PL/pgSQL)

-- SP 1: marca como VENCIDA toda tarea PENDIENTE con plazo expirado.
-- Devuelve la cantidad marcada en el parámetro INOUT.
-- Uso: CALL sp_marcar_vencidas(0);
CREATE OR REPLACE PROCEDURE sp_marcar_vencidas(INOUT p_marcadas INTEGER)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE tareas
       SET estado_id = (SELECT id FROM estado WHERE nombre = 'VENCIDA')
     WHERE estado_id = (SELECT id FROM estado WHERE nombre = 'PENDIENTE')
       AND fecha_limite < LOCALTIMESTAMP;

    GET DIAGNOSTICS p_marcadas = ROW_COUNT;
END;
$$;

-- SP 2: consolida por materia el total y el conteo por estado.
-- Incluye materias sin tareas (con ceros).
CREATE OR REPLACE FUNCTION sp_estadisticas_entrega()
RETURNS TABLE (
    materia_id  INTEGER,
    materia     VARCHAR,
    total       BIGINT,
    pendientes  BIGINT,
    completadas BIGINT,
    vencidas    BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT m.id,
           m.nombre,
           COUNT(t.id),
           COUNT(t.id) FILTER (WHERE e.nombre = 'PENDIENTE'),
           COUNT(t.id) FILTER (WHERE e.nombre = 'COMPLETADA'),
           COUNT(t.id) FILTER (WHERE e.nombre = 'VENCIDA')
      FROM materias m
      LEFT JOIN tareas t ON t.materia_id = m.id
      LEFT JOIN estado e ON e.id = t.estado_id
     GROUP BY m.id, m.nombre
     ORDER BY m.nombre, m.id;
END;
$$;

-- Trigger 1: bloquea INSERT de tareas con fecha límite pasada (SQLSTATE 23514).
CREATE OR REPLACE FUNCTION fn_tareas_validar_plazo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.fecha_limite < LOCALTIMESTAMP THEN
        RAISE EXCEPTION 'La fecha limite (%) no puede ser anterior al momento actual', NEW.fecha_limite
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tareas_validar_plazo
    BEFORE INSERT ON tareas
    FOR EACH ROW
    EXECUTE FUNCTION fn_tareas_validar_plazo();

-- Trigger 2: registra cada cambio de estado en auditoria_tareas.
CREATE OR REPLACE FUNCTION fn_tareas_auditar_estado()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO auditoria_tareas (tarea_id, estado_anterior_id, estado_nuevo_id)
    VALUES (NEW.id, OLD.estado_id, NEW.estado_id);
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_tareas_auditar_estado
    AFTER UPDATE OF estado_id ON tareas
    FOR EACH ROW
    WHEN (OLD.estado_id IS DISTINCT FROM NEW.estado_id)
    EXECUTE FUNCTION fn_tareas_auditar_estado();