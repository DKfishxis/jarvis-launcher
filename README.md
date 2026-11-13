# J.A.R.V.I.S. Mark XLII — Launcher para TV Box (MXQ Pro)

Launcher em WebView que carrega **só o painel local** (`painel_tv.html`). Kick e Twitch abrem
direto nos **apps oficiais** por Intent nativo; nenhuma live roda dentro do WebView.

## Como cada botão funciona

| Botão | O que faz |
|---|---|
| Kick ▶ (canal) | `ACTION_VIEW https://kick.com/<canal>` restrito ao pacote `com.kick.mobile`. Se o app não aceitar o link, abre a tela inicial do app. |
| Kick 📹 VODs | Igual, com `https://kick.com/<canal>/videos`. |
| Twitch | Abre o app `tv.twitch.android.app` (tela inicial). |
| 🌐 Web | Abre a mesma página no Chrome (ou no navegador padrão). Só aceita https de kick.com / twitch.tv. |
| App não instalado | Mostra um aviso e abre a versão web no navegador. |

Esquema interno usado pelo HTML: `jarvis://kick/<canal>`, `kickvod/<canal>`, `kickapp`,
`twitchapp`, `twitch/<canal>`, `web?u=<https codificado>`. Nomes de canal são validados em Java
(`[A-Za-z0-9_-]`, até 40 caracteres).

## Android 7.1 (API 25)

- `minSdk 21`, `targetSdk 32`: o mesmo APK roda no 7.1 e em versões novas.
- A tag `<queries>` do manifest (com `com.kick.mobile` e `tv.twitch.android.app`) só existe a partir
  do Android 11; no 7.1 o sistema a **ignora sem erro**, e lá não há restrição de visibilidade de apps.
  Ela fica no manifest para o APK também funcionar se você trocar de aparelho.
- O app não pede permissão de INTERNET (o WebView só lê arquivos locais).
- O código evita recursos novos de JavaScript/CSS, por causa do WebView antigo do 7.1.

## Compilar na nuvem (GitHub Actions) e baixar pelo celular

1. Crie um repositório no GitHub (pode ser privado) e envie **todo o conteúdo** desta pasta,
   incluindo `.github/workflows/build.yml`.
   - Pelo celular, o upload do site do GitHub costuma ignorar pastas que começam com ponto.
     Se `.github` não subir, use **Add file → Create new file**, digite o nome
     `.github/workflows/build.yml` e cole o conteúdo do `build.yml` entregue junto.
2. A cada push em `main`/`master` (ou em **Actions → Build APK → Run workflow**) o GitHub compila.
3. Baixe o APK em **Releases** (link direto para `jarvis-launcher.apk`; é a forma mais fácil no
   celular). Ele também fica em **Actions → execução → Artifacts** (vem dentro de um .zip).
4. O APK é *debug* (assinado com a chave de debug): instala normalmente na TV Box.

Para compilar no PC: abra a pasta no Android Studio e use *Build > Build APK(s)*,
ou `gradle assembleDebug` (Gradle 8.2+ e JDK 17).

## Instalar na TV Box

- **Pendrive:** copie o APK e abra pelo gerenciador de arquivos; permita "fontes desconhecidas".
- **ADB:** `adb connect IP:5555` e `adb install -r jarvis-launcher.apk`.
- **Tela inicial:** aperte HOME no controle → escolha **J.A.R.V.I.S.** → "Sempre".

## Controle remoto

- Setas: navegação espacial feita no JavaScript (vai para o botão mais próximo na direção apertada).
- OK/Enter: ativa o item em foco; no campo de busca, abre a live do canal digitado.
- VOLTAR: na tela Kick volta ao menu; na Home não faz nada (somos o launcher).

## Pacotes usados (confira em Configurações > Apps)

`com.kick.mobile`, `tv.twitch.android.app`, `com.google.android.youtube.tv` (cai para
`com.google.android.youtube` e SmartTube), `com.unitvnet.tvvod`, `com.instantbits.cast.receiver`.

## Não verificado

Este projeto não foi compilado nem testado em aparelho real. Em especial: não dá para confirmar
daqui se o app `com.kick.mobile` registra links `kick.com/<canal>` (se não registrar, o fallback abre
a tela inicial do app). O app da Kick é de celular (interface de toque): no controle remoto, a
navegação *dentro* dele depende do próprio app.
