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
