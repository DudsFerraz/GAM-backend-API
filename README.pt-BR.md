# API do GAM Piracicaba

> English version: [README.md](README.md)

API backend da plataforma de gestão do GAM Piracicaba, um projeto de software desenvolvido voluntariamente para apoiar a missão, a organização e o trabalho pastoral do GAM Piracicaba.

O GAM Piracicaba é um grupo missionário juvenil salesiano de Piracicaba, Brasil. O grupo realiza atividades sociais, educativas e evangelizadoras, como o oratório semanal, ações missionárias e apoio às semanas missionárias dos colégios salesianos.

## Propósito

Este projeto está sendo desenvolvido voluntariamente para ajudar o GAM Piracicaba a organizar suas operações internas com mais cuidado e confiabilidade.

A API tem como objetivo apoiar:

- gestão de membros e contas;
- controle de acesso baseado em papéis;
- cadastro e busca de eventos;
- registros de oratório e missa;
- controle de presenças;
- cadastro de locais;
- autenticação com tokens de acesso e renovação;
- persistência auditável com exclusão lógica.

O objetivo não é apenas criar software, mas reduzir atritos operacionais para que os voluntários possam dedicar mais energia à missão, à formação, ao serviço e à comunidade.

## Contexto do Projeto

Para conhecer o grupo que motiva este sistema, veja:

- [Sobre o GAM Piracicaba](docs/about-gam/gam-piracicaba.pt-BR.md)

## Tecnologias

- Java 21
- Spring Boot 3.5.7
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- MapStruct
- Lombok
- autenticação JWT
- Maven

## Arquitetura Atual

O projeto é uma API REST em Spring Boot com áreas de domínio orientadas por funcionalidade, como contas, membros, eventos, locais, presenças e RBAC.

Atualmente, o código usa camadas e padrões explícitos, incluindo controllers, serviços de caso de uso, repositórios, DTOs, mappers, entidades de persistência, objetos de domínio, exceções customizadas, especificações dinâmicas de busca, auditoria e exclusão lógica.

Notas de arquitetura e direcionamento de refatoração estão documentados em:

- [Revisão de Arquitetura do Projeto](docs/refactor/project-refactor-roadmap.md)
- [Roadmap de Refatoração da Arquitetura](docs/refactor/architecture-refactor-roadmap.md)

## Como Executar

Pré-requisitos:

- Java 21
- Maven, ou o Maven Wrapper incluído no projeto
- PostgreSQL

Crie um arquivo de configuração local a partir do exemplo:

```powershell
Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

Preencha os valores de datasource, chave JWT, logs SQL e CORS em `application-local.properties`.

Execute a suíte de testes:

```powershell
.\mvnw.cmd test
```

Execute a aplicação:

```powershell
.\mvnw.cmd spring-boot:run
```

Por padrão, a aplicação usa o profile Spring `local` e valida o schema do banco por meio de migrations gerenciadas pelo Flyway.

## Documentação

A documentação do projeto está organizada em `docs/`.

- `docs/about-gam/` descreve o contexto social e religioso por trás do projeto.
- `docs/refactor/` registra análises de arquitetura e melhorias planejadas.

## Status

Este é um projeto voluntário em andamento. A base já possui fundações importantes de backend, mas o produto e a arquitetura ainda evoluem conforme as necessidades reais do GAM Piracicaba ficam mais claras.

## Motivação

O software deste projeto é um meio de serviço. Seu valor está em ajudar uma comunidade voluntária a cuidar de pessoas, lembrar compromissos, organizar eventos e sustentar uma presença missionária salesiana com mais clareza e continuidade.
