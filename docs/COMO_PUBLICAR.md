# Como alterar e publicar o FocaEstudo

## A ideia

Existe **um só código-fonte**: a pasta `src\` deste repositório. O `.bat` e o `.exe` saem dele.

| | `Executar_Programa.bat` | `FocaEstudo-Windows.zip` (release) |
|---|---|---|
| Para quem | Só para quem desenvolve | Para quem usa |
| O que faz | Compila `src\` e abre o app na hora | Leva o app já compilado, com Java embutido |
| Precisa de Java instalado | Sim (JDK) | Não |
| Como é gerado | Já existe, é só rodar | `empacotamento\empacotar.ps1` |

Ninguém altera o `.exe` diretamente. As mudanças são feitas em `src\`, testadas com o `.bat` e só depois viram uma nova release.

## Passo a passo de cada mudança

1. **Alterar** os arquivos em `src\`.
2. **Testar** rodando `Executar_Programa.bat`. Ele usa os seus dados de verdade (os mesmos do `.exe`), então faça antes um backup em *Ajustes > Backup > Exportar dados...*.
3. **Commit e push** em `main`:
   ```powershell
   git add src\Arquivo.java
   git commit -m "fix(area): o que mudou"
   git push
   ```
4. **Escolher o número da versão** (`MAIOR.MENOR.CORREÇÃO`):
   - correção de bug: `1.0.1` → `1.0.2`;
   - função nova: `1.0.2` → `1.1.0`;
   - mudança que quebra dados ou backups antigos: `1.1.0` → `2.0.0`.
5. **Gerar o zip:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File empacotamento\empacotar.ps1 -Versao X.Y.Z
   ```
   No final, o script mostra o SHA-256.
6. **Testar o zip** antes de publicar: extraia `dist\FocaEstudo-Windows.zip` numa pasta nova e abra o `FocaEstudo.exe`. Se não quiser usar os dados reais, abra pelo PowerShell com `$env:APPDATA="C:\pasta\de\teste"; .\FocaEstudo.exe`.
7. **Publicar** (cole o SHA-256 nas notas):
   ```powershell
   & "C:\Program Files\GitHub CLI\gh.exe" release create vX.Y.Z dist\FocaEstudo-Windows.zip `
       --target main --title "FocaEstudo X.Y.Z" --latest `
       --notes "O que mudou.`n`nSHA-256: <hash>"
   ```
   O arquivo precisa se chamar `FocaEstudo-Windows.zip`, senão o link fixo deixa de funcionar.
8. **Conferir o link** (deve retornar `200`):
   ```
   curl -sL -o NUL -w "%{http_code}" https://github.com/ErickDraber/FocaEstudo/releases/latest/download/FocaEstudo-Windows.zip
   ```

## Link para divulgar

Sempre este, porque ele aponta para a versão mais recente:

https://github.com/ErickDraber/FocaEstudo/releases/latest/download/FocaEstudo-Windows.zip

A partir da 1.1.0, o app consulta o GitHub (no máximo 1× por dia) e avisa quando sai uma versão nova. Ele só **avisa**: a pessoa baixa o zip e extrai sozinha. O aviso compara o número da release mais recente com o `-Versao` usado no `empacotar.ps1`, então **a tag precisa ser `vX.Y.Z` e o `-Versao` precisa ser o mesmo `X.Y.Z`**. Pelo `.bat`, o aviso nunca aparece.

## Onde ficam os dados

Desde a 1.1.0, os dados (`study_*.properties`) ficam em `%APPDATA%\FocaEstudo`, um por usuário do Windows. O `.bat` e o `.exe` usam a **mesma** pasta, e a trava `focaestudo.lock` impede que os dois fiquem abertos ao mesmo tempo.

- **Migração:** na primeira abertura, se `%APPDATA%\FocaEstudo` ainda não tem dados, o app **copia** os `study_*.properties` da pasta do programa e deixa um `DADOS_MOVIDOS.txt` lá. Ele nunca sobrescreve dados que já estão em `%APPDATA%`.
- **Testar sem mexer nos dados reais:** `java -Dfocaestudo.dados=C:\pasta\de\teste -cp bin StudyTracker`.
- **Testar o aviso de versão pelo `.bat`:** acrescente `-Djpackage.app-version=1.0.0` (finge ser uma versão antiga).
