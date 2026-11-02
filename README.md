# bands-api

API REST para o desafio Backend Java da DWS. A aplicação consulta o catálogo de bandas em uma API externa e oferece listagem, pesquisa por nome, ordenação e consulta de detalhes.

## Funcionalidades e limites dos dados

- A listagem inclui `id`, `name`, `image` e `numPlays`.
- A pesquisa por nome é parcial, ignora maiúsculas/minúsculas e remove espaços externos do termo.
- A listagem pode ser ordenada por `name` ou `numPlays` em ordem crescente ou decrescente.
- Os detalhes incluem `genre`, `biography` e `albums`.
- `albums` contém somente UUIDs, conforme o contrato observado da API externa. Esta aplicação não resolve nomes ou detalhes de álbuns.
- A biografia é recebida do serviço externo como texto e pode conter HTML. Clientes devem tratá-la como conteúdo externo não confiável ao renderizar HTML.
- Não há paginação.

## Tecnologias

- Java 21
- Spring Boot 3.5.16
- Maven
- Spring Web e `RestClient`
- Spring Cache e Caffeine
- JUnit 5, Mockito e Spring Boot Test

## Arquitetura

O projeto usa uma estrutura simples inspirada em Ports & Adapters:

- `domain`: modelo `Band` e porta `BandCatalogPort`, sem dependência de Spring ou HTTP.
- `application`: `BandService` implementa busca, ordenação e consulta por UUID sobre a porta.
- `infrastructure/client`: configuração e adapter `BandApiClient` para consumir a API externa com `RestClient`.
- `infrastructure/cache`: `CachedBandCatalogAdapter` decora a porta e armazena o catálogo completo em Caffeine.
- `web`: controller, DTOs de resposta e tratamento global de erros.

O serviço depende da porta. O adapter com cache é selecionado como implementação principal; ele delega as cargas ao adapter HTTP.

```text
src/main/java/br/com/challenge/bands/
├── domain/{model,port}
├── application/service
├── infrastructure/{client,cache}
└── web/{controller,dto,exception}
```

## Pré-requisitos

- JDK 21
- Maven 3.6.3 ou superior

Confira o Java ativo com `java -version`. O projeto configura `release` 21 pelo parent do Spring Boot.

## Configuração

| Variável | Padrão | Finalidade |
|---|---|---|
| `BANDS_API_URL` | `https://bands-api.vercel.app/api/bands` | URL usada pelo `RestClient` |
| `BANDS_API_CONNECT_TIMEOUT` | `2s` | Timeout de conexão |
| `BANDS_API_READ_TIMEOUT` | `5s` | Timeout de leitura |
| `BANDS_CACHE_TTL` | `5m` | Tempo de expiração após gravação |
| `BANDS_CACHE_MAXIMUM_SIZE` | `100` | Limite de entradas do cache Caffeine |

A aplicação escuta na porta `8080` por padrão. Para mudar a porta, use a propriedade Spring `server.port`.

## Compilar, testar e executar

```bash
mvn clean verify
mvn spring-boot:run
```

O build também produz `target/bands-api-0.0.1-SNAPSHOT.jar`, que pode ser iniciado com:

```bash
java -jar target/bands-api-0.0.1-SNAPSHOT.jar
```

## Endpoints REST

### `GET /api/bands`

Lista bandas. Os parâmetros são opcionais:

| Parâmetro | Valores | Padrão |
|---|---|---|
| `query` | Texto parcial do nome | Sem filtro |
| `sortBy` | `name`, `numPlays` | `name` |
| `direction` | `asc`, `desc` | `asc` |

Exemplos:

```bash
curl 'http://localhost:8080/api/bands'
curl 'http://localhost:8080/api/bands?query=nickelback'
curl 'http://localhost:8080/api/bands?sortBy=name&direction=asc'
curl 'http://localhost:8080/api/bands?sortBy=numPlays&direction=desc'
```

Resposta de listagem:

```json
[
  {
    "id": "2386cd66-e923-4e8e-bf14-2eebe2e9b973",
    "name": "3 Doors Down",
    "image": "https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png",
    "numPlays": 4471468
  }
]
```

O exemplo mostra a estrutura observada; os dados são fornecidos pela API externa e podem mudar.

### `GET /api/bands/{id}`

Retorna detalhes da banda identificada por UUID:

```bash
curl 'http://localhost:8080/api/bands/e2c00c56-8365-4160-9f40-a64682917633'
```

Resposta de exemplo:

```json
{
  "id": "e2c00c56-8365-4160-9f40-a64682917633",
  "name": "Nickelback",
  "image": "https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png",
  "genre": "rock",
  "biography": "Nickelback is a rock band ...",
  "numPlays": 17605491,
  "albums": [
    "3c5794a0-d913-390d-ab24-6762af38c112"
  ]
}
```

Os valores são exemplos da estrutura observada; `albums` é uma lista de UUIDs, não uma lista de objetos de álbum.

## Cache

O adapter usa o padrão cache-aside com Spring Cache e Caffeine. Na primeira consulta, `CachedBandCatalogAdapter` chama `BandApiClient` e o catálogo completo é armazenado na entrada `bandsCatalog`. Enquanto a entrada estiver válida, busca, ordenação e consulta por UUID operam sobre o mesmo catálogo em memória, sem novas chamadas à API externa.

O TTL padrão é cinco minutos e o máximo configurado é 100 entradas. O cache é local à instância: reiniciar o processo o limpa e instâncias diferentes não compartilham conteúdo. Após a expiração, a próxima consulta volta a carregar o catálogo sincronamente. Se o serviço externo falhar nessa carga, a falha é propagada; não há dados expirados como fallback, retry nem invalidação manual.

## Erros HTTP

Erros usam JSON com `timestamp`, `status`, `error`, `message` e `path`.

| Status | Situação |
|---|---|
| 400 | UUID, `sortBy` ou `direction` inválidos |
| 404 | UUID de banda não encontrado |
| 502 | Resposta externa inválida ou não desserializável |
| 503 | Serviço externo indisponível ou resposta HTTP de erro |
| 504 | Timeout externo identificável |
| 500 | Erro interno inesperado |

As mensagens de falhas upstream são genéricas; stack traces e payloads externos não são enviados ao consumidor.

## Testes

Execute toda a suíte com:

```bash
mvn clean verify
```

Os testes unitários isolam o serviço e o adapter. Os testes de cache usam o proxy real do Spring e um ticker controlável. Os testes E2E iniciam a aplicação em porta local aleatória e usam um servidor HTTP simulado para a API externa; não dependem de acesso à internet.

## Decisões técnicas e trade-offs

- O catálogo é pequeno na amostra observada e a API externa entrega a lista completa; por isso, pesquisa, ordenação e busca por ID são locais e não há paginação.
- A porta mantém o serviço de aplicação independente do protocolo HTTP. O adapter usa `RestClient`, timeouts configuráveis e mapeia falhas para exceções de integração.
- O cache local reduz chamadas repetidas, mas não coordena instâncias. Uma futura implantação com múltiplas instâncias aceitaria catálogos temporariamente diferentes até cada cache expirar.
- `numPlays` é o campo disponível usado para ordenar por reproduções; não existe um campo separado de popularidade no contrato observado.
- IDs de álbuns não fornecem nomes ou outros detalhes; adicionar esses dados exigiria um endpoint externo documentado e verificado.
