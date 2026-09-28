package com.unifebe.devsecops.config;

/**
 * Configuracoes de credenciais e integracoes da aplicacao.
 *
 * Correcao de seguranca (Shift Left - Gestao de Segredos):
 * As chaves e senhas foram removidas do codigo-fonte para evitar Secret Sprawl
 * e vazamento acidental em repositorios. Os valores agora sao obtidos via
 * variaveis de ambiente injetadas em tempo de execucao (runtime), as quais
 * devem ser gerenciadas por um cofre centralizado (ex: AWS Secrets Manager,
 * HashiCorp Vault, Azure Key Vault).
 */
public class AppConfig {

    public static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    public static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    public static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");

    public static final String PAYMENT_GATEWAY_API_KEY = System.getenv("PAYMENT_GATEWAY_API_KEY");

}
