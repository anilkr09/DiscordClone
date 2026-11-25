-- The schema as Hibernate created it before migrations were introduced. Databases that already
-- hold it are baselined at this version and start at V2.

CREATE TABLE public.channels (
    id bigint NOT NULL,
    description character varying(255),
    dm_key character varying(255),
    name character varying(255),
    type character varying(255),
    server_id bigint,
    CONSTRAINT channels_type_check CHECK (((type)::text = ANY ((ARRAY['TEXT'::character varying, 'ANNOUNCEMENT'::character varying, 'DM'::character varying])::text[])))
);

CREATE SEQUENCE public.channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.channels_id_seq OWNED BY public.channels.id;

CREATE TABLE public.dm_channels (
    id bigint NOT NULL,
    channel_id bigint NOT NULL,
    user1_id bigint NOT NULL,
    user2_id bigint NOT NULL
);

CREATE SEQUENCE public.dm_channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.dm_channels_id_seq OWNED BY public.dm_channels.id;

CREATE TABLE public.friendships (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    receiver_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    CONSTRAINT friendships_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'BLOCKED'::character varying])::text[])))
);

CREATE SEQUENCE public.friendships_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.friendships_id_seq OWNED BY public.friendships.id;

CREATE TABLE public.invites (
    code character varying(255) NOT NULL,
    expiry timestamp(6) without time zone,
    max_uses integer NOT NULL,
    uses integer NOT NULL,
    server_id bigint NOT NULL
);

CREATE TABLE public.messages (
    id character varying(255) NOT NULL,
    content text NOT NULL,
    edited boolean NOT NULL,
    "timestamp" timestamp(6) without time zone NOT NULL,
    channel_id bigint NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.server_members (
    joined_at timestamp(6) without time zone NOT NULL,
    nickname character varying(100),
    role character varying(255) NOT NULL,
    server_id bigint NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT server_members_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'MEMBER'::character varying, 'OWNER'::character varying])::text[])))
);

CREATE TABLE public.servers (
    id bigint NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    type character varying(255),
    owner_id bigint NOT NULL,
    CONSTRAINT servers_type_check CHECK (((type)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[])))
);

CREATE SEQUENCE public.servers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.servers_id_seq OWNED BY public.servers.id;

CREATE TABLE public.user_status (
    id bigint NOT NULL,
    custom_status character varying(255),
    last_activity timestamp(6) without time zone,
    status_expires_at timestamp(6) without time zone,
    user_id bigint NOT NULL,
    CONSTRAINT user_status_custom_status_check CHECK (((custom_status)::text = ANY ((ARRAY['ONLINE'::character varying, 'OFFLINE'::character varying, 'IDLE'::character varying, 'DO_NOT_DISTURB'::character varying])::text[])))
);

CREATE SEQUENCE public.user_status_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.user_status_id_seq OWNED BY public.user_status.id;

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    username character varying(255) NOT NULL
);

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;

ALTER TABLE ONLY public.channels ALTER COLUMN id SET DEFAULT nextval('public.channels_id_seq'::regclass);

ALTER TABLE ONLY public.dm_channels ALTER COLUMN id SET DEFAULT nextval('public.dm_channels_id_seq'::regclass);

ALTER TABLE ONLY public.friendships ALTER COLUMN id SET DEFAULT nextval('public.friendships_id_seq'::regclass);

ALTER TABLE ONLY public.servers ALTER COLUMN id SET DEFAULT nextval('public.servers_id_seq'::regclass);

ALTER TABLE ONLY public.user_status ALTER COLUMN id SET DEFAULT nextval('public.user_status_id_seq'::regclass);

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT channels_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT dm_channels_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT friendships_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT invites_pkey PRIMARY KEY (code);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.server_members
    ADD CONSTRAINT server_members_pkey PRIMARY KEY (server_id, user_id);

ALTER TABLE ONLY public.servers
    ADD CONSTRAINT servers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT uk1ief542llhkggs7wvceaxnl4r UNIQUE (server_id, name);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT uk_2oirid71wtpw5ae8n5vo83b2g UNIQUE (channel_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);

ALTER TABLE ONLY public.user_status
    ADD CONSTRAINT uk_9onp4jxeo4eyc7i5124wp38me UNIQUE (user_id);

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT uk_gune4hf8ay4w2osl3hvw58cu1 UNIQUE (dm_key);

ALTER TABLE ONLY public.servers
    ADD CONSTRAINT uk_pfl9ik2kpxl5wvac40qf3ebbo UNIQUE (name);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_r43af9ap4edm43mmtq01oddj6 UNIQUE (username);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT ukcqj9xmuq1jagtvesm7mg3iewo UNIQUE (user1_id, user2_id, channel_id);

ALTER TABLE ONLY public.user_status
    ADD CONSTRAINT user_status_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.server_members
    ADD CONSTRAINT fk264q5e480bvf297331scv3nv1 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk3u3ckbhwq9se1cmopk2pq05b2 FOREIGN KEY (channel_id) REFERENCES public.channels(id);

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT fkacxri6fsdtdwloddqhewkt62w FOREIGN KEY (server_id) REFERENCES public.servers(id);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT fkc9qf02kyvfveq0qr7hjycwk2j FOREIGN KEY (channel_id) REFERENCES public.channels(id);

ALTER TABLE ONLY public.servers
    ADD CONSTRAINT fkcoqnnqwj926ook0at6p01ygq4 FOREIGN KEY (owner_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT fkg3nma3og6xh0aqtnb75434q3c FOREIGN KEY (server_id) REFERENCES public.servers(id);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT fkgavhqhmgkl5xyeinya2y9hyd1 FOREIGN KEY (user1_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT fkpk7w2cj6m9n224ny2t7fhi47 FOREIGN KEY (receiver_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fkpsmh6clh3csorw43eaodlqvkn FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.server_members
    ADD CONSTRAINT fkqu0vrc783yq288y2r92gjurw2 FOREIGN KEY (server_id) REFERENCES public.servers(id);

ALTER TABLE ONLY public.dm_channels
    ADD CONSTRAINT fks11x6tjnofbt8cy09y6y2xylq FOREIGN KEY (user2_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.friendships
    ADD CONSTRAINT fks7n4v837jm41ijdacqgfe9acw FOREIGN KEY (sender_id) REFERENCES public.users(id);

