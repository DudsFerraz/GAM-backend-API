# API do GAM Piracicaba

> English version: [README.md](README.md)

API backend da plataforma de gestão do GAM Piracicaba, um projeto de software desenvolvido voluntariamente para apoiar a missão, a organização e o trabalho pastoral do GAM Piracicaba.

O GAM Piracicaba é um grupo missionário juvenil salesiano de Piracicaba, Brasil. O grupo realiza atividades sociais, educativas e evangelizadoras, como o Oratorio semanal, ações missionárias e apoio às semanas missionárias dos colégios salesianos.

## Propósito

Este projeto está sendo desenvolvido voluntariamente para ajudar o GAM Piracicaba a organizar suas operações internas com mais cuidado e confiabilidade.

A API tem como objetivo apoiar:

- gestão de membros e contas;
- controle de acesso baseado em papéis;
- cadastro e busca de eventos;
- registros de eventos de Oratorio e Missa;
- cadastro de Oratorianos;
- controle de presenças;
- cadastro de locais;
- autenticação com tokens de acesso e renovação;
- persistência auditável com exclusão lógica.

O objetivo não é apenas criar software, mas reduzir atritos operacionais para que os voluntários possam dedicar mais energia à missão, à formação, ao serviço e à comunidade.

## Contexto do Projeto

Para conhecer o grupo que motiva este sistema, veja:

- [Sobre o GAM Piracicaba](docs/about-gam/gam-piracicaba.pt-BR.md)

A terminologia canônica do projeto é mantida no [vocabulário ubíquo do GAM](docs/ubiquitous-language.md).

## Tecnologias

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- MapStruct
- Lombok
- autenticação JWT
- Maven
- Testcontainers
- REST Assured
- suítes de teste do JUnit Platform

## Arquitetura Atual

O projeto é uma API REST em Spring Boot organizada em áreas de domínio orientadas por funcionalidade, como contas, membros, eventos, Oratorios, Missas, locais, presenças, Oratorianos, RBAC, segurança e infraestrutura compartilhada.

Atualmente, o código usa camadas e padrões explícitos, incluindo controllers web, casos de uso de aplicação, DTOs/RDTOs, mappers, loaders de entidades e domínio, objetos de domínio, entidades de persistência, repositórios, exceções customizadas, especificações dinâmicas de busca, especificações de segurança, auditoria, log de atividades, tokens de renovação e exclusão lógica.

Convenções de arquitetura e implementação estão documentadas em:

- [Guias de Software](docs/software-guidelines/)
- [Guias de Documentação](docs/documentation-guidelines/README.md)

## Como Executar Localmente

Para configuração local, inicialização do backend, comandos Maven, comandos Docker Compose e verificações de dependências, consulte [Executando o Sistema](docs/dev-guidelines/running-the-system/README.md).

## Documentação

A documentação do projeto está organizada em `docs/`.

- `docs/about-gam/` descreve o contexto social e religioso por trás do projeto.
- `docs/dev-guidelines/` contém guias práticos para desenvolvedores humanos executarem o backend, usarem Docker e Maven, inspecionarem dependências e trabalharem com agentes neste projeto.
- `docs/documentation-guidelines/` define o sistema de documentação do projeto, incluindo Requirement Specifications, ADRs, diagramas, notas de OpenAPI, vídeos, fluxo de agentes e regras de fonte da verdade.
- `docs/software-guidelines/` registra convenções de implementação do backend para organização de pacotes, controllers, serviços de aplicação, modelos de domínio, persistência, migrations, mappers, exceções, interfaces, especificações de busca, RBAC, logs de auditoria e testes.
- `docs/ubiquitous-language.md` é o glossário global dos termos canônicos de domínio do GAM.
- `docs/ideas/` contém notas exploratórias que ainda não são requisitos aceitos.

## Status

Este é um projeto voluntário em andamento. A base já possui fundações importantes de backend, mas o produto e a arquitetura ainda evoluem conforme as necessidades reais do GAM Piracicaba ficam mais claras.

## Motivação

O software deste projeto é um meio de serviço. Seu valor está em ajudar uma comunidade voluntária a cuidar de pessoas, lembrar compromissos, organizar eventos e sustentar uma presença missionária salesiana com mais clareza e continuidade.
