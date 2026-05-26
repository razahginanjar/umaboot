spring.application.name=${projectName}
spring.datasource.url=${r"${SPRING_DATASOURCE_URL:"}${jdbcUrl}${r"}"}
spring.datasource.username=${r"${SPRING_DATASOURCE_USERNAME:"}${jdbcUsername}${r"}"}
spring.datasource.password=${r"${SPRING_DATASOURCE_PASSWORD:"}${jdbcPassword}${r"}"}
spring.datasource.driver-class-name=${jdbcDriverClass}
<#if isJpa>
spring.jpa.hibernate.ddl-auto=validate
<#if dbIsPostgres>
spring.jpa.properties.hibernate.default_schema=${schemaName}
</#if>
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
</#if>
spring.jackson.serialization.write-dates-as-timestamps=false

<#if isMyBatis && myBatisXml>
mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.configuration.map-underscore-to-camel-case=true
<#elseif isMyBatis>
mybatis.configuration.map-underscore-to-camel-case=true
</#if>

server.port=8080

<#if securityJwt>
umaboot.security.jwt.secret=${r"${SPRING_SECURITY_JWT_SECRET:"}${jwtSecret}${r"}"}
umaboot.security.jwt.expiration-minutes=${jwtExpirationMinutes?c}
umaboot.security.jwt.header=${jwtHeader}
umaboot.security.jwt.prefix=${jwtPrefix}

</#if>
<#if isJpa>
logging.level.org.hibernate.SQL=DEBUG
<#else>
logging.level.${basePackage}.infrastructure.persistence=DEBUG
</#if>
