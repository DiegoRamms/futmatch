# Monthly rankings

All routes require the same JWT and App Check protection as other authenticated routes. Responses and errors use the existing application envelope.

## Home and summary

`GET /user/home` already includes the authenticated player's current UTC summary as `ranking`; the Home card does not need a separate request. See the [Home contract](../user/user.md#12-obtener-home-del-usuario). `GET /rankings/summary` exposes the same summary independently when a client needs to refresh it without reloading Home. Each request recalculates the summary. `lastMatch` remains available in Home for compatibility.

The summary contains `period` (`year`, `month`, `startTime`, `endTimeExclusive`, `timeZone`), `community` (`id: "global"`, `name: "Global"`), `category: "VICTORIES"`, `status` (`RANKED` or `NOT_PARTICIPATING`), nullable `position`, `victories`, `matchesPlayed`, nullable `nextTarget` (`position`, `victories`), and `calculatedAt`. Timestamps are epoch milliseconds; the end is exclusive. A null target means there is no higher score to match. Clients compute the difference and localize the text.

## Classification

`GET /rankings?year=2026&month=10&category=victories&community=global&page=1&pageSize=20`

`year`, `month`, and `category` are required; `category` is `victories` or `goals`. The only supported community is `global`, which is also the default. Pages start at 1; page size defaults to 20 and is limited to 100. Invalid parameters return the standard bad-request response.

The response includes `period`, `community`, `category`, `podium`, `players`, `currentUser`, `totalPlayers`, `page`, `pageSize`, and `calculatedAt`. `currentUser` is null when the user has no eligible participation. Player entries contain `id`, `name`, `avatarUrl`, `gender`, `country`, `position`, `matchesPlayed`, `victories`, and `goals`. The name, gender, country, and avatar follow Match Detail conventions so clients can reuse their flag and avatar presentation.

Eligible entries are present, joined or reserved players in completed matches with a result, using the match start time. A draw adds no victories. Goals come from individual goal records for the same player and match. All eligible players appear in both categories, including those with zero wins or goals. Ties share competition positions (1, 1, 3); UUID ordering stabilizes the display. The podium contains at most three players. Statistics are read from current records at request time, so corrections are reflected in subsequent requests.

The database aggregates eligible match participants and calculates ranks before returning the requested page, podium, and current user. The Home summary skips the individual-goals join. The query still processes eligible participation for the requested month to calculate exact global ranks; monitor query plans and latency with production-sized data before assuming a particular capacity.
