/*
 * qwq233
 * Copyright (C) 2019-2021 qwq233@qwq2333.top
 * https://qwq2333.top
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by qwq233.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/qwq233/qwq233/blob/master/eula.md>.
 */

use qn_auth;
create table user
(
    uin        long primary key,
    status     int      not null,
    reason     text     not null,
    lastUpdate datetime not null
);
create table admin
(
    id         int primary key auto_increment,
    token      text     not null,
    nickname   text     not null,
    creator    text,
    role       int      not null,
    reason     text,
    lastUpdate datetime not null
);
create table log
(
    id        int primary key auto_increment,
    uin       long     not null,
    operator  text     not null,
    operation text     not null,
    changes   text     not null,
    reason    text     not null,
    date      datetime not null
);

