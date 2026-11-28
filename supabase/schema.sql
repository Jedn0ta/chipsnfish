-- =====================================================================
-- Hold'em Chips&Fish — online server (Supabase / PostgreSQL)
-- The whole game runs here: shuffling, dealing, betting, side pots,
-- showdown. Clients only call the cf_* functions below; they can never
-- read the deck or other players' cards. Safe to run again (idempotent).
-- =====================================================================

create table if not exists cf_tables (
  code text primary key,
  host uuid not null,
  name text not null default '',
  gid uuid,
  cfg jsonb not null default '{}',
  status text not null default 'lobby',
  state jsonb not null,
  ver int not null default 1,
  created timestamptz not null default now(),
  updated timestamptz not null default now()
);

create table if not exists cf_players (
  code text not null references cf_tables(code) on delete cascade,
  uid uuid not null,
  name text not null default '',
  img text not null default '',
  joined timestamptz not null default now(),
  seen timestamptz not null default now(),
  pending boolean not null default false,
  left_at timestamptz,
  primary key (code, uid)
);

create table if not exists cf_secret (
  code text primary key references cf_tables(code) on delete cascade,
  hand int not null,
  hole jsonb not null,   -- {"<seat index>": [c1, c2]}
  board jsonb not null   -- [c1..c5], revealed street by street
);

create table if not exists cf_shows (
  code text not null references cf_tables(code) on delete cascade,
  uid uuid not null,
  hand int not null,
  cards jsonb not null,
  primary key (code, uid, hand)
);

create table if not exists cf_groups (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner uuid not null,
  created timestamptz not null default now()
);

create table if not exists cf_group_members (
  gid uuid not null references cf_groups(id) on delete cascade,
  uid uuid not null,
  name text not null default '',
  img text not null default '',
  joined timestamptz not null default now(),
  primary key (gid, uid)
);

create table if not exists cf_profiles (
  uid uuid primary key,
  name text not null default '',
  img text not null default '',
  t timestamptz not null default now()
);

create index if not exists cf_tables_gid on cf_tables(gid);
create index if not exists cf_players_uid on cf_players(uid);
create index if not exists cf_group_members_uid on cf_group_members(uid);

-- Clients have no direct access to any table; everything goes through the functions.
alter table cf_tables enable row level security;
alter table cf_players enable row level security;
alter table cf_secret enable row level security;
alter table cf_shows enable row level security;
alter table cf_groups enable row level security;
alter table cf_group_members enable row level security;
alter table cf_profiles enable row level security;
revoke all on cf_tables, cf_players, cf_secret, cf_shows, cf_groups, cf_group_members, cf_profiles from anon, authenticated;

-- ---------------------------------------------------------------------
-- small helpers
-- ---------------------------------------------------------------------
create or replace function cf_now() returns bigint language sql volatile as
$$ select (extract(epoch from clock_timestamp()) * 1000)::bigint $$;

create or replace function cf_me() returns uuid language plpgsql stable as $$
declare u uuid := auth.uid();
begin
  if u is null then raise exception 'not signed in'; end if;
  return u;
end $$;

create or replace function cf_si(st jsonb, i int, k text) returns int language sql immutable as
$$ select coalesce((st->'seats'->i->>k)::int, 0) $$;
create or replace function cf_sb(st jsonb, i int, k text) returns boolean language sql immutable as
$$ select coalesce((st->'seats'->i->>k)::boolean, false) $$;
create or replace function cf_ss(st jsonb, i int, k text, v jsonb) returns jsonb language sql immutable as
$$ select jsonb_set(st, array['seats', i::text, k], v) $$;
create or replace function cf_gi(st jsonb, k text) returns int language sql immutable as
$$ select coalesce((st->>k)::int, 0) $$;
create or replace function cf_set(st jsonb, k text, v jsonb) returns jsonb language sql immutable as
$$ select jsonb_set(st, array[k], v) $$;

create or replace function cf_level(l int) returns int[] language sql immutable as $$
  select array[a[k][1], a[k][2]] from (select array[[1,2],[2,4],[3,6],[4,8],[5,10],[10,20],[15,30],[20,40],[25,50],[50,100]] as a,
    greatest(1, least(10, l + 1)) as k) z
$$;

-- ---------------------------------------------------------------------
-- hand evaluator (port of the app's ev(): category << 20 | ranks)
-- card = rank*4 + suit, rank 0 = "2" … 12 = "A"
-- ---------------------------------------------------------------------
create or replace function cf_straight_high(m int) returns int language plpgsql immutable as $$
declare e int := (m << 1) | ((m >> 12) & 1); h int;
begin
  for h in reverse 13..4 loop
    if ((e >> (h - 4)) & 31) = 31 then return h - 1; end if;
  end loop;
  return -1;
end $$;

create or replace function cf_kick(mask int, n int, sft int) returns int language plpgsql immutable as $$
declare v int := 0; c int := 0; r int := 12;
begin
  while r >= 0 and c < n loop
    if ((mask >> r) & 1) = 1 then v := v | (r << sft); sft := sft - 4; c := c + 1; end if;
    r := r - 1;
  end loop;
  return v;
end $$;

create or replace function cf_ev(cs int[]) returns int language plpgsql immutable as $$
declare
  rc int[] := array_fill(0, array[13]); sc int[] := array_fill(0, array[4]); sm int[] := array_fill(0, array[4]);
  mask int := 0; c int; r int; s int; sh int;
  q int := -1; t1 int := -1; t2 int := -1; p1 int := -1; p2 int := -1;
begin
  foreach c in array cs loop
    r := c >> 2; s := c & 3;
    rc[r + 1] := rc[r + 1] + 1; sc[s + 1] := sc[s + 1] + 1;
    sm[s + 1] := sm[s + 1] | (1 << r); mask := mask | (1 << r);
  end loop;
  for s in 0..3 loop
    if sc[s + 1] >= 5 then
      sh := cf_straight_high(sm[s + 1]);
      if sh >= 0 then return (8 << 20) | (sh << 16); end if;
      return (5 << 20) | cf_kick(sm[s + 1], 5, 16);
    end if;
  end loop;
  for r in reverse 12..0 loop
    if rc[r + 1] = 4 then q := r;
    elsif rc[r + 1] = 3 then if t1 < 0 then t1 := r; elsif t2 < 0 then t2 := r; end if;
    elsif rc[r + 1] = 2 then if p1 < 0 then p1 := r; elsif p2 < 0 then p2 := r; end if;
    end if;
  end loop;
  if q >= 0 then return (7 << 20) | (q << 16) | cf_kick(mask & ~(1 << q), 1, 12); end if;
  if t1 >= 0 and (t2 >= 0 or p1 >= 0) then return (6 << 20) | (t1 << 16) | (greatest(t2, p1) << 12); end if;
  sh := cf_straight_high(mask);
  if sh >= 0 then return (4 << 20) | (sh << 16); end if;
  if t1 >= 0 then return (3 << 20) | (t1 << 16) | cf_kick(mask & ~(1 << t1), 2, 12); end if;
  if p2 >= 0 then return (2 << 20) | (p1 << 16) | (p2 << 12) | cf_kick(mask & ~(1 << p1) & ~(1 << p2), 1, 8); end if;
  if p1 >= 0 then return (1 << 20) | (p1 << 16) | cf_kick(mask & ~(1 << p1), 3, 12); end if;
  return cf_kick(mask, 5, 16);
end $$;

-- ---------------------------------------------------------------------
-- engine (port of the app's Texas Hold'em rules)
-- ---------------------------------------------------------------------
create or replace function cf_nxt(st jsonb, i int) returns int language plpgsql immutable as $$
declare n int := cf_gi(st, 'n'); k int; j int;
begin
  for k in 1..n loop
    j := (i + k) % n;
    if not cf_sb(st, j, 'out') then return j; end if;
  end loop;
  return (i + 1) % n;
end $$;

create or replace function cf_active(st jsonb) returns int language sql immutable as
$$ select count(*)::int from jsonb_array_elements(st->'seats') s where not coalesce((s->>'out')::boolean, false) $$;
create or replace function cf_live(st jsonb) returns int language sql immutable as
$$ select count(*)::int from jsonb_array_elements(st->'seats') s where not coalesce((s->>'folded')::boolean, false) $$;
create or replace function cf_live_free(st jsonb) returns int language sql immutable as
$$ select count(*)::int from jsonb_array_elements(st->'seats') s where not coalesce((s->>'folded')::boolean, false) and not coalesce((s->>'allin')::boolean, false) $$;
create or replace function cf_pot(st jsonb) returns int language sql immutable as
$$ select coalesce(sum((s->>'total')::int), 0)::int from jsonb_array_elements(st->'seats') s $$;

create or replace function cf_positions(st jsonb, out sb int, out bb int) language plpgsql immutable as $$
declare d int := cf_gi(st, 'dealer');
begin
  if cf_active(st) = 2 then sb := d; bb := cf_nxt(st, d);
  else sb := cf_nxt(st, d); bb := cf_nxt(st, sb); end if;
end $$;

create or replace function cf_put(inout st jsonb, i int, amt int, out paid int) language plpgsql immutable as $$
declare stack int := cf_si(st, i, 'stack');
begin
  paid := greatest(0, least(amt, stack));
  st := cf_ss(st, i, 'stack', to_jsonb(stack - paid));
  st := cf_ss(st, i, 'bet', to_jsonb(cf_si(st, i, 'bet') + paid));
  st := cf_ss(st, i, 'total', to_jsonb(cf_si(st, i, 'total') + paid));
  if stack - paid = 0 then st := cf_ss(st, i, 'allin', 'true'); end if;
end $$;

create or replace function cf_needs(st jsonb, i int) returns boolean language sql immutable as $$
  select not cf_sb(st, i, 'folded') and not cf_sb(st, i, 'allin')
     and (not cf_sb(st, i, 'acted') or cf_si(st, i, 'bet') < cf_gi(st, 'curBet'))
$$;

create or replace function cf_next_to_act(st jsonb, frm int) returns int language plpgsql immutable as $$
declare i int := frm; k int;
begin
  for k in 1..cf_gi(st, 'n') loop
    i := cf_nxt(st, i);
    if cf_needs(st, i) then return i; end if;
  end loop;
  return -1;
end $$;

create or replace function cf_start_street(st jsonb) returns jsonb language plpgsql volatile as $$
declare n int := cf_gi(st, 'n'); i int;
begin
  for i in 0..n - 1 loop
    st := cf_ss(st, i, 'bet', '0'); st := cf_ss(st, i, 'acted', 'false');
  end loop;
  st := st || jsonb_build_object('curBet', 0, 'lastRaise', cf_gi(st, 'bb'), 'turnAt', cf_now());
  if cf_live(st) > 1 and cf_live_free(st) > 1 then
    st := cf_set(st, 'turn', to_jsonb(cf_next_to_act(st, cf_gi(st, 'dealer'))));
  else
    st := cf_set(st, 'turn', '-1');
  end if;
  return st;
end $$;

-- deals a new hand; returns null when fewer than two players have chips
create or replace function cf_start_hand(p_code text, st jsonb) returns jsonb language plpgsql volatile as $$
declare
  n int := cf_gi(st, 'n'); i int; lv int; lb int[]; sb int; bb int; paid int;
  v_deck int[]; v_hole jsonb := '{}'; k int := 1;
begin
  for i in 0..n - 1 loop
    if cf_si(st, i, 'stack') <= 0 then st := cf_ss(st, i, 'out', 'true'); end if;
  end loop;
  if cf_active(st) < 2 then return null; end if;
  for i in 0..n - 1 loop
    st := cf_ss(st, i, 'folded', to_jsonb(cf_sb(st, i, 'out')));
    st := cf_ss(st, i, 'allin', 'false'); st := cf_ss(st, i, 'bet', '0'); st := cf_ss(st, i, 'total', '0');
    st := cf_ss(st, i, 'acted', 'false'); st := cf_ss(st, i, 'actions', '[]');
  end loop;
  lv := cf_gi(st, 'level');
  if cf_gi(st, 'blindHands') > 0 and cf_gi(st, 'handNo') > 0 and cf_gi(st, 'handNo') % cf_gi(st, 'blindHands') = 0 and lv < 9 then
    lv := lv + 1;
  end if;
  lb := cf_level(lv);
  st := st || jsonb_build_object('done', null, 'reveal', null, 'board', '[null,null,null,null,null]'::jsonb,
    'betStreet', 0, 'curBet', 0, 'turn', -1, 'level', lv, 'curLevel', lv, 'sb', lb[1], 'bb', lb[2],
    'lastRaise', lb[2], 'handNo', cf_gi(st, 'handNo') + 1, 'doneAt', 0);
  select * into sb, bb from cf_positions(st);
  select * into st, paid from cf_put(st, sb, lb[1]);
  st := cf_ss(st, sb, 'actions', jsonb_build_array(jsonb_build_object('st', 0, 'a', 'sb', 'amt', paid)));
  select * into st, paid from cf_put(st, bb, lb[2]);
  st := cf_ss(st, bb, 'actions', jsonb_build_array(jsonb_build_object('st', 0, 'a', 'bb', 'amt', paid)));
  st := cf_set(st, 'curBet', to_jsonb(greatest(cf_si(st, sb, 'bet'), cf_si(st, bb, 'bet'))));
  st := cf_set(st, 'turn', to_jsonb(cf_next_to_act(st, bb)));
  st := cf_set(st, 'turnAt', to_jsonb(cf_now()));
  -- shuffle with the cryptographic UUID generator
  select array_agg(c order by gen_random_uuid()) into v_deck from generate_series(0, 51) c;
  for i in 0..n - 1 loop
    if not cf_sb(st, i, 'out') then
      v_hole := v_hole || jsonb_build_object(i::text, jsonb_build_array(v_deck[k], v_deck[k + 1]));
      k := k + 2;
    end if;
  end loop;
  insert into cf_secret(code, hand, hole, board)
  values (p_code, cf_gi(st, 'handNo'), v_hole, to_jsonb(v_deck[k:k + 4]))
  on conflict (code) do update set hand = excluded.hand, hole = excluded.hole, board = excluded.board;
  return st;
end $$;

create or replace function cf_act(st jsonb, i int, typ text, p_to int) returns jsonb language plpgsql volatile as $$
declare
  pot_before int := cf_pot(st); bet int := cf_si(st, i, 'bet'); stack int := cf_si(st, i, 'stack');
  cur int := cf_gi(st, 'curBet'); call_amt int := greatest(0, cur - bet);
  rec jsonb := jsonb_build_object('st', cf_gi(st, 'betStreet'), 'a', typ);
  paid int; tgt int := p_to; mx int; mn int; inc int; ratio float; j int; t int;
begin
  if typ = 'fold' then
    st := cf_ss(st, i, 'folded', 'true');
  elsif typ = 'check' and call_amt > 0 then
    st := cf_ss(st, i, 'folded', 'true'); rec := rec || '{"a":"fold"}';
  elsif typ = 'call' then
    select * into st, paid from cf_put(st, i, call_amt);
    rec := rec || jsonb_build_object('amt', paid);
    if call_amt = 0 then rec := rec || '{"a":"check"}'; end if;
  elsif typ in ('raise', 'bet', 'allin') then
    mx := bet + stack;
    mn := least(case when cur = 0 then cf_gi(st, 'bb') else cur + cf_gi(st, 'lastRaise') end, mx);
    if typ = 'allin' then tgt := mx; else tgt := greatest(mn, least(mx, coalesce(tgt, mn))); end if;
    select * into st, paid from cf_put(st, i, tgt - bet);
    bet := cf_si(st, i, 'bet');
    if bet > cur then
      inc := bet - cur;
      ratio := inc::float / greatest(1, pot_before + call_amt);
      rec := rec || jsonb_build_object('sz', case when ratio <= 0.4 then 's' when ratio <= 0.75 then 'm' when ratio <= 1.1 then 'l' else 'o' end);
      if inc >= cf_gi(st, 'lastRaise') then st := cf_set(st, 'lastRaise', to_jsonb(inc)); end if;
      st := cf_set(st, 'curBet', to_jsonb(bet));
      for j in 0..cf_gi(st, 'n') - 1 loop
        if j <> i then st := cf_ss(st, j, 'acted', 'false'); end if;
      end loop;
      if typ <> 'allin' then rec := rec || jsonb_build_object('a', case when cur = 0 then 'bet' else 'raise' end); end if;
    elsif typ = 'allin' then
      rec := rec || '{"a":"call"}';
    end if;
    rec := rec || jsonb_build_object('amt', bet);
    if cf_sb(st, i, 'allin') and typ <> 'allin' and rec->>'a' <> 'call' then rec := rec || '{"a":"allin"}'; end if;
  end if;
  st := cf_ss(st, i, 'acted', 'true');
  st := cf_ss(st, i, 'actions', (st->'seats'->i->'actions') || jsonb_build_array(rec));
  if cf_live(st) <= 1 then t := -1; else t := cf_next_to_act(st, i); end if;
  if t >= 0 and cf_live_free(st) = 1 and cf_si(st, t, 'bet') >= cf_gi(st, 'curBet') then t := -1; end if;
  st := st || jsonb_build_object('turn', t, 'turnAt', cf_now());
  return st;
end $$;

-- settles the pot with side pots; hole cards stay private (only winners' categories are published)
create or replace function cf_finish(st jsonb, hole jsonb) returns jsonb language plpgsql volatile as $$
declare
  n int := cf_gi(st, 'n'); win int[] := array_fill(0, array[n]); sc int[] := array_fill(null::int, array[n]);
  bd int[]; i int; k int; lvls int[]; lvl int; prev int := 0; pot int; best int; ws int[]; sh int; rest int;
  ord int[] := '{}'; cats jsonb := '{}'; wl jsonb := '[]'; al jsonb := '[]'; alive int := 0; sd boolean := false;
begin
  select coalesce(array_agg((x)::int) filter (where x is not null), '{}') into bd from jsonb_array_elements_text(st->'board') x;
  if cf_live(st) = 1 then
    for i in 0..n - 1 loop
      if not cf_sb(st, i, 'folded') then win[i + 1] := cf_pot(st); end if;
    end loop;
  else
    sd := true;
    for i in 0..n - 1 loop
      if not cf_sb(st, i, 'folded') then
        sc[i + 1] := cf_ev(array[(hole->i::text->>0)::int, (hole->i::text->>1)::int] || bd);
      end if;
    end loop;
    k := cf_gi(st, 'dealer');
    for i in 1..n loop k := (k + 1) % n; ord := ord || k; end loop;
    select array_agg(distinct (cf_si(st, g.x, 'total')) order by (cf_si(st, g.x, 'total'))) into lvls
      from generate_series(0, n - 1) g(x) where not cf_sb(st, g.x, 'folded');
    foreach lvl in array lvls loop
      pot := 0;
      for i in 0..n - 1 loop pot := pot + greatest(0, least(cf_si(st, i, 'total'), lvl) - prev); end loop;
      best := null; ws := '{}';
      foreach k in array ord loop
        if sc[k + 1] is not null and cf_si(st, k, 'total') >= lvl then
          if best is null or sc[k + 1] > best then best := sc[k + 1]; ws := array[k];
          elsif sc[k + 1] = best then ws := ws || k; end if;
        end if;
      end loop;
      sh := pot / array_length(ws, 1);
      foreach k in array ws loop win[k + 1] := win[k + 1] + sh; end loop;
      win[ws[1] + 1] := win[ws[1] + 1] + pot - sh * array_length(ws, 1);
      prev := lvl;
    end loop;
    rest := 0;
    for i in 0..n - 1 loop rest := rest + greatest(0, cf_si(st, i, 'total') - prev); end loop;
    if rest > 0 then
      best := null;
      for i in 0..n - 1 loop if sc[i + 1] is not null and (best is null or sc[i + 1] > sc[best + 1]) then best := i; end if; end loop;
      win[best + 1] := win[best + 1] + rest;
    end if;
  end if;
  for i in 0..n - 1 loop
    st := cf_ss(st, i, 'stack', to_jsonb(cf_si(st, i, 'stack') + win[i + 1]));
    st := cf_ss(st, i, 'bet', '0');
    al := al || to_jsonb(win[i + 1]);
    if win[i + 1] > 0 then
      wl := wl || to_jsonb(i);
      if sc[i + 1] is not null then cats := cats || jsonb_build_object(i::text, sc[i + 1] >> 20); end if;
    end if;
    if cf_si(st, i, 'stack') > 0 then alive := alive + 1; end if;
  end loop;
  st := st || jsonb_build_object('done', jsonb_build_object('w', wl, 'amt', al, 'sd', sd, 'cat', cats, 'net', 0),
                                 'turn', -1, 'doneAt', cf_now());
  if alive <= 1 then st := cf_set(st, 'over', '{"win":true,"place":1}'); end if;
  return st;
end $$;

-- runs the hand forward when nobody has to act: deals streets / settles
create or replace function cf_advance(p_code text, st jsonb) returns jsonb language plpgsql volatile as $$
declare sec cf_secret; n int; c int; i int; guard int := 0;
begin
  select * into sec from cf_secret where code = p_code;
  loop
    guard := guard + 1; exit when guard > 10;
    exit when st->'done' is not null and st->>'done' is not null;
    if cf_live(st) <= 1 then st := cf_finish(st, sec.hole); exit; end if;
    exit when cf_gi(st, 'turn') >= 0;
    select count(*) into n from jsonb_array_elements(st->'board') x where x <> 'null'::jsonb;
    if n >= 5 then st := cf_finish(st, sec.hole); exit; end if;
    c := case when n = 0 then 3 else 1 end;
    for i in n..n + c - 1 loop
      st := jsonb_set(st, array['board', i::text], sec.board->i);
    end loop;
    st := cf_set(st, 'betStreet', to_jsonb(case when n + c >= 5 then 3 when n + c = 4 then 2 else 1 end));
    st := cf_start_street(st);
  end loop;
  -- showdown: everyone who didn't fold shows their cards to the whole table
  if st->'done' is not null and st->>'done' is not null and coalesce((st->'done'->>'sd')::boolean, false) then
    insert into cf_shows(code, uid, hand, cards)
      select p_code, (x.s->>'pid')::uuid, cf_gi(st, 'handNo'), sec.hole->((x.i - 1)::text)
      from jsonb_array_elements(st->'seats') with ordinality x(s, i)
      where not coalesce((x.s->>'folded')::boolean, false) and sec.hole->((x.i - 1)::text) is not null
    on conflict do nothing;
  end if;
  return st;
end $$;

-- ---------------------------------------------------------------------
-- table lifecycle (called by the app)
-- ---------------------------------------------------------------------
create or replace function cf_save(p_code text, st jsonb) returns void language sql volatile as $$
  update cf_tables set state = st, ver = ver + 1, updated = now(),
    status = case when st->'over' is not null and st->>'over' is not null then 'over' when st->>'phase' = 'play' then 'play' else status end
  where code = p_code
$$;

create or replace function cf_lock(p_code text) returns cf_tables language plpgsql volatile as $$
declare t cf_tables;
begin
  select * into t from cf_tables where code = p_code for update;
  if not found then raise exception 'table not found'; end if;
  return t;
end $$;

create or replace function cf_profile(p_name text, p_img text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); nm text := left(coalesce(nullif(trim(p_name), ''), 'Hráč'), 24); im text := left(coalesce(p_img, ''), 60000);
begin
  insert into cf_profiles(uid, name, img, t) values (u, nm, im, now())
  on conflict (uid) do update set name = excluded.name, img = excluded.img, t = now();
  update cf_players set name = nm, img = im where uid = u;
  update cf_group_members set name = nm, img = im where uid = u;
end $$;

create or replace function cf_create(p_name text, p_cfg jsonb, p_gid uuid default null) returns text
language plpgsql security definer set search_path = public as $$
declare
  u uuid := cf_me(); v_code text; tries int := 0; alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  stack int := greatest(10, least(1000000, coalesce((p_cfg->>'stack')::int, 100)));
  lv int := greatest(0, least(9, coalesce((p_cfg->>'level')::int, 0)));
  every int := greatest(0, least(100, coalesce((p_cfg->>'every')::int, 10)));
  adv boolean := coalesce((p_cfg->>'advisor')::boolean, true);
  lb int[] := cf_level(lv); pr cf_profiles; st jsonb;
begin
  if p_gid is not null and not exists (select 1 from cf_group_members where gid = p_gid and uid = u) then
    raise exception 'not a group member';
  end if;
  loop
    v_code := (select string_agg(substr(alphabet, 1 + floor(random() * 32)::int, 1), '') from generate_series(1, 6));
    exit when not exists (select 1 from cf_tables t where t.code = v_code);
    tries := tries + 1; if tries > 20 then raise exception 'no free code'; end if;
  end loop;
  select * into pr from cf_profiles where uid = u;
  st := jsonb_build_object('mode', 'online', 'phase', 'setup', 'n', 1, 'dealer', 0, 'startLevel', lv, 'level', lv,
    'curLevel', lv, 'sb', lb[1], 'bb', lb[2], 'startStack', stack, 'autoBlinds', true, 'blindHands', every,
    'blindMin', 0, 'handNo', 0, 'board', '[null,null,null,null,null]'::jsonb, 'betStreet', 0, 'curBet', 0,
    'lastRaise', lb[2], 'turn', -1, 'turnAt', 0, 'doneAt', 0,
    'seats', jsonb_build_array(jsonb_build_object('pid', u, 'stack', stack, 'folded', false, 'allin', false,
      'bet', 0, 'total', 0, 'acted', false, 'actions', '[]'::jsonb, 'out', false, 'mood', '[]'::jsonb)));
  insert into cf_tables(code, host, name, gid, cfg, state)
  values (v_code, u, left(coalesce(p_name, ''), 40), p_gid, jsonb_build_object('stack', stack, 'level', lv, 'every', every, 'advisor', adv), st);
  insert into cf_players(code, uid, name, img) values (v_code, u, coalesce(pr.name, 'Hráč'), coalesce(pr.img, ''));
  return v_code;
end $$;

create or replace function cf_seat_obj(u uuid, stack int) returns jsonb language sql immutable as $$
  select jsonb_build_object('pid', u, 'stack', stack, 'folded', true, 'allin', false, 'bet', 0, 'total', 0,
    'acted', false, 'actions', '[]'::jsonb, 'out', false, 'mood', '[]'::jsonb)
$$;

create or replace function cf_join(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; pr cf_profiles; st jsonb; seated boolean;
begin
  t := cf_lock(upper(p_code));
  select * into pr from cf_profiles where uid = u;
  st := t.state;
  seated := exists (select 1 from jsonb_array_elements(st->'seats') s where s->>'pid' = u::text);
  if exists (select 1 from cf_players where code = t.code and uid = u and left_at is null) then
    return jsonb_build_object('ok', true, 'code', t.code);
  end if;
  if t.status = 'over' then raise exception 'game over'; end if;
  if (select count(*) from cf_players where code = t.code and left_at is null) >= 10 then raise exception 'table full'; end if;
  insert into cf_players(code, uid, name, img, pending)
  values (t.code, u, coalesce(pr.name, 'Hráč'), coalesce(pr.img, ''), st->>'phase' = 'play' and not seated)
  on conflict (code, uid) do update set left_at = null, pending = excluded.pending, seen = now(), name = excluded.name, img = excluded.img;
  if st->>'phase' = 'setup' and not seated then
    st := jsonb_set(st, '{seats}', (st->'seats') || jsonb_build_array(cf_seat_obj(u, cf_gi(st, 'startStack')) || '{"folded":false}'));
    st := cf_set(st, 'n', to_jsonb(jsonb_array_length(st->'seats')));
    perform cf_save(t.code, st);
  end if;
  return jsonb_build_object('ok', true, 'code', t.code);
end $$;

create or replace function cf_start(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb;
begin
  t := cf_lock(p_code);
  if t.host <> u then raise exception 'only the host can start'; end if;
  st := t.state;
  if st->>'phase' <> 'setup' then return; end if;
  if jsonb_array_length(st->'seats') < 2 then raise exception 'need two players'; end if;
  st := st || jsonb_build_object('phase', 'play', 'dealer', floor(random() * jsonb_array_length(st->'seats'))::int);
  st := cf_start_hand(t.code, st);
  perform cf_save(t.code, st);
end $$;

create or replace function cf_seat_of(st jsonb, u uuid) returns int language sql immutable as $$
  select coalesce((select (o - 1)::int from jsonb_array_elements(st->'seats') with ordinality e(s, o) where s->>'pid' = u::text limit 1), -1)
$$;

create or replace function cf_move(p_code text, p_hand int, p_type text, p_to int default 0) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb; i int;
begin
  if p_type not in ('fold', 'check', 'call', 'bet', 'raise', 'allin') then raise exception 'bad move'; end if;
  t := cf_lock(p_code);
  st := t.state;
  if st->>'phase' <> 'play' or cf_gi(st, 'handNo') <> p_hand then return; end if;
  i := cf_seat_of(st, u);
  if i < 0 or cf_gi(st, 'turn') <> i then return; end if;
  if p_type = 'bet' then p_type := 'raise'; end if;
  st := cf_act(st, i, p_type, p_to);
  st := cf_advance(t.code, st);
  perform cf_save(t.code, st);
end $$;

-- anybody may push the game on when the player to act has been idle for 60 s
create or replace function cf_timeout(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb; i int;
begin
  t := cf_lock(p_code);
  st := t.state;
  i := cf_gi(st, 'turn');
  if st->>'phase' <> 'play' or i < 0 or (st->>'done') is not null then return; end if;
  if cf_now() < (st->>'turnAt')::bigint + 60000 then return; end if;
  st := cf_act(st, i, case when cf_gi(st, 'curBet') > cf_si(st, i, 'bet') then 'fold' else 'call' end, 0);
  st := cf_advance(t.code, st);
  perform cf_save(t.code, st);
  perform u;
end $$;

-- next hand: the host at any time, everyone else 6 s after the hand ended
create or replace function cf_next(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb; seats jsonb := '[]'; s jsonb; dpid text; d int; p record; nst jsonb;
begin
  t := cf_lock(p_code);
  st := t.state;
  if st->>'phase' <> 'play' or (st->>'done') is null or (st->>'over') is not null then return; end if;
  if t.host <> u and cf_now() < (st->>'doneAt')::bigint + 6000 then return; end if;
  dpid := st->'seats'->cf_gi(st, 'dealer')->>'pid';
  -- drop players who left, add those who joined during the hand
  for s in select x from jsonb_array_elements(st->'seats') x loop
    if exists (select 1 from cf_players where code = t.code and uid = (s->>'pid')::uuid and left_at is null) then
      seats := seats || jsonb_build_array(s);
    end if;
  end loop;
  for p in select uid from cf_players where code = t.code and pending and left_at is null order by joined loop
    seats := seats || jsonb_build_array(cf_seat_obj(p.uid, cf_gi(st, 'startStack')));
  end loop;
  update cf_players set pending = false where code = t.code and pending;
  st := jsonb_set(st, '{seats}', seats);
  st := cf_set(st, 'n', to_jsonb(jsonb_array_length(seats)));
  d := coalesce((select (o - 1)::int from jsonb_array_elements(seats) with ordinality e(x, o) where x->>'pid' = dpid limit 1), 0);
  st := cf_set(st, 'dealer', to_jsonb(d));
  if jsonb_array_length(seats) < 2 then
    st := cf_set(st, 'over', '{"win":true,"place":1}');
    perform cf_save(t.code, st); return;
  end if;
  st := cf_set(st, 'dealer', to_jsonb(cf_nxt(st, d)));
  nst := cf_start_hand(t.code, st);
  if nst is null then st := cf_set(st, 'over', '{"win":true,"place":1}'); else st := nst; end if;
  perform cf_save(t.code, st);
end $$;

create or replace function cf_show(p_code text, p_hand int) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; i int;
begin
  select * into t from cf_tables where code = p_code;
  select * into sec from cf_secret where code = p_code;
  if t is null or sec is null or sec.hand <> p_hand or (t.state->>'done') is null then return; end if;
  i := cf_seat_of(t.state, u);
  if i < 0 or sec.hole->(i::text) is null then return; end if;
  insert into cf_shows(code, uid, hand, cards) values (p_code, u, p_hand, sec.hole->(i::text)) on conflict do nothing;
  update cf_tables set ver = ver + 1 where code = p_code;
end $$;

create or replace function cf_leave(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb; i int;
begin
  t := cf_lock(p_code);
  st := t.state;
  update cf_players set left_at = now() where code = t.code and uid = u;
  i := cf_seat_of(st, u);
  if i < 0 then return; end if;
  if st->>'phase' = 'setup' then
    st := jsonb_set(st, '{seats}', (st->'seats') - i);
    st := cf_set(st, 'n', to_jsonb(jsonb_array_length(st->'seats')));
    if t.host = u then
      update cf_tables set host = coalesce((select uid from cf_players where code = t.code and left_at is null order by joined limit 1), host) where code = t.code;
    end if;
  elsif (st->>'done') is null and not cf_sb(st, i, 'folded') then
    if cf_gi(st, 'turn') = i then st := cf_act(st, i, 'fold', 0);
    else st := cf_ss(st, i, 'folded', 'true');
      if cf_live(st) <= 1 then st := cf_set(st, 'turn', '-1'); end if;
    end if;
    st := cf_advance(t.code, st);
  end if;
  if t.host = u and st->>'phase' <> 'setup' then
    update cf_tables set host = coalesce((select uid from cf_players where code = t.code and left_at is null order by joined limit 1), host) where code = t.code;
  end if;
  perform cf_save(t.code, st);
end $$;

create or replace function cf_set_advisor(p_code text, p_on boolean) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  update cf_tables set cfg = cfg || jsonb_build_object('advisor', p_on), ver = ver + 1 where code = p_code and host = u;
end $$;

-- after the game is over the host can start again with the same players
create or replace function cf_restart(p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; st jsonb; seats jsonb := '[]'; p record;
begin
  t := cf_lock(p_code);
  if t.host <> u then raise exception 'only the host can restart'; end if;
  st := t.state;
  for p in select uid from cf_players where code = t.code and left_at is null order by joined loop
    seats := seats || jsonb_build_array(cf_seat_obj(p.uid, cf_gi(st, 'startStack')) || '{"folded":false}');
  end loop;
  update cf_players set pending = false where code = t.code;
  update cf_tables set cfg = cfg - 'cup' - 'cupName' - 'cupVote' where code = t.code;  -- a cup tournament needs a new vote
  st := st || jsonb_build_object('phase', 'setup', 'seats', seats, 'n', jsonb_array_length(seats), 'over', null, 'done', null,
    'handNo', 0, 'level', cf_gi(st, 'startLevel'), 'curLevel', cf_gi(st, 'startLevel'), 'turn', -1,
    'board', '[null,null,null,null,null]'::jsonb, 'betStreet', 0, 'curBet', 0);
  update cf_tables set status = 'lobby' where code = t.code;
  perform cf_save(t.code, st);
end $$;

-- everything a client needs, polled about once per second; state only when it changed
create or replace function cf_view(p_code text, p_ver int default 0) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; i int; v_hole jsonb := null; v_hand int;
begin
  select * into t from cf_tables where code = upper(p_code);
  if not found then return jsonb_build_object('ok', false, 'err', 'notfound'); end if;
  update cf_players set seen = now() where code = t.code and uid = u;
  update cf_profiles set seen = now() where uid = u;
  v_hand := cf_gi(t.state, 'handNo');
  i := cf_seat_of(t.state, u);
  if i >= 0 then
    select * into sec from cf_secret where code = t.code;
    if sec.hand = v_hand then v_hole := sec.hole->(i::text); end if;
  end if;
  return jsonb_build_object(
    'ok', true, 'code', t.code, 'ver', t.ver, 'now', cf_now(), 'host', t.host, 'name', t.name, 'gid', t.gid,
    'cfg', t.cfg, 'status', t.status, 'me', u,
    'state', case when t.ver <> p_ver then t.state else null end,
    'hole', jsonb_build_object('hand', v_hand, 'cards', v_hole),
    'players', (select coalesce(jsonb_agg(jsonb_build_object('uid', uid, 'name', name, 'img', case when p_ver = 0 or t.ver <> p_ver then img else null end,
                  'on', seen > now() - interval '15 seconds', 'pending', pending, 'left', left_at is not null) order by joined), '[]')
                from cf_players where code = t.code),
    'shows', (select coalesce(jsonb_agg(jsonb_build_object('uid', s.uid, 'cards', s.cards)), '[]') from cf_shows s where s.code = t.code and s.hand = v_hand)
  );
end $$;

-- ---------------------------------------------------------------------
-- groups
-- ---------------------------------------------------------------------
create or replace function cf_group_create(p_name text) returns uuid
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); g uuid; pr cf_profiles;
begin
  select * into pr from cf_profiles where uid = u;
  insert into cf_groups(name, owner) values (left(coalesce(nullif(trim(p_name), ''), 'Poker'), 40), u) returning id into g;
  insert into cf_group_members(gid, uid, name, img) values (g, u, coalesce(pr.name, 'Hráč'), coalesce(pr.img, ''));
  return g;
end $$;

create or replace function cf_group_join(p_gid uuid) returns text
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); pr cf_profiles; nm text;
begin
  select name into nm from cf_groups where id = p_gid;
  if nm is null then raise exception 'group not found'; end if;
  select * into pr from cf_profiles where uid = u;
  insert into cf_group_members(gid, uid, name, img) values (p_gid, u, coalesce(pr.name, 'Hráč'), coalesce(pr.img, ''))
  on conflict do nothing;
  return nm;
end $$;

create or replace function cf_group_leave(p_gid uuid) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  delete from cf_group_members where gid = p_gid and uid = u;
  delete from cf_groups g where g.id = p_gid and not exists (select 1 from cf_group_members m where m.gid = g.id);
end $$;

create or replace function cf_group_rename(p_gid uuid, p_name text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  update cf_groups set name = left(coalesce(nullif(trim(p_name), ''), name), 40) where id = p_gid and owner = u;
end $$;

-- my groups with members and the tables that are open right now
create or replace function cf_groups() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  return (select coalesce(jsonb_agg(jsonb_build_object(
      'id', g.id, 'name', g.name, 'owner', g.owner,
      'members', (select coalesce(jsonb_agg(jsonb_build_object('uid', m.uid, 'name', m.name, 'img', m.img) order by m.joined), '[]') from cf_group_members m where m.gid = g.id),
      'tables', (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'name', t.name, 'status', t.status,
                    'players', (select count(*) from cf_players p where p.code = t.code and p.left_at is null)) order by t.updated desc), '[]')
                 from cf_tables t where t.gid = g.id and t.status <> 'over' and t.updated > now() - interval '12 hours')
    ) order by g.created), '[]')
    from cf_groups g where exists (select 1 from cf_group_members m where m.gid = g.id and m.uid = u));
end $$;

-- my recent tables (to continue after closing the app)
create or replace function cf_my_tables() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  return (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'name', t.name, 'status', t.status, 'host', t.host = u,
      'players', (select count(*) from cf_players q where q.code = t.code and q.left_at is null)) order by t.updated desc), '[]')
    from cf_tables t join cf_players p on p.code = t.code and p.uid = u and p.left_at is null
    where t.status <> 'over' and t.updated > now() - interval '24 hours');
end $$;


-- ---------------------------------------------------------------------
-- friends and table invites
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists seen timestamptz not null default now();
create table if not exists cf_friends (uid uuid not null, fid uuid not null, t timestamptz not null default now(), primary key (uid, fid));
create table if not exists cf_invites (id bigserial primary key, to_uid uuid not null, from_uid uuid not null, code text not null, t timestamptz not null default now());
create index if not exists cf_invites_to on cf_invites(to_uid);
alter table cf_friends enable row level security;
alter table cf_invites enable row level security;
revoke all on cf_friends, cf_invites from anon, authenticated;

create or replace function cf_touch() returns void language sql security definer set search_path = public as $$
  update cf_profiles set seen = now() where uid = auth.uid()
$$;

create or replace function cf_friend_add(p_uid uuid) returns boolean
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  if p_uid = u or not exists (select 1 from cf_profiles where uid = p_uid) then return false; end if;
  insert into cf_friends(uid, fid) values (u, p_uid) on conflict do nothing;
  return true;
end $$;

create or replace function cf_friend_remove(p_uid uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from cf_friends where uid = cf_me() and fid = p_uid;
end $$;

-- my friends: online status and the table they sit at right now
create or replace function cf_friends() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  perform cf_touch();
  return (select coalesce(jsonb_agg(jsonb_build_object(
      'uid', p.uid, 'name', p.name, 'img', p.img, 'online', p.seen > now() - interval '3 minutes',
      'table', (select t.code from cf_tables t join cf_players q on q.code = t.code
                where q.uid = p.uid and q.left_at is null and q.seen > now() - interval '2 minutes' and t.status <> 'over'
                order by q.seen desc limit 1)
    ) order by (p.seen > now() - interval '3 minutes') desc, p.name), '[]')
    from cf_friends f join cf_profiles p on p.uid = f.fid where f.uid = u);
end $$;

create or replace function cf_invite(p_uid uuid, p_code text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  if not exists (select 1 from cf_tables where code = p_code) then raise exception 'table not found'; end if;
  delete from cf_invites where to_uid = p_uid and from_uid = u;
  insert into cf_invites(to_uid, from_uid, code) values (p_uid, u, p_code);
end $$;

-- invites for me from the last 30 minutes
create or replace function cf_inbox() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  perform cf_touch();
  delete from cf_invites where t < now() - interval '30 minutes';
  return (select coalesce(jsonb_agg(jsonb_build_object('id', i.id, 'code', i.code, 'from', coalesce(p.name, '?'), 'name', coalesce(t.name, ''))
      order by i.t desc), '[]')
    from cf_invites i left join cf_profiles p on p.uid = i.from_uid left join cf_tables t on t.code = i.code
    where i.to_uid = u and coalesce(t.status, 'over') <> 'over');
end $$;

create or replace function cf_inbox_dismiss(p_id bigint) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from cf_invites where id = p_id and to_uid = cf_me();
end $$;

-- keep-alive: the app calls this every couple of days so the free project never pauses
create table if not exists cf_ping_log (id int primary key, t timestamptz not null default now(), n bigint not null default 0);
alter table cf_ping_log enable row level security;
revoke all on cf_ping_log from anon, authenticated;
create or replace function cf_ping() returns bigint
language plpgsql security definer set search_path = public as $$
declare v bigint;
begin
  insert into cf_ping_log(id, t, n) values (1, now(), 1) on conflict (id) do update set t = now(), n = cf_ping_log.n + 1 returning n into v;
  delete from cf_tables where updated < now() - interval '7 days';
  return v;
end $$;

-- housekeeping: forget tables nobody touched for a week
create or replace function cf_cleanup() returns void language sql security definer set search_path = public as $$
  delete from cf_tables where updated < now() - interval '7 days';
$$;

-- ---------------------------------------------------------------------
-- emoji chat and drinks (paid from the wallet, never from chips in play)
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists bank int not null default 1000;
alter table cf_profiles add column if not exists bonus_day date;
create table if not exists cf_chat (
  id bigserial primary key, code text not null references cf_tables(code) on delete cascade,
  uid uuid not null, msg text not null, t timestamptz not null default now());
create index if not exists cf_chat_code on cf_chat(code, id);
create table if not exists cf_drinks (
  id bigserial primary key, code text references cf_tables(code) on delete cascade,
  from_uid uuid not null, to_uid uuid, kind text not null, price int not null, t timestamptz not null default now());
create index if not exists cf_drinks_code on cf_drinks(code, id);
alter table cf_chat enable row level security;
alter table cf_drinks enable row level security;
revoke all on cf_chat, cf_drinks from anon, authenticated;

create or replace function cf_drink_price(k text) returns int language sql immutable as $$
  select case k when 'coffee' then 20 when 'beer' then 50 when 'wine' then 80
                when 'cocktail' then 120 when 'whisky' then 150 when 'champagne' then 400
                when 'fries' then 30 when 'hotdog' then 40 when 'pizza' then 60 when 'burger' then 80
                when 'sushi' then 120 when 'cake' then 100 end
$$;

-- an emoji message: up to 5 indexes into the app's emoji set, e.g. '3,17,5'
create or replace function cf_say(p_code text, p_msg text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c text := upper(p_code);
begin
  if p_msg is null or p_msg !~ '^[0-9]{1,2}(,[0-9]{1,2}){0,4}$' then raise exception 'bad message'; end if;
  if exists (select 1 from unnest(string_to_array(p_msg, ',')) x where x::int > 63) then raise exception 'bad message'; end if;
  if not exists (select 1 from cf_players where code = c and uid = u and left_at is null) then raise exception 'not at table'; end if;
  if exists (select 1 from cf_chat where code = c and uid = u and t > now() - interval '1 second') then raise exception 'too fast'; end if;
  insert into cf_chat(code, uid, msg) values (c, u, p_msg);
  delete from cf_chat where code = c and t < now() - interval '5 minutes';
end $$;

-- buy a drink for someone at the table (p_code null = practice game vs computer, only pays)
create or replace function cf_drink(p_code text, p_to uuid, p_kind text) returns int
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c text := upper(p_code); pr int := cf_drink_price(p_kind); b int;
begin
  if pr is null then raise exception 'bad drink'; end if;
  if c is not null then
    if not exists (select 1 from cf_players where code = c and uid = u and left_at is null) then raise exception 'not at table'; end if;
    if p_to is null or not exists (select 1 from cf_players where code = c and uid = p_to and left_at is null) then raise exception 'player not at table'; end if;
  end if;
  perform cf_wallet();
  update cf_profiles set bank = bank - pr where uid = u and bank >= pr returning bank into b;
  if not found then raise exception 'no money'; end if;
  insert into cf_drinks(code, from_uid, to_uid, kind, price) values (c, u, case when c is null then null else p_to end, p_kind, pr);
  delete from cf_drinks where t < now() - interval '1 day';
  return b;
end $$;

create or replace function cf_view(p_code text, p_ver int default 0) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; i int; v_hole jsonb := null; v_hand int;
begin
  select * into t from cf_tables where code = upper(p_code);
  if not found then return jsonb_build_object('ok', false, 'err', 'notfound'); end if;
  update cf_players set seen = now() where code = t.code and uid = u;
  update cf_profiles set seen = now() where uid = u;
  v_hand := cf_gi(t.state, 'handNo');
  i := cf_seat_of(t.state, u);
  if i >= 0 then
    select * into sec from cf_secret where code = t.code;
    if sec.hand = v_hand then v_hole := sec.hole->(i::text); end if;
  end if;
  return jsonb_build_object(
    'ok', true, 'code', t.code, 'ver', t.ver, 'now', cf_now(), 'host', t.host, 'name', t.name, 'gid', t.gid,
    'cfg', t.cfg, 'status', t.status, 'me', u,
    'state', case when t.ver <> p_ver then t.state else null end,
    'hole', jsonb_build_object('hand', v_hand, 'cards', v_hole),
    'players', (select coalesce(jsonb_agg(jsonb_build_object('uid', cp.uid, 'name', cp.name, 'img', case when p_ver = 0 or t.ver <> p_ver then cp.img else null end,
                  'on', cp.seen > now() - interval '15 seconds', 'pending', cp.pending, 'left', cp.left_at is not null,
                  'hat', coalesce((select pr.hat from cf_profiles pr where pr.uid = cp.uid), '')) order by cp.joined), '[]')
                from cf_players cp where cp.code = t.code),
    'shows', (select coalesce(jsonb_agg(jsonb_build_object('uid', s.uid, 'cards', s.cards)), '[]') from cf_shows s where s.code = t.code and s.hand = v_hand)
  ) || cf_social(t.code);
end $$;

-- ---------------------------------------------------------------------
-- rewards: chips for practice wins (wallet), honor points and cups for
-- online games with friends, hall of fame
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists earn_day date;
alter table cf_profiles add column if not exists earned int not null default 0;
create table if not exists cf_honor (
  id bigserial primary key, uid uuid not null, pts int not null, kind text not null, code text,
  t timestamptz not null default now());
create index if not exists cf_honor_t on cf_honor(t);
create index if not exists cf_honor_uid on cf_honor(uid, t);
create table if not exists cf_trophies (
  id bigserial primary key, uid uuid not null, code text, name text not null default '', players int not null,
  tier text not null, t timestamptz not null default now());
create index if not exists cf_trophies_uid on cf_trophies(uid);
alter table cf_honor enable row level security;
alter table cf_trophies enable row level security;
revoke all on cf_honor, cf_trophies from anon, authenticated;

-- wallet + my honor summary; the first call of each day adds a bonus of 200
create or replace function cf_wallet() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); b int; bonus int := 0;
begin
  insert into cf_profiles(uid) values (u) on conflict do nothing;
  update cf_profiles set bank = bank + 200, bonus_day = current_date
    where uid = u and (bonus_day is null or bonus_day < current_date) returning bank into b;
  if found then bonus := 200; else select bank into b from cf_profiles where uid = u; end if;
  return jsonb_build_object('bank', b, 'bonus', bonus,
    'honor', (select coalesce(sum(h.pts), 0) from cf_honor h where h.uid = u),
    'cups', (select count(*) from cf_trophies x where x.uid = u),
    'hat', (select pr.hat from cf_profiles pr where pr.uid = u));
end $$;

-- chips won in practice games vs the computer go to the wallet (at most 300 a call, 1500 a day)
create or replace function cf_earn(p_amount int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); w jsonb; a int := least(greatest(coalesce(p_amount, 0), 0), 300); b int;
begin
  w := cf_wallet();
  update cf_profiles set earned = case when earn_day = current_date then earned else 0 end, earn_day = current_date where uid = u;
  select greatest(0, least(a, 1500 - earned)) into a from cf_profiles where uid = u;
  update cf_profiles set bank = bank + a, earned = earned + a where uid = u returning bank into b;
  return jsonb_build_object('bank', b, 'added', a, 'bonus', w->'bonus', 'honor', w->'honor', 'cups', w->'cups');
end $$;

-- after every online hand: +1 per pot won; a player who busts gets 10 per player behind him;
-- the last one standing gets 10 per opponent + 10; in a cup tournament (voted at the table) also a cup:
-- bronze 3, silver 4–6, gold 7–10 players
create or replace function cf_award() returns trigger
language plpgsql security definer set search_path = public as $$
declare st jsonb := new.state; n int; alive int := 0; busted uuid[] := '{}'; s jsonb; place int; w uuid;
begin
  if st->'done' is null or jsonb_typeof(st->'done') = 'null' then return new; end if;
  if old.state->'done' is not null and jsonb_typeof(old.state->'done') <> 'null'
     and old.state->>'handNo' = st->>'handNo' then return new; end if;
  n := jsonb_array_length(st->'seats');
  if n < 2 then return new; end if;
  insert into cf_honor(uid, pts, kind, code)
    select (st->'seats'->(x::int)->>'pid')::uuid, 1, 'hand', new.code from jsonb_array_elements_text(st->'done'->'w') x;
  for s in select x from jsonb_array_elements(st->'seats') x loop
    if coalesce((s->>'out')::boolean, false) then continue; end if;
    if coalesce((s->>'stack')::int, 0) > 0 then alive := alive + 1; else busted := busted || (s->>'pid')::uuid; end if;
  end loop;
  place := alive + 1;
  if n - place > 0 then
    insert into cf_honor(uid, pts, kind, code) select b, (n - place) * 10, 'place', new.code from unnest(busted) b;
  end if;
  if alive = 1 then
    select (x->>'pid')::uuid into w from jsonb_array_elements(st->'seats') x
      where not coalesce((x->>'out')::boolean, false) and coalesce((x->>'stack')::int, 0) > 0;
    insert into cf_honor(uid, pts, kind, code) values (w, (n - 1) * 10 + 10, 'win', new.code);
    if coalesce((new.cfg->>'cup')::boolean, false) then
      insert into cf_trophies(uid, code, name, players, tier)
        values (w, new.code, coalesce(new.cfg->>'cupName', new.name), n, case when n >= 7 then 'gold' when n >= 4 then 'silver' else 'bronze' end);
    end if;
  end if;
  return new;
end $$;
drop trigger if exists cf_tables_award on cf_tables;
create trigger cf_tables_award after update of state on cf_tables for each row execute function cf_award();

-- hall of fame: p_period week | month | all, p_scope all | friends
create or replace function cf_fame(p_period text, p_scope text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); since timestamptz; prev timestamptz; mine int; r int; sc uuid[]; fr boolean := p_scope = 'friends';
begin
  since := case p_period when 'week' then date_trunc('week', now()) when 'month' then date_trunc('month', now()) else '-infinity'::timestamptz end;
  prev := case p_period when 'week' then since - interval '1 week' when 'month' then since - interval '1 month' else null end;
  sc := array(select fid from cf_friends where cf_friends.uid = u) || u;
  select coalesce(sum(h.pts), 0) into mine from cf_honor h where h.uid = u and h.t >= since;
  select count(*) + 1 into r from (select h.uid from cf_honor h where h.t >= since
      and (not fr or h.uid = any(sc))
      group by h.uid having sum(h.pts) > mine) q;
  return jsonb_build_object(
    'top', (select coalesce(jsonb_agg(q.o order by q.p desc, q.n), '[]') from (
        select sum(h.pts) p, coalesce(max(pr.name), '?') n,
          jsonb_build_object('uid', h.uid, 'name', coalesce(max(pr.name), '?'), 'img', coalesce(max(pr.img), ''), 'pts', sum(h.pts),
            'hands', count(*) filter (where h.kind = 'hand'), 'wins', count(*) filter (where h.kind = 'win')) o
        from cf_honor h left join cf_profiles pr on pr.uid = h.uid
        where h.t >= since and (not fr or h.uid = any(sc))
        group by h.uid order by sum(h.pts) desc limit 30) q),
    'me', jsonb_build_object('pts', mine, 'rank', case when mine > 0 then r else null end),
    'champ', case when prev is null then null else (
        select jsonb_build_object('name', coalesce(max(pr.name), '?'), 'pts', sum(h.pts))
        from cf_honor h left join cf_profiles pr on pr.uid = h.uid
        where h.t >= prev and h.t < since and (not fr or h.uid = any(sc))
        group by h.uid order by sum(h.pts) desc limit 1) end,
    'cups', (select coalesce(jsonb_agg(jsonb_build_object('name', x.name, 'players', x.players, 'tier', x.tier,
               't', (extract(epoch from x.t) * 1000)::bigint) order by x.t desc), '[]')
             from (select * from cf_trophies where cf_trophies.uid = u order by t desc limit 24) x),
    'total', (select coalesce(sum(h.pts), 0) from cf_honor h where h.uid = u)
  );
end $$;

-- ---------------------------------------------------------------------
-- cup tournaments: a special event the players at the table vote for
-- (before the start, at least 3 players, more than half must agree)
-- ---------------------------------------------------------------------
create table if not exists cf_cup_votes (
  code text not null references cf_tables(code) on delete cascade, uid uuid not null, yes boolean not null,
  primary key (code, uid));
alter table cf_cup_votes enable row level security;
revoke all on cf_cup_votes from anon, authenticated;

create or replace function cf_cup_check(c text) returns void
language plpgsql security definer set search_path = public as $$
declare t cf_tables; act int; y int; nn int;
begin
  select * into t from cf_tables where code = c;
  if t.cfg->'cupVote' is null or jsonb_typeof(t.cfg->'cupVote') = 'null' then return; end if;
  select count(*) into act from cf_players where code = c and left_at is null;
  select count(*) filter (where v.yes), count(*) filter (where not v.yes) into y, nn
    from cf_cup_votes v join cf_players p on p.code = v.code and p.uid = v.uid and p.left_at is null where v.code = c;
  if y * 2 > act and act >= 3 then
    update cf_tables set cfg = (cfg - 'cupVote') || jsonb_build_object('cup', true, 'cupName', t.cfg->'cupVote'->>'name'), updated = now() where code = c;
    delete from cf_cup_votes where code = c;
  elsif nn * 2 >= act then
    update cf_tables set cfg = cfg - 'cupVote', updated = now() where code = c;
    delete from cf_cup_votes where code = c;
  end if;
end $$;

create or replace function cf_cup_propose(p_code text, p_name text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; nm text;
begin
  t := cf_lock(upper(p_code));
  if not exists (select 1 from cf_players where code = t.code and uid = u and left_at is null) then raise exception 'not at table'; end if;
  if t.state->>'phase' <> 'setup' then raise exception 'already started'; end if;
  if coalesce((t.cfg->>'cup')::boolean, false) then return; end if;
  if (select count(*) from cf_players where code = t.code and left_at is null) < 3 then raise exception 'need three'; end if;
  nm := left(coalesce(nullif(trim(p_name), ''), nullif(t.name, ''), 'Turnaj o pohár'), 40);
  update cf_tables set cfg = cfg || jsonb_build_object('cupVote', jsonb_build_object('name', nm, 'by', u)), updated = now() where code = t.code;
  delete from cf_cup_votes where code = t.code;
  insert into cf_cup_votes(code, uid, yes) values (t.code, u, true);
  perform cf_cup_check(t.code);
end $$;

create or replace function cf_cup_vote(p_code text, p_yes boolean) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables;
begin
  t := cf_lock(upper(p_code));
  if t.cfg->'cupVote' is null or jsonb_typeof(t.cfg->'cupVote') = 'null' or t.state->>'phase' <> 'setup' then return; end if;
  if not exists (select 1 from cf_players where code = t.code and uid = u and left_at is null) then raise exception 'not at table'; end if;
  insert into cf_cup_votes(code, uid, yes) values (t.code, u, coalesce(p_yes, false))
    on conflict (code, uid) do update set yes = excluded.yes;
  perform cf_cup_check(t.code);
end $$;

-- recent emoji, drinks and the running cup vote for cf_view
create or replace function cf_social(p_code text) returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'chat', (select coalesce(jsonb_agg(jsonb_build_object('id', c.id, 'uid', c.uid, 'm', c.msg) order by c.id), '[]')
             from cf_chat c where c.code = p_code and c.t > now() - interval '20 seconds'),
    'drinks', (select coalesce(jsonb_agg(x.o order by x.id), '[]') from (
               select d.id, jsonb_build_object('id', d.id, 'from', d.from_uid, 'to', d.to_uid, 'kind', d.kind,
                        'age', (extract(epoch from now() - d.t) * 1000)::bigint) o
               from cf_drinks d where d.code = p_code and d.t > now() - interval '15 minutes' order by d.id desc limit 40) x),
    'cupv', (select coalesce(jsonb_agg(jsonb_build_object('uid', v.uid, 'yes', v.yes)), '[]') from cf_cup_votes v where v.code = p_code))
$$;

-- ---------------------------------------------------------------------
-- hats (worn on the avatar, kept forever) and a short text chat
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists hat text not null default '';
create table if not exists cf_hats (
  id bigserial primary key, uid uuid not null, kind text not null, from_uid uuid, t timestamptz not null default now());
create index if not exists cf_hats_uid on cf_hats(uid);
alter table cf_hats enable row level security;
revoke all on cf_hats from anon, authenticated;

create or replace function cf_hat_price(k text) returns int language sql immutable as $$
  select case k when 'cap' then 100 when 'straw' then 150 when 'grad' then 200 when 'top' then 300
                when 'helmet' then 120 when 'crown' then 1000 end
$$;

-- buy a hat: for someone at the table (p_code + p_to), for myself (p_self), or for a computer player (only pays)
create or replace function cf_hat_buy(p_code text, p_to uuid, p_kind text, p_self boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c text := upper(p_code); pr int := cf_hat_price(p_kind); b int; who uuid;
begin
  if pr is null then raise exception 'bad hat'; end if;
  who := case when coalesce(p_self, false) then u else p_to end;
  if c is not null then
    if not exists (select 1 from cf_players where code = c and uid = u and left_at is null) then raise exception 'not at table'; end if;
    if who is null or not exists (select 1 from cf_players where code = c and uid = who and left_at is null) then raise exception 'player not at table'; end if;
  end if;
  perform cf_wallet();
  update cf_profiles set bank = bank - pr where uid = u and bank >= pr returning bank into b;
  if not found then raise exception 'no money'; end if;
  if who is not null then
    insert into cf_profiles(uid) values (who) on conflict do nothing;
    insert into cf_hats(uid, kind, from_uid) values (who, p_kind, u);
    update cf_profiles set hat = p_kind where uid = who;
    if c is not null then
      insert into cf_drinks(code, from_uid, to_uid, kind, price) values (c, u, who, 'hat:' || p_kind, pr);
      update cf_tables set ver = ver + 1 where code = c;
    end if;
  end if;
  return jsonb_build_object('bank', b, 'hat', (select hat from cf_profiles where uid = u));
end $$;

-- my hats and the one I'm wearing; p_kind '' = take it off
create or replace function cf_hat_wear(p_kind text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  if coalesce(p_kind, '') <> '' and not exists (select 1 from cf_hats where uid = u and kind = p_kind) then raise exception 'not your hat'; end if;
  update cf_profiles set hat = coalesce(p_kind, '') where uid = u;
  update cf_tables t set ver = ver + 1 where exists (select 1 from cf_players p where p.code = t.code and p.uid = u and p.left_at is null);
end $$;

create or replace function cf_my_hats() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'hat', coalesce((select hat from cf_profiles where uid = cf_me()), ''),
    'hats', (select coalesce(jsonb_agg(distinct kind), '[]') from cf_hats where uid = cf_me()))
$$;

-- short text message (max 60 characters, one every 3 seconds); stored as 't:<text>'
create or replace function cf_say_text(p_code text, p_text text) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c text := upper(p_code); m text := btrim(regexp_replace(coalesce(p_text, ''), '\s+', ' ', 'g'));
begin
  if m = '' or char_length(m) > 60 then raise exception 'bad message'; end if;
  if not exists (select 1 from cf_players where code = c and uid = u and left_at is null) then raise exception 'not at table'; end if;
  if exists (select 1 from cf_chat where code = c and uid = u and t > now() - interval '3 seconds') then raise exception 'too fast'; end if;
  insert into cf_chat(code, uid, msg) values (c, u, 't:' || m);
  delete from cf_chat where code = c and t < now() - interval '5 minutes';
end $$;

-- ---------------------------------------------------------------------
-- friend codes: share your code or link, the other one adds you (both become friends)
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists fcode text;
create unique index if not exists cf_profiles_fcode on cf_profiles(fcode);

create or replace function cf_friend_code() returns text
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c text; abc text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; i int; tries int := 0;
begin
  insert into cf_profiles(uid) values (u) on conflict do nothing;
  select fcode into c from cf_profiles where uid = u;
  while c is null loop
    tries := tries + 1; if tries > 50 then raise exception 'no code'; end if;
    c := '';
    for i in 1..6 loop c := c || substr(abc, 1 + floor(random() * length(abc))::int, 1); end loop;
    begin
      update cf_profiles set fcode = c where uid = u;
    exception when unique_violation then c := null;
    end;
  end loop;
  return c;
end $$;

create or replace function cf_friend_add_code(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); f cf_profiles;
begin
  select * into f from cf_profiles where fcode = upper(btrim(p_code));
  if not found then raise exception 'no such friend code'; end if;
  if f.uid = u then raise exception 'own friend code'; end if;
  insert into cf_profiles(uid) values (u) on conflict do nothing;
  insert into cf_friends(uid, fid) values (u, f.uid) on conflict do nothing;
  insert into cf_friends(uid, fid) values (f.uid, u) on conflict do nothing;
  return jsonb_build_object('uid', f.uid, 'name', f.name);
end $$;

-- ---------------------------------------------------------------------
-- delete my online profile and everything tied to it (Google Play requirement)
-- ---------------------------------------------------------------------
create or replace function cf_delete_me() returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); c record;
begin
  for c in select code from cf_players where uid = u and left_at is null loop
    begin perform cf_leave(c.code); exception when others then null; end;
  end loop;
  update cf_players set name = '', img = '' where uid = u;
  delete from cf_group_members where uid = u;
  delete from cf_groups g where g.owner = u and not exists (select 1 from cf_group_members m where m.gid = g.id);
  delete from cf_friends where uid = u or fid = u;
  delete from cf_invites where to_uid = u or from_uid = u;
  delete from cf_honor where uid = u;
  delete from cf_trophies where uid = u;
  delete from cf_chat where uid = u;
  delete from cf_drinks where from_uid = u or to_uid = u;
  delete from cf_cup_votes where uid = u;
  delete from cf_hats where uid = u;
  delete from cf_profiles where uid = u;
end $$;

-- ---------------------------------------------------------------------
-- peek: a player who folded may see the hole cards of the players
-- who allow it in their profile (cf_profiles.peek)
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists peek boolean not null default false;

create or replace function cf_set_peek(p boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  update cf_profiles set peek = coalesce(p, false) where uid = cf_me();
end $$;

create or replace function cf_peek(t cf_tables, u uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare i int; sec cf_secret; v_hand int; r jsonb;
begin
  i := cf_seat_of(t.state, u);
  if i < 0 or coalesce((t.state->'seats'->i->>'folded')::boolean, false) = false then return '[]'::jsonb; end if;
  if jsonb_typeof(t.state->'done') = 'object' then return '[]'::jsonb; end if;
  v_hand := cf_gi(t.state, 'handNo');
  select * into sec from cf_secret where code = t.code;
  if not found or sec.hand <> v_hand then return '[]'::jsonb; end if;
  select coalesce(jsonb_agg(jsonb_build_object('uid', e.s->>'pid', 'cards', sec.hole->((e.o - 1)::text))), '[]'::jsonb) into r
    from jsonb_array_elements(t.state->'seats') with ordinality e(s, o)
    join cf_profiles pr on pr.uid::text = e.s->>'pid'
    where (e.o - 1) <> i and pr.peek
      and coalesce((e.s->>'folded')::boolean, false) = false
      and sec.hole ? ((e.o - 1)::text);
  return r;
end $$;

-- ---------------------------------------------------------------------
-- "show me a card": ask an opponent to show one hole card; he picks
-- which one (everyone at the table sees it) or declines
-- ---------------------------------------------------------------------
create table if not exists cf_asks (
  code text not null references cf_tables(code) on delete cascade,
  hand int not null,
  from_uid uuid not null,
  to_uid uuid not null,
  status text not null default 'ask',   -- ask | shown | no
  card int,
  at timestamptz not null default now(),
  primary key (code, hand, from_uid, to_uid)
);
alter table cf_asks enable row level security;

create or replace function cf_ask_card(p_code text, p_to uuid) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; j int; v_hand int;
begin
  select * into t from cf_tables where code = upper(p_code);
  if not found then raise exception 'table not found'; end if;
  if u = p_to or cf_seat_of(t.state, u) < 0 then raise exception 'not allowed'; end if;
  j := cf_seat_of(t.state, p_to);
  v_hand := cf_gi(t.state, 'handNo');
  select * into sec from cf_secret where code = t.code;
  if j < 0 or not found or sec.hand <> v_hand or not (sec.hole ? (j::text)) then raise exception 'no cards'; end if;
  insert into cf_asks(code, hand, from_uid, to_uid) values (t.code, v_hand, u, p_to) on conflict do nothing;
end $$;

-- p_idx 0 or 1 = show that card, anything else = decline
create or replace function cf_ask_answer(p_code text, p_from uuid, p_idx int) returns void
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; i int; v_hand int; c int;
begin
  select * into t from cf_tables where code = upper(p_code);
  if not found then return; end if;
  v_hand := cf_gi(t.state, 'handNo');
  i := cf_seat_of(t.state, u);
  select * into sec from cf_secret where code = t.code;
  if p_idx in (0, 1) and i >= 0 and found and sec.hand = v_hand and sec.hole ? (i::text) then
    c := (sec.hole->(i::text)->>p_idx)::int;
    update cf_asks set status = 'shown', card = c where code = t.code and hand = v_hand and from_uid = p_from and to_uid = u and status = 'ask';
    -- one shown card is enough for everybody who asked this hand
    update cf_asks set status = 'shown', card = c where code = t.code and hand = v_hand and to_uid = u and status = 'ask';
  else
    update cf_asks set status = 'no' where code = t.code and hand = v_hand and from_uid = p_from and to_uid = u and status = 'ask';
  end if;
end $$;

create or replace function cf_asks_view(t cf_tables, u uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_hand int := cf_gi(t.state, 'handNo');
begin
  return jsonb_build_object(
    'asks', (select coalesce(jsonb_agg(jsonb_build_object('from', a.from_uid, 'name', coalesce(p.name, '?'))), '[]'::jsonb)
             from cf_asks a left join cf_players p on p.code = a.code and p.uid = a.from_uid
             where a.code = t.code and a.hand = v_hand and a.to_uid = u and a.status = 'ask'),
    'ones', (select coalesce(jsonb_agg(distinct jsonb_build_object('uid', a.to_uid, 'card', a.card)), '[]'::jsonb)
             from cf_asks a where a.code = t.code and a.hand = v_hand and a.status = 'shown'),
    'asknos', (select coalesce(jsonb_agg(a.to_uid), '[]'::jsonb)
             from cf_asks a where a.code = t.code and a.hand = v_hand and a.from_uid = u and a.status = 'no')
  );
end $$;

create or replace function cf_view(p_code text, p_ver int default 0) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); t cf_tables; sec cf_secret; i int; v_hole jsonb := null; v_hand int;
begin
  select * into t from cf_tables where code = upper(p_code);
  if not found then return jsonb_build_object('ok', false, 'err', 'notfound'); end if;
  update cf_players set seen = now() where code = t.code and uid = u;
  update cf_profiles set seen = now() where uid = u;
  v_hand := cf_gi(t.state, 'handNo');
  i := cf_seat_of(t.state, u);
  if i >= 0 then
    select * into sec from cf_secret where code = t.code;
    if sec.hand = v_hand then v_hole := sec.hole->(i::text); end if;
  end if;
  return jsonb_build_object(
    'ok', true, 'code', t.code, 'ver', t.ver, 'now', cf_now(), 'host', t.host, 'name', t.name, 'gid', t.gid,
    'cfg', t.cfg, 'status', t.status, 'me', u,
    'state', case when t.ver <> p_ver then t.state else null end,
    'hole', jsonb_build_object('hand', v_hand, 'cards', v_hole),
    'players', (select coalesce(jsonb_agg(jsonb_build_object('uid', cp.uid, 'name', cp.name, 'img', case when p_ver = 0 or t.ver <> p_ver then cp.img else null end,
                  'on', cp.seen > now() - interval '15 seconds', 'pending', cp.pending, 'left', cp.left_at is not null,
                  'hat', coalesce((select pr.hat from cf_profiles pr where pr.uid = cp.uid), '')) order by cp.joined), '[]')
                from cf_players cp where cp.code = t.code),
    'shows', (select coalesce(jsonb_agg(jsonb_build_object('uid', s.uid, 'cards', s.cards)), '[]') from cf_shows s where s.code = t.code and s.hand = v_hand),
    'peek', cf_peek(t, u)
  ) || cf_social(t.code) || cf_asks_view(t, u);
end $$;

-- ---------------------------------------------------------------------
-- advent calendar (1–24 December): one window a day, the same item for
-- everybody; items are wearable like hats (stored in cf_hats)
-- ---------------------------------------------------------------------
create table if not exists cf_advent (
  uid uuid not null,
  day int not null,
  t timestamptz not null default now(),
  primary key (uid, day)
);
alter table cf_advent enable row level security;
revoke all on cf_advent from anon, authenticated;

create or replace function cf_advent_item(d int) returns text language sql immutable as $$
  select (array['santa','mustache','crescent','paperhat','devil','beer','cowboy','bowtie','beanie','whisky','bowler','slippers',
                'nightcap','shades','tophat','extinguisher','captain','viking','antlers','beard','gift','crown','carp','dinner'])[d]
$$;

create or replace function cf_today() returns date language sql stable as $$
  select coalesce(nullif(current_setting('test.today', true), '')::date, (now() at time zone 'Europe/Prague')::date)
$$;

create or replace function cf_advent_open(p_day int) returns text
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); d date := cf_today(); k text;
begin
  if u is null then raise exception 'not signed in'; end if;
  if p_day < 1 or p_day > 24 then raise exception 'bad day'; end if;
  if extract(month from d) <> 12 or p_day > extract(day from d) then raise exception 'not yet'; end if;
  k := cf_advent_item(p_day);
  insert into cf_advent(uid, day) values (u, p_day) on conflict do nothing;
  if not exists (select 1 from cf_hats where uid = u and kind = k) then insert into cf_hats(uid, kind) values (u, k); end if;
  return k;
end $$;

-- remove what was opened while testing in November 2026 (safe to run again: it only touches November)
update cf_profiles set hat = '' where hat in (select cf_advent_item(g) from generate_series(1, 24) g)
  and not exists (select 1 from cf_hats h where h.uid = cf_profiles.uid and h.kind = cf_profiles.hat and h.t >= '2026-12-01');
delete from cf_hats where t < '2026-12-01' and kind in (select cf_advent_item(g) from generate_series(1, 24) g);
delete from cf_advent where t < '2026-12-01';

create or replace function cf_advent_days() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(day order by day), '[]'::jsonb) from cf_advent where uid = cf_me()
$$;

-- ---------------------------------------------------------------------
-- invites and duel challenges, also for a set time (at least 15 minutes
-- ahead); friends answer yes / no (also straight from the notification)
-- ---------------------------------------------------------------------
alter table cf_invites add column if not exists at timestamptz;
alter table cf_invites add column if not exists kind text not null default 'invite';
alter table cf_invites add column if not exists ans text;

create or replace function cf_invite_at(p_uid uuid, p_code text, p_at timestamptz, p_kind text default 'duel') returns bigint
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); v bigint;
begin
  if not exists (select 1 from cf_tables where code = p_code) then raise exception 'table not found'; end if;
  if p_at is not null and p_at < now() + interval '14 minutes' then raise exception 'at least 15 minutes ahead'; end if;
  if p_at is not null and p_at > now() + interval '7 days' then raise exception 'too far ahead'; end if;
  delete from cf_invites where to_uid = p_uid and from_uid = u and code = p_code;
  insert into cf_invites(to_uid, from_uid, code, at, kind) values (p_uid, u, p_code, p_at, case when p_kind = 'duel' then 'duel' else 'invite' end)
    returning id into v;
  return v;
end $$;

-- invites for me: unanswered or accepted, until 30 minutes after they were sent / after their time
create or replace function cf_inbox() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  perform cf_touch();
  delete from cf_invites where coalesce(at, t) < now() - interval '30 minutes';
  return (select coalesce(jsonb_agg(jsonb_build_object('id', i.id, 'code', i.code, 'from', coalesce(p.name, '?'), 'name', coalesce(t.name, ''),
                  'at', i.at, 'kind', i.kind, 'ans', i.ans) order by coalesce(i.at, i.t)), '[]')
    from cf_invites i left join cf_profiles p on p.uid = i.from_uid left join cf_tables t on t.code = i.code
    where i.to_uid = u and coalesce(i.ans, '') <> 'no' and coalesce(t.status, 'over') <> 'over');
end $$;

create or replace function cf_invite_answer(p_id bigint, p_yes boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  update cf_invites set ans = case when p_yes then 'yes' else 'no' end where id = p_id and to_uid = cf_me();
end $$;

-- what I sent: answers and upcoming games
create or replace function cf_outbox() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  return (select coalesce(jsonb_agg(jsonb_build_object('id', i.id, 'code', i.code, 'to', coalesce(p.name, '?'), 'at', i.at, 'kind', i.kind, 'ans', i.ans)
                  order by coalesce(i.at, i.t)), '[]')
    from cf_invites i left join cf_profiles p on p.uid = i.to_uid
    where i.from_uid = u and coalesce(i.at, i.t) > now() - interval '30 minutes');
end $$;

-- ---------------------------------------------------------------------
-- everybody with the app is everybody's friend (a private app for friends):
-- no adding by hand, and it works back in time for all existing players
-- ---------------------------------------------------------------------
create or replace function cf_friends() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me();
begin
  perform cf_touch();
  return (select coalesce(jsonb_agg(jsonb_build_object(
      'uid', p.uid, 'name', p.name, 'img', p.img, 'online', p.seen > now() - interval '3 minutes',
      'table', (select t.code from cf_tables t join cf_players q on q.code = t.code
                where q.uid = p.uid and q.left_at is null and q.seen > now() - interval '2 minutes' and t.status <> 'over'
                order by q.seen desc limit 1)
    ) order by (p.seen > now() - interval '3 minutes') desc, p.seen desc), '[]')
    -- one entry per name: after a reinstall only the newest profile shows, and never my own old one
    from (select distinct on (lower(btrim(x.name))) x.*
            from cf_profiles x
           where x.uid <> u and coalesce(btrim(x.name), '') <> '' and x.name not like 'Test %'
             and lower(btrim(x.name)) <> lower(btrim(coalesce((select me.name from cf_profiles me where me.uid = u), '')))
           order by lower(btrim(x.name)), x.seen desc) p);
end $$;

-- ---------------------------------------------------------------------
-- one profile per person: players sign in with their Google account, so the
-- profile survives reinstalls; only listed testers (cf_allowed) are shown
-- ---------------------------------------------------------------------
alter table cf_profiles add column if not exists email text;
create unique index if not exists cf_profiles_email on cf_profiles(lower(email)) where email is not null;

-- the testers' Google e-mails; while this table is empty, every Google-signed player is shown
create table if not exists cf_allowed (email text primary key);
alter table cf_allowed enable row level security;
revoke all on cf_allowed from anon, authenticated;

-- e-mail of the signed-in user (from the login token)
create or replace function cf_email() returns text language sql stable as $$
  select nullif(lower(coalesce(current_setting('request.jwt.claims', true)::jsonb ->> 'email', '')), '')
$$;

-- is this player a real, listed person (not a test or anonymous account)?
create or replace function cf_visible(p uuid) returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from cf_profiles x where x.uid = p and x.email is not null
                 and (not exists (select 1 from cf_allowed) or exists (select 1 from cf_allowed a where lower(a.email) = lower(x.email))))
$$;

-- remember my e-mail on my profile whenever I'm signed in with Google
create or replace function cf_touch_email() returns void language plpgsql security definer set search_path = public as $$
declare e text := cf_email(); u uuid := cf_me();
begin
  if e is null or u is null then return; end if;
  update cf_profiles set email = e where uid = u and (email is distinct from e)
    and not exists (select 1 from cf_profiles o where lower(o.email) = e and o.uid <> u);
end $$;

-- move everything that belongs to a profile (wallet, honor, cups, hats, advent, stats) to another account
create or replace function cf_move_profile(p_old uuid, p_new uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_old = p_new then return; end if;
  if exists (select 1 from cf_profiles where uid = p_new) then
    -- the new account already has a profile: add the old wallet to it, keep its name
    update cf_profiles n set bank = n.bank + coalesce(o.bank, 0), earned = greatest(n.earned, o.earned),
           img = case when n.img = '' then o.img else n.img end
      from cf_profiles o where n.uid = p_new and o.uid = p_old;
    delete from cf_profiles where uid = p_old;
  else
    update cf_profiles set uid = p_new where uid = p_old;
  end if;
  update cf_honor set uid = p_new where uid = p_old;
  update cf_trophies set uid = p_new where uid = p_old;
  update cf_hats set uid = p_new where uid = p_old;
  update cf_hats set from_uid = p_new where from_uid = p_old;
  delete from cf_advent a where a.uid = p_old and exists (select 1 from cf_advent b where b.uid = p_new and b.day = a.day);
  update cf_advent set uid = p_new where uid = p_old;
end $$;
revoke all on function cf_move_profile(uuid, uuid) from public, anon, authenticated;

-- a phone that still has its old (anonymous) profile hands it over to the Google account:
-- with the old login it asks for a one-time code, with the new login it claims it
create table if not exists cf_links (code text primary key, uid uuid not null, t timestamptz not null default now());
alter table cf_links enable row level security;
revoke all on cf_links from anon, authenticated;

create or replace function cf_link_code() returns text language plpgsql security definer set search_path = public as $$
declare c text := md5(random()::text || clock_timestamp()::text || coalesce(cf_me()::text, ''));
begin
  delete from cf_links where t < now() - interval '1 hour';
  insert into cf_links(code, uid) values (c, cf_me());
  return c;
end $$;

create or replace function cf_link_claim(p_code text) returns boolean language plpgsql security definer set search_path = public as $$
declare o uuid; u uuid := cf_me();
begin
  if cf_email() is null then raise exception 'sign in with Google first'; end if;
  select uid into o from cf_links where code = p_code and t > now() - interval '1 hour';
  if o is null then return false; end if;
  delete from cf_links where code = p_code;
  -- only an anonymous profile can be handed over
  if exists (select 1 from cf_profiles where uid = o and email is not null) then return false; end if;
  perform cf_move_profile(o, u);
  perform cf_touch_email();
  return true;
end $$;

-- my profile as the server knows it (used right after signing in on a new phone)
create or replace function cf_me_profile() returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform cf_touch_email();
  return (select jsonb_build_object('name', name, 'img', img, 'email', email) from cf_profiles where uid = cf_me());
end $$;

-- friends: listed people only, one per e-mail, never me
create or replace function cf_friends() returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); me text;
begin
  perform cf_touch();
  perform cf_touch_email();
  select lower(email) into me from cf_profiles where uid = u;
  return (select coalesce(jsonb_agg(jsonb_build_object(
      'uid', p.uid, 'name', p.name, 'img', p.img, 'online', p.seen > now() - interval '3 minutes',
      'table', (select t.code from cf_tables t join cf_players q on q.code = t.code
                where q.uid = p.uid and q.left_at is null and q.seen > now() - interval '2 minutes' and t.status <> 'over'
                order by q.seen desc limit 1)
    ) order by (p.seen > now() - interval '3 minutes') desc, p.seen desc), '[]')
    from (select distinct on (lower(x.email)) x.*
            from cf_profiles x
           where x.uid <> u and cf_visible(x.uid) and coalesce(btrim(x.name), '') <> ''
             and lower(x.email) is distinct from me
           order by lower(x.email), x.seen desc) p);
end $$;

create or replace function cf_fame(p_period text, p_scope text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare u uuid := cf_me(); since timestamptz; prev timestamptz; mine int; r int; sc uuid[]; fr boolean := p_scope = 'friends';
begin
  since := case p_period when 'week' then date_trunc('week', now()) when 'month' then date_trunc('month', now()) else '-infinity'::timestamptz end;
  prev := case p_period when 'week' then since - interval '1 week' when 'month' then since - interval '1 month' else null end;
  sc := array(select fid from cf_friends where cf_friends.uid = u) || u;
  select coalesce(sum(h.pts), 0) into mine from cf_honor h where h.uid = u and h.t >= since;
  select count(*) + 1 into r from (select h.uid from cf_honor h where h.t >= since and cf_visible(h.uid)
      and (not fr or h.uid = any(sc))
      group by h.uid having sum(h.pts) > mine) q;
  return jsonb_build_object(
    'top', (select coalesce(jsonb_agg(q.o order by q.p desc, q.n), '[]') from (
        select sum(h.pts) p, coalesce(max(pr.name), '?') n,
          jsonb_build_object('uid', h.uid, 'name', coalesce(max(pr.name), '?'), 'img', coalesce(max(pr.img), ''), 'pts', sum(h.pts),
            'hands', count(*) filter (where h.kind = 'hand'), 'wins', count(*) filter (where h.kind = 'win')) o
        from cf_honor h left join cf_profiles pr on pr.uid = h.uid
        where h.t >= since and cf_visible(h.uid) and (not fr or h.uid = any(sc))
        group by h.uid order by sum(h.pts) desc limit 30) q),
    'me', jsonb_build_object('pts', mine, 'rank', case when mine > 0 then r else null end),
    'champ', case when prev is null then null else (
        select jsonb_build_object('name', coalesce(max(pr.name), '?'), 'pts', sum(h.pts))
        from cf_honor h left join cf_profiles pr on pr.uid = h.uid
        where h.t >= prev and h.t < since and cf_visible(h.uid) and (not fr or h.uid = any(sc))
        group by h.uid order by sum(h.pts) desc limit 1) end,
    'cups', (select coalesce(jsonb_agg(jsonb_build_object('name', x.name, 'players', x.players, 'tier', x.tier,
               't', (extract(epoch from x.t) * 1000)::bigint) order by x.t desc), '[]')
             from (select * from cf_trophies where cf_trophies.uid = u order by t desc limit 24) x),
    'total', (select coalesce(sum(h.pts), 0) from cf_honor h where h.uid = u)
  );
end $$;

-- ---------------------------------------------------------------------
-- permissions: only signed-in users may call the app's functions
-- ---------------------------------------------------------------------
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig, p.proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname like 'cf\_%' loop
    execute format('revoke all on function %s from public, anon', f.sig);
    if f.proname in ('cf_profile','cf_create','cf_join','cf_start','cf_move','cf_timeout','cf_next','cf_show','cf_leave',
                     'cf_set_advisor','cf_restart','cf_view','cf_group_create','cf_group_join','cf_group_leave',
                     'cf_group_rename','cf_groups','cf_my_tables','cf_ping',
                     'cf_friend_add','cf_friend_remove','cf_friends','cf_invite','cf_inbox','cf_inbox_dismiss',
                     'cf_wallet','cf_say','cf_drink','cf_earn','cf_fame','cf_cup_propose','cf_cup_vote','cf_delete_me',
                     'cf_hat_buy','cf_hat_wear','cf_my_hats','cf_say_text','cf_friend_code','cf_friend_add_code','cf_set_peek','cf_ask_card','cf_ask_answer','cf_advent_open','cf_advent_days','cf_invite_at','cf_invite_answer','cf_outbox','cf_link_code','cf_link_claim','cf_me_profile') then
      execute format('grant execute on function %s to authenticated', f.sig);
    else
      execute format('revoke all on function %s from authenticated', f.sig);
    end if;
  end loop;
end $$;
