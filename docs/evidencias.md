# Registro de Evidências da Pipeline DevSecOps - BancoFácil

Este documento registra a execução incremental dos gates de segurança na pipeline do GitHub Actions para a disciplina de Segurança da Informação.

Repositório: `jeffSv16/seguracao-info`

---

## 1. Gate 1: Detecção de Segredos (Gitleaks)

- **Job**: `secret-scan`
- **Execução GitHub Actions**: [Run 36374123293](https://github.com/jeffSv16/seguracao-info/actions/runs/36374123293)
- **Status do Gate**: ❌ **FALHOU** (Código de saída: 1)
- **Status dos Gates Subsequentes**: ⚪ Pulados (`unit-tests`, `sast`, `sca`, `dockerfile-lint`, `build-and-push`)

### Detalhes da Falha
- **Ferramenta**: Gitleaks v8.18.4
- **Problema**: Secret Sprawl (Credenciais e segredos de API gravados diretamente no código-fonte)
- **Arquivo Afetado**: `src/main/java/com/unifebe/devsecops/config/AppConfig.java`
- **Linhas e Regras**:
  - Linha 14: Regra `aws-access-token` (Chave de exemplo AWS)
    - Fingerprint: `9d7b54140c56d7a6cc683677ffb6aa4fd213287f:src/main/java/com/unifebe/devsecops/config/AppConfig.java:aws-access-token:14`
  - Linha 18: Regra `stripe-access-token` (Chave de API de gateway de pagamentos)
    - Fingerprint: `9d7b54140c56d7a6cc683677ffb6aa4fd213287f:src/main/java/com/unifebe/devsecops/config/AppConfig.java:stripe-access-token:18`
- **Total de Leaks Encontrados**: 2

### Correção Aplicada
1. Remoção das constantes expostas no código de `AppConfig.java`, substituindo-as pela leitura de variáveis de ambiente (`System.getenv(...)`). Em produção, essas variáveis devem ser supridas por um cofre de segredos centralizado (ex.: HashiCorp Vault, AWS Secrets Manager).
2. Como o Gitleaks inspeciona o histórico completo do Git e os segredos faziam parte do histórico de teste da atividade, as ocorrências passadas foram mapeadas no arquivo `.gitleaksignore` utilizando os fingerprints exatos fornecidos pelo log da execução.

> **PRECISA DE PRINT MANUAL**:
> - Tela da execução do GitHub Actions: `https://github.com/jeffSv16/seguracao-info/actions/runs/36374123293`
> - Deve mostrar o job `Deteccao de Segredos (Gitleaks)` com ícone vermelho ❌ e os demais gates em branco ⚪ (Pulados).
> - No log expandido do job `Deteccao de Segredos (Gitleaks)`, mostrar o trecho `Finding: ... RuleID: aws-access-token` e `RuleID: stripe-access-token`.

---

## 2. Gate 2: Testes Unitários + Quality Gate (Maven)

- **Job**: `unit-tests`
- **Execução GitHub Actions**: [Run 36374312595](https://github.com/jeffSv16/seguracao-info/actions/runs/36374312595)
- **Status do Gate 1 (Gitleaks)**: 🟢 **SUCESSO** (Passou após correção)
- **Status do Gate 2 (Testes)**: ❌ **FALHOU** (Código de saída: 1)
- **Status dos Gates Subsequentes**: ⚪ Pulados (`sast`, `sca`, `dockerfile-lint`, `build-and-push`)

### Detalhes da Falha
- **Ferramenta**: Maven Surefire Plugin / JUnit 5
- **Problema**: Erro de lógica/aritmética no cálculo de desconto de pagamentos
- **Arquivo Testado**: `src/main/java/com/unifebe/devsecops/service/PaymentService.java`
- **Arquivo de Teste**: `src/test/java/com/unifebe/devsecops/service/PaymentServiceTest.java` (Linha 16)
- **Erro Apresentado no Log**:
  ```text
  [ERROR] Failures: 
  [ERROR]   PaymentServiceTest.deveAplicarDezPorCentoDeDesconto:16 expected: <180.0> but was: <198.0>
  ```
- **Causa Raiz**: Em `PaymentService.java` (linha 20), a fórmula dividia por `1000` em vez de `100` (`price - (price * discountPercent / 1000)`), aplicando um desconto 10 vezes menor do que o solicitado pela regra de negócio.

### Correção Aplicada
- Em `PaymentService.java`, alterada a divisão da porcentagem de `1000` para `100`:
  ```java
  return price - (price * discountPercent / 100.0);
  ```

> **PRECISA DE PRINT MANUAL**:
> - Tela da execução do GitHub Actions: `https://github.com/jeffSv16/seguracao-info/actions/runs/36374312595`
> - Deve mostrar `Deteccao de Segredos (Gitleaks)` com verde 🟢, `Testes Unitarios (Maven)` com vermelho ❌ e os seguintes como pulados ⚪.
> - No log expandido do job `Testes Unitarios (Maven)`, mostrar o trecho `[ERROR] Failures: PaymentServiceTest.deveAplicarDezPorCentoDeDesconto:16 expected: <180.0> but was: <198.0>`.

---

## 3. Gate 3: SAST - Análise Estática de Código (Semgrep)

- **Job**: `sast`
- **Execução GitHub Actions**: [Run 36374452853](https://github.com/jeffSv16/seguracao-info/actions/runs/36374452853)
- **Status do Gate 1 (Gitleaks)**: 🟢 **SUCESSO**
- **Status do Gate 2 (Testes)**: 🟢 **SUCESSO**
- **Status do Gate 3 (Semgrep)**: ❌ **FALHOU** (Código de saída: 1 - 2 achados bloqueantes)
- **Status dos Gates Subsequentes**: ⚪ Pulados (`sca`, `dockerfile-lint`, `build-and-push`)

### Detalhes da Falha
- **Ferramenta**: Semgrep v1.178.0 (`--config p/ci --config p/java --config p/secrets --error`)
- **Problema**: SQL Injection (CWE-89) / Concatenação insegura de entrada de usuário em comando SQL
- **Arquivo Afetado**: `src/main/java/com/unifebe/devsecops/controller/AccountController.java`
- **Linha**: 38
- **Regras Disparadas**:
  1. `java.lang.security.audit.formatted-sql-string.formatted-sql-string`: Detectou formatação/concatenação de string em declaração SQL.
  2. `java.spring.security.injection.tainted-sql-string.tainted-sql-string`: Detectou fluxo direto de dados não confiáveis (`@RequestParam String id`) para a string SQL.
- **Trecho Inseguro**:
  ```java
  ResultSet rs = stmt.executeQuery("SELECT * FROM contas WHERE id = '" + id + "'");
  ```

### Correção Aplicada
- Substituída a interface genérica `Statement` e a concatenação insegura por `PreparedStatement` parametrizado com placeholder `?`:
  ```java
  PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM contas WHERE id = ?");
  pstmt.setString(1, id);
  ResultSet rs = pstmt.executeQuery();
  ```
- Isso garante a separação estrita entre a instrução SQL e os dados enviados pelo usuário, mitigando integralmente a vulnerabilidade de SQL Injection.

> **PRECISA DE PRINT MANUAL**:
> - Tela da execução do GitHub Actions: `https://github.com/jeffSv16/seguracao-info/actions/runs/36374452853`
> - Deve mostrar `Deteccao de Segredos` e `Testes Unitarios` em verde 🟢, e `Analise Estatica de Codigo (Semgrep)` em vermelho ❌.
> - No log expandido do job `Analise Estatica de Codigo (Semgrep)`, mostrar o painel com os 2 achados em `AccountController.java` (regras `formatted-sql-string` e `tainted-sql-string`).

---

## 4. Gate 4: SCA - Software Composition Analysis (Trivy)

- **Job**: `sca`
- **Execução GitHub Actions**: [Run 36374639161](https://github.com/jeffSv16/seguracao-info/actions/runs/36374639161)
- **Status do Gate 1 (Gitleaks)**: 🟢 **SUCESSO**
- **Status do Gate 2 (Testes)**: 🟢 **SUCESSO**
- **Status do Gate 3 (Semgrep)**: 🟢 **SUCESSO** (Passou após PreparedStatement)
- **Status do Gate 4 (Trivy)**: ❌ **FALHOU** (Código de saída: 1 - Severidades CRITICAL/HIGH detectadas)
- **Status dos Gates Subsequentes**: ⚪ Pulados (`dockerfile-lint`, `build-and-push`)

### Detalhes da Falha
- **Ferramenta**: Aquasecurity Trivy v0.36.0 (`scanners: vuln`, `severity: CRITICAL,HIGH`)
- **Problema**: Dependência vulnerável grave na cadeia de suprimentos (Log4Shell) e dependências não utilizadas
- **Arquivo Afetado**: `pom.xml`
- **Vulnerabilidades Detectadas na biblioteca `org.apache.logging.log4j:log4j-core:2.14.1`**:
  1. `CVE-2021-44228` (CRITICAL): Execução remota de código (RCE) via lookup JNDI no Log4j (Log4Shell).
  2. `CVE-2021-45046` (CRITICAL): Negação de serviço (DoS) e potencial RCE com padrões de contexto de thread.
  3. `CVE-2021-45105` (HIGH): Negação de serviço (DoS) com recursão descontrolada no MDC.
- **Falha de Higiene / Superfície de Ataque**:
  - Declaração duplicada da biblioteca `com.google.code.gson:gson:2.10.1`, sem nenhum uso no código-fonte (`grep` por `com.google.gson` não retorna nada).

### Correção Aplicada
1. Remoção da dependência vulnerável `org.apache.logging.log4j:log4j-core:2.14.1` do `pom.xml`, pois o código do `PaymentService` depende unicamente do `log4j-api`, que já é fornecido e gerenciado com segurança pelo próprio Spring Boot starter.
2. Remoção de todas as entradas da dependência não utilizada `com.google.code.gson:gson` para redução de superfície de ataque e higiene da cadeia de suprimentos de software.
3. Atualização do `spring-boot-starter-parent` para versão recente suportada (`3.3.4` / `3.4.x`) para prevenir vulnerabilidades conhecidas em componentes como Tomcat e Jackson.

> **PRECISA DE PRINT MANUAL**:
> - Tela da execução do GitHub Actions: `https://github.com/jeffSv16/seguracao-info/actions/runs/36374639161`
> - Deve mostrar `Deteccao de Segredos`, `Testes Unitarios` e `Analise Estatica` em verde 🟢, e `Analise de Dependencias (Trivy)` em vermelho ❌.
> - No log expandido do job `Analise de Dependencias (Trivy)`, mostrar a tabela do Trivy listando o `pom.xml` com a biblioteca `log4j-core:2.14.1` e as vulnerabilidades `CVE-2021-44228` (CRITICAL), `CVE-2021-45046` e `CVE-2021-45105`.

---

## 5. Gate 5: Hardening de Contêiner (Hadolint)

- **Job**: `dockerfile-lint`
- **Execução GitHub Actions**: [Run 36374858143](https://github.com/jeffSv16/seguracao-info/actions/runs/36374858143)
- **Status dos Gates 1 a 4**: 🟢 **SUCESSO** (`secret-scan`, `unit-tests`, `sast`, `sca`)
- **Status do Gate 5 (Hadolint)**: ❌ **FALHOU** (Código de saída: 1 - threshold: warning)
- **Status do Gate 6 (Build & Push)**: ⚪ Pulado

### Detalhes da Falha
- **Ferramenta**: Hadolint v3.1.0 (`failure-threshold: warning`)
- **Problema**: Más práticas de segurança e ausência de hardening no `Dockerfile`
- **Arquivo Afetado**: `Dockerfile`
- **Violações Detectadas no Log**:
  1. `Dockerfile:1 DL3007 warning: Using latest is prone to errors if the image will ever update. Pin the version explicitly to a release tag`
     - Causa: Uso da imagem `FROM openjdk:latest`, que além de ser obsoleta e deprecada, introduz imprevisibilidade em builds futuros.
  2. `Dockerfile:9 DL3002 warning: Last USER should not be root`
     - Causa: Execução explícita do processo como superusuário `USER root`, violando frontalmente o Princípio do Menor Privilégio (PoLP). Em caso de exploração de vulnerabilidade na aplicação (como escape de JVM ou RCE), o atacante ganharia privilégios de root no contêiner.

### Correção Aplicada
- Substituição da imagem base por uma imagem mínima e com versão fixada: `eclipse-temurin:17-jre-alpine`.
- Criação de um grupo e usuário de sistema dedicado não privilegiado (`app`).
- Definição do usuário de execução como `USER app`.
- Ajuste de permissões do diretório da aplicação (`/app`).

> **PRECISA DE PRINT MANUAL**:
> - Tela da execução do GitHub Actions: `https://github.com/jeffSv16/seguracao-info/actions/runs/36374858143`
> - Deve mostrar os Gates 1 a 4 com verde 🟢, e `Lint do Dockerfile (Hadolint)` com vermelho ❌.
> - No log expandido do job `Lint do Dockerfile (Hadolint)`, mostrar as mensagens de erro das regras `DL3007` (linha 1) e `DL3002` (linha 9).

---
