# Temperature FX — OTP2 In-class assignment (updated version)

JavaFX desktop application for **Ohjelmistotuotantoprojekti 2 (TX00EY30-3013)**,
Metropolia University of Applied Sciences, Autumn 2026.

The console converter of In-class 1 became a **graphical application** in
In-class 2. This updated version adds the part the new task description asks
for: **the unit names and descriptions the user reads are no longer in the
`.properties` files — they are stored in the database and read back in the
selected language**, through the **translation-table** approach.

The interface is therefore localized on **two layers at once**:

| Layer | What it covers | Where it comes from |
|---|---|---|
| Static UI text | titles, buttons, column headers, error messages | `MessagesBundle_xx.properties` (ResourceBundle) |
| Data text | unit names and short descriptions | `unit_translation` table, queried in the active language |

Choosing a language in the selector reloads the resource bundle **and**
re-queries the database in one step.

It still ships with **five languages**, a **trip log (distance, time, speed)**
in related tables, a **Jenkins pipeline**, a **Docker image** and a
**Kubernetes manifest**.

---

## Requirement checklist

| Requirement | Where it is implemented |
|---|---|
| JavaFX GUI | `MainApp`, `main-view.fxml`, `MainController` |
| Language selection: English, Finnish, Swedish, Japanese, Arabic | `LocalizationService`, language selector in the header |
| Enter a temperature and convert it to the selected unit | Temperature section, Celsius / Fahrenheit / Kelvin |
| Localization with **FXML + .properties** | every label in the FXML uses `%key`; five `MessagesBundle_*.properties` |
| UTF-8 encoding of all property files | verified, see *Localization notes* |
| Database with **at least two related tables** | `unit_system` (parent) and `trip_record` (child, foreign key) |
| The interface interacts with the database | add / delete / list trips, live totals in the table |
| Speed, distance and time | `SpeedCalculator`, stored in `trip_record` |
| JaCoCo test coverage | `jacoco-maven-plugin`, report in `target/site/jacoco` |
| Jenkins CI/CD building, testing and pushing to Docker Hub | `Jenkinsfile` |
| Docker image tested with an X server | `Dockerfile` + `entrypoint.sh`, instructions below |
| Kubernetes YAML run on minikube | `k8s/temperature-fx.yaml` |
| **Localized unit names and descriptions read from the database** | `unit_translation` + `UnitTranslationDao` |
| **One DB localization approach, fully implemented and justified** | **Translation table** — see *Chosen DB localization approach* below |
| **DAO returning localized names with English fallback** | `UnitTranslationDao.findByCategory` / `findByUnitKey` |
| **Every conversion saved (value, units, result, language, timestamp)** | `conversion_log` + `ConversionLogDao` |
| **The last 5 conversions shown in the GUI** | history table, `ConversionLogDao.findRecent` |
| **UTF-8 / utf8mb4 for all text columns and the connection** | H2 is Unicode natively, `db/init-mariadb.sql` sets `utf8mb4` |
| `init.sql` with the schema and the data in all five languages | `src/main/resources/db/init.sql` |

---

## Chosen DB localization approach: **translation table**

### The schema

```
language                     measurement_unit                 unit_translation
--------                     ----------------                 ----------------
id            PK  <--+       id            PK  <----------+   id           PK
code (unique)        |       unit_key (unique)            |   unit_id      FK -> measurement_unit(id)
native_name          |       category                     +-- lang         FK -> language(code)
rtl                  |       symbol                           name
                     |       unit_system_id FK                description
                     |                     |                  UNIQUE (unit_id, lang)
                     |                     v
                     |              unit_system  ----<  trip_record
                     |
                     +--------------------------------<  conversion_log
```

* **`measurement_unit`** is the language-neutral base table. It holds the key,
  the category (`TEMPERATURE`, `DISTANCE`, `SPEED`, `TIME`), the symbol and an
  optional link to the measurement system — **no translatable text at all**.
* **`unit_translation`** holds one row per unit *and* language, with the
  `name` and the `description`. `UNIQUE (unit_id, lang)` makes a duplicated
  translation impossible and the two foreign keys make an orphan row
  impossible.
* **`language`** is a table rather than a hard-coded list, precisely so that
  `unit_translation.lang` can reference it.

Currently: **10 units × 5 languages = 48 translation rows** (two gaps on
purpose, see *Fallback* below).

### Why this approach and not the other three

| Approach | Adding a language | Why it was rejected |
|---|---|---|
| **Field (column)** `name_en, name_fi, name_sv, name_ja, name_ar` | **schema change** | A sixth language means `ALTER TABLE` on a live database, and a seventh means another one. Every non-text column would sit next to ten text columns, most of them NULL for partially translated rows. |
| **Row** one row per unit and language, sharing a `unit_key` | new rows | No schema change, but the non-text data — `category`, `symbol`, `unit_system_id` — would be **repeated in all five rows**. Changing a symbol would mean updating five rows, and nothing in the schema would stop them from disagreeing. |
| **Table** `unit_en`, `unit_fi`, `unit_sv`, `unit_ja`, `unit_ar` | new table **and** code change | The table name would have to be chosen at runtime, which means string-concatenated SQL instead of a prepared statement. Five copies of the same structure also drift apart, and a fallback would need a second query against a different table. |
| **Translation table** *(chosen)* | **new rows only** | Normalized: the non-text data exists exactly once, so it cannot disagree with itself. Adding Spanish is one row in `language` plus ten rows in `unit_translation` — no `ALTER TABLE`, no code change, no redeploy. The fallback is a second `LEFT JOIN` in the **same** query, so one round trip still returns a complete result. The cost is one extra JOIN per query, which on tables of this size is not measurable. |

The deciding argument is the **fallback**. With the field approach a missing
translation is a NULL I would have to handle in Java; with the table approach
it needs a query against a different table. With a translation table it is
part of the same statement:

```sql
SELECT m.unit_key, m.symbol,
       COALESCE(requested.name, fallback.name, m.unit_key) AS name,
       CASE WHEN requested.name IS NULL THEN 1 ELSE 0 END  AS used_fallback
  FROM measurement_unit m
  LEFT JOIN unit_translation requested ON requested.unit_id = m.id AND requested.lang = ?
  LEFT JOIN unit_translation fallback  ON fallback.unit_id  = m.id AND fallback.lang  = 'en'
 WHERE m.category = ?
```

The chain is **requested language → English → the unit key itself**, so a name
is never `null` and the drop-down is never empty.

### Fallback, demonstrated and not just claimed

`init.sql` translates `nautical_mile` into English, Finnish and Swedish only.
Select **日本語** or **العربية** in the GUI and that one entry reads
*Nautical mile* with a note that it is being shown in English, while every
other unit is translated. `UnitTranslationDaoTest` asserts exactly this, and
also that a unit with no translation in any language falls back to its key.

### What stays out of the database

Static UI text — buttons, titles, column headers, error messages — stays in
the `.properties` files, as the task description requires. The task status
values and the unit **keys** also stay in English in the database, because
they are identifiers, not prose: the GUI translates them for display only.

---

## The database

The task description asks for three groups of tables. All six are created by
`src/main/resources/db/init.sql`.

| Group | Tables | Purpose |
|---|---|---|
| Measurement tables | `unit_system`, `trip_record` | track distance, time and the resulting speed |
| Unit / lookup data | `measurement_unit` | language-neutral source of the units shown in the GUI |
| Localization structure | `language`, `unit_translation` | the names and descriptions in `en`, `fi`, `sv`, `ja`, `ar` |
| Conversion history | `conversion_log` | every conversion, with the language and the timestamp |

The measurement tables are unchanged from In-class 2: `unit_system` is the
parent, `trip_record` the child that references it through a foreign key, so
every trip belongs to one measurement system:

```
unit_system                        trip_record
-----------                        -----------
id            PK  <---------+      id             PK
code (unique)               |      label
distance_unit               +----  unit_system_id  FK -> unit_system(id)
speed_unit                         distance
                                   time_hours
                                   speed
                                   created_at
```

`conversion_log` references `measurement_unit` twice (the source and the
target unit) and `language` once, so a conversion can never point at a unit or
a language that does not exist.

### H2 or MariaDB

The application uses an **embedded H2 database running in MySQL compatibility
mode**, so the very same `init.sql` also runs on MariaDB and nothing has to be
installed. That is what makes the Docker image and the Kubernetes deployment
self-contained.

H2 stores text as Unicode, so Japanese and Arabic need no configuration. For a
real MariaDB server use **`db/init-mariadb.sql`**, generated from the same
file, which adds what MariaDB does need:

```sql
CREATE DATABASE tempfx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ... every table: ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci
```

```bash
mariadb -u root -p < db/init-mariadb.sql
DB_URL="jdbc:mariadb://localhost:3306/tempfx?useUnicode=true&characterEncoding=UTF-8" \
DB_USER=root DB_PASSWORD=yourpassword mvn javafx:run
```

`init.sql` is applied on every start, but it is **idempotent**: every
`CREATE TABLE` is `IF NOT EXISTS`, and an `INSERT` is skipped when its target
table already holds rows. The script can therefore be run against a fresh
database or one created earlier by hand.

The connection can be pointed somewhere else with the environment variables
`DB_URL`, `DB_USER` and `DB_PASSWORD` (or the system properties `db.url`,
`db.user`, `db.password`).

---

## Requirements

* JDK 21 or newer
* Maven 3.9 or newer
* Docker (for the container), minikube and kubectl (for the Kubernetes test)
* An X server to display the GUI from the container — **XQuartz** on macOS,
  XMing on Windows

---

## Build, test and coverage

```bash
mvn clean test
open target/site/jacoco/index.html      # coverage report
```

Build the jar as well:

```bash
mvn clean package
```

---

## Run the application locally

```bash
mvn clean javafx:run
```

Start directly in one language:

```bash
mvn clean javafx:run -Djavafx.args=ar        # Arabic
APP_LANG=ja mvn clean javafx:run             # Japanese
```

The language can also be changed at any moment from the selector in the top
right corner; the whole window is reloaded with the new resource bundle.

---

## Localization notes

* The five bundles — `en_US`, `fi_FI`, `sv_SE`, `ja_JP`, `ar_SA` — are stored
  in **UTF-8**. Since Java 9 `ResourceBundle` reads property files as UTF-8,
  so Japanese and Arabic are written directly, without `\uXXXX` escaping.
* Every text in `main-view.fxml` is written as `%key`, so the FXML itself
  contains no hard-coded language.
* **Arabic is a right-to-left language**: when it is selected the whole layout
  is mirrored with `NodeOrientation.RIGHT_TO_LEFT`.
* Numbers follow each locale: Finnish and Swedish show a decimal comma,
  English and Japanese a decimal point. Input is accepted in both notations.
* Dates in both tables are formatted with the conventions of the active locale.
* **Unit names and descriptions come from the database**, not from the
  bundles: `UnitTranslationDao` queries `unit_translation` in the active
  language on every language switch. The `.properties` files keep only the
  static UI text.
* If the database cannot be reached at all, the converter still works: the
  unit names fall back to the `unit.*` keys in the resource bundle and the
  status bar says so. Only the trip log and the history are disabled.

---

## Docker + X server (XQuartz on macOS)

Build the image:

```bash
docker build -t jnmarenco/otp2-temperature-fx:latest .
```

Prepare XQuartz once:

1. Install it if needed: `brew install --cask xquartz`
2. Open **XQuartz → Settings → Security** and tick
   **"Allow connections from network clients"**.
3. Quit XQuartz and open it again (the setting only applies after a restart).
4. Allow the local connection:

```bash
xhost + 127.0.0.1
```

Run the container so the window appears on your desktop:

```bash
docker run --rm -e DISPLAY=host.docker.internal:0 \
    -v "$(pwd)/data:/app/data" \
    jnmarenco/otp2-temperature-fx:latest
```

Start it directly in Arabic or Japanese for the screenshots:

```bash
docker run --rm -e DISPLAY=host.docker.internal:0 -e APP_LANG=ar \
    jnmarenco/otp2-temperature-fx:latest
```

If `DISPLAY` is **not** given, the entrypoint starts a virtual framebuffer
(Xvfb) instead, so the container also runs where no X server exists — which is
exactly what happens inside Kubernetes.

---

## Kubernetes (minikube)

```bash
minikube start

# Make the image available to the cluster (or let it pull from Docker Hub)
minikube image load jnmarenco/otp2-temperature-fx:latest

kubectl apply -f k8s/temperature-fx.yaml
kubectl get pods -w
kubectl logs deployment/temperature-fx
```

The manifest contains a **ConfigMap** (language and database settings), a
**Deployment** and a **Service** that exposes the H2 database port so the data
can be inspected with `kubectl port-forward service/temperature-fx 9092:9092`.

Clean up:

```bash
kubectl delete -f k8s/temperature-fx.yaml
```

---

## CI/CD with Jenkins

The `Jenkinsfile` runs: checkout → build → unit tests → JaCoCo report →
publish test and coverage results → package → Docker build → push to Docker
Hub. The Docker Hub credentials are taken from the Jenkins credential with the
id `dockerhub`.

---

## Project structure

```
.
├── Dockerfile
├── Jenkinsfile
├── entrypoint.sh
├── pom.xml
├── README.md
├── db/init-mariadb.sql            # the same script with utf8mb4, for MariaDB
├── k8s/temperature-fx.yaml        # ConfigMap + Deployment + Service
└── src
    ├── main
    │   ├── java/fi/metropolia/tempfx
    │   │   ├── MainApp.java                 # JavaFX entry point, language switching
    │   │   ├── Main.java                    # plain launcher for the jar
    │   │   ├── TemperatureConverter.java    # Celsius / Fahrenheit / Kelvin
    │   │   ├── SpeedCalculator.java         # speed, distance, time
    │   │   ├── i18n/LocalizationService.java
    │   │   ├── i18n/LanguageOption.java
    │   │   ├── model/TemperatureUnit.java   # conversion identity + unit_key
    │   │   ├── model/UnitCategory.java      # TEMPERATURE / DISTANCE / SPEED / TIME
    │   │   ├── model/MeasurementUnit.java   # language-neutral base row
    │   │   ├── model/LocalizedUnit.java     # a unit + its name in one language
    │   │   ├── model/SupportedLanguage.java # a row of the language table
    │   │   ├── model/ConversionRecord.java  # a row of conversion_log
    │   │   ├── model/UnitSystem.java        # parent table
    │   │   ├── model/TripRecord.java        # child table
    │   │   ├── db/Database.java             # connection + applies init.sql
    │   │   ├── db/UnitTranslationDao.java   # localized names, English fallback
    │   │   ├── db/ConversionLogDao.java     # saves and reads the last 5
    │   │   ├── db/UnitSystemDao.java
    │   │   ├── db/TripRecordDao.java
    │   │   └── ui/MainController.java
    │   └── resources
    │       ├── db/init.sql                  # schema + data in five languages
    │       ├── fi/metropolia/tempfx/main-view.fxml
    │       ├── fi/metropolia/tempfx/styles.css
    │       ├── MessagesBundle.properties        # fallback (English)
    │       ├── MessagesBundle_en_US.properties
    │       ├── MessagesBundle_fi_FI.properties
    │       ├── MessagesBundle_sv_SE.properties
    │       ├── MessagesBundle_ja_JP.properties
    │       └── MessagesBundle_ar_SA.properties
    └── test/java/fi/metropolia/tempfx
        ├── TemperatureConverterTest.java
        ├── TemperatureUnitTest.java         # enum <-> unit_key bridge
        ├── SpeedCalculatorTest.java
        ├── LocalizationServiceTest.java
        ├── DatabaseTest.java                # init.sql, the six tables, idempotency
        ├── UnitTranslationDaoTest.java      # five languages + the fallback
        ├── ConversionLogDaoTest.java        # saving and the last five
        ├── UnitSystemDaoTest.java
        └── TripRecordDaoTest.java
```

The tests cover the conversion logic, the speed/distance/time formulas, the
five resource bundles and the whole database layer against an in-memory H2
database. The localization tests are the important ones here: they assert that
the five bundles agree on their key sets, that the database returns a
different name per language, that Japanese and Arabic survive the UTF-8 round
trip, and that a missing translation falls back to English **and reports that
it did**. The JavaFX view classes are exercised by hand, since running them
would need a graphical toolkit.

---

## Author

José Noel Marenco — Ohjelmistotuotantoprojekti 2 (TX00EY30-3013), Autumn 2026.
