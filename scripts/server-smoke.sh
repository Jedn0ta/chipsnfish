#!/usr/bin/env bash
# End-to-end test of the online server: two anonymous players play a full hand.
set -euo pipefail
URL="https://leobbaixgbnftirzdudl.supabase.co"
KEY="sb_publishable_esmekdxKuc2F2NbSobe_kw_yfAZtjm6"

signup() {
  local r; r=$(curl -sS -X POST "$URL/auth/v1/signup" -H "apikey: $KEY" -H "Content-Type: application/json" -d '{"data":{}}')
  local t; t=$(echo "$r" | jq -r '.access_token // empty')
  if [ -z "$t" ]; then echo "SIGNUP FAILED (anonymous sign-ins enabled?): $r" >&2; exit 1; fi
  echo "$t"
}
rpc() { # token fn json
  local r code
  r=$(curl -sS -w '\n%{http_code}' -X POST "$URL/rest/v1/rpc/$2" -H "apikey: $KEY" -H "Authorization: Bearer $1" -H "Content-Type: application/json" -d "$3")
  code=$(echo "$r" | tail -1); r=$(echo "$r" | sed '$d')
  if [ "${code:0:1}" != "2" ]; then echo "RPC $2 FAILED ($code): $r" >&2; exit 1; fi
  echo "$r"
}

A=$(signup); B=$(signup); echo "✓ anonymous sign-in"
rpc "$A" cf_profile '{"p_name":"Test A","p_img":""}' >/dev/null
rpc "$B" cf_profile '{"p_name":"Test B","p_img":""}' >/dev/null; echo "✓ profiles"
CODE=$(rpc "$A" cf_create '{"p_name":"smoke test","p_cfg":{"stack":200,"level":0,"every":0,"advisor":true},"p_gid":null}' | jq -r .)
echo "✓ table $CODE"
rpc "$B" cf_join "{\"p_code\":\"$CODE\"}" >/dev/null; echo "✓ join"
rpc "$A" cf_start "{\"p_code\":\"$CODE\"}" >/dev/null; echo "✓ start"
VA=$(rpc "$A" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
VB=$(rpc "$B" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
HA=$(echo "$VA" | jq -c '.hole.cards'); HB=$(echo "$VB" | jq -c '.hole.cards')
[ "$(echo "$HA" | jq length)" = 2 ] && [ "$(echo "$HB" | jq length)" = 2 ] || { echo "no hole cards"; exit 1; }
[ "$HA" != "$HB" ] || { echo "same cards"; exit 1; }
echo "$VA" | jq -e '.state | has("deck") | not' >/dev/null
echo "✓ private hole cards A=$HA B=$HB"
UA=$(echo "$VA" | jq -r .me)
for i in $(seq 1 20); do
  V=$(rpc "$A" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
  if echo "$V" | jq -e '.state.done != null' >/dev/null; then break; fi
  T=$(echo "$V" | jq -r '.state.turn'); P=$(echo "$V" | jq -r ".state.seats[$T].pid"); H=$(echo "$V" | jq -r '.state.handNo')
  if [ "$P" = "$UA" ]; then TOK=$A; else TOK=$B; fi
  rpc "$TOK" cf_move "{\"p_code\":\"$CODE\",\"p_hand\":$H,\"p_type\":\"call\",\"p_to\":0}" >/dev/null
done
V=$(rpc "$A" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
echo "$V" | jq -e '.state.done != null' >/dev/null || { echo "hand did not finish"; exit 1; }
echo "✓ hand played to showdown: board $(echo "$V" | jq -c .state.board), winners $(echo "$V" | jq -c .state.done.w), stacks $(echo "$V" | jq -c '[.state.seats[].stack]')"
[ "$(echo "$V" | jq '[.state.seats[].stack] | add')" = 400 ] || { echo "chips not conserved"; exit 1; }
rpc "$A" cf_show "{\"p_code\":\"$CODE\",\"p_hand\":1}" >/dev/null
V=$(rpc "$B" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
[ "$(echo "$V" | jq '.shows | length')" -ge 1 ] || { echo "show failed"; exit 1; }
echo "✓ show cards"
rpc "$A" cf_next "{\"p_code\":\"$CODE\"}" >/dev/null
V=$(rpc "$A" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
[ "$(echo "$V" | jq .state.handNo)" = 2 ] || { echo "next hand failed"; exit 1; }
echo "✓ next hand"
G=$(rpc "$A" cf_group_create '{"p_name":"Smoke group"}' | jq -r .)
rpc "$B" cf_group_join "{\"p_gid\":\"$G\"}" >/dev/null
[ "$(rpc "$B" cf_groups '{}' | jq '.[0].members | length')" = 2 ] || { echo "groups failed"; exit 1; }
rpc "$A" cf_group_leave "{\"p_gid\":\"$G\"}" >/dev/null; rpc "$B" cf_group_leave "{\"p_gid\":\"$G\"}" >/dev/null
echo "✓ groups"
UB=$(rpc "$B" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}" | jq -r .me)
[ "$(rpc "$A" cf_friend_add "{\"p_uid\":\"$UB\"}")" = "true" ] || { echo "friend add failed"; exit 1; }
# everybody is a friend now, test players are left out of the list
rpc "$A" cf_friends '{}' | jq -e 'type == "array" and length >= 1' >/dev/null || { echo "friends failed (everyone should be listed)"; exit 1; }
echo "✓ everybody with the app is listed as a friend ($(rpc "$A" cf_friends '{}' | jq length) players)"
rpc "$A" cf_invite "{\"p_uid\":\"$UB\",\"p_code\":\"$CODE\"}" >/dev/null
[ "$(rpc "$B" cf_inbox '{}' | jq -r '.[0].from')" = "Test A" ] || { echo "invite failed"; exit 1; }
rpc "$A" cf_friend_remove "{\"p_uid\":\"$UB\"}" >/dev/null
echo "✓ friends and invites"
[ "$(rpc "$A" cf_wallet '{}' | jq -r .bank)" -ge 50 ] || { echo "wallet failed"; exit 1; }
rpc "$A" cf_say "{\"p_code\":\"$CODE\",\"p_msg\":\"0,4,21\"}" >/dev/null
rpc "$A" cf_drink "{\"p_code\":\"$CODE\",\"p_to\":\"$UB\",\"p_kind\":\"beer\"}" >/dev/null
VS=$(rpc "$B" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}")
[ "$(echo "$VS" | jq -r '.chat[-1].m')" = "0,4,21" ] && [ "$(echo "$VS" | jq -r '.drinks[-1].kind')" = "beer" ] || { echo "emoji/drinks failed: $(echo "$VS" | jq -c '{chat,drinks}')"; exit 1; }
rpc "$A" cf_drink "{\"p_code\":\"$CODE\",\"p_to\":\"$UB\",\"p_kind\":\"pizza\"}" >/dev/null
echo "✓ emoji chat, wallet, drinks and food"
[ "$(rpc "$A" cf_earn '{"p_amount":25}' | jq -r .added)" = "25" ] || { echo "practice earnings failed"; exit 1; }
F=$(rpc "$A" cf_fame '{"p_period":"week","p_scope":"all"}')
[ "$(echo "$F" | jq '.top | length')" -ge 1 ] || { echo "hall of fame failed: $F"; exit 1; }
echo "✓ practice earnings and hall of fame (honor points from the test hand: $(echo "$F" | jq -c '[.top[] | select(.name | startswith("Test")) | .pts]'))"
rpc "$A" cf_ping '{}' >/dev/null; echo "✓ keep-alive ping"
sleep 4
rpc "$A" cf_say_text "{\"p_code\":\"$CODE\",\"p_text\":\"GG\"}" >/dev/null
[ "$(rpc "$B" cf_view "{\"p_code\":\"$CODE\",\"p_ver\":0}" | jq -r '.chat[-1].m')" = "t:GG" ] || { echo "text chat failed"; exit 1; }
echo "✓ text chat"
FC=$(rpc "$B" cf_friend_code '{}' | jq -r .)
rpc "$A" cf_friend_add_code "{\"p_code\":\"$FC\"}" >/dev/null
[ "$(rpc "$A" cf_friend_add_code "{\"p_code\":\"$FC\"}" | jq -r 'type')" != "" ] || { echo "friend code failed"; exit 1; }
echo "✓ friend code $FC"
rpc "$A" cf_leave "{\"p_code\":\"$CODE\"}" >/dev/null; rpc "$B" cf_leave "{\"p_code\":\"$CODE\"}" >/dev/null
# three players: peek after fold, ask for a card
C=$(signup); rpc "$C" cf_profile '{"p_name":"Test C","p_img":""}' >/dev/null
rpc "$C" cf_set_peek '{"p":true}' >/dev/null
T3=$(rpc "$A" cf_create '{"p_name":"smoke 3","p_cfg":{"stack":200,"level":0,"every":0,"advisor":true},"p_gid":null}' | jq -r .)
rpc "$B" cf_join "{\"p_code\":\"$T3\"}" >/dev/null; rpc "$C" cf_join "{\"p_code\":\"$T3\"}" >/dev/null
rpc "$A" cf_start "{\"p_code\":\"$T3\"}" >/dev/null
tok_of() { local u=$1; if [ "$u" = "$UA" ]; then echo "$A"; elif [ "$u" = "$UB" ]; then echo "$B"; else echo "$C"; fi; }
V=$(rpc "$A" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}"); UC=$(rpc "$C" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}" | jq -r .me)
H=$(echo "$V" | jq -r .state.handNo); T=$(echo "$V" | jq -r .state.turn); P=$(echo "$V" | jq -r ".state.seats[$T].pid")
if [ "$P" = "$UC" ]; then
  rpc "$C" cf_move "{\"p_code\":\"$T3\",\"p_hand\":$H,\"p_type\":\"call\",\"p_to\":0}" >/dev/null
  V=$(rpc "$A" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}"); T=$(echo "$V" | jq -r .state.turn); P=$(echo "$V" | jq -r ".state.seats[$T].pid")
fi
FT=$(tok_of "$P")
rpc "$FT" cf_move "{\"p_code\":\"$T3\",\"p_hand\":$H,\"p_type\":\"fold\",\"p_to\":0}" >/dev/null
PK=$(rpc "$FT" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}" | jq -c .peek)
[ "$(echo "$PK" | jq length)" = 1 ] && [ "$(echo "$PK" | jq -r '.[0].uid')" = "$UC" ] || { echo "peek failed: $PK"; exit 1; }
echo "✓ folded player sees only the cards of the player who allows it"
rpc "$A" cf_ask_card "{\"p_code\":\"$T3\",\"p_to\":\"$UC\"}" >/dev/null
[ "$(rpc "$C" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}" | jq '.asks | length')" = 1 ] || { echo "ask not delivered"; exit 1; }
rpc "$C" cf_ask_answer "{\"p_code\":\"$T3\",\"p_from\":\"$UA\",\"p_idx\":0}" >/dev/null
[ "$(rpc "$B" cf_view "{\"p_code\":\"$T3\",\"p_ver\":0}" | jq '.ones | length')" = 1 ] || { echo "shown card missing"; exit 1; }
echo "✓ ask to show a card"
# a duel 20 minutes from now, accepted
I=$(rpc "$A" cf_invite_at "{\"p_uid\":\"$UB\",\"p_code\":\"$T3\",\"p_at\":\"$(date -u -d '+20 minutes' +%Y-%m-%dT%H:%M:%SZ)\",\"p_kind\":\"duel\"}" | jq -r .)
[ "$(rpc "$B" cf_inbox '{}' | jq -r "[.[] | select(.id == $I)][0].kind")" = "duel" ] || { echo "scheduled duel missing"; exit 1; }
rpc "$B" cf_invite_answer "{\"p_id\":$I,\"p_yes\":true}" >/dev/null
[ "$(rpc "$A" cf_outbox '{}' | jq -r "[.[] | select(.id == $I)][0].ans")" = "yes" ] || { echo "duel answer missing"; exit 1; }
echo "✓ duel at a set time, accepted"
[ "$(rpc "$A" cf_advent_days '{}' | jq -r 'type')" = "array" ] || { echo "advent failed"; exit 1; }
# November 2026 = test month: any window opens
if [ "$(TZ=Europe/Prague date +%Y-%m)" = "2026-11" ]; then
  [ "$(rpc "$A" cf_advent_open '{"p_day":3}' | jq -r .)" = "fish" ] || { echo "advent test window failed"; exit 1; }
  echo "✓ advent calendar (test month: window 3 = fish)"
else echo "✓ advent calendar"; fi
for t in "$A" "$B" "$C"; do rpc "$t" cf_leave "{\"p_code\":\"$T3\"}" >/dev/null || true; done
# clean up: delete the test accounts' profiles
for t in "$A" "$B" "$C"; do rpc "$t" cf_delete_me '{}' >/dev/null; done
echo "✓ test profiles deleted"
echo "ALL SERVER TESTS PASSED"
