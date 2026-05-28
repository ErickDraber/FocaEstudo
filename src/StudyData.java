import java.awt.Color;
import java.io.Serializable;

public class StudyData implements Serializable {
    private int minutes;
    private Color color;

    public StudyData(int minutes, Color color) {
        this.minutes = minutes;
        this.color = color;
    }

    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public void addMinutes(int minutesToAdd) { this.minutes += minutesToAdd; }
}
