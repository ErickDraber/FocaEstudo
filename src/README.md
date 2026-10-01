# FocaEstudo 📚

Rastreador de estudos com foco, desenvolvido em Java Swing para desktop.

---

## Funcionalidades

- ⏱ Cronômetro de sessões de estudo
- 🍅 Timer Pomodoro com pausar/retomar
- ✏ Adição manual de tempo
- 📊 Gráfico donut com distribuição por matéria
- 📋 Histórico de sessões com filtro
- 🎯 Metas diárias por matéria
- 🎨 Cores personalizáveis por matéria
- 🌙 Tema claro e escuro

---

## Como instalar

### Windows (recomendado — não precisa instalar Java)

1. Baixe o programa: **[FocaEstudo-Windows.zip](https://github.com/ErickDraber/FocaEstudo/releases/latest/download/FocaEstudo-Windows.zip)**
2. Clique com o botão direito no arquivo baixado → **Extrair Tudo...** (em Documentos ou na Área de Trabalho)
3. Abra a pasta **FocaEstudo** e dê dois cliques em **`FocaEstudo.exe`**
4. Se aparecer "O Windows protegeu o computador", clique em **Mais informações → Executar assim mesmo** (só na primeira vez)

> ⚠️ Não abra direto de dentro do `.zip` nem coloque a pasta em "Arquivos de Programas" — os dados são salvos na própria pasta do programa.

Instruções completas ficam no `LEIA-ME.txt` dentro do zip.

### A partir do código-fonte

Requer o **JDK 17 ou superior** (ex.: [Eclipse Temurin](https://adoptium.net/pt-BR/)).

- Clone o repositório e dê dois cliques em **`Executar_Programa.bat`** — ele compila e abre o programa.
- Para gerar o `.zip` com Java embutido: `powershell -ExecutionPolicy Bypass -File empacotamento\empacotar.ps1 -Versao 1.0.0` (saída em `dist\`).

---

## Como usar

1. Na primeira execução, digite suas matérias separadas por vírgula
2. Selecione a matéria ativa no menu **Matéria**
3. Use uma das três abas para registrar tempo:
   - **Cronômetro** — inicia e para quando quiser, depois clica em Salvar
   - **Manual** — insere horas e minutos diretamente
   - **Pomodoro** — define o tempo de foco e descanso e deixa o timer controlar
4. Os dados são salvos automaticamente ao fechar

---

## Tecnologias

- Java 17+
- Java Swing
