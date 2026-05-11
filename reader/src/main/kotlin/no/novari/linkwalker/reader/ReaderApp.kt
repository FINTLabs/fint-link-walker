package no.novari.linkwalker.reader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan(basePackages = ["no.novari.linkwalker", "no.novari.metamodel"])
@ConfigurationPropertiesScan(basePackages = ["no.novari.linkwalker"])
@EnableScheduling
class ReaderApp

fun main(args: Array<String>) {
    runApplication<ReaderApp>(*args)
}
