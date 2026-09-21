# Attribution

The Kodik public-player stream resolution in `StreamResolver.kt` is adapted from
`pewaru-333/ShikiApp`, `KodikParser.kt`, copyright its contributors, GPL-3.0:
https://github.com/pewaru-333/ShikiApp

Adaptation changes: uses the public amove search endpoint without borrowed API
tokens, validates player URLs, decodes each returned quality independently,
preserves signed request parameters, and provides explicit failure states.

ShikiMove is distributed under GPL-3.0 with corresponding source in this repository.
AndroidX / Media3, Kotlin / kotlinx.coroutines, OkHttp, Gson and Coil retain their
respective upstream licenses. Gradle wrapper is distributed under Apache-2.0.
