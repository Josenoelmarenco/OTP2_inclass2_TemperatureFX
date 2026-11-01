# Temperature FX — SEP2 / OTP2 In-class assignment 2 (Sprint 5)

JavaFX desktop application for **Ohjelmistotuotantoprojekti 2 (TX00EY30-3013)**,
Metropolia University of Applied Sciences, Autumn 2026.

The console converter of In-class 1 is now a **graphical application** that
speaks **five languages**, stores a **trip log (distance, time and speed) in a
relational database with two related tables**, and ships with a **Jenkins
pipeline**, a **Docker image** and a **Kubernetes manifest**.

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

---

## The database

Two related tables. `unit_system` is the parent, `trip_record` the child that
references it through a foreign key, so every trip belongs to one measurement
system:

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

The application uses an **embedded H2 database running in MySQL compatibility
mode**, so the very same `schema.sql` also runs on MariaDB and nothing has to
be installed. That is what makes the Docker image and the Kubernetes
deployment self-contained.

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
* Dates in the table are formatted with the conventions of the active locale.

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
├── db/schema-mariadb.sql          # the same schema, for a MariaDB server
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
    │   │   ├── model/TemperatureUnit.java
    │   │   ├── model/UnitSystem.java        # parent table
    │   │   ├── model/TripRecord.java        # child table
    │   │   ├── db/Database.java             # connection + schema creation
    │   │   ├── db/UnitSystemDao.java
    │   │   ├── db/TripRecordDao.java
    │   │   └── ui/MainController.java
    │   └── resources
    │       ├── db/schema.sql
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
        ├── SpeedCalculatorTest.java
        ├── LocalizationServiceTest.java
        ├── DatabaseTest.java
        ├── UnitSystemDaoTest.java
        └── TripRecordDaoTest.java
```

The tests cover the conversion logic, the speed/distance/time formulas, the
five resource bundles and the whole database layer against an in-memory
database. The JavaFX view classes are exercised by hand, since running them
would need a graphical toolkit.

---

## Author

José Noel Marenco — Ohjelmistotuotantoprojekti 2 (TX00EY30-3013), Autumn 2026.
