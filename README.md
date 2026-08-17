# Raidborn: Ally with Illagers

> Join the wrong side

Mod para Minecraft 1.20.1 (Forge) que coloca o jogador do lado dos illagers: recrutamento,
assentamentos, artefatos e transmutação.

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Forge** | 47.4.9 |
| **Java** | 17 |
| **Versão do mod** | 0.1-1.20.1 |
| **Autores** | Randomcara7 e I_DRAW_THINGS |
| **Licença** | [MIT](LICENSE) |

## Dependências

Obrigatórias em runtime:

- **BentosLib** `0.1-1.20.1` — biblioteca interna do projeto, veja o passo de build abaixo
- [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) `5.14.1+1.20.1`

Opcionais (integração):

- [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) `1.20.1-84.1`
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) `15.20.0.106`

## Build

O Raidborn depende de `net.randomcara.bentoslib:bentoslib`, que é resolvida via `mavenLocal()`.
**Publique a BentosLib antes de buildar o Raidborn**, senão o Gradle não resolve a dependência:

```bash
cd ../bentoslib && ./gradlew publishToMavenLocal
```

Com a biblioteca publicada:

```bash
./gradlew build
```

O jar sai em `build/libs/`.

> O `gradlew` deste projeto é um script bootstrap próprio: ele baixa o Gradle 8.8 para
> `.gradle/wrapper/` na primeira execução, então não existe `gradle-wrapper.jar` versionado.

## Rodar em desenvolvimento

```bash
./gradlew runClient
```

```bash
./gradlew runServer
```

O diretório de trabalho das duas tasks é `run/`, que não é versionado.

## Estrutura do código

```
net/randomcara/raidborn/
├── core/            registries, config, utilitários e camada de compat
├── content/         itens, entidades, efeitos e artefatos
├── gameplay/        recrutamento, assentamentos, ataques, banners, trocas e loot
├── transmutation/   bloco, block entity, menu e receitas de transmutação
├── world/           geração e dados de assentamento
├── client/          renderers, modelos e HUD
├── integration/     compat com JEI
└── mixin/           mixins (config em raidborn.mixins.json)
```
