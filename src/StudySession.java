import java.io.Serializable;

public class StudySession implements Serializable {
    private final String subject;
    private final int minutes;
    private final long timestamp;
    private final String type; // "cronometro", "pomodoro", "manual"

    public StudySession(String subject, int minutes, long timestamp, String type) {
        this.subject = subject;
        this.minutes = minutes;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getSubject()   { return subject; }
    public int    getMinutes()   { return minutes; }
    public long   getTimestamp() { return timestamp; }
    public String getType()      { return type; }

    public String serialize() {
        return subject + "|" + minutes + "|" + timestamp + "|" + type;
    }

    public static StudySession deserialize(String data) {
        String[] parts = data.split("\\|", 4);
        if (parts.length < 4) return null;
        try {
            return new StudySession(parts[0],
                    Integer.parseInt(parts[1]),
                    Long.parseLong(parts[2]),
                    parts[3]);
        } catch (Exception e) { return null; }
    }
}
