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
[ "$(echo "$V" | jq '.shows | length')" = 1 ] || { echo "show failed"; exit 1; }
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
[ "$(rpc "$A" cf_friends '{}' | jq -r '.[0].table')" = "$CODE" ] || { echo "friend status failed"; exit 1; }
rpc "$A" cf_invite "{\"p_uid\":\"$UB\",\"p_code\":\"$CODE\"}" >/dev/null
[ "$(rpc "$B" cf_inbox '{}' | jq -r '.[0].from')" = "Test A" ] || { echo "invite failed"; exit 1; }
rpc "$A" cf_friend_remove "{\"p_uid\":\"$UB\"}" >/dev/null
echo "✓ friends and invites"
rpc "$A" cf_ping '{}' >/dev/null; echo "✓ keep-alive ping"
rpc "$B" cf_leave "{\"p_code\":\"$CODE\"}" >/dev/null; rpc "$A" cf_leave "{\"p_code\":\"$CODE\"}" >/dev/null
echo "ALL SERVER TESTS PASSED"
