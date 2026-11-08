-- =============================================================================
-- MariaDB / MySQL variant of src/main/resources/db/init.sql
--
-- GENERATED from that file: the only differences are the utf8mb4 character set
-- declarations, which H2 does not need but which MariaDB requires so that the
-- Japanese and Arabic translations are stored correctly.
--
-- Run it with:
--   mariadb -u root -p < db/init-mariadb.sql
--
-- Then point the application at the server:
--   DB_URL="jdbc:mariadb://localhost:3306/tempfx?useUnicode=true&characterEncoding=UTF-8" \
--   DB_USER=root DB_PASSWORD=yourpassword mvn javafx:run
-- =============================================================================

CREATE DATABASE IF NOT EXISTS tempfx
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tempfx;

-- The connection itself must speak utf8mb4 as well, otherwise the non-Latin
-- text is mangled on the way in.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- GROUP 1 - Measurement tables (from the original assignment)
-- Two related tables that track distance, time and the resulting speed.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS unit_system (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(20) NOT NULL UNIQUE,
    distance_unit VARCHAR(20) NOT NULL,
    speed_unit    VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS trip_record (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    label          VARCHAR(100) NOT NULL,
    distance       DOUBLE       NOT NULL,
    time_hours     DOUBLE       NOT NULL,
    speed          DOUBLE       NOT NULL,
    unit_system_id INT          NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_unit_system FOREIGN KEY (unit_system_id)
        REFERENCES unit_system (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -----------------------------------------------------------------------------
-- GROUP 3a - Localization structure: the list of languages
-- Kept as a table rather than a hard-coded list so that unit_translation can
-- reference it and so the GUI can read the language menu from the database.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS language (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(5)  NOT NULL UNIQUE,
    native_name VARCHAR(40) NOT NULL,
    rtl         BOOLEAN     NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -----------------------------------------------------------------------------
-- GROUP 2 - Unit / lookup data, language neutral
-- No translatable text in this table: only the key, the category, the symbol
-- and the optional link to the measurement system the unit belongs to.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS measurement_unit (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    unit_key       VARCHAR(40) NOT NULL UNIQUE,
    category       VARCHAR(20) NOT NULL,
    symbol         VARCHAR(10) NOT NULL,
    unit_system_id INT NULL,
    CONSTRAINT fk_unit_system FOREIGN KEY (unit_system_id)
        REFERENCES unit_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -----------------------------------------------------------------------------
-- GROUP 3b - Localization structure: the translation table itself
-- One row per (unit, language). The unique constraint makes a duplicated
-- translation impossible, and ON DELETE CASCADE keeps the table clean.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS unit_translation (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    unit_id     INT          NOT NULL,
    lang        VARCHAR(5)   NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uq_unit_translation UNIQUE (unit_id, lang),
    CONSTRAINT fk_translation_unit FOREIGN KEY (unit_id)
        REFERENCES measurement_unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_translation_language FOREIGN KEY (lang)
        REFERENCES language (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -----------------------------------------------------------------------------
-- Conversion history - every conversion the user performs is stored with the
-- language that was active, so the GUI can show the last five of them.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS conversion_log (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    input_value  DOUBLE     NOT NULL,
    from_unit_id INT        NOT NULL,
    to_unit_id   INT        NOT NULL,
    result_value DOUBLE     NOT NULL,
    lang         VARCHAR(5) NOT NULL,
    created_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_from_unit FOREIGN KEY (from_unit_id)
        REFERENCES measurement_unit (id),
    CONSTRAINT fk_log_to_unit FOREIGN KEY (to_unit_id)
        REFERENCES measurement_unit (id),
    CONSTRAINT fk_log_language FOREIGN KEY (lang)
        REFERENCES language (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
-- SEED DATA
-- The application skips an INSERT whose target table already has rows, so this
-- same file can be applied to a fresh database or to an existing one.
-- =============================================================================

INSERT INTO unit_system (code, distance_unit, speed_unit) VALUES
    ('METRIC',   'km', 'km/h'),
    ('IMPERIAL', 'mi', 'mph');

INSERT INTO language (code, native_name, rtl) VALUES
    ('en', 'English',  FALSE),
    ('fi', 'Suomi',    FALSE),
    ('sv', 'Svenska',  FALSE),
    ('ja', '日本語',     FALSE),
    ('ar', 'العربية',    TRUE);

-- Temperature and time units belong to no measurement system, so their
-- unit_system_id stays NULL. Distance and speed units point at the system
-- the GUI offers in the trip log.
INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id) VALUES
    ('celsius',            'TEMPERATURE', '°C',   NULL),
    ('fahrenheit',         'TEMPERATURE', '°F',   NULL),
    ('kelvin',             'TEMPERATURE', 'K',    NULL),
    ('hour',               'TIME',        'h',    NULL),
    ('minute',             'TIME',        'min',  NULL);

INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id)
SELECT 'kilometre', 'DISTANCE', 'km', id FROM unit_system WHERE code = 'METRIC';

INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id)
SELECT 'kilometre_per_hour', 'SPEED', 'km/h', id FROM unit_system WHERE code = 'METRIC';

INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id)
SELECT 'mile', 'DISTANCE', 'mi', id FROM unit_system WHERE code = 'IMPERIAL';

INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id)
SELECT 'mile_per_hour', 'SPEED', 'mph', id FROM unit_system WHERE code = 'IMPERIAL';

-- Deliberately left untranslated in Japanese and Arabic. It is the live proof
-- of the fallback rule: with those two languages selected the GUI shows the
-- English name, never an empty row. UnitTranslationDaoTest asserts exactly this.
INSERT INTO measurement_unit (unit_key, category, symbol, unit_system_id) VALUES
    ('nautical_mile', 'DISTANCE', 'nmi', NULL);


-- -----------------------------------------------------------------------------
-- Translations: 10 units x 5 languages, minus the deliberate gap on
-- nautical_mile, which has English and Finnish only.
-- Each row is inserted through a lookup on unit_key, so the file does not
-- depend on auto-generated id values and stays readable.
-- -----------------------------------------------------------------------------

-- ---------- English (en) - the fallback language -----------------------------
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Celsius', 'Water freezes at 0 degrees and boils at 100 degrees at sea level.' FROM measurement_unit WHERE unit_key = 'celsius';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Fahrenheit', 'Scale used mainly in the United States, where water freezes at 32 degrees.' FROM measurement_unit WHERE unit_key = 'fahrenheit';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Kelvin', 'SI base unit of temperature, counted from absolute zero.' FROM measurement_unit WHERE unit_key = 'kelvin';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Kilometre', 'Metric unit of distance equal to 1000 metres.' FROM measurement_unit WHERE unit_key = 'kilometre';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Mile', 'Imperial unit of distance equal to about 1.609 kilometres.' FROM measurement_unit WHERE unit_key = 'mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Nautical mile', 'Distance of one minute of latitude, 1852 metres.' FROM measurement_unit WHERE unit_key = 'nautical_mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Kilometre per hour', 'Metric unit of speed, distance in kilometres divided by time in hours.' FROM measurement_unit WHERE unit_key = 'kilometre_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Mile per hour', 'Imperial unit of speed, distance in miles divided by time in hours.' FROM measurement_unit WHERE unit_key = 'mile_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Hour', 'Unit of time equal to 60 minutes.' FROM measurement_unit WHERE unit_key = 'hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'en', 'Minute', 'Unit of time equal to 60 seconds.' FROM measurement_unit WHERE unit_key = 'minute';

-- ---------- Finnish (fi) -----------------------------------------------------
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Celsius', 'Vesi jäätyy 0 asteessa ja kiehuu 100 asteessa merenpinnan tasolla.' FROM measurement_unit WHERE unit_key = 'celsius';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Fahrenheit', 'Pääosin Yhdysvalloissa käytetty asteikko, jossa vesi jäätyy 32 asteessa.' FROM measurement_unit WHERE unit_key = 'fahrenheit';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Kelvin', 'Lämpötilan SI-perusyksikkö, joka lasketaan absoluuttisesta nollapisteestä.' FROM measurement_unit WHERE unit_key = 'kelvin';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Kilometri', 'Metrinen pituusyksikkö, joka on 1000 metriä.' FROM measurement_unit WHERE unit_key = 'kilometre';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Maili', 'Brittiläinen pituusyksikkö, noin 1,609 kilometriä.' FROM measurement_unit WHERE unit_key = 'mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Meripeninkulma', 'Yhden leveysasteminuutin matka, 1852 metriä.' FROM measurement_unit WHERE unit_key = 'nautical_mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Kilometriä tunnissa', 'Metrinen nopeusyksikkö, matka kilometreinä jaettuna ajalla tunteina.' FROM measurement_unit WHERE unit_key = 'kilometre_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Mailia tunnissa', 'Brittiläinen nopeusyksikkö, matka maileina jaettuna ajalla tunteina.' FROM measurement_unit WHERE unit_key = 'mile_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Tunti', 'Ajan yksikkö, joka on 60 minuuttia.' FROM measurement_unit WHERE unit_key = 'hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'fi', 'Minuutti', 'Ajan yksikkö, joka on 60 sekuntia.' FROM measurement_unit WHERE unit_key = 'minute';

-- ---------- Swedish (sv) -----------------------------------------------------
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Celsius', 'Vatten fryser vid 0 grader och kokar vid 100 grader vid havsnivå.' FROM measurement_unit WHERE unit_key = 'celsius';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Fahrenheit', 'Skala som främst används i USA, där vatten fryser vid 32 grader.' FROM measurement_unit WHERE unit_key = 'fahrenheit';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Kelvin', 'SI-basenhet för temperatur, räknad från den absoluta nollpunkten.' FROM measurement_unit WHERE unit_key = 'kelvin';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Kilometer', 'Metrisk längdenhet som motsvarar 1000 meter.' FROM measurement_unit WHERE unit_key = 'kilometre';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Engelsk mil', 'Imperiell längdenhet, cirka 1,609 kilometer.' FROM measurement_unit WHERE unit_key = 'mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Nautisk mil', 'Avståndet för en breddgradsminut, 1852 meter.' FROM measurement_unit WHERE unit_key = 'nautical_mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Kilometer i timmen', 'Metrisk hastighetsenhet, sträcka i kilometer delat med tid i timmar.' FROM measurement_unit WHERE unit_key = 'kilometre_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Engelska mil i timmen', 'Imperiell hastighetsenhet, sträcka i mil delat med tid i timmar.' FROM measurement_unit WHERE unit_key = 'mile_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Timme', 'Tidsenhet som motsvarar 60 minuter.' FROM measurement_unit WHERE unit_key = 'hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'sv', 'Minut', 'Tidsenhet som motsvarar 60 sekunder.' FROM measurement_unit WHERE unit_key = 'minute';

-- ---------- Japanese (ja) - non-Latin script, stored as UTF-8 ----------------
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '摂氏', '海面の高さでは水は0度で凍り、100度で沸騰します。' FROM measurement_unit WHERE unit_key = 'celsius';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '華氏', '主にアメリカで使われる目盛りで、水は32度で凍ります。' FROM measurement_unit WHERE unit_key = 'fahrenheit';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', 'ケルビン', '絶対零度から数える温度のSI基本単位です。' FROM measurement_unit WHERE unit_key = 'kelvin';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', 'キロメートル', '1000メートルに等しいメートル法の距離の単位です。' FROM measurement_unit WHERE unit_key = 'kilometre';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', 'マイル', '約1.609キロメートルに等しいヤード・ポンド法の距離の単位です。' FROM measurement_unit WHERE unit_key = 'mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '時速キロメートル', 'メートル法の速さの単位で、距離をキロメートル、時間を時で割った値です。' FROM measurement_unit WHERE unit_key = 'kilometre_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '時速マイル', 'ヤード・ポンド法の速さの単位で、距離をマイル、時間を時で割った値です。' FROM measurement_unit WHERE unit_key = 'mile_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '時間', '60分に等しい時間の単位です。' FROM measurement_unit WHERE unit_key = 'hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ja', '分', '60秒に等しい時間の単位です。' FROM measurement_unit WHERE unit_key = 'minute';

-- ---------- Arabic (ar) - non-Latin script, right to left -------------------
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'مئوية', 'يتجمد الماء عند صفر درجة ويغلي عند 100 درجة عند مستوى سطح البحر.' FROM measurement_unit WHERE unit_key = 'celsius';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'فهرنهايت', 'مقياس يستخدم أساسا في الولايات المتحدة، حيث يتجمد الماء عند 32 درجة.' FROM measurement_unit WHERE unit_key = 'fahrenheit';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'كلفن', 'وحدة القياس الأساسية للحرارة في النظام الدولي، وتحسب من الصفر المطلق.' FROM measurement_unit WHERE unit_key = 'kelvin';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'كيلومتر', 'وحدة مسافة مترية تساوي 1000 متر.' FROM measurement_unit WHERE unit_key = 'kilometre';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'ميل', 'وحدة مسافة إمبراطورية تساوي نحو 1.609 كيلومتر.' FROM measurement_unit WHERE unit_key = 'mile';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'كيلومتر في الساعة', 'وحدة سرعة مترية، المسافة بالكيلومترات مقسومة على الزمن بالساعات.' FROM measurement_unit WHERE unit_key = 'kilometre_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'ميل في الساعة', 'وحدة سرعة إمبراطورية، المسافة بالأميال مقسومة على الزمن بالساعات.' FROM measurement_unit WHERE unit_key = 'mile_per_hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'ساعة', 'وحدة زمن تساوي 60 دقيقة.' FROM measurement_unit WHERE unit_key = 'hour';
INSERT INTO unit_translation (unit_id, lang, name, description)
SELECT id, 'ar', 'دقيقة', 'وحدة زمن تساوي 60 ثانية.' FROM measurement_unit WHERE unit_key = 'minute';
