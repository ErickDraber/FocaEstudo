/** Um item de checklist ("Lista 4", "Rever cap. 3"), com estimativa de tempo opcional. */
public class ChecklistItem {
    public String subject;
    public String text;
    public int    estMin;   // estimativa de minutos; 0 = sem estimativa
    public boolean done;

    public ChecklistItem(String subject, String text, int estMin, boolean done) {
        this.subject = subject;
        this.text = text;
        this.estMin = estMin;
        this.done = done;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    public String serialize() {
        return clean(subject) + "|" + (done ? 1 : 0) + "|" + estMin + "|" + clean(text);
    }

    public static ChecklistItem deserialize(String data) {
        String[] p = data.split("\\|", 4);
        if (p.length < 4) return null;
        try {
            return new ChecklistItem(p[0], p[3], Integer.parseInt(p[2]), "1".equals(p[1]));
        } catch (Exception e) { return null; }
    }
}
