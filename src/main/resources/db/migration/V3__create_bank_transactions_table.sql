create table bank_transactions (
                                   amount decimal(14,2) not null,
                                   from_account_id bigint not null,
                                   id bigint not null auto_increment,
                                   occurred_at datetime(6) not null,
                                   to_account_id bigint,
                                   description varchar(255),
                                   type enum ('DEPOSIT','TRANSFER','WITHDRAWAL') not null,
                                   primary key (id),
                                   foreign key (from_account_id) references accounts (id),
                                   foreign key (to_account_id) references accounts (id)
) engine=InnoDB DEFAULT CHARSET=utf8mb4;