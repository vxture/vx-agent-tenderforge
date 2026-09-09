// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-10
package com.td.czghagent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 集成测试的数据库底座：**真 Postgres，跑生产用的那一份 DDL，以生产用的那个
 * 受限角色连接**。
 *
 * <p>三件事都是有意的，每一件都对应一类只在生产出现的故障：
 *
 * <ul>
 *   <li><strong>真 Postgres 而不是嵌入式替身</strong>。基线用了 schema、UUID
 *       类型、TIMESTAMPTZ 与 {@code gen_random_uuid()}，H2 哪个兼容模式都跑不了。
 *       更要紧的是「测试跑一种引擎、生产跑另一种」这件事本身——两边都成立的
 *       SQL 才叫验过，而只在一边成立的那些，暴露的地方是生产。</li>
 *   <li><strong>跑 {@code deploy/database/ddl/} 里那份文件本身</strong>，由
 *       maven-resources-plugin 复制进测试 classpath，不是抄一份进 test/resources。
 *       抄的那份会过期，而过期的表现是测试全绿、生产建不出表。</li>
 *   <li><strong>以 {@code tenderforge_svc} 连接，不是库 owner</strong>。列级
 *       UPDATE 白名单少给一列，以 owner 连库时完全不显形——那个 permission
 *       denied 只会在生产出现。以受限角色跑测试，这类缺口在这里就红。</li>
 * </ul>
 *
 * <p>容器是 JVM 内单例（静态字段 + 静态块启动），四个测试类共用一个。
 * 各测试类清理自己用到的表，互不干扰；不做全库 truncate，因为那会把
 * 某个测试自己在 {@code @BeforeEach} 之前铺好的数据一起冲掉。
 */
public abstract class PostgresBackedTest {

    /** 与 compose、db-init 用的镜像保持同一个大版本——测试验的引擎必须是要上线的那个。 */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18-alpine");

    /** 服务角色在测试里的口令。只活在这个容器里，容器随 JVM 一起消失。 */
    private static final String SERVICE_PASSWORD = "test-only";

    /** 与 97_service_role.sql 给服务角色设的 search_path 一致。 */
    private static final String SEARCH_PATH = "bid,vx_provision,local_authz,local_usage,public";

    /**
     * 连接串**只在这里拼一次**。
     *
     * <p>应用连接与夹具连接必须带同一套参数，尤其是
     * {@code stringtype=unspecified}——少了它，任何 `WHERE id = ?` 都会报
     * 「operator does not exist: uuid = character varying」，因为 id 列是 UUID
     * 而仓储层全用 setString 绑参。
     *
     * <p>两处各拼一遍的代价已经付过了：夹具那条漏了这个参数，症状是
     * BadSqlGrammar，而报出来的 SQL 看着完全正常——错在连接上，不在语句上。
     */
    private static String jdbcUrl() {
        return POSTGRES.getJdbcUrl()
                + "&stringtype=unspecified"
                + "&currentSchema=" + SEARCH_PATH;
    }

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("vx_tenderforge_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
        applyDdl();
    }

    /**
     * 施加三段基线，然后给服务角色一个口令。
     *
     * <p>以容器 superuser 施加——DDL 本来就该由拥有 DDL 权限的人跑，而
     * {@code tenderforge_svc} 恰恰没有这个权限（那正是 97 要保证的事）。
     */
    private static void applyDdl() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String file : List.of("00_baseline.sql", "97_service_role.sql", "98_column_locks.sql")) {
                statement.execute(read(file));
            }
            statement.execute("ALTER ROLE tenderforge_svc PASSWORD '" + SERVICE_PASSWORD + "'");
        } catch (SQLException exception) {
            throw new IllegalStateException("施加基线 DDL 失败", exception);
        }
    }

    /**
     * 给**测试夹具**用的 owner 连接，与被测应用用的那个受限连接分开。
     *
     * <p>测试有时需要写生产代码从不写的东西——比如往标题里塞前后空白，
     * 验应用会不会规范化；而生产改评分条款走的是删+插，从不 UPDATE 标题。
     * 那样的写入落在列锁白名单之外是**对的**，用受限角色去做它只会得到
     * 一个与被测行为无关的 permission denied。
     *
     * <p>所以夹具走 owner，应用自己的每一条路径仍然走受限角色——
     * 后者才是这套测试要验的东西，不能为了让夹具好写而放宽它。
     */
    protected static JdbcTemplate fixtureJdbc() {
        // currentSchema 必须显式给。`ALTER ROLE tenderforge_svc SET search_path`
        // 只对那个角色生效，owner 连过来是默认的 "$user", public——于是
        // 不带 schema 前缀的表名一律 relation does not exist，而 Spring 把它
        // 和权限拒绝一样包成 BadSqlGrammar，两者从异常类型上分不出来。
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }

    private static String read(String name) {
        String path = "/ddl/" + name;
        try (InputStream stream = PostgresBackedTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                // 找不到 = maven-resources-plugin 那一步没跑或 DDL 搬了家。
                // 说清楚，否则下一个人看到的是一串建表失败。
                throw new IllegalStateException(
                        path + " 不在测试 classpath 上。它由 project-name-start/pom.xml 的 "
                                + "copy-ddl-for-tests 从 deploy/database/ddl/ 复制而来——"
                                + "DDL 搬家了就要同步改那一段。");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 " + path + " 失败", exception);
        }
    }

    /**
     * 指向容器，并且**以受限角色连接**。
     *
     * <p>连接参数由 {@link #jdbcUrl()} 统一给出，与夹具连接同源——
     * 测试与生产必须用同一套参数，否则这里验过的东西在那边不成立。
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresBackedTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> "tenderforge_svc");
        registry.add("spring.datasource.password", () -> SERVICE_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
