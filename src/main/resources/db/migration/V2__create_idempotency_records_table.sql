create table idempotency_records (
                                     response_status integer not null,
                                     created_at datetime(6) not null,
                                     expires_at datetime(6) not null,
                                     id bigint not null auto_increment,
                                     user_id bigint not null,
                                     idempotency_key varchar(100) not null,
                                     request_hash varchar(100) not null,
                                     request_path varchar(255) not null,
                                     response_body tinytext not null,
                                     primary key (id),
                                     unique (idempotency_key, user_id)
) engine=InnoDB DEFAULT CHARSET=utf8mb4;