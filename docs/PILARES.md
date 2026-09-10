# FocaEstudo — Pilares do projeto

Referência curta do que o app É e das regras que não se quebram.

## Para que serve

Acompanhamento das **áreas da vida** com foco em quem tem TDAH. Não é só estudo:
Estudo, Físico, Lazer e Trabalho entram no mesmo lugar. Descanso e corpo contam.

## Pilares de produto (TDAH)

1. **Externalizar o invisível.** Tempo, progresso, "o que vem agora" — tudo à vista.
2. **Menos fricção pra começar.** Idealmente 1 toque. Menos decisão = menos travamento.
3. **Reforço positivo, não punição.** Sem tudo-ou-nada. Bater o mínimo já vale. Linguagem gentil,
   sem vermelho agressivo, sem cobrança.
4. **Flexibilidade.** Folga automática na sequência, "dia difícil", meta em faixa (mínimo/ideal).
5. **Equilíbrio.** Metas de Lazer e Físico são de primeira classe. Aviso se a semana está desequilibrada.
6. **Uma coisa por vez.** Um card "Agora" com UMA sugestão, não uma lista que assusta.
7. **Transições são difíceis.** Timer de pausa entre blocos; aviso de hiperfoco.
8. **Comemorar.** Sequência, marcos, toasts. Ganhos visíveis.

## Pilares técnicos (não quebrar)

- **Java 11+** de linguagem-alvo (a máquina tem 25). Evitar APIs > 11 onde der.
- **Gravação atômica** (`storeAtomic`: `.tmp` + `fsync` + rename) + `.bak` antes de cada save.
  Queda de luz no meio da gravação não corrompe. `*.bak`/`*.tmp` no `.gitignore`.
- **Arquivos de dados na raiz** (`BASE_DIR` via localização das classes), não no diretório de origem.
  `study_data.properties`, `study_sessions.properties`, `study_checklist.properties` — **gitignored**,
  o app cria/atualiza. **Não versionar dados pessoais. Não "restaurar" o que o usuário alterou.**
- `.properties` é lido como ISO-8859-1 → acentos em chaves como `\uXXXX` (ex.: `Estatística`).
- **Instância única** (socket `127.0.0.1:52147`). `javaw` aparece como 2 processos no Windows
  (encaminhador do `javapath` + JVM real) — é normal, é uma instância só.
- **L&F Nimbus** (`AppTheme.installLookAndFeel`), paleta dark/light em `applyLafPalette()`;
  toggle de tema chama `updateComponentTreeUI`. Cores só via tokens do `AppTheme`.
- `updateUI()` chama `saveData()`; só grava depois de `ready == true` (fim de `initializeUI`).
- Retrocompatibilidade dos saves: campos novos = novos tokens/prefixos, linhas antigas continuam válidas.
  `StudySession.serialize`: `subject|min|ts|type|nota|kind` (+ novos ao fim).

## Modelo de dados (rumo)

- **Área** (era "Matéria"): nome + cor + **tipo** (estudo/físico/lazer/trabalho/outro) + metas
  (diária/semanal/mensal, min/ideal) + dias da meta + data de prova + arquivada.
- **Sub-foco**: item dentro de uma área (tópico da prova, grupo muscular, hobby específico).
- **Sessão**: área + minutos + timestamp + método (cronômetro/manual/pomodoro) + nota + sub-foco + energia.
- **Meta de equilíbrio**: mínimo semanal por *tipo* (ex.: ≥ 3h de Lazer).

## Onde está o quê

- Quase tudo em `src/StudyTracker.java`. Apoio: `AppTheme`, `StudyData`, `StudySession`,
  `ChecklistItem`, `CalendarPanel`, `PieChartPanel`, `HistoryPanel`, `StatsPanel`, `ChecklistPanel`,
  `CustomTabs`, `RoundedPanel`, `StyledButton`.
- Planos: `docs/PLANO.md` (features 1–6), `docs/PLANO_VISUAL.md` (V1–V5), `docs/PLANO_TDAH.md` (áreas/TDAH).
- Andamento e retomada: `docs/PROGRESSO.md`.
