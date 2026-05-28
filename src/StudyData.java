import java.awt.Color;
import java.io.Serializable;

// Esta classe serve como um "pacote" para guardar os minutos e a cor de cada matéria.
// Serializable é importante para salvar e carregar os dados, embora não usemos neste formato.
public class StudyData implements Serializable {
    private int minutes;
    private Color color;

    public StudyData(int minutes, Color color) {
        this.minutes = minutes;
        this.color = color;
    }

    // Métodos para obter (get) e definir (set) os valores
    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void addMinutes(int minutesToAdd) {
        this.minutes += minutesToAdd;
    }
}