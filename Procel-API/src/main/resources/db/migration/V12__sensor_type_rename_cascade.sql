alter table if exists parametro_def
    drop constraint if exists FKnqqirvaar485kib9v4tmb0mpl;

alter table if exists parametro_def
    add constraint FKnqqirvaar485kib9v4tmb0mpl
    foreign key (tipo_nome)
    references tipo_de_sensor (nome)
    on update cascade;

alter table if exists sensor
    drop constraint if exists FK5bxbugmwlobsjk0hfd8nibffn;

alter table if exists sensor
    add constraint FK5bxbugmwlobsjk0hfd8nibffn
    foreign key (tipo_nome)
    references tipo_de_sensor (nome)
    on update cascade;
