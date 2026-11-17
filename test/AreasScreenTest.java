import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/** Testes da tela Áreas: como a lista se organiza e se cada botão chama a ação certa. */
public class AreasScreenTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static final String[] KEYS   = {"estudo", "fisico", "lazer", "trabalho", "outro"};
    static final String[] LABELS = {"Estudo", "Físico", "Lazer", "Trabalho", "Outro"};

    Map<String, StudyData> data = new LinkedHashMap<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, String> tipo = new LinkedHashMap<>();
    Function<String, String> typeOf = a -> tipo.getOrDefault(a, "estudo");
    List<String> chamadas = new ArrayList<>();

    void area(String nome, int min) { data.put(nome, new StudyData(min, Color.BLUE)); }

    String secoes() {
        StringBuilder sb = new StringBuilder();
        for (AreasScreen.Section s : AreasScreen.sections(data.keySet(), archived, typeOf, KEYS, LABELS))
            sb.append(s.label()).append(s.archived() ? "*" : "").append(s.areas()).append(' ');
        return sb.toString().trim();
    }

    AreasScreen tela() {
        return new AreasScreen(data, archived, typeOf, KEYS, LABELS, new AreasScreen.Actions() {
            public void add()                  { chamadas.add("add"); }
            public void subFocos(String a)     { chamadas.add("subFocos " + a); }
            public void edit(String a)         { chamadas.add("edit " + a); }
            public void color(String a)        { chamadas.add("color " + a); }
            public void zero(String a)         { chamadas.add("zero " + a); }
            public void archive(String a)      { chamadas.add("archive " + a); }
            public void unarchive(String a)    { chamadas.add("unarchive " + a); }
            public void delete(String a)       { chamadas.add("delete " + a); }
        });
    }

    /** Textos de todos os JLabel e botões da tela, na ordem em que aparecem. */
    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + "]");
            if (k instanceof Container cc) out.addAll(textos(cc));
        }
        return out;
    }

    /** Clica no botão {@code texto} da linha da área {@code area}. */
    static void clicar(Container c, String area, String texto) {
        AbstractButton b = acharBotao(c, area, texto, new boolean[1]);
        if (b == null) throw new IllegalStateException("botão " + texto + " de " + area + " não achado");
        b.doClick();
    }

    // Percorre na ordem; depois de achar o JLabel da área, o primeiro botão com o texto é o dela.
    static AbstractButton acharBotao(Container c, String area, String texto, boolean[] naLinha) {
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l && l.getIcon() != null) naLinha[0] = l.getText().equals(area);
            if (naLinha[0] && k instanceof AbstractButton b && b.getText().equals(texto)) return b;
            if (k instanceof Container cc) {
                AbstractButton r = acharBotao(cc, area, texto, naLinha);
                if (r != null) return r;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // ── sections(): a organização da lista ────────────────────────────
        AreasScreenTest t = new AreasScreenTest();
        eq("sem áreas: lista vazia", "", t.secoes());

        t.area("Cálculo", 0); t.area("Corrida", 0); t.area("Física", 0); t.area("Violão", 0);
        t.tipo.put("Corrida", "fisico"); t.tipo.put("Violão", "lazer");
        eq("um bloco por tipo, na ordem dos tipos; áreas na ordem do mapa",
                "Estudo[Cálculo, Física] Físico[Corrida] Lazer[Violão]", t.secoes());

        t.tipo.put("Cálculo", "outro");
        eq("tipo sem área não aparece; 'Outro' vai por último",
                "Estudo[Física] Físico[Corrida] Lazer[Violão] Outro[Cálculo]", t.secoes());

        t.archived.add("Violão"); t.archived.add("Física");
        eq("arquivadas saem do tipo e vão para o fim, na ordem em que foram arquivadas",
                "Físico[Corrida] Outro[Cálculo] Arquivadas*[Violão, Física]", t.secoes());

        t.archived.add("Fantasma");   // nome em 'archived' sem dado (save antigo)
        eq("arquivada que não existe mais não aparece",
                "Físico[Corrida] Outro[Cálculo] Arquivadas*[Violão, Física]", t.secoes());

        AreasScreenTest u = new AreasScreenTest();
        u.area("A", 0); u.archived.add("Fantasma");
        eq("'Arquivadas' sem nenhuma área existente não vira título vazio", "Estudo[A]", u.secoes());

        // ── a tela montada (headless: só componentes, sem janela) ─────────
        AreasScreenTest v = new AreasScreenTest();
        v.area("Cálculo", 190); v.area("Corrida", 52); v.area("Inglês", 0);
        v.tipo.put("Corrida", "fisico");
        v.archived.add("Inglês");
        AreasScreen tela = v.tela();
        List<String> antes = textos(tela);
        eq("antes do refresh: só o cabeçalho", true, !antes.contains("Cálculo"));
        eq("cabeçalho: título", true, antes.contains("Áreas"));
        eq("cabeçalho: botão + Área", true, antes.contains("[+ Área]"));

        tela.refresh();
        List<String> tx = textos(tela);
        int iCalc = tx.indexOf("Cálculo");
        eq("linha: nome e total formatado (190 min = 3h10)", "3h10", tx.get(iCalc + 1));
        eq("linha ativa: botões na ordem",
                List.of("[Sub-focos]", "[Editar]", "[Cor]", "[Zerar]", "[Arquivar]", "[Deletar]"),
                tx.subList(iCalc + 2, iCalc + 8));
        int iCorr = tx.indexOf("Corrida");
        eq("total abaixo de 1h", "52m", tx.get(iCorr + 1));
        eq("títulos na ordem", List.of("Estudo", "Físico", "Arquivadas"),
                List.of(tx.get(iCalc - 1), tx.get(iCorr - 1), tx.get(tx.indexOf("Inglês") - 1)));
        int iIng = tx.indexOf("Inglês");
        eq("linha arquivada: só Reativar e Deletar", List.of("[Reativar]", "[Deletar]"),
                tx.subList(iIng + 2, iIng + 4));

        // cada botão chama a ação certa, com a área certa
        clicar(tela, "Cálculo", "Sub-focos"); clicar(tela, "Cálculo", "Editar");
        clicar(tela, "Cálculo", "Cor");       clicar(tela, "Corrida", "Zerar");
        clicar(tela, "Corrida", "Arquivar");  clicar(tela, "Corrida", "Deletar");
        clicar(tela, "Inglês", "Reativar");   clicar(tela, "Inglês", "Deletar");
        for (Component k : findAll(tela)) if (k instanceof AbstractButton b && "+ Área".equals(b.getText())) b.doClick();
        eq("cliques ⇒ ações", List.of("subFocos Cálculo", "edit Cálculo", "color Cálculo", "zero Corrida",
                "archive Corrida", "delete Corrida", "unarchive Inglês", "delete Inglês", "add"), v.chamadas);

        // refresh lê as coleções de novo (são as mesmas do app, não cópias)
        v.data.remove("Corrida");
        v.archived.remove("Inglês");
        v.data.put("Física", new StudyData(0, Color.RED));
        tela.refresh();
        List<String> depois = textos(tela);
        eq("refresh: área apagada some", false, depois.contains("Corrida"));
        eq("refresh: área nova aparece", true, depois.contains("Física"));
        eq("refresh: sem título 'Físico' nem 'Arquivadas'", false,
                depois.contains("Físico") || depois.contains("Arquivadas"));
        eq("refresh: não duplica linhas", 1, Collections.frequency(depois, "Cálculo"));

        System.out.println("AreasScreen: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }

    static List<Component> findAll(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            out.add(k);
            if (k instanceof Container cc) out.addAll(findAll(cc));
        }
        return out;
    }
}
