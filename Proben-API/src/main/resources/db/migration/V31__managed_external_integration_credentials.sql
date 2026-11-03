alter table contexto_integracao_externa
    add column credential_ciphertext bytea,
    add column credential_iv bytea;

alter table contexto_integracao_externa
    add constraint ck_contexto_managed_credential_pair check (
        (credential_ciphertext is null and credential_iv is null)
        or (credential_ciphertext is not null and credential_iv is not null)
    );

comment on column contexto_integracao_externa.credential_ciphertext is
    'Credencial do provedor cifrada com AES-GCM; nunca contem o segredo em texto puro.';
comment on column contexto_integracao_externa.credential_iv is
    'IV aleatorio de 96 bits usado exclusivamente por esta credencial AES-GCM.';
