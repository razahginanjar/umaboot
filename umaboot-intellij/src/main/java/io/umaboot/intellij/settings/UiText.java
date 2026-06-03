package io.umaboot.intellij.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;

import java.util.Map;

public final class UiText {

    static final String PROPERTY_KEY = "umaboot.ui.language";
    public static final int MAX_DISPLAY_CHARS = 45;

    private UiText() {}

    public enum Language {
        ENGLISH("en", "English"),
        INDONESIAN("id", "Bahasa Indonesia"),
        JAPANESE("ja", "日本語");

        private final String id;
        private final String displayName;

        Language(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        @Override
        public String toString() {
            return displayName;
        }

        static Language fromId(String id) {
            for (Language language : values()) {
                if (language.id.equalsIgnoreCase(id)) {
                    return language;
                }
            }
            return ENGLISH;
        }
    }

    public static Language load(Project project) {
        if (project == null) {
            return Language.ENGLISH;
        }
        String id = PropertiesComponent.getInstance(project).getValue(PROPERTY_KEY, Language.ENGLISH.id());
        return Language.fromId(id);
    }

    public static void save(Project project, Language language) {
        PropertiesComponent.getInstance(project).setValue(PROPERTY_KEY, language.id(), Language.ENGLISH.id());
    }

    public static String text(Language language, String key) {
        if (key == null || key.isEmpty() || language == Language.ENGLISH) {
            return key == null ? "" : key;
        }
        return switch (language) {
            case INDONESIAN -> ID.getOrDefault(key, key);
            case JAPANESE -> JA.getOrDefault(key, key);
            case ENGLISH -> key;
        };
    }

    public static String format(Language language, String key, Object... args) {
        String template = text(language, key);
        if (args == null || args.length == 0) {
            return template;
        }
        return String.format(template, args);
    }

    public static String display(String value) {
        if (value == null) {
            return "";
        }
        int length = value.codePointCount(0, value.length());
        if (length <= MAX_DISPLAY_CHARS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, MAX_DISPLAY_CHARS);
        return value.substring(0, end) + "...";
    }

    public static String help(Language language, String key) {
        HelpEntry entry = HELP.get(key);
        String label = text(language, key == null || key.isBlank() ? "Setting" : key);
        if (entry == null) {
            return format(language, "No help is available for %s yet.", label);
        }
        return format(language, "Attribute: %s", label)
                + "\n\n" + text(language, "Description:") + " " + entry.description()
                + "\n" + text(language, "Example:") + " " + entry.example();
    }

    private record HelpEntry(String description, String example) {}

    private static final Map<String, HelpEntry> HELP = Map.ofEntries(
            Map.entry("Language:", new HelpEntry("Controls the language used by the plugin UI for this IntelliJ project.", "English, Bahasa Indonesia, or Japanese.")),
            Map.entry("Database type:", new HelpEntry("Selects the database dialect used for JDBC URLs, table introspection, scripts, generated dependencies, Docker, CI, and migrations.", "mysql")),
            Map.entry("Source:", new HelpEntry("Chooses how Umaboot reads the schema: build a JDBC URL from host fields, use a raw JDBC URL, or parse a SQL script file.", "Host for a live PostgreSQL database, Script for schema.sql.")),
            Map.entry("Host:", new HelpEntry("Host and port for live database access when Source is Host.", "localhost:5432")),
            Map.entry("Database:", new HelpEntry("Database name used in Host mode. For MySQL and MariaDB this is also the introspection target.", "inventory")),
            Map.entry("Parameters:", new HelpEntry("Optional JDBC query parameters. Do not start with '?'; Umaboot adds it automatically.", "sslmode=disable")),
            Map.entry("JDBC URL:", new HelpEntry("Full JDBC URL used when Source is URL. It overrides the Host and Database fields for connection details.", "jdbc:mysql://localhost:3306/inventory")),
            Map.entry("Schema file:", new HelpEntry("SQL DDL file parsed when Source is Script. Project-relative paths are kept portable in umaboot.yaml.", "src/main/resources/schema.sql")),
            Map.entry("Schema:", new HelpEntry("Schema name used by PostgreSQL and SQL Server introspection. SQLite ignores this field.", "public")),
            Map.entry("Username:", new HelpEntry("Database username used for Test Connection and Refresh Tables in live database modes.", "postgres")),
            Map.entry("Password:", new HelpEntry("Database password used for Test Connection and Refresh Tables in live database modes.", "postgres")),
            Map.entry("Strip prefix from class names:", new HelpEntry("Removes a common table prefix before generating Java class names.", "tbl_users becomes User when prefix is tbl_.")),
            Map.entry("Architecture:", new HelpEntry("Controls the generated project structure and package layout.", "mvc")),
            Map.entry("Persistence:", new HelpEntry("Selects the persistence technology for repositories, dependencies, and generated tests.", "jpa")),
            Map.entry("Build tool:", new HelpEntry("Chooses the generated build files and CI commands.", "gradle")),
            Map.entry("MyBatis style:", new HelpEntry("Controls whether MyBatis SQL is generated as XML mapper files or annotations.", "xml")),
            Map.entry("Use MapStruct (JPA only)", new HelpEntry("Adds MapStruct mapper generation for JPA DTO/entity conversion.", "Enabled for JPA MVC projects.")),
            Map.entry("Base package:", new HelpEntry("Root Java package for generated source files.", "com.example.inventory")),
            Map.entry("Project name:", new HelpEntry("Application artifact/name used in build files, settings, and generated output.", "inventory-service")),
            Map.entry("Project group:", new HelpEntry("Build group ID or organization coordinate.", "com.example")),
            Map.entry("Spring Boot version:", new HelpEntry("Spring Boot version used in generated Maven or Gradle build files.", "3.3.5")),
            Map.entry("Java version:", new HelpEntry("Java language version for generated source compatibility and build configuration.", "17")),
            Map.entry("Use Lombok", new HelpEntry("Adds Lombok and uses Lombok annotations where supported by the selected Java/Spring combination.", "Enabled to generate @Getter/@Setter or constructor annotations.")),
            Map.entry("Injection style:", new HelpEntry("Controls how dependencies are injected in generated Spring components.", "constructor")),
            Map.entry("Validation style:", new HelpEntry("Controls whether request validation annotations and validation handling are generated.", "jakarta")),
            Map.entry("DTO style:", new HelpEntry("Controls whether DTOs are generated as classes or records when the Java/Spring version supports it.", "class for Java 8, record for Java 17+.")),
            Map.entry("DTO shape:", new HelpEntry("Controls whether request and response DTOs are separate types or a single shared DTO.", "separate")),
            Map.entry("Exception style:", new HelpEntry("Controls generated API error response style.", "problemdetail for Spring Boot 3, envelope for Spring Boot 2.")),
            Map.entry("Detect audit fields (created_at / updated_at / created_by / updated_by)", new HelpEntry("Detects standard audit columns and maps them into generated models instead of treating them as ordinary business fields.", "created_at and updated_by are recognized as audit fields.")),
            Map.entry("Detect soft delete (deleted_at / is_deleted)", new HelpEntry("Detects soft-delete columns and adjusts generated behavior to avoid hard-delete assumptions.", "is_deleted")),
            Map.entry("Emit Dockerfile + docker-compose.yml", new HelpEntry("Generates container files for the application and selected database.", "Dockerfile plus docker-compose.yml for MySQL.")),
            Map.entry("CI:", new HelpEntry("Generates CI workflow files for the selected provider, or disables CI generation.", "github")),
            Map.entry("Logging:", new HelpEntry("Controls generated logging format.", "json")),
            Map.entry("Add correlation-id filter (X-Correlation-Id -> MDC)", new HelpEntry("Adds a request filter that copies X-Correlation-Id into MDC for traceable logs.", "Header X-Correlation-Id=order-123.")),
            Map.entry("Generate integration tests (@SpringBootTest + Testcontainers)", new HelpEntry("Generates Spring Boot integration tests backed by Testcontainers for the selected database.", "Enabled for repository/controller integration tests.")),
            Map.entry("Migrations:", new HelpEntry("Controls database migration support in the generated project.", "flyway")),
            Map.entry("Pagination:", new HelpEntry("Controls generated list endpoint pagination style.", "offset")),
            Map.entry("Security:", new HelpEntry("Controls generated Spring Security setup.", "jwt")),
            Map.entry("OpenAPI style:", new HelpEntry("Controls generated OpenAPI support.", "annotation")),
            Map.entry("App config format:", new HelpEntry("Chooses whether application configuration is generated as YAML or properties.", "yaml")),
            Map.entry("Output mode:", new HelpEntry("Controls whether generation creates a standalone project or overlays files into the current project.", "standalone")),
            Map.entry("Use project directory (where umaboot.yaml lives)", new HelpEntry("Writes generated files next to umaboot.yaml instead of using a separate output directory.", "Enabled when outputDir should be '.'.")),
            Map.entry("Output dir:", new HelpEntry("Directory where generated files are written when project-directory output is disabled.", "generated/inventory-service")),
            Map.entry("Table:", new HelpEntry("The database table currently being customized.", "users")),
            Map.entry("Class name (override):", new HelpEntry("Optional Java class name override for this table.", "AccountUser"))
    );

    private static final Map<String, String> ID = Map.ofEntries(
            Map.entry("View", "Tampilan"),
            Map.entry("Language:", "Bahasa:"),
            Map.entry("Connection", "Koneksi"),
            Map.entry("Tables to generate", "Tabel yang dibuat"),
            Map.entry("Generation", "Generasi"),
            Map.entry("Database type:", "Jenis database:"),
            Map.entry("Source:", "Sumber:"),
            Map.entry("Host", "Host"),
            Map.entry("URL", "URL"),
            Map.entry("Script", "Skrip"),
            Map.entry("Host:", "Host:"),
            Map.entry("Database:", "Database:"),
            Map.entry("Parameters:", "Parameter:"),
            Map.entry("JDBC URL:", "URL JDBC:"),
            Map.entry("Schema file:", "File skema:"),
            Map.entry("Use Refresh Tables below to parse the file.", "Gunakan Muat Ulang Tabel di bawah untuk membaca file."),
            Map.entry("Schema:", "Skema:"),
            Map.entry("Username:", "Username:"),
            Map.entry("Password:", "Password:"),
            Map.entry("Test Connection", "Uji Koneksi"),
            Map.entry("Browse...", "Telusuri..."),
            Map.entry("Refresh Tables", "Muat Ulang Tabel"),
            Map.entry("Strip prefix from class names:", "Hapus prefiks dari nama class:"),
            Map.entry("Architecture:", "Arsitektur:"),
            Map.entry("Persistence:", "Persistensi:"),
            Map.entry("Build tool:", "Build tool:"),
            Map.entry("MyBatis style:", "Style MyBatis:"),
            Map.entry("Use MapStruct (JPA only)", "Gunakan MapStruct (hanya JPA)"),
            Map.entry("Base package:", "Base package:"),
            Map.entry("Project name:", "Nama project:"),
            Map.entry("Project group:", "Group project:"),
            Map.entry("Spring Boot version:", "Versi Spring Boot:"),
            Map.entry("Java version:", "Versi Java:"),
            Map.entry("Use Lombok", "Gunakan Lombok"),
            Map.entry("Injection style:", "Style injection:"),
            Map.entry("Validation style:", "Style validasi:"),
            Map.entry("DTO style:", "Style DTO:"),
            Map.entry("DTO shape:", "Bentuk DTO:"),
            Map.entry("Exception style:", "Style exception:"),
            Map.entry("Detect audit fields (created_at / updated_at / created_by / updated_by)", "Deteksi field audit (created_at / updated_at / created_by / updated_by)"),
            Map.entry("Detect soft delete (deleted_at / is_deleted)", "Deteksi soft delete (deleted_at / is_deleted)"),
            Map.entry("Emit Dockerfile + docker-compose.yml", "Buat Dockerfile + docker-compose.yml"),
            Map.entry("CI:", "CI:"),
            Map.entry("Logging:", "Logging:"),
            Map.entry("Add correlation-id filter (X-Correlation-Id -> MDC)", "Tambahkan filter correlation-id (X-Correlation-Id -> MDC)"),
            Map.entry("Generate integration tests (@SpringBootTest + Testcontainers)", "Buat integration test (@SpringBootTest + Testcontainers)"),
            Map.entry("Migrations:", "Migrasi:"),
            Map.entry("Pagination:", "Paginasi:"),
            Map.entry("Security:", "Security:"),
            Map.entry("OpenAPI style:", "Style OpenAPI:"),
            Map.entry("App config format:", "Format config app:"),
            Map.entry("Output mode:", "Mode output:"),
            Map.entry("Use project directory (where umaboot.yaml lives)", "Gunakan direktori project (lokasi umaboot.yaml)"),
            Map.entry("Output dir:", "Direktori output:"),
            Map.entry("Click Refresh Tables to load from the database", "Klik Muat Ulang Tabel untuk memuat dari database"),
            Map.entry("Help", "Bantuan"),
            Map.entry("Setting", "Pengaturan"),
            Map.entry("Attribute: %s", "Atribut: %s"),
            Map.entry("Description:", "Deskripsi:"),
            Map.entry("Example:", "Contoh:"),
            Map.entry("No help is available for %s yet.", "Bantuan untuk %s belum tersedia."),
            Map.entry("Testing...", "Menguji..."),
            Map.entry("Invalid form: %s", "Form tidak valid: %s"),
            Map.entry("Connected: %s", "Terhubung: %s"),
            Map.entry("Connection invalid", "Koneksi tidak valid"),
            Map.entry("Failed: %s", "Gagal: %s"),
            Map.entry("Connected: %s - database is empty; fill in before Apply / Refresh Tables", "Terhubung: %s - database kosong; isi sebelum Terapkan / Muat Ulang Tabel"),
            Map.entry("Connected: %s - schema is empty; fill in before Apply / Refresh Tables", "Terhubung: %s - skema kosong; isi sebelum Terapkan / Muat Ulang Tabel"),
            Map.entry("Parameters field must not start with '?' - the program adds it automatically", "Field parameter tidak boleh diawali '?' - program menambahkannya otomatis"),
            Map.entry("Reading schema...", "Membaca skema..."),
            Map.entry("Pick a .sql file first", "Pilih file .sql terlebih dahulu"),
            Map.entry("Cannot read: %s", "Tidak dapat membaca: %s"),
            Map.entry("%d tables parsed from %s", "%d tabel dibaca dari %s"),
            Map.entry("Parse failed: %s", "Parsing gagal: %s"),
            Map.entry("Please fill in the database before refreshing tables", "Isi database sebelum memuat ulang tabel"),
            Map.entry("Please fill in the schema before refreshing tables", "Isi skema sebelum memuat ulang tabel"),
            Map.entry("%d tables found", "%d tabel ditemukan"),
            Map.entry("Run Refresh Tables first to populate column metadata", "Jalankan Muat Ulang Tabel dulu untuk memuat metadata kolom"),
            Map.entry("Table '%s' not in last introspection", "Tabel '%s' tidak ada di introspeksi terakhir"),
            Map.entry("Failed to read %s: %s", "Gagal membaca %s: %s"),
            Map.entry("%d tables in include list", "%d tabel dalam daftar include"),
            Map.entry("Select Schema SQL File", "Pilih File SQL Skema"),
            Map.entry("Configuration", "Konfigurasi"),
            Map.entry("Apply", "Terapkan"),
            Map.entry("Generate", "Generate"),
            Map.entry("Preview / Merge", "Preview / Merge"),
            Map.entry("Summary Log", "Log Ringkas"),
            Map.entry("Detail Log", "Log Detail"),
            Map.entry("Copy", "Salin"),
            Map.entry("Clear", "Bersihkan"),
            Map.entry("Open in Settings", "Buka di Settings"),
            Map.entry("Write the form values back to umaboot.yaml", "Tulis nilai form kembali ke umaboot.yaml"),
            Map.entry("Run Umaboot against the current configuration", "Jalankan Umaboot dengan konfigurasi saat ini"),
            Map.entry("Preview generated changes before writing files", "Preview perubahan sebelum menulis file"),
            Map.entry("Show concise Umaboot process log", "Tampilkan log proses Umaboot ringkas"),
            Map.entry("Show detailed Umaboot process log", "Tampilkan log proses Umaboot detail"),
            Map.entry("Open this panel inside the IDE Settings dialog", "Buka panel ini di dialog Settings IDE"),
            Map.entry("Saved umaboot.yaml", "umaboot.yaml tersimpan"),
            Map.entry("Failed to save: ", "Gagal menyimpan: "),
            Map.entry("Failed to save umaboot.yaml: %s", "Gagal menyimpan umaboot.yaml: %s"),
            Map.entry("Umaboot: Generate", "Umaboot: Generate"),
            Map.entry("Run Umaboot against the project's umaboot.yaml", "Jalankan Umaboot dengan umaboot.yaml project"),
            Map.entry("Run Umaboot against this project's umaboot.yaml", "Jalankan Umaboot dengan umaboot.yaml project ini"),
            Map.entry("Add a umaboot.yaml to the project root to enable", "Tambahkan umaboot.yaml di root project untuk mengaktifkan"),
            Map.entry("Umaboot: open a project first.", "Umaboot: buka project terlebih dahulu."),
            Map.entry("Umaboot: Generating", "Umaboot: Generating"),
            Map.entry("Introspecting database and rendering project...", "Membaca database dan merender project..."),
            Map.entry("Umaboot: generated %d files in %s [%s/%s, %s%s]", "Umaboot: menghasilkan %d file di %s [%s/%s, %s%s]"),
            Map.entry(" (auto)", " (otomatis)"),
            Map.entry("Umaboot failed: %s. See detail log for diagnostics.", "Umaboot gagal: %s. Lihat log detail untuk diagnostik."),
            Map.entry("Umaboot preview failed: %s. See detail log for diagnostics.", "Preview Umaboot gagal: %s. Lihat log detail untuk diagnostik."),
            Map.entry("Umaboot: Show Summary Log", "Umaboot: Tampilkan Log Ringkas"),
            Map.entry("Umaboot: Show Detail Log", "Umaboot: Tampilkan Log Detail"),
            Map.entry("Umaboot failed: %s", "Umaboot gagal: %s"),
            Map.entry("Run Umaboot: Generate", "Jalankan Umaboot: Generate"),
            Map.entry("Customize table - ", "Kustomisasi tabel - "),
            Map.entry("Table:", "Tabel:"),
            Map.entry("Class name (override):", "Nama class (override):"),
            Map.entry("Leave empty to use the default (singularize + PascalCase, with classNameStripPrefix applied).", "Kosongkan untuk memakai default (singularize + PascalCase, dengan classNameStripPrefix)."),
            Map.entry("Column", "Kolom"),
            Map.entry("DB type", "Tipe DB"),
            Map.entry("Java type", "Tipe Java"),
            Map.entry("(default)", "(default)"),
            Map.entry("Tip - '(default)' uses the JDBC-type mapping. Pick a Java type to override per column.", "Tip - '(default)' memakai mapping tipe JDBC. Pilih tipe Java untuk override per kolom."),
            Map.entry("Save", "Simpan")
    );

    private static final Map<String, String> JA = Map.ofEntries(
            Map.entry("Help", "ヘルプ"),
            Map.entry("Setting", "設定"),
            Map.entry("Attribute: %s", "属性: %s"),
            Map.entry("Description:", "説明:"),
            Map.entry("Example:", "例:"),
            Map.entry("No help is available for %s yet.", "%sのヘルプはまだありません。"),
            Map.entry("View", "表示"),
            Map.entry("Language:", "言語:"),
            Map.entry("Connection", "接続"),
            Map.entry("Tables to generate", "生成するテーブル"),
            Map.entry("Generation", "生成"),
            Map.entry("Database type:", "データベース種別:"),
            Map.entry("Source:", "入力元:"),
            Map.entry("Host", "ホスト"),
            Map.entry("URL", "URL"),
            Map.entry("Script", "スクリプト"),
            Map.entry("Host:", "ホスト:"),
            Map.entry("Database:", "データベース:"),
            Map.entry("Parameters:", "パラメータ:"),
            Map.entry("JDBC URL:", "JDBC URL:"),
            Map.entry("Schema file:", "スキーマファイル:"),
            Map.entry("Use Refresh Tables below to parse the file.", "下のテーブル更新でファイルを解析します。"),
            Map.entry("Schema:", "スキーマ:"),
            Map.entry("Username:", "ユーザー名:"),
            Map.entry("Password:", "パスワード:"),
            Map.entry("Test Connection", "接続テスト"),
            Map.entry("Browse...", "参照..."),
            Map.entry("Refresh Tables", "テーブルを更新"),
            Map.entry("Strip prefix from class names:", "クラス名から接頭辞を削除:"),
            Map.entry("Architecture:", "アーキテクチャ:"),
            Map.entry("Persistence:", "永続化:"),
            Map.entry("Build tool:", "ビルドツール:"),
            Map.entry("MyBatis style:", "MyBatisスタイル:"),
            Map.entry("Use MapStruct (JPA only)", "MapStructを使用 (JPAのみ)"),
            Map.entry("Base package:", "ベースパッケージ:"),
            Map.entry("Project name:", "プロジェクト名:"),
            Map.entry("Project group:", "プロジェクトグループ:"),
            Map.entry("Spring Boot version:", "Spring Bootバージョン:"),
            Map.entry("Java version:", "Javaバージョン:"),
            Map.entry("Use Lombok", "Lombokを使用"),
            Map.entry("Injection style:", "インジェクション方式:"),
            Map.entry("Validation style:", "バリデーション方式:"),
            Map.entry("DTO style:", "DTOスタイル:"),
            Map.entry("DTO shape:", "DTO構成:"),
            Map.entry("Exception style:", "例外スタイル:"),
            Map.entry("Detect audit fields (created_at / updated_at / created_by / updated_by)", "監査フィールドを検出 (created_at / updated_at / created_by / updated_by)"),
            Map.entry("Detect soft delete (deleted_at / is_deleted)", "論理削除を検出 (deleted_at / is_deleted)"),
            Map.entry("Emit Dockerfile + docker-compose.yml", "Dockerfile + docker-compose.ymlを生成"),
            Map.entry("CI:", "CI:"),
            Map.entry("Logging:", "ログ:"),
            Map.entry("Add correlation-id filter (X-Correlation-Id -> MDC)", "correlation-idフィルターを追加 (X-Correlation-Id -> MDC)"),
            Map.entry("Generate integration tests (@SpringBootTest + Testcontainers)", "統合テストを生成 (@SpringBootTest + Testcontainers)"),
            Map.entry("Migrations:", "マイグレーション:"),
            Map.entry("Pagination:", "ページング:"),
            Map.entry("Security:", "セキュリティ:"),
            Map.entry("OpenAPI style:", "OpenAPIスタイル:"),
            Map.entry("App config format:", "アプリ設定形式:"),
            Map.entry("Output mode:", "出力モード:"),
            Map.entry("Use project directory (where umaboot.yaml lives)", "プロジェクトディレクトリを使用 (umaboot.yamlの場所)"),
            Map.entry("Output dir:", "出力先:"),
            Map.entry("Click Refresh Tables to load from the database", "テーブル更新をクリックしてデータベースから読み込みます"),
            Map.entry("Testing...", "テスト中..."),
            Map.entry("Invalid form: %s", "フォームが無効です: %s"),
            Map.entry("Connected: %s", "接続しました: %s"),
            Map.entry("Connection invalid", "接続が無効です"),
            Map.entry("Failed: %s", "失敗しました: %s"),
            Map.entry("Connected: %s - database is empty; fill in before Apply / Refresh Tables", "接続しました: %s - データベースが空です。適用/テーブル更新の前に入力してください"),
            Map.entry("Connected: %s - schema is empty; fill in before Apply / Refresh Tables", "接続しました: %s - スキーマが空です。適用/テーブル更新の前に入力してください"),
            Map.entry("Parameters field must not start with '?' - the program adds it automatically", "パラメータ欄は '?' で始めないでください - プログラムが自動で追加します"),
            Map.entry("Reading schema...", "スキーマを読み込み中..."),
            Map.entry("Pick a .sql file first", ".sqlファイルを先に選択してください"),
            Map.entry("Cannot read: %s", "読み込めません: %s"),
            Map.entry("%d tables parsed from %s", "%d個のテーブルを%sから解析しました"),
            Map.entry("Parse failed: %s", "解析に失敗しました: %s"),
            Map.entry("Please fill in the database before refreshing tables", "テーブル更新の前にデータベースを入力してください"),
            Map.entry("Please fill in the schema before refreshing tables", "テーブル更新の前にスキーマを入力してください"),
            Map.entry("%d tables found", "%d個のテーブルが見つかりました"),
            Map.entry("Run Refresh Tables first to populate column metadata", "先にテーブル更新を実行してカラムメタデータを読み込んでください"),
            Map.entry("Table '%s' not in last introspection", "テーブル'%s'は最後のイントロスペクションにありません"),
            Map.entry("Failed to read %s: %s", "%sの読み込みに失敗しました: %s"),
            Map.entry("%d tables in include list", "includeリストに%d個のテーブルがあります"),
            Map.entry("Select Schema SQL File", "スキーマSQLファイルを選択"),
            Map.entry("Configuration", "設定"),
            Map.entry("Apply", "適用"),
            Map.entry("Generate", "生成"),
            Map.entry("Preview / Merge", "プレビュー / マージ"),
            Map.entry("Summary Log", "概要ログ"),
            Map.entry("Detail Log", "詳細ログ"),
            Map.entry("Copy", "コピー"),
            Map.entry("Clear", "クリア"),
            Map.entry("Open in Settings", "設定で開く"),
            Map.entry("Write the form values back to umaboot.yaml", "フォームの値をumaboot.yamlへ書き戻します"),
            Map.entry("Run Umaboot against the current configuration", "現在の設定でUmabootを実行します"),
            Map.entry("Preview generated changes before writing files", "ファイルを書き込む前に生成差分を確認します"),
            Map.entry("Show concise Umaboot process log", "Umabootの概要ログを表示します"),
            Map.entry("Show detailed Umaboot process log", "Umabootの詳細ログを表示します"),
            Map.entry("Open this panel inside the IDE Settings dialog", "IDE設定ダイアログ内でこのパネルを開きます"),
            Map.entry("Saved umaboot.yaml", "umaboot.yamlを保存しました"),
            Map.entry("Failed to save: ", "保存に失敗しました: "),
            Map.entry("Failed to save umaboot.yaml: %s", "umaboot.yamlの保存に失敗しました: %s"),
            Map.entry("Umaboot: Generate", "Umaboot: 生成"),
            Map.entry("Run Umaboot against the project's umaboot.yaml", "プロジェクトのumaboot.yamlでUmabootを実行します"),
            Map.entry("Run Umaboot against this project's umaboot.yaml", "このプロジェクトのumaboot.yamlでUmabootを実行します"),
            Map.entry("Add a umaboot.yaml to the project root to enable", "有効にするにはプロジェクトルートにumaboot.yamlを追加してください"),
            Map.entry("Umaboot: open a project first.", "Umaboot: 先にプロジェクトを開いてください。"),
            Map.entry("Umaboot: Generating", "Umaboot: 生成中"),
            Map.entry("Introspecting database and rendering project...", "データベースを解析してプロジェクトをレンダリングしています..."),
            Map.entry("Umaboot: generated %d files in %s [%s/%s, %s%s]", "Umaboot: %d個のファイルを%sに生成しました [%s/%s, %s%s]"),
            Map.entry(" (auto)", " (自動)"),
            Map.entry("Umaboot failed: %s. See detail log for diagnostics.", "Umabootが失敗しました: %s。診断には詳細ログを確認してください。"),
            Map.entry("Umaboot preview failed: %s. See detail log for diagnostics.", "Umabootプレビューが失敗しました: %s。診断には詳細ログを確認してください。"),
            Map.entry("Umaboot: Show Summary Log", "Umaboot: 概要ログを表示"),
            Map.entry("Umaboot: Show Detail Log", "Umaboot: 詳細ログを表示"),
            Map.entry("Umaboot failed: %s", "Umabootが失敗しました: %s"),
            Map.entry("Run Umaboot: Generate", "Umabootを実行: 生成"),
            Map.entry("Customize table - ", "テーブルをカスタマイズ - "),
            Map.entry("Table:", "テーブル:"),
            Map.entry("Class name (override):", "クラス名 (上書き):"),
            Map.entry("Leave empty to use the default (singularize + PascalCase, with classNameStripPrefix applied).", "空のままにすると既定値を使います (単数化 + PascalCase、classNameStripPrefix適用)。"),
            Map.entry("Column", "カラム"),
            Map.entry("DB type", "DB型"),
            Map.entry("Java type", "Java型"),
            Map.entry("(default)", "(既定)"),
            Map.entry("Tip - '(default)' uses the JDBC-type mapping. Pick a Java type to override per column.", "ヒント - '(既定)' はJDBC型マッピングを使います。カラムごとにJava型を選択できます。"),
            Map.entry("Save", "保存")
    );
}
