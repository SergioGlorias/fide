$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service PlayerService {
  version: "0.0.1",
  operations: [GetPlayers, GetPlayerById, GetPlayerByIds],
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/players", code: 200)
operation GetPlayers {
  input: GetPlayersInput,
  output: GetPlayersOutput
  errors: [InternalServerError]
}

@readonly
@http(method: "GET", uri: "/api/players/{id}", code: 200)
operation GetPlayerById {
  input: GetPlayerByIdInput,
  output: Player
  errors: [PlayerNotFound, InternalServerError]
}

// todo limit the number of ids
@readonly
@http(method: "POST", uri: "/api/players", code: 200)
operation GetPlayerByIds {
  input: GetPlayerByIdsInput
  output: GetPlayersByIdsOutput
  errors: [InternalServerError]
}

structure GetPlayerByIdInput {
  @httpLabel
  @required
  id: PlayerId
}

structure GetPlayerByIdsInput {
  @required
  ids: SetPlayerIds
}

map PlayerMap {
  key: String
  value: Player
}

structure GetPlayersByIdsOutput {
  @required
  players: PlayerMap
}

structure GetPlayersInput with [SortingMixin, FilterMixin] {
  @httpQuery("page")
  page: PageNumber
  @httpQuery("page_size")
  @range(min: 1, max: 100)
  pageSize: Integer
}

structure GetPlayersOutput {
  @required
  items: Players
  nextPage: PageNumber
}

@uniqueItems
list SetPlayerIds {
  member: PlayerId
}

@error("client")
@httpError(404)
structure PlayerNotFound {
  @required
  id: PlayerId
}
