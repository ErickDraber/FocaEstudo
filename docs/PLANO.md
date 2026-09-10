# FocaEstudo — Plano de evolução

Aplicativo Java Swing para organização e incentivo de estudos, com metas diárias/semanais/mensais,
cronômetro, Pomodoro, histórico, calendário, gráfico de distribuição e tema claro/escuro.

O trabalho está dividido em 6 etapas, das mais rápidas e isoladas para as mais estruturais.
Cada etapa termina com **compilar + testar + atualizar `docs/PROGRESSO.md`** (e, de preferência, um commit).
Tempos = horas de trabalho focado.

---

## Etapa 0 — Reparos (CONCLUÍDA)

- Arquivos de dados sempre na raiz do projeto (`BASE_DIR` via localização das classes), não no diretório de origem.
- Proteções de gravação: não salva antes da UI carregar, nem com mapa vazio, nem se a leitura falhou.
- Backup automático `*.bak` antes de cada gravação; linhas ilegíveis do save são preservadas.
- Trava de instância única (socket `127.0.0.1:52147`) — 2 cópias abertas corrompiam os dados.
- Layout: donut deixou de ser espremido pelo cartão de progresso (split vertical).
- Cronômetro: botão **Zerar** próprio (não mexe na matéria); "Parar"→"Pausar"; gerência renomeada p/ "Zerar matéria".
- Diálogo de metas: aceita `90`, `1h30`, `1:30`, `2h`, `1,5h`; rótulos visíveis (não força cor do tema escuro);
  sugestões de meta (botões rápidos + média dos últimos 30 dias + média nos dias estudados).

---

## Etapa 1 — Micro-melhorias do cronômetro e de UX  (~4–6 h)

| Item | Descrição |
|---|---|
| 4  | Aviso de reta final ("faltam X min para a meta de hoje") + aviso de meta batida |
| 15 | Tempo bruto × líquido (desconta pausas) + "foco mínimo" configurável |
| 16 | Pomodoro em ciclos (4 focos → pausa longa) + contador de pomodoros do dia |
| 17 | Detecção de inatividade: sem interação por N min → pausa e pergunta "ainda estudando?" |
| 18 | Atalhos de teclado (espaço = iniciar/pausar cronômetro; Ctrl+H/T/G) |

Toca só em `buildTopBar`, `buildStopwatchTab`, `buildPomodoroTab`, timers e um diálogo de Ajustes.
Novos ajustes persistidos em `study_data.properties` como `__min_focus__`, `__idle_min__`, `__pomo_autocycle__`.

---

## Etapa 2 — Incentivo (usa só o histórico existente)  (~6–9 h)

| Item | Descrição |
|---|---|
| 6  | Streak com 1 "folga"/semana + marcos 7/30/100 dias com selo |
| 3  | Saldo semanal de meta ("+45 min acima" / "−1h20 para recuperar") |
| 10 | Comparação com você mesmo: este mês × mês passado, por matéria |
| 9  | Mapa de consistência no calendário (cor por intensidade, estilo GitHub) |

Tudo derivado de `sessions` + `goals`. Sem mudança de formato de arquivo.

---

## Etapa 3 — Metas mais inteligentes  (~5–7 h)

| Item | Descrição |
|---|---|
| 1  | Meta adaptativa: bateu X dias seguidos → sugere subir 10–15%; falhou muito → sugere baixar |
| 2  | Dias da semana na meta (ex.: seg/qua/sex) — fim de semana não quebra a sequência |
| 12 | Contagem regressiva de prova: data-alvo por matéria + carga diária sugerida |

Adiciona campos em `goals` (padrão de dias, data-alvo) — compatível com o formato atual.

---

## Etapa 4 — Histórico editável + pequenas adições de dados  (~5–8 h)

Primeira mudança em `StudySession` — leve e retrocompatível (campos novos opcionais no serialize/deserialize).

| Item | Descrição |
|---|---|
| 20 | Editar/excluir sessão na tela de Histórico |
| 13 | Anotação rápida ao salvar tempo ("o que estudei"), visível no histórico |
| 14 | Arquivar matéria sem apagar o histórico |

---

## Etapa 5 — Base de dados robusta + padronização dos diálogos  (~7–11 h)

| Item | Descrição |
|---|---|
| 21 | Migrar `.properties` soltos para **um JSON único com escrita atômica** (.tmp + rename) + migração automática |
| 19 | Exportar/importar dados (JSON) para backup manual e troca de PC |
| 22 | Componente de `JDialog` no tema do app + conversão de todos os diálogos |

Etapa mais delicada; vem depois das features pequenas, com backup já implementado.
Até aqui, diálogos novos das etapas 3–4 seguem no estilo claro atual (consistente com o resto).

---

## Etapa 6 — Telas novas e automações  (~12–18 h)

| Item | Descrição |
|---|---|
| 11 | Checklist por matéria ("Cap. 3", "Lista 4") com estimativa de tempo e marcação de concluído |
| 23 | Aba **Estatísticas**: horas por dia da semana, melhor horário, evolução mensal |
| 7  | Resumo semanal automático (domingo) |
| 5  | Metas por tipo de atividade (teoria / exercícios / revisão) — exige "tipo" em sessões e metas |
| 8  | Lembrete diário no horário habitual (`SystemTray`/notificação do Windows) |

---

## Resumo

| Etapa | Foco | Tempo |
|---|---|---|
| 1 | Cronômetro / atalhos | 4–6 h |
| 2 | Incentivo (dados atuais) | 6–9 h |
| 3 | Metas inteligentes | 5–7 h |
| 4 | Histórico editável | 5–8 h |
| 5 | Storage JSON + diálogos temáticos | 7–11 h |
| 6 | Telas novas + automações | 12–18 h |
| | **Total** | **~39–59 h** |
