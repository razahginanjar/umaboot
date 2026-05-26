<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <springProperty scope="context" name="appName" source="spring.application.name" defaultValue="${projectName}"/>

<#if loggingJson>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdc>true</includeMdc>
            <customFields>{"app":"${r"${appName}"}"}</customFields>
        </encoder>
    </appender>
<#else>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
<#if loggingCorrelationId>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [cid=%X{correlationId:-}] %logger{36} - %msg%n</pattern>
<#else>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
</#if>
        </encoder>
    </appender>
</#if>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
