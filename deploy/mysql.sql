create table department
(
    id           bigint auto_increment comment '部门ID'
        primary key,
    name         varchar(50)                        not null comment '部门名称',
    state        tinyint  default 0                 not null comment '状态 0正常 1停用',
    delete_state tinyint  default 0                 not null comment '删除状态 0未删除 1删除',
    create_time  datetime default CURRENT_TIMESTAMP not null,
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint name
        unique (name)
)
    comment '部门信息表';

create table board
(
    id            bigint auto_increment
        primary key,
    name          varchar(50)                        not null comment '版块名称',
    description   varchar(200)                       null,
    article_count bigint   default 0                 null comment '发帖数量',
    department_id bigint                             null comment '所属部门（NULL为全站）',
    visibility    tinyint  default 1                 not null comment '可见性 1全校 2科创内 3部门内',
    post_level    tinyint  default 1                 not null comment '发帖权限 1干事 2部长 3主席',
    sort_priority int      default 0                 null comment '排序优先级',
    state         tinyint  default 0                 not null comment '状态 0正常 1禁用',
    delete_state  tinyint  default 0                 not null comment '删除状态',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint board_ibfk_1
        foreign key (department_id) references department (id)
)
    comment '版块信息表';

create index department_id
    on board (department_id);

create table email_verification
(
    id                bigint auto_increment
        primary key,
    email             varchar(255)         not null,
    verification_code varchar(10)          not null,
    expiry_time       datetime             not null,
    verified          tinyint(1) default 0 not null,
    constraint email
        unique (email)
);

create table permission
(
    position         varchar(20)          not null comment '职位名称'
        primary key,
    can_manage_user  tinyint(1) default 0 not null comment '用户管理权限',
    can_manage_board tinyint(1) default 0 not null comment '版块管理权限',
    can_audit_post   tinyint(1) default 0 not null comment '内容审核权限',
    can_send_notice  tinyint(1) default 1 not null comment '发布通知权限'
)
    comment '职位权限配置表';

create table user
(
    id            bigint auto_increment
        primary key,
    user_name     varchar(50)                                not null comment '登录账号',
    password      varchar(64)                                not null comment '加密密码',
    nick_name     varchar(50)                                not null comment '显示名称',
    phone         varchar(20)                                null,
    email         varchar(50)                                null,
    gender        tinyint      default 2                     null comment '性别 0女 1男 2保密',
    avatar_url    varchar(255) default '/default_avatar.png' null comment '头像路径',
    article_count bigint       default 0                     null comment '发帖数量',
    remark        varchar(200)                               null comment '自我介绍',
    state         tinyint      default 0                     not null comment '状态',
    delete_state  tinyint      default 0                     not null comment '删除状态 0未删除 1删除',
    create_time   datetime     default CURRENT_TIMESTAMP     not null,
    update_time   datetime     default CURRENT_TIMESTAMP     not null on update CURRENT_TIMESTAMP,
    constraint email
        unique (email)
)
    comment '用户信息表';

create table article
(
    id           bigint auto_increment
        primary key,
    board_id     bigint                             not null,
    user_id      bigint                             not null,
    title        varchar(100)                       not null comment '标题',
    content      text                               not null comment '正文内容',
    visit_count  int      default 0                 null comment '访问次数',
    reply_count  int      default 0                 null comment '回复数量',
    like_count   int      default 0                 null comment '点赞数量',
    is_top       tinyint  default 0                 null comment '置顶标记',
    state        tinyint  default 0                 not null comment '状态',
    delete_state tinyint  default 0                 not null comment '删除状态',
    create_time  datetime default CURRENT_TIMESTAMP not null,
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint article_ibfk_1
        foreign key (board_id) references board (id),
    constraint article_ibfk_2
        foreign key (user_id) references user (id)
)
    comment '主帖表';

create index board_id
    on article (board_id);

create index user_id
    on article (user_id);

create table article_image
(
    id            bigint auto_increment
        primary key,
    article_id    bigint                             not null,
    file_path     varchar(255)                       not null comment '图片存储路径',
    file_name     varchar(100)                       not null comment '原始文件名',
    file_size     int                                not null comment '文件大小(KB)',
    file_type     varchar(50)                        not null comment '文件类型(MIME)',
    display_order int      default 0                 null comment '显示顺序',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    constraint article_image_ibfk_1
        foreign key (article_id) references article (id)
            on delete cascade
);

create index article_id
    on article_image (article_id);

create table article_reply
(
    id             bigint auto_increment
        primary key,
    article_id     bigint                             not null,
    post_user_id   bigint                             not null,
    reply_id       bigint                             null,
    reply_user_id  bigint                             null,
    content        varchar(500)                       not null,
    like_count     int      default 0                 null,
    state          tinyint  default 0                 not null comment '状态',
    delete_state   tinyint  default 0                 not null comment '删除状态',
    create_time    datetime default CURRENT_TIMESTAMP not null,
    update_time    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    children_count int      default 0                 not null comment '子回复',
    constraint article_reply_ibfk_1
        foreign key (article_id) references article (id),
    constraint article_reply_ibfk_2
        foreign key (post_user_id) references user (id)
)
    comment '帖子回复表';

create index article_id
    on article_reply (article_id);

create index post_user_id
    on article_reply (post_user_id);

create table likes
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                             not null,
    target_id   bigint                             not null,
    target_type varchar(50)                        not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    constraint unique_user_target
        unique (user_id, target_id, target_type),
    constraint likes_ibfk_1
        foreign key (user_id) references user (id)
);

create table message
(
    id              bigint auto_increment
        primary key,
    post_user_id    bigint                             not null,
    receive_user_id bigint                             not null,
    content         varchar(500)                       not null,
    state           tinyint  default 0                 not null comment '状态 0未读 1已读 2已回复',
    delete_state    tinyint  default 0                 not null comment '删除状态',
    create_time     datetime default CURRENT_TIMESTAMP not null,
    update_time     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint message_ibfk_1
        foreign key (post_user_id) references user (id),
    constraint message_ibfk_2
        foreign key (receive_user_id) references user (id)
)
    comment '站内信表';

create index post_user_id
    on message (post_user_id);

create index receive_user_id
    on message (receive_user_id);

create table user_position
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                             not null,
    department_id bigint                             not null,
    position      varchar(20)                        not null comment '职位',
    start_year    year                               not null comment '开始年份',
    end_year      year                               null comment '结束年份',
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint user_position_ibfk_1
        foreign key (user_id) references user (id),
    constraint user_position_ibfk_2
        foreign key (department_id) references department (id)
)
    comment '用户职位历史表';

create index department_id
    on user_position (department_id);

create index user_id
    on user_position (user_id);

