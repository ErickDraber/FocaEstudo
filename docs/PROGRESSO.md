# FocaEstudo — Progresso e contexto

> Arquivo de retomada. Se o PC desligar / cair luz ou internet, comece a ler por aqui.
> Plano completo das etapas: **`docs/PLANO.md`**.

**Última atualização:** 2026-09-10 — **6 etapas de features + 5 etapas visuais (V1–V5) concluídas.**
App compila (36 classes, L&F Nimbus) e roda; fechar/reabrir mantém os dados; nenhum `.tmp` órfão.
Dados do usuário restaurados ao estado original + chaves de ajuste com valores padrão.

---

## Como retomar rápido

1. Ler este arquivo + `docs/PLANO.md`.
2. Compilar e rodar: dois cliques em `Executar_Programa.bat` (raiz).
   Ou pelo terminal, na raiz do projeto:
   ```
   javac -d bin src/StudyData.java src/StudySession.java src/ChecklistItem.java src/AppTheme.java src/RoundedPanel.java src/StyledButton.java src/CustomTabs.java src/PieChartPanel.java src/CalendarPanel.java src/HistoryPanel.java src/StatsPanel.java src/ChecklistPanel.java src/StudyTracker.java
   java -cp bin StudyTracker
   ```
3. Conferir "Estado atual" abaixo para saber onde o trabalho parou.

## Ambiente

- **Java 25** instalado; o código deve continuar compilando em **Java 11+** (não usar APIs > 11, ex.: não usar `Locale.of`).
- Praticamente tudo está em `src/StudyTracker.java` (~1450 linhas). Classes de apoio: `AppTheme`, `StudyData`,
  `StudySession`, `CalendarPanel`, `PieChartPanel`, `HistoryPanel`, `CustomTabs`, `RoundedPanel`, `StyledButton`.
- Dados (na **raiz** do projeto): `study_data.properties` (matérias, cores, metas, tema, ajustes) e
  `study_sessions.properties` (log de sessões).
- Backups automáticos `*.bak` são gerados antes de cada gravação (estão no `.gitignore`).

## Armadilhas conhecidas

- **Nunca rodar duas instâncias ao mesmo tempo** — elas gravam o mesmo arquivo e corrompem os dados.
  Há trava por socket (`127.0.0.1:52147`); a 2ª cópia pergunta antes de abrir.
- `.properties` é lido como ISO-8859-1: acentos em chaves aparecem como `\uXXXX`
  (ex.: `Estatística`). Ao editar o arquivo à mão, manter esse escape.
- `updateUI()` chama `saveData()`. Só grava depois de `ready == true` (fim de `initializeUI`).
- L&F = **Nimbus** (`AppTheme.installLookAndFeel`, desde V3). Respeita as cores do `UIManager`; a
  paleta vem de `applyLafPalette()` (dark/light) e o toggle de tema chama `updateComponentTreeUI`.
  Ao adicionar cor, use os tokens do `AppTheme`. Diálogos custom seguem com `dlgLabel`/`dlgHint`.
- `.gitignore`: `*.bak`, `*.tmp`. Arquivos de dados na raiz: `study_data.properties`,
  `study_sessions.properties`, `study_checklist.properties`.

## Dados de referência do usuário (para recuperação)

Matérias / minutos / cor(RGB):
```
AED3=1306,-2439620
AOC2=238,-5598335
Estatística=642,-10105406
Leitura=56,-13324435
goal_AED3=10
goal_Leitura=30
__streakbest_general__=0
__theme__=dark
```
Sessões: `s0..s7` (Leitura×1, Estatística×5, AED3×2), junho/2026 — ver `study_sessions.properties`.

---

## Estado por etapa

### Etapa 0 — Reparos — ✅ CONCLUÍDA (2026-09-10)
Ver `docs/PLANO.md` › Etapa 0. Bugs de perda de dados, layout do donut, "Zerar" do cronômetro e
diálogo de metas corrigidos; trava de instância e backups adicionados.

### Etapa 1 — Micro-melhorias do cronômetro e de UX — ✅ CONCLUÍDA (2026-09-10)

- [x] 18 — Atalhos: **Espaço** = iniciar/pausar cronômetro (via `KeyEventDispatcher` global — um botão
      com foco engoliria o espaço se fosse `InputMap`); **Ctrl+H** histórico, **Ctrl+T** tema, **Ctrl+G** metas.
- [x] 4  — `goalNudge()` em `addTime()`: toast "🎯 Meta diária batida!" ao cruzar a meta, ou
      "Faltam X min…" quando falta ≤ 15 min. Toast atrasado 2,5 s p/ não colidir com o de "tempo salvo".
- [x] 15 — Linha `stopwatchInfoLabel` sob o cronômetro: "líquido · N pausas · bruto HH:MM:SS"
      (`stopwatchGrossStartMs`, `stopwatchPauseCount`). "Foco mínimo" (`setMinFocusMin`, padrão 1)
      aplicado em `submitStopwatchTime` — abaixo disso não salva e mantém o tempo.
- [x] 16 — `pomoCountLabel` "🍅 N focos hoje" (deriva de `sessions` tipo pomodoro no dia).
      Checkbox "Ciclos automáticos" (`autoCycleCheck`/`setPomoAutoCycle`): `stopCountdown` encadeia
      foco→pausa→foco; pausa longa (`max(15, pausa×3)`) a cada 4 focos (`pomoCycleFocos`).
      Com auto-ciclo, usa `toast` no lugar dos diálogos modais.
- [x] 17 — `setupIdleWatch()`: `AWTEventListener` atualiza `lastActivityMs`; `idleTimer` (20 s)
      chama `checkIdle()` → se o cronômetro roda e passou `setIdleMinutes` sem atividade, pausa e
      pergunta "Ainda está estudando?". Sim = retoma; Não = fica pausado. 0 = desligado.
- [x] Botão **"Ajustes"** no topo → `showSettingsDialog()` (foco mínimo, inatividade, ciclos automáticos).
- [x] Ajustes persistidos em `study_data.properties`: `__min_focus__`, `__idle_min__`, `__pomo_autocycle__`
      (tratados em `loadData`/`saveData`).
- [x] Compilado, testado com 1 instância (Ajustes, Espaço/pausa, linha bruto×líquido conferidos em tela).
      Itens 4, 16, 17 conferidos por código (cenários longos não exercitados ao vivo).

**Arquivos tocados:** só `src/StudyTracker.java`.
**Não testado ao vivo:** disparo real do `goalNudge` (precisa cruzar a meta), ciclo automático completo
do Pomodoro (precisa esperar os timers), diálogo de inatividade (precisa 10 min parado).

### Etapa 2 — Incentivo (usa só o histórico existente) — ✅ CONCLUÍDA (2026-09-10)

- [x] 6 — `currentStreak` com **folga**: 1 dia abaixo da meta tolerado por janela de 7 dias.
      Cuidado corrigido: a folga não cria sequência do nada (exige dia batido no começo; apara falhas do fim).
      **Marcos** 7/30/100/365 → selo (`streakBadge` 🥉🥈🥇🏆) na linha de sequência + toast único ao
      alcançar (`celebratedStreakMilestone`, persistido em `__streak_milestone__`).
- [x] 3 — `weeklyBalance()` + `insightLine()` no cartão Progresso: "semana: +Xh sobre a meta" /
      "faltam Xh p/ a meta".
- [x] 10 — `insightLine()` também mostra "mês: Xh (▲/▼ Yh vs. mês passado)" (mês atual até hoje ×
      mês anterior até o mesmo dia).
- [x] 9 — `CalendarPanel`: `heatLevel()`/`heatColor()`/`blend()` — fundo das células em 4 intensidades
      de azul conforme o tempo (proporcional à meta, ou faixas 30/60/120 min). Texto escuro/claro
      conforme a intensidade.

**Arquivos tocados:** `src/StudyTracker.java`, `src/CalendarPanel.java`.
**Verificado ao vivo:** sequência não cria "1 dia" falso; cartão Progresso renderiza com a linha de insights.
**Não exercitado ao vivo:** heatmap do calendário (setembro está vazio; código é função de cor isolada),
toast de marco, comparação mensal com dados reais.

### Etapa 3 — Metas mais inteligentes — ✅ CONCLUÍDA (2026-09-10)

- [x] 2 — `goalDays` (Map<String, Set<DayOfWeek>>): 7 toggles no diálogo de metas. Dias desmarcados
      não exigem meta e são pulados no `currentStreak` (sequência = dias agendados batidos) e no
      `weeklyBalance`. Persist. `goaldays_<mat>=1,2,3,4,5` (ISO; ausente/7 dias = todos).
- [x] 12 — `examDate` (Map<String, LocalDate>): campo "Data da prova" (dd/mm/aaaa) no diálogo.
      `examLine()` no cartão Progresso do assunto: "📅 prova em N dias (dd/MM) · no ritmo da meta: ~Xh até lá".
      Persist. `exam_<mat>=yyyy-MM-dd`.
- [x] 1 — `adaptiveGoalSuggestion()`: analisa os últimos 14 dias agendados. Sobe ~15% se streak ≥ 7 e
      média ≥ 120% da meta; baixa ~20% se bateu em < 40% dos dias. Aparece como linha "Usar" no diálogo.
- [x] Persistência de rename/delete de matéria cobre `goalDays` e `examDate`.
- [x] `leftSplit` do painel esquerdo: 0.62 → 0.52 (mais espaço p/ o cartão Progresso, que cresceu).

**Arquivos tocados:** `src/StudyTracker.java`.
**Verificado ao vivo:** diálogo de metas com os 3 blocos novos renderiza; round-trip de persistência
(`exam_Leitura=2026-11-20`, `goaldays_Leitura=1,2,3,4,5`).
**Pendência de limpeza:** removi esses 2 valores de teste do `study_data.properties` no fim (ver commit/verificação final).
**Não exercitado ao vivo:** `examLine` com prova futura real, meta adaptativa subindo, streak com dias de descanso.

### Etapa 4 — Histórico editável + pequenas adições de dados — ✅ CONCLUÍDA (2026-09-10)

- [x] 13 — `StudySession` ganhou campo `note` (5º token no serialize, retrocompatível: linhas antigas
      com 4 tokens → nota vazia). Campo **"Nota:"** ao lado do seletor de matéria; aplicado à próxima
      sessão salva e limpo depois. Exibido no Histórico entre aspas.
- [x] 20 — `HistoryPanel` reescrito: agora recebe a **lista real** de sessões + `Runnable onChange`.
      Cada card tem **Editar** (matéria, duração h/min, data/hora, nota) e **Excluir** (com confirmação).
      Editar/excluir ajustam o total da matéria pelo delta (`adjust()`), salvam e atualizam a UI.
- [x] 14 — `archived` (Set<String>): botão **"Arquivar"** na barra de gerência. Matéria arquivada some
      do seletor (não recebe tempo novo) mas mantém total, sessões e histórico. Reativar = "+ Matéria"
      com o mesmo nome. Persist. `__archived__=Mat1,Mat2`. Cobre rename/delete. Bloqueia arquivar a última ativa.

**Arquivos tocados:** `src/StudySession.java`, `src/HistoryPanel.java`, `src/StudyTracker.java`.
**Cuidado corrigido:** um byte de controle `\x01` tinha entrado na linha do `String.join` de `__archived__`
(glitch de edição) — reescrito, arquivo sem control chars.
**Verificado ao vivo:** campo Nota renderiza; Histórico abre com botões Editar/Excluir nos cards.
**Não exercitado ao vivo:** edição/exclusão real de sessão com ajuste de total, ciclo arquivar/reativar.

### Etapa 5 — Base de dados robusta + padronização dos diálogos — ✅ CONCLUÍDA (2026-09-10)

- [x] 21 — **Escrita atômica**: `storeAtomic()` grava em `<arquivo>.tmp`, faz `fsync`, e renomeia com
      `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (fallback sem atomic). Queda de luz no meio da
      gravação não corrompe mais o save. `saveData` e `saveSessions` usam. `.tmp` no `.gitignore`.
      **Decisão:** NÃO migrei para JSON único — sem lib de JSON, um parser à mão seria mais risco que
      benefício. `.properties` + atômico + `.bak` já é robusto. Consolidação em JSON fica como opcional.
- [x] 19 — **Exportar/Importar** no diálogo Ajustes (seção "Backup"). Exporta um `.txt` único com as
      duas seções (`===== FOCAESTUDO DADOS/SESSOES =====`) via `JFileChooser`. Importar valida (tem
      matéria?), confirma, faz `.bak` do atual, grava e recarrega em memória (`reloadFromDisk`).
- [~] 22 — **PARCIAL.** `AppTheme.applyDialogTheme()` empurra cores/fonte do tema para o `UIManager`
      (chamado em `applyLight/applyDark` e no construtor). Funciona em L&Fs que respeitam o UIManager,
      **mas o Windows L&F ignora `OptionPane.background`/`Panel.background`**, então os diálogos
      continuam claros. Estão legíveis (helpers `dlgLabel`/`dlgHint` não forçam cor). Escurecer de
      verdade exigiria trocar o L&F (Nimbus/Metal) ou `JDialog` custom — adiado por risco.

**Arquivos tocados:** `src/StudyTracker.java`, `src/AppTheme.java`.
**Verificado ao vivo:** seção Backup aparece no Ajustes; sem `.tmp` órfão após rodar.
**Não exercitado ao vivo:** exportar/importar de ponta a ponta (JFileChooser difícil de automatizar);
recuperação real por queda de luz.

### Etapa 6 — Telas novas e automações — ✅ CONCLUÍDA (2026-09-10)

- [x] 23 — Janela **Estatísticas** (`StatsPanel`, botão no topo): barras de tempo por dia da semana
      (últimas 8 semanas), por horário do dia (0-23h), evolução dos últimos 6 meses, e por tipo de
      atividade. Tudo derivado de `sessions`.
- [x] 11 — Janela **Tarefas** (`ChecklistPanel` + `ChecklistItem`, botão no topo): checklist por
      matéria com texto, estimativa em minutos e concluído. Arquivo próprio `study_checklist.properties`
      (atômico + `.bak`). Cobre rename/delete de matéria, export/import e `reloadFromDisk`.
- [x] 5 — `StudySession` ganhou `kind` (6º token): dropdown **"Tipo"** (Teoria/Exercícios/Revisão/Outro)
      ao lado da Nota, aplicado à próxima sessão. Aparece no Histórico e vira uma seção nas Estatísticas.
      (Sem metas por tipo — versão reduzida, como planejado.)
- [x] 7 — `maybeShowWeeklySummary()` no `initializeUI`: 1ª abertura de uma semana ISO nova (a partir de
      sáb/dom) mostra um resumo dos últimos 7 dias (total, dias com estudo, metas batidas, matéria
      destaque, vs. semana anterior). Persiste `__week_summary_shown__`.
- [x] 8 — `setupReminder()`: `SystemTray`/`TrayIcon` (com fallback para toast). `Timer` de 5 min
      verifica; se passou da hora escolhida e não estudou hoje, notifica. Ajuste "Lembrete diário"
      (combo Desligado / 05:00-23:00) no diálogo Ajustes. Persiste `__reminder_hour__` (-1 = off).

**Arquivos novos:** `src/StatsPanel.java`, `src/ChecklistPanel.java`, `src/ChecklistItem.java`.
**Arquivos tocados:** `src/StudySession.java`, `src/HistoryPanel.java`, `src/StudyTracker.java`,
`Executar_Programa.bat` (+ `src/Executar_Programa.bat`).
**Verificado ao vivo:** botões novos no topo; Estatísticas com barras de horário reais; Tarefas
adiciona item e grava `study_checklist.properties`; dropdown Tipo presente.
**Não exercitado ao vivo:** resumo semanal (precisa fim de semana + dados na janela), lembrete
(precisa esperar a hora), notificação do Windows.

---

## Estado final (2026-09-10)

- **6 etapas implementadas.** Item 22 (diálogos escuros) ficou parcial — hook pronto, mas o Windows L&F
  ignora as cores de fundo do `UIManager`; diálogos seguem claros e legíveis.
- App compila com o comando do `.bat` (13 fontes, 35 `.class`), abre e fecha sem erro, sem `.tmp` órfão.
- `study_data.properties` de volta aos valores originais do usuário + chaves de ajuste com padrão
  (`__min_focus__=1`, `__idle_min__=10`, `__reminder_hour__=-1`, etc.).
- **Recomendado:** rodar `Executar_Programa.bat` uma vez para conferir, e commitar
  (há 3 arquivos novos em `src/`, `docs/`, `.gitignore`, e o `.bat` atualizado).

---

## Log de sessões de trabalho

| Data | Etapa | O que foi feito |
|---|---|---|
| 2026-09-10 | 0 | Reparos concluídos e verificados no app rodando. |
| 2026-09-10 | 1 | `docs/` criado. Etapa 1 (cronômetro/atalhos/ajustes). |
| 2026-09-10 | 2 | Etapa 2 (streak com folga, marcos, saldo semanal, comparação mensal, heatmap). |
| 2026-09-10 | 3 | Etapa 3 (dias da meta, data de prova, meta adaptativa). |
| 2026-09-10 | 4 | Etapa 4 (nota na sessão, histórico editável, arquivar matéria). |
| 2026-09-10 | 5 | Etapa 5 (escrita atômica, exportar/importar, hook de tema em diálogos). |
| 2026-09-10 | 6 | Etapa 6 (Estatísticas, Tarefas, tipo de atividade, resumo semanal, lembrete). Fim do plano de features. |
| 2026-09-10 | fix | Legibilidade: `applyDialogTheme` reduzido a fontes (as cores globais deixavam texto quase invisível nos campos/combos no L&F do Windows); `TEXT_SEC` do tema escuro clareado (`0x9298BB`→`0xAEB4D6`); rótulos "Matéria:"/"Nota:"/"Horas:"/"Minutos:" agora em `TEXT_PRI` negrito. Plano de design em `docs/PLANO_VISUAL.md`. |
| 2026-09-10 | V1 | `styleInput()` (borda do tema, fonte, cursor/seleção) em combos/campos/spinners do painel principal. `FIELD_BG` claro (o L&F do Windows não escurece combos — texto escuro legível + borda `FIELD_BORDER`). Tokens: `TEXT_MUT`, `FIELD_BG`, `FIELD_BORDER`, `SELECTION`. `TEXT_SEC` claro escurecido p/ AA. |
| 2026-09-10 | V4 | Ícone do app desenhado (`AppTheme.appIcon`/`appIcons`) na janela e na bandeja (clique no tray restaura a janela). Estados vazios com glifo + título + subtítulo (`AppTheme.emptyState`) em Histórico/Estatísticas/Tarefas. Primeiro uso: diálogo de boas-vindas com ícone + campo pré-preenchido (em vez de `showInputDialog` cru). |
| 2026-09-10 | V5 | Toast com slide-up + fade (in/hold/out), cantos arredondados. Calendário: hover realça a célula; legenda "menos ▢▢▢▢▢ mais" do mapa de intensidade abaixo da grade. |
| 2026-09-10 | V2 | Linha "Matéria" desafogada: Nota+Tipo em 2ª linha (Nota mais larga, "Tipo:" com rótulo). Emoji removido do botão Meta. Escala de fonte: `FONT_SECTION` (14 bold) e `FONT_CAPTION` (10); aplicada aos títulos "Progresso"/Estatísticas/Histórico/Tarefas. |
| 2026-09-10 | V3 | **Nimbus** como L&F (`AppTheme.installLookAndFeel` + `applyLafPalette` com paleta dark/light; reinstala o L&F p/ recalcular; `updateComponentTreeUI` no toggle de tema). Diálogos, combos, spinners, scrollbars e janelas (Histórico/Estatísticas/Tarefas) agora seguem o tema. `styleInput` reduzido a fonte. Tokens `FIELD_*`/`SELECTION` e `applyDialogTheme` removidos. **Item 22 resolvido.** |
| 2026-09-10 | fix2 | Metas semana/mês seguem o nº de dias da meta (`8284996`). Botão "Limpar metas" + sugestão "Repetir sua melhor semana" (`057f9bb`). Cartão "Metas" + diálogo "Gerenciar metas" em massa (`2ce9ec7`). |
| 2026-09-10 | A1 | **áreas com tipo** (Estudo/Físico/Lazer/Trabalho/Outro), cartão "Equilíbrio" (tempo da semana por tipo), metas de equilíbrio (mínimo semanal por tipo), aviso gentil de desequilíbrio. `type_<área>`, `typegoalw_<tipo>`, `__balance_nudge_week__`. Botões "+ Área"/"Editar área". `docs/PILARES.md` + `docs/PLANO_TDAH.md`. Commit `5ec8eb8`. Verificado: compila, cartão Equilíbrio renderiza (empty state). NÃO exercitado ao vivo: barras com dados reais de tipo, o aviso semanal. |
| 2026-09-10 | A2 | **sub-focos por área** (`subFocos`, `subfocos_<área>`). Combo "Sub-foco:" área-aware + "gerenciar" (editor um-por-linha + presets por tipo: Físico→Peito/Costas/…). Sessão guarda sub-foco em `kind` (texto livre). Stats `kindChart` dinâmico; edição de sessão vira texto livre. Commit `ce1f9aa`. Verificado: compila, combo renomeado "Sub-foco:" renderiza. Não exercitado ao vivo: editor de sub-focos, quebra por sub-foco nas Estatísticas com dados. |
| 2026-09-10 | A3 | Cartão **"Agora"** (topo do painel esquerdo): 1 sugestão via `nextSuggestion()` (meta do dia não batida priorizando prova próxima → tipo com equilíbrio zerado → área parada há mais tempo) + botão "▶ Começar" (seleciona área, vai pra aba Cronômetro, liga). **Anti-hiperfoco** (aviso após N h seguidas, `setHyperfocusH`) e **pausa entre blocos** (`setTransitionMin`) — ambos em Ajustes; `__hyperfocus_h__`, `__transition_min__`. Commit a seguir. Verificado ao vivo: "Agora" mostrou CLP corretamente (hoje é dia de meta dele + prova próxima). |
| 2026-09-10 | A4 | **Meta em faixa**: `goalsMin` (mínimo diário; ausente = ideal). Sequência (`currentStreak`) e `goalNudge` passam a usar o MÍNIMO (`dailyMinFor`); campo "Mínimo aceitável" no diálogo de metas; `goalmin_<área>`. **Dia difícil**: `hardDays` (ISO), link "hoje tá difícil?" no cartão Agora — não cobra, não quebra sequência, suprime avisos/lembrete/equilíbrio; `__hard_days__`. Cobre rename/delete/import. Commit a seguir. Verificado: compila, cartão Agora com link. Não ao vivo: streak com mínimo/dia-difícil, faixa no diálogo. |
| 2026-09-10 | A5 | **Energia** por sessão (`StudySession.energy` 0-3, 7º token; combo 😴/🙂/🔥 na linha de registro; seção "Energia por horário" nas Estatísticas; ícone no Histórico). **Rodízio** (academia): `nextSuggestion()` passo 1b — área de Físico com sub-foco/grupo parado ≥ 3 dias vira sugestão do cartão "Agora" (e o "Começar" já seleciona o grupo). Commit a seguir. Verificado: compila, linha de registro com Nota+Sub-foco+Energia renderiza. Não ao vivo: seção Energia nas Stats, rodízio com dados de Físico. |
| 2026-09-10 | A6-parcial | Contagem regressiva de prova agora mostra "plano X/Y" (itens do checklist da matéria feitos). **Planejador semanal (grade seg–dom drag-drop) NÃO feito** — maior peça, fica pra próxima sessão. Ver `docs/PLANO_TDAH.md`. |
| 2026-09-10 | A6 | **Planejador semanal** (`PlannerPanel`, botão "Semana" no topo): grade 7 dias × Manhã/Tarde/Noite, clique na célula → escolhe a área (cor da área), "Limpar" por célula. "Preencher pelos dias da meta" distribui cada área nos seus `goalDays`, no 1º slot livre do dia. "Limpar tudo". Persistência `plan_<dia 1-7>_<slot m|t|n>` em `study_data.properties`; cobre rename/delete/import/reload. Hoje (Qui) marcado com "•". Commit a seguir. Verificado ao vivo: janela abre, grade renderiza, autofill preencheu Ter/Qua/Qui/Sex conforme `goaldays_*`, "Limpar tudo" apagou as chaves `plan_`. **Etapa A6 concluída — fim do plano TDAH.** |
