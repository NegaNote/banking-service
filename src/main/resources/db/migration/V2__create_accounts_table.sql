create table accounts (
                          balance decimal(14,2) not null,
                          created_at datetime(6) not null,
                          id bigint not null auto_increment,
                          owner_id bigint not null,
                          version bigint,
                          account_number varchar(20) not null,
                          status enum ('ACTIVE','CLOSED','FROZEN') not null,
                          primary key (id),
                          unique (account_number),
                          foreign key (owner_id) references users (id)
) engine=InnoDB DEFAULT CHARSET=utf8mb4;