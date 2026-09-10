# FocaEstudo — Plano "áreas da vida / TDAH"

Ver `docs/PILARES.md` para os princípios. Mesmo formato dos outros planos: mais rápido/seguro primeiro,
cada etapa fecha com compilar + testar (app fechado) + atualizar `PROGRESSO.md` + commit + push.
Tempos = horas de trabalho focado.

---

## Etapa A1 — Áreas com tipo + resumo por tipo + metas de equilíbrio  (~5–8 h)

- Toda "matéria" vira **área** com **tipo**: Estudo · Físico · Lazer · Trabalho · Outro (ícone + cor).
- Escolher o tipo ao criar/editar a área. Selo do tipo no combo, no cartão Metas e no Histórico.
- Cartão **"Equilíbrio"** perto do cartão Metas: barras do tempo da semana por tipo.
- **Meta mínima semanal por tipo** (ex.: ≥ 3h de Lazer, ≥ 4 treinos). Aviso gentil se a semana
  está com 0 num tipo que tem meta.
- Persistência: `type_<área>=fisico`, `typegoalw_<tipo>=180`.

## Etapa A2 — Sub-focos configuráveis por área  (~5–7 h)

- Cada área tem uma lista de **sub-focos** (tópicos / grupos musculares / hobbies).
- Diálogo para gerenciar os sub-focos da área. Presets por tipo (Físico → Peito/Costas/Perna/…).
- O campo "Tipo:" da linha de registro passa a mostrar os sub-focos da área ativa (+ "gerenciar…").
- Histórico e Estatísticas quebram por sub-foco.
- Persistência: `subfocos_<área>=Peito,Costas,Perna` (sessão já guarda o sub-foco no campo `kind`).

## Etapa A3 — "Agora" + começar rápido + transição  (~4–6 h)

- Cartão **"Agora"**: UMA sugestão (meta do dia não batida + prova mais próxima + sub-foco/treino
  atrasado) + botão grande **"Começar"** que seleciona e liga o cronômetro.
- **Timer de transição** ao fim de um bloco ("pausa de 5 min — levanta, água").
- **Anti-hiperfoco**: cronômetro > X h seguidas numa área → aviso pra respirar.

## Etapa A4 — Flexibilidade: meta em faixa + dia difícil  (~4–6 h)

- Meta **mínimo/ideal**: bater o mínimo já mantém a sequência; ideal é o alvo cheio.
  Persistência: `goalmin_<área>` (o `goal_` atual = ideal).
- Botão **"Dia difícil"**: marca o dia — não quebra sequência, não gera cobrança.
  Persistência: `__hard_days__=2026-09-10,2026-09-12`.
- Varredura de linguagem dos avisos (positiva).

## Etapa A5 — Rodízio (academia) + energia  (~4–6 h)

- Áreas de Físico: chips de grupos treinados por sessão; "faz N dias sem treinar X";
  frequência por grupo nas Estatísticas.
- **Meta por contagem** (nº de sessões), não só tempo: "3 de 4 treinos essa semana".
- **Energia** rápida ao salvar (😴 🙂 🔥) → campo novo na sessão; Estatística "melhor energia por horário/tipo".

## Etapa A6 — Planejador semanal  (~6–9 h)  ✅ concluída

- Grade seg–dom × blocos; colocar áreas nos horários. Visual, não obriga.
- Contagem regressiva de prova/entrega com barra de "quanto do plano já cobriu".

**Feito:** `PlannerPanel` (botão "Semana" no topo) — grade 7 dias × Manhã/Tarde/Noite,
clique na célula escolhe a área (nas cores da área), "Preencher pelos dias da meta"
usa os `goalDays`, "Limpar tudo". Persiste em `plan_<dia>_<slot>`. A contagem
regressiva de prova com "plano X/Y" (itens do checklist) já entrou na A6-parcial.

---

## Resumo

| Etapa | Foco | Tempo |
|---|---|---|
| A1 | Áreas com tipo + equilíbrio | 5–8 h |
| A2 | Sub-focos por área | 5–7 h |
| A3 | "Agora" + começar rápido + transição | 4–6 h |
| A4 | Meta em faixa + dia difícil | 4–6 h |
| A5 | Rodízio academia + energia | 4–6 h |
| A6 | Planejador semanal | 6–9 h |
| | **Total** | **~28–42 h** |
