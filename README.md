# Experimental FIDE API

## How to use

Check [Open API docs](https://fide.thanh.se/docs/index.html)

## Examples

Get all players

```bash
curl 'fide.thanh.se/api/players'
```

Get top 10 players by standard rating with descending order

```bash
curl 'fide.thanh.se/api/players?sort_by=standard&order=desc&size=10'
```

Get all players sort by blitz rating and is active on page 5

```bash
curl 'fide.thanh.se/api/players?sort_by=blitz&order=desc&page=5&is_active=true'
```

## Development

### Prerequisites:

- Docker

### Run (with sbt locally)

Also requires JDK 21 with [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)

```bash
cp .env.example .env
docker compose up -d
sbt backend/run
```

### Run (with sbt in Docker)

```bash
COMPOSE_PROFILES=sbt docker compose up -d
```

### Usage

```bash
open http://localhost:9669/docs // you may need to wait a bit for syncing
curl http://localhost:9669/api/players
```

### Database viewer

http://localhost:8180/?pgsql=db&username=admin&db=fide&ns=fide (password: dummy)

### Before submitting PR

```bash
sbt test
sbt lint
```
