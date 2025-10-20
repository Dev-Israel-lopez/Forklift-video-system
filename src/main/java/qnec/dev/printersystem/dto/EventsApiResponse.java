package qnec.dev.printersystem.dto;

import java.util.List;
import java.util.Map;

/** DTO para /api/events con meta.stats */
public class EventsApiResponse {

    public List<EventData> data;
    public Meta meta;

    // --- meta
    public static class Meta {
        public Stats stats;
        // puedes agregar paginación u otros campos si tu API los envía
        public Map<String, Object> extra; // opcional, por si mandas más cosas
    }

    // --- stats
    public static class Stats {
        public Integer critical; // rojo
        public Integer warning;  // amarillo
        public Integer normal;   // verde
    }

    // --- event item
    public static class EventData {
        public Integer id;                 // puede ser null si no lo envías
        public User user;                  // { id, name } (opcional)
        public String timestamp_str;       // "yyyyMMdd_HHmmss"
        public String event;               // "Persona detectada"
        public Integer zone_idx;           // 0..N
        public String level;               // "red" | "yellow" | "green"
        public Boolean save;
        public String filename;
        public String forklift_name;       // "qnec-forklift-1"
        public Double confidence;          // 0..1
        public Object meta;                // lo que sea que mandes por evento
        public String created_at;          // ISO string o null

        public static class User {
            public Integer id;
            public String name;
        }
    }
}
