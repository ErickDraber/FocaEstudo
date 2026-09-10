import java.io.Serializable;

public class StudySession implements Serializable {
    private final String subject;
    private final int minutes;
    private final long timestamp;
    private final String type; // método: "cronometro", "pomodoro", "manual"
    private final String note; // anotação opcional ("o que estudei")
    private final String kind; // sub-foco: tópico / grupo muscular / hobby (texto livre)
    private final int energy;  // 0 = não informado, 1 = baixa, 2 = ok, 3 = alta

    public StudySession(String subject, int minutes, long timestamp, String type) {
        this(subject, minutes, timestamp, type, "", "", 0);
    }

    public StudySession(String subject, int minutes, long timestamp, String type, String note) {
        this(subject, minutes, timestamp, type, note, "", 0);
    }

    public StudySession(String subject, int minutes, long timestamp, String type, String note, String kind) {
        this(subject, minutes, timestamp, type, note, kind, 0);
    }

    public StudySession(String subject, int minutes, long timestamp, String type,
                        String note, String kind, int energy) {
        this.subject = subject;
        this.minutes = minutes;
        this.timestamp = timestamp;
        this.type = type;
        this.note = note == null ? "" : note;
        this.kind = kind == null ? "" : kind;
        this.energy = energy < 0 ? 0 : (energy > 3 ? 3 : energy);
    }

    public String getSubject()   { return subject; }
    public int    getMinutes()   { return minutes; }
    public long   getTimestamp() { return timestamp; }
    public String getType()      { return type; }
    public String getNote()      { return note; }
    public String getKind()      { return kind; }
    public int    getEnergy()    { return energy; }

    /** Barras verticais e quebras de linha não podem entrar no arquivo. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    public String serialize() {
        return subject + "|" + minutes + "|" + timestamp + "|" + type
             + "|" + clean(note) + "|" + clean(kind) + "|" + energy;
    }

    public static StudySession deserialize(String data) {
        String[] parts = data.split("\\|", 7);
        if (parts.length < 4) return null;
        try {
            int en = 0;
            if (parts.length >= 7) { try { en = Integer.parseInt(parts[6].trim()); } catch (Exception ignored) {} }
            return new StudySession(parts[0],
                    Integer.parseInt(parts[1]),
                    Long.parseLong(parts[2]),
                    parts[3],
                    parts.length >= 5 ? parts[4] : "",
                    parts.length >= 6 ? parts[5] : "",
                    en);
        } catch (Exception e) { return null; }
    }
}
