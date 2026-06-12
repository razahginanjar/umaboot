spring:
  application:
    name: ${projectName}
  datasource:
    url: ${r"${SPRING_DATASOURCE_URL:"}${jdbcUrl}${r"}"}
    username: ${r"${SPRING_DATASOURCE_USERNAME:"}${jdbcUsername}${r"}"}
    password: ${r"${SPRING_DATASOURCE_PASSWORD:"}${jdbcPassword}${r"}"}
    driver-class-name: ${jdbcDriverClass}
<#if isJpa>
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
<#if dbIsPostgres || dbIsSqlserver>
        default_schema: ${schemaName}
</#if>
<#if dbIsSqlite>
        # SQLite has no built-in Hibernate dialect; Umaboot adds a compatible
        # community dialect dependency for the selected Spring Boot line.
        dialect: ${sqliteHibernateDialectClass}
</#if>
        format_sql: true
    open-in-view: false
</#if>
  jackson:
    serialization:
      write-dates-as-timestamps: false

<#if isMyBatis && myBatisXml>
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
<#elseif isMyBatis>
mybatis:
  configuration:
    map-underscore-to-camel-case: true
</#if>

server:
  port: 8080

<#if securityJwt>
umaboot:
  security:
    jwt:
      # SECURITY: replace this with a real secret before deploying.
      # Use SPRING_SECURITY_JWT_SECRET env var or your secret manager.
      secret: ${r"${SPRING_SECURITY_JWT_SECRET:"}${jwtSecret}${r"}"}
      expiration-minutes: ${jwtExpirationMinutes?c}
      header: ${jwtHeader}
      prefix: "${jwtPrefix}"

</#if>
logging:
  level:
<#if isJpa>
    org.hibernate.SQL: DEBUG
<#elseif isMyBatis>
    ${basePackage}.mapper: DEBUG
<#else>
    org.jooq.tools.LoggerListener: DEBUG
</#if>
