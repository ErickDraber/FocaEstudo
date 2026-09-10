# FocaEstudo — Plano de melhorias visuais / design

Mesmo formato do `PLANO.md`: etapas das mais rápidas/seguras para as mais estruturais.
Cada etapa termina com compilar + testar nos 2 temas + atualizar `PROGRESSO.md`.
Tempos = horas de trabalho focado.

## Status: V1–V5 CONCLUÍDAS (2026-09-10) — ver `PROGRESSO.md` para o detalhe de cada uma.

## Já feito (correção pontual, 2026-09-10)
- Rótulos "Matéria:" / "Nota:" / "Horas:" / "Minutos:" agora em `TEXT_PRI` negrito (eram cinza apagado).
- `TEXT_SEC` do tema escuro clareado: `0x9298BB` → `0xAEB4D6` (texto pequeno estava ilegível).
- `applyDialogTheme()` reduzido a fontes — as cores globais no `UIManager` eram aplicadas pela
  metade pelo Look&Feel do Windows (fundo claro + texto claro = quase invisível nos campos e combos).

---

## Etapa V1 — Legibilidade e campos no tema  (~3–5 h)

- **Estilizar os campos do painel principal** (`JTextField`, `JComboBox`, `JSpinner`): fundo escuro,
  texto claro, borda sutil, cor de cursor/seleção. Helper `styleInput(JComponent)` aplicado em todos.
  Hoje são caixas brancas dentro do app escuro.
- Varredura de contraste: separar "secundário legível" (`TEXT_SEC`) de "dica bem apagada"
  (`TEXT_MUT`, nova), e conferir AA (≥ 4.5:1) em ambos os temas.
- `styledField()` e afins passam a usar as cores do tema.

## Etapa V2 — Tipografia e espaçamento  (~4–6 h)

- Escala tipográfica explícita no `AppTheme` (Display / Title / Section / Body / Label / Caption) e
  aplicação consistente — hoje `FONT_SMALL`/`FONT_LABEL`/`FONT_BOLD` são usados meio ad hoc.
- Grid de 8 px: padronizar insets de cards, struts e bordas.
- Desafogar a linha "Matéria" (ficou cheia: Matéria + Meta + Nota + Tipo). Mover Nota/Tipo para
  dentro de cada aba ou para uma 2ª linha.
- Padronizar emoji: manter poucos e intencionais (ou trocar por ícones vetoriais desenhados) —
  no Windows eles saem coloridos e destoam do resto plano.

## Etapa V3 — Diálogos no tema (o "item 22" de verdade)  (~6–9 h)

- **Spike (1–2 h):** testar trocar o Look&Feel para **Nimbus**. Nimbus respeita as cores do
  `UIManager` melhor que o L&F do Windows — se ficar bom, diálogos/scrollbars/spinners herdam o
  tema quase de graça. Se não, seguir com o item abaixo. (Fazer numa branch: muda o visual de tudo.)
- **`AppDialog`** (base): `JDialog` escuro com cabeçalho, área de conteúdo e rodapé de botões
  `StyledButton`; ESC fecha, Enter confirma; centraliza no pai.
- Migrar para ela: metas, ajustes, editar sessão, adicionar tarefa, + matéria, renomear, deletar,
  cor, resumo semanal.
- `JColorChooser` e `JFileChooser` continuam nativos (são padrão do sistema — ok).

## Etapa V4 — Identidade e estados vazios  (~4–6 h)

- Ícone do app desenhado (janela + bandeja — hoje o `TrayIcon` é um quadrado transparente).
- Estados vazios com texto-guia + ícone leve: gráfico sem dados, calendário, histórico, tarefas.
- Tela de boas-vindas no primeiro uso, no lugar do `showInputDialog` cru de matérias.

## Etapa V5 — Microinterações e polimento dos gráficos  (~6–9 h)

- Hover/press consistentes nos botões; toast com slide-in/out em vez de aparecer seco.
- Donut: espaçamento de legenda e rótulos, leve destaque na fatia da matéria ativa.
- Calendário: hover na célula + legenda de intensidade ("menos → mais").
- Barras de progresso com cantos arredondados e animação curta ao mudar.
- Indicadores de foco de teclado visíveis; revisão final de contraste AA nos 2 temas.

---

## Resumo

| Etapa | Foco | Tempo |
|---|---|---|
| V1 | Legibilidade + campos no tema | 3–5 h |
| V2 | Tipografia + espaçamento | 4–6 h |
| V3 | Diálogos no tema (spike Nimbus + AppDialog) | 6–9 h |
| V4 | Ícone + estados vazios + boas-vindas | 4–6 h |
| V5 | Microinterações + gráficos | 6–9 h |
| | **Total** | **~23–35 h** |
