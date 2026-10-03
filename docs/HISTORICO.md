# FocaEstudo: histórico completo e base para continuidade

> Documento de referência com tudo o que foi feito desde a criação do app (maio/2026) até 02/10/2026.
> Serve de ponto de partida para continuar o projeto de forma estruturada.
> Detalhes finos de cada etapa: `docs/PROGRESSO.md`. Regras que não se quebram: `docs/PILARES.md`.

---

## 1. O que é o FocaEstudo

App de **desktop para Windows** (Java Swing) que acompanha o tempo dedicado às **áreas da vida**
(Estudo, Físico, Lazer, Trabalho e Outro), pensado para **quem tem TDAH**. Ele nasceu como
rastreador de estudos e virou um organizador de rotina com metas, cronômetro, Pomodoro, planejamento
da semana e reforço positivo.

- **Público:** estudantes e pessoas com TDAH que precisam ver o tempo, saber o que fazer agora e ter
  pouco atrito para começar.
- **Distribuição:** um `.zip` com `FocaEstudo.exe` e Java embutido, publicado nas releases do GitHub
  (`ErickDraber/FocaEstudo`). Quem baixa não precisa instalar nada.
- **Link fixo para divulgar:**
  https://github.com/ErickDraber/FocaEstudo/releases/latest/download/FocaEstudo-Windows.zip

### Pilares de produto (resumo de `docs/PILARES.md`)

1. Externalizar o invisível: tempo, progresso e "o que vem agora" ficam à vista.
2. Pouco atrito para começar: idealmente 1 clique.
3. Reforço positivo, sem punição: bater o mínimo já vale; linguagem gentil.
4. Flexibilidade: folga na sequência, "dia difícil", meta mínima/ideal.
5. Equilíbrio: metas de Lazer e Físico são tão importantes quanto as de Estudo.
6. Uma coisa por vez: o card "Agora" mostra uma sugestão só.
7. Transições são difíceis: pausa entre blocos e aviso de hiperfoco.
8. Comemorar: sequência, marcos e toasts.

---

## 2. Estado atual (02/10/2026)

| Item | Situação |
|---|---|
| Última release publicada | **v1.2.0** (02/10/2026) |
| Branch | `main`, igual ao `origin/main` |
| Código da v1.2.0 | `60e0319`: cartão "Hoje" + semana com várias áreas por turno + combo "Matéria" |
| Código | ~5.300 linhas em 15 classes; `StudyTracker.java` sozinho tem ~3.350 |
| Testes automatizados | **Nenhum no repositório** (só testes manuais e scripts descartáveis por reflexão) |

### Releases

| Versão | Data | Conteúdo |
|---|---|---|
| v1.0.0 | 01/10/2026 | Primeiro `.exe` com Java embutido. **Obsoleta**: use a 1.0.1 ou posterior. |
| v1.0.1 | 01/10/2026 | Segurança: bloqueio de HTML vindo de dados, trava de instância sem rede e SHA-256 nas notas. |
| v1.0.2 | 02/10/2026 | Importar backup reconstrói a tela inteira (tema misturado e textos invisíveis). |
| v1.1.0 | 02/10/2026 | Dados em `%APPDATA%\FocaEstudo` com migração automática; aviso de versão nova; "Abrir pasta dos dados". |
| v1.2.0 | 02/10/2026 | Cartão "Hoje" na tela principal; semana com várias áreas por turno; "Preencher pelas metas" corrigido; "Agora" segue o plano; combo "Matéria" mantém a seleção. |

---

## 3. Linha do tempo

### Fase 1: criação (28/05/2026)

| Commit | O que entrou |
|---|---|
| `493d781` | Versão inicial: `StudyTracker` (~520 linhas), `StudyData`, gráfico de pizza, toast e `Executar_Programa.bat`. |
| `bfc5d7f`, `5dfbc07` | README inicial. |
| `6712b80` | **v2**, a primeira repaginação: `AppTheme`, `HistoryPanel`, `PieChartPanel`, `RoundedPanel`, `StudySession`, `StyledButton`. |
| `0e1c3f2` | Tema claro/escuro, `CustomTabs` (abas Cronômetro/Manual/Pomodoro), histórico, metas e correções no Pomodoro. |
| `b92afa6` | README com instruções de instalação. |

Nessa fase, os arquivos `.class` e os dados pessoais (`study_*.properties`) eram versionados.
Isso foi corrigido depois.

### Fase 2: reparos, 6 etapas de funcionalidades e repaginação visual (10/09/2026, `d97eb6c`)

Plano em `docs/PLANO.md` e `docs/PLANO_VISUAL.md`; registro detalhado em `docs/PROGRESSO.md`.

- **Etapa 0, reparos:** os dados passaram a ficar sempre num lugar só (antes o save mudava conforme o
  `.bat` usado e as matérias "sumiam"). O app só grava com a interface pronta, nunca grava um mapa
  vazio e faz `.bak` antes de cada gravação. Também ganhou trava de instância única, o donut deixou de
  ser espremido, o cronômetro ganhou "Zerar" e as metas passaram a aceitar `90`, `1h30` e `1:30`.
- **Etapa 1, cronômetro e UX:** atalhos (Espaço, Ctrl+H/T/G), tempo bruto × líquido, foco mínimo,
  Pomodoro em ciclos com contador, detecção de inatividade e diálogo Ajustes.
- **Etapa 2, incentivo:** sequência com folga, marcos 7/30/100/365, saldo semanal, comparação com o
  mês passado e heatmap no calendário.
- **Etapa 3, metas inteligentes:** dias da semana da meta, data de prova com contagem regressiva e
  meta adaptativa.
- **Etapa 4, histórico editável:** nota por sessão, editar/excluir sessão e arquivar matéria.
- **Etapa 5, dados robustos:** escrita atômica (`.tmp` + `fsync` + rename) e exportar/importar backup.
- **Etapa 6, telas novas:** Estatísticas, Tarefas (checklist), tipo de atividade, resumo semanal e
  lembrete diário na bandeja.
- **V1–V5, visual:** legibilidade, escala tipográfica, **Look&Feel Nimbus** com paleta claro/escuro,
  ícone do app, estados vazios, boa-vinda e toast animado.
- Dados pessoais saíram do git (`.gitignore`).

### Fase 3: foco em TDAH e áreas da vida (10/09/2026)

Plano em `docs/PLANO_TDAH.md`.

| Commit | Etapa | O que entrou |
|---|---|---|
| `8284996`, `057f9bb`, `2ce9ec7` | metas | Semana/mês seguem o nº de dias da meta; "Limpar metas"; "Repetir sua melhor semana"; cartão "Metas" e "Gerenciar metas". |
| `5ec8eb8` | A1 | Matéria vira **área com tipo**; cartão "Equilíbrio"; meta mínima semanal por tipo. |
| `ce1f9aa` | A2 | **Sub-focos** por área (tópicos, grupos musculares, hobbies), com presets por tipo. |
| `4d47845` | A3 | Cartão **"Agora"** com uma sugestão e "Começar"; anti-hiperfoco; pausa entre blocos. |
| `b1ca2ac` | A4 | **Meta mínima/ideal**; **"dia difícil"** (não cobra nem quebra a sequência). |
| `0557035` | A5 | **Energia** por sessão; rodízio de treino (grupo parado há mais tempo). |
| `19915ef`, `8bd2494` | A6 | Cobertura do plano na contagem de prova; **planejador semanal** (grade 7 × 3). |
| `3a68a4d` | A6+ | Metas e combo agrupados por tipo de área. |
| `afb98f0` | viz | Donut de dois anéis (tipo → área); `GaugeBar` com gradiente na cor da meta. |

### Fase 4: distribuição e segurança (01/10/2026)

| Commit | O que entrou |
|---|---|
| `cb7ff4b` | `empacotamento/empacotar.ps1` (jpackage → `.exe` + runtime reduzido → zip), `MakeIco.java`, `LEIA-ME.txt`. Releases v1.0.0. |
| `b3b006a` | `AppTheme.semHtml` (o Swing renderiza `<html>` em rótulos: um backup malicioso faria o app acessar a internet); trava por arquivo em vez de porta TCP; importação limitada a 20 MB; SHA-256 do zip; `bin/` sai do git. Release v1.0.1. |

### Fase 5: manutenção, publicação e revisão da semana (02/10/2026)

| Commit | O que entrou |
|---|---|
| `ace66ee` | Importar backup chama `rebuildUI()`: o tema do backup é aplicado na tela inteira, os ajustes voltam ao padrão antes de carregar e o planejador aberto é fechado. `docs/COMO_PUBLICAR.md`. Release **v1.0.2**. |
| `4087e32` | **Dados em `%APPDATA%\FocaEstudo`**: `migrateLegacyData()` copia uma vez da pasta antiga, sem sobrescrever, e deixa um `DADOS_MOVIDOS.txt`. **Aviso de versão nova** (HEAD em `/releases/latest`, 1×/dia, desligável). "Abrir pasta dos dados". Release **v1.1.0**. |
| `60e0319` | Combo "Matéria" mantém a seleção depois de `rebuildUI`. **Semana revista para TDAH:** cartão **"Hoje"** no topo da tela; o botão "Semana" saiu do topo; vários turnos por área e várias áreas por turno; "Preencher pelas metas" corrigido; "Agora" segue o plano do turno. Release **v1.2.0**. |
| (este doc) | `docs/HISTORICO.md`: histórico completo e base para a próxima fase. |

#### Bugs do planejador corrigidos no `60e0319`

O "Preencher pelos dias da meta" antigo:
1. ignorava áreas com meta em todos os dias (sem dias marcados);
2. duplicava a área a cada clique;
3. sobrescrevia o turno da Tarde quando o dia estava cheio;
4. só aceitava **1 área por turno**. Com 4 ou 5 metas no mesmo dia, algumas sumiam, e como a ordem
   das áreas muda a cada abertura do app, os "dias errados" variavam.

---

## 4. Funcionalidades atuais

### Tela principal
- **Topo:** total geral; botões Histórico, Estatísticas, Tarefas, Ajustes e Tema.
- **Esquerda, em cima:** donut de dois anéis (tipo → área) com legenda agrupada.
- **Esquerda, embaixo (com rolagem):**
  - **Hoje:** os 3 turnos do dia, com o turno atual destacado, "Começar", "· feito" e "editar semana";
  - **Agora:** uma sugestão e "Começar", mais o link "hoje tá difícil?";
  - **Equilíbrio:** tempo da semana por tipo;
  - **Metas:** agrupadas por tipo, com sub-focos;
  - **Progresso:** dia/semana/mês da área, sequência, saldo, comparação mensal e prova.
- **Direita:** calendário do mês com heatmap e hover.
- **Rodapé:** área + meta + barra; nota, sub-foco e energia; abas Cronômetro, Manual e Pomodoro;
  gerência (+ Área, Editar área, Cor, Zerar matéria, Arquivar, Deletar).

### Janelas e diálogos
- **Semana:** grade 7 dias × Manhã/Tarde/Noite; vários turnos por área; "Preencher pelas metas"
  (Completar/Refazer do zero); "Limpar tudo".
- **Histórico:** filtro, editar e excluir sessão.
- **Estatísticas:** por dia da semana, horário, mês, sub-foco e energia por horário.
- **Tarefas:** checklist por área com estimativa.
- **Metas:** diária ideal/mínima, semanal, mensal, dias da meta, data de prova e sugestões.
- **Ajustes:** foco mínimo, inatividade, pausa entre blocos, hiperfoco, lembrete, Pomodoro
  automático, backup (exportar, importar, abrir pasta) e atualizações.

### Automações
Resumo semanal no fim de semana; lembrete diário na bandeja; aviso de inatividade; aviso de
hiperfoco; aviso de desequilíbrio; marcos de sequência; aviso de versão nova.

### Atalhos
Espaço (iniciar/pausar o cronômetro), Ctrl+H (histórico), Ctrl+T (tema), Ctrl+G (metas).

---

## 5. Arquitetura

Java puro (Swing), **sem Maven/Gradle e sem bibliotecas externas**. Todas as classes ficam no pacote
padrão, em `src/`.

| Classe | Papel |
|---|---|
| `StudyTracker` | `JFrame` principal: monta a UI, guarda o estado, persiste, faz timers e regras de metas/sequência/sugestão. **Concentra quase tudo** (~3.350 linhas). |
| `AppTheme` | Tokens de cor e fonte, paletas claro/escuro, Nimbus, `semHtml`, ícone do app, estados vazios. |
| `PlannerPanel` | Janela da grade da semana; constantes de turno (`slotAt`, `SLOT`, `DOW_LONG`) e o ícone `dot`. |
| `CalendarPanel` | Calendário mensal com heatmap. |
| `PieChartPanel` | Donut de dois anéis (`Group`/`Slice`). |
| `HistoryPanel`, `StatsPanel`, `ChecklistPanel` | Janelas de Histórico, Estatísticas e Tarefas. |
| `StudyData`, `StudySession`, `ChecklistItem` | Modelos com `serialize`/`deserialize`. |
| `CustomTabs`, `RoundedPanel`, `StyledButton`, `GaugeBar` | Componentes visuais. |

### Fluxo
`main` → cria `DATA_DIR` → trava `focaestudo.lock` → `migrateLegacyData()` → Nimbus →
`new StudyTracker()` (`loadData`/`loadSessions`/`loadChecklist`) → `initializeUI()` (monta a UI,
`ready = true`, avisos, lembrete, timer do "Hoje", verificação de versão).
`updateUI()` redesenha os cartões e chama `saveData()`. `rebuildUI()` recria a tela inteira (tema,
importação) e preserva timers e área selecionada.

### Persistência
Pasta: `%APPDATA%\FocaEstudo` (`-Dfocaestudo.dados=<pasta>` para testes). Cada gravação é atômica e
deixa um `.bak`.

- **`study_data.properties`:** áreas (`Nome=minutos,corRGB`) e chaves com prefixo:
  - por área: `goal_` (ideal), `goalmin_`, `goalw_`, `goalm_`, `goaldays_` (ISO 1–7), `exam_` (ISO),
    `type_`, `subfocos_` (vírgulas), `streakbest_`;
  - por tipo: `typegoalw_<tipo>`;
  - semana: `plan_<dia>_<m|t|n>=Área1|Área2`;
  - estado e ajustes: `__theme__`, `__archived__`, `__hard_days__`, `__min_focus__`, `__idle_min__`,
    `__pomo_autocycle__`, `__reminder_hour__`, `__transition_min__`, `__hyperfocus_h__`,
    `__update_check__`, `__update_checked_day__`, `__streakbest_general__`, `__streak_milestone__`,
    `__week_summary_shown__`, `__reminder_shown_day__`, `__balance_nudge_week__`.
  - Chaves desconhecidas são preservadas (`unknownProps`). Assim, versões antigas e novas convivem.
- **`study_sessions.properties`:** `sN=área|min|timestamp|método|nota|sub-foco|energia`. Linhas
  antigas, com 4 a 6 campos, continuam válidas.
- **`study_checklist.properties`:** `área|feito(0/1)|estimativaMin|texto`.
- **Backup exportado:** um `.txt` com as seções `===== FOCAESTUDO DADOS/SESSOES/TAREFAS =====`.
- `.properties` é ISO-8859-1: os acentos das chaves aparecem como `\uXXXX`.

### Rede
Só o aviso de versão: um HEAD sem redirecionamento em `https://github.com/ErickDraber/FocaEstudo/releases/latest`.
A versão vem do cabeçalho `Location` (`/tag/vX.Y.Z`) e é comparada com `jpackage.app-version`.
Nenhum dado do usuário é enviado.

---

## 6. Build, execução e publicação

- **Desenvolvimento:** `Executar_Programa.bat` compila `src/` em `bin/` e abre o app. Exige JDK 17+
  (a máquina tem o JDK 25). Usa os **mesmos dados** do `.exe`.
- **Testar sem mexer nos dados reais:** `java -Dfocaestudo.dados=C:\teste -cp bin StudyTracker`.
- **Testar o aviso de versão pelo `.bat`:** acrescente `-Djpackage.app-version=1.0.0`.
- **Empacotar:** `powershell -ExecutionPolicy Bypass -File empacotamento\empacotar.ps1 -Versao X.Y.Z`
  gera `dist\FocaEstudo-Windows.zip` e o `.sha256`.
- **Publicar:** passo a passo em `docs/COMO_PUBLICAR.md`. Regras: tag `vX.Y.Z` igual ao `-Versao`; asset
  chamado `FocaEstudo-Windows.zip`; SHA-256 nas notas; conferir que o link `latest` responde 200.

---

## 7. Decisões técnicas (e por quê)

| Decisão | Motivo |
|---|---|
| Java Swing sem dependências | App simples de distribuir; o jpackage embute o runtime. |
| `.properties` em vez de JSON | Sem biblioteca de JSON, um parser à mão traria mais risco. Escrita atômica + `.bak` já é robusto. |
| Nimbus como Look&Feel | O L&F do Windows ignorava as cores do tema nos diálogos. |
| Trava por arquivo, não por porta TCP | Não abre ponto de rede e não conflita com outros programas. |
| `semHtml` em tudo que vem de dados | O Swing interpreta `<html>`, o que permite rastrear quem abre um backup malicioso. |
| Dados em `%APPDATA%` | Atualizar = extrair por cima; `.bat` e `.exe` compartilham dados; cada usuário do Windows tem os seus. |
| Migração por **cópia** | Nunca apagar dados do usuário. |
| Aviso de versão sem baixar nada sozinho | Menos risco e mais controle para o usuário; um auto-update exigiria assinatura de código. |
| Cartão "Hoje" na tela principal | Para TDAH, o que fica fora da vista é esquecido. A grade virou só editor. |

---

## 8. Problemas conhecidos e dívida técnica

**Bugs e limitações**
- **Glifos viram quadrado** na fonte do Swing no Windows: ▶ ✓ e emojis (abas "⏱ Cronômetro", "▶ Começar"
  do "Agora", ícones de tipo 📚💪). O código novo já usa texto ou ícones desenhados; o antigo ainda tem
  esses glifos.
- **A ordem das áreas muda a cada abertura** (`loadData` itera um `HashSet` do `Properties`). O combo
  agrupa por tipo, mas dentro do tipo a ordem é aleatória.
- `__archived__` e `subfocos_` usam vírgula como separador, mas o nome de uma área pode ter vírgula.
- Não existe meta por contagem de sessões ("3 de 4 treinos"), prevista na A5.
- Áreas só com meta **semanal** (sem diária) não entram no "Preencher pelas metas".
- Diálogos são `JOptionPane` (a `AppDialog` própria da V3 não foi feita, porque o Nimbus resolveu a cor).
- O app não é assinado digitalmente: o Windows SmartScreen avisa na primeira execução.

**Dívida técnica**
- `StudyTracker.java` com ~3.350 linhas mistura UI, regras e persistência.
- Nenhum teste automatizado; nenhum CI.
- Sem ferramenta de build (Maven/Gradle). As duas cópias do `Executar_Programa.bat` listam os arquivos à mão.
- ~~Os docs divergiam na versão mínima do Java~~: corrigido em 03/10/2026; todos usam Java 17+.
- O `Executar_Programa.bat` não passa `-encoding UTF-8` (funciona porque o JDK 18+ já usa UTF-8 por padrão).
- Os commits antigos (fase 1) têm `.class` e dados pessoais no histórico do git.

---

## 9. Sugestões para a próxima fase (mais estruturada)

Em ordem de retorno sobre o esforço:

1. **Organizar o trabalho:** usar GitHub Issues + Projects (quadro), labels (`bug`, `feature`, `tdah`,
   `ux`, `tech-debt`), milestones por versão e um `CHANGELOG.md` no formato *Keep a Changelog*.
   Branch por feature e PR para `main`, mesmo trabalhando sozinho.
2. **Build com Gradle ou Maven:** acaba com a lista manual de arquivos, permite testes, fixa o Java 17
   e facilita o CI.
3. **Testes automatizados (JUnit 5)**, começando pelo que é puro e crítico:
   - serialize/deserialize de sessões, tarefas e `study_data` (incluindo formatos antigos);
   - sequência com folga e dia difícil; metas semana/mês; `autofillWeekPlan`; migração de dados;
     `compareVersions`.
4. **CI no GitHub Actions** (Windows): compilar e testar a cada push; empacotar e anexar o zip na tag.
5. **Quebrar o `StudyTracker`**, sem mudar o comportamento e já com testes:
   - `DataStore` (persistência);
   - `GoalService`/`StreakService` (regras);
   - `SuggestionService` (o "Agora");
   - `WeekPlan` (semana);
   - um painel por cartão.
6. **Corrigir a ordem das áreas** (guardar a ordem explicitamente) e trocar os glifos restantes.
7. **Assinatura de código** do `.exe` (certificado), para tirar o aviso do SmartScreen.
8. Funções em aberto do produto: meta por contagem de sessões; áreas só com meta semanal na semana;
   arrastar e soltar na grade; notificação ao mudar de turno.

---

## 10. Ambiente local (máquina do desenvolvedor)

- **Código e repositório:** `C:\Users\Erick Draber\Desktop\Projeto Rastreador de Estudos`
  (remoto `https://github.com/ErickDraber/FocaEstudo`).
- **Dados reais:** `%APPDATA%\FocaEstudo`. Na raiz do repositório ainda há uma cópia antiga
  (`study_*.properties` + `DADOS_MOVIDOS.txt`), que é ignorada pelo git.
- **`C:\Users\Erick Draber\Desktop\FocaEstudo`:** zip antigo (v1.0.x) extraído por cima de um projeto
  Android antigo (`com.example.focaestudo`). Não é necessário; vale extrair a versão nova numa pasta
  limpa e decidir o destino dessa.
- **Pendente:** remover o `.git` criado por engano em `C:\Users\Erick Draber` (sem commits, remoto
  `TSP-AED3`). O Claude Code bloqueou a remoção automática; o comando é
  `Remove-Item "C:\Users\Erick Draber\.git" -Recurse -Force`.
- Ferramentas: JDK 25 (com jpackage), GitHub CLI em `C:\Program Files\GitHub CLI\gh.exe`.
- Arquivos de passagem de sessão, **fora do git:** `PROXIMOS_PASSOS.md` e `SESSAO_2026-10-02.md`.
