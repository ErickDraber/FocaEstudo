import java.io.Serializable;

public class StudySession implements Serializable {
    private final String subject;
    private final int minutes;
    private final long timestamp;
    private final String type; // método: "cronometro", "pomodoro", "manual"
    private final String note; // anotação opcional ("o que estudei")
    private final String kind; // tipo de atividade: "", "teoria", "exercicios", "revisao", "outro"

    public StudySession(String subject, int minutes, long timestamp, String type) {
        this(subject, minutes, timestamp, type, "", "");
    }

    public StudySession(String subject, int minutes, long timestamp, String type, String note) {
        this(subject, minutes, timestamp, type, note, "");
    }

    public StudySession(String subject, int minutes, long timestamp, String type, String note, String kind) {
        this.subject = subject;
        this.minutes = minutes;
        this.timestamp = timestamp;
        this.type = type;
        this.note = note == null ? "" : note;
        this.kind = kind == null ? "" : kind;
    }

    public String getSubject()   { return subject; }
    public int    getMinutes()   { return minutes; }
    public long   getTimestamp() { return timestamp; }
    public String getType()      { return type; }
    public String getNote()      { return note; }
    public String getKind()      { return kind; }

    /** Barras verticais e quebras de linha não podem entrar no arquivo. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    public String serialize() {
        return subject + "|" + minutes + "|" + timestamp + "|" + type
             + "|" + clean(note) + "|" + clean(kind);
    }

    public static StudySession deserialize(String data) {
        String[] parts = data.split("\\|", 6);
        if (parts.length < 4) return null;
        try {
            return new StudySession(parts[0],
                    Integer.parseInt(parts[1]),
                    Long.parseLong(parts[2]),
                    parts[3],
                    parts.length >= 5 ? parts[4] : "",
                    parts.length >= 6 ? parts[5] : "");
        } catch (Exception e) { return null; }
    }
}
